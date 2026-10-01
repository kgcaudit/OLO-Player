package org.olo.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.olo.player.R
import org.olo.player.ftp.RemoteEntry

@Composable
internal fun OpenUrlDialog(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    OloCardDialog(
        title = stringResource(R.string.url_title),
        onDismiss = onDismiss,
        actions = {
            OloDialogButton(stringResource(R.string.url_cancel), onClick = onDismiss, primary = false)
            OloDialogButton(stringResource(R.string.url_open), onClick = { if (text.isNotBlank()) onOpen(text) })
        },
    ) {
        Spacer(Modifier.height(8.dp))
        org.olo.player.ui.components.CpField(
            label = "주소",
            value = text,
            onValueChange = { text = it },
            placeholder = "http(s):// 또는 ftp://",
            keyboardType = KeyboardType.Uri,
        )
    }
}

@Composable
internal fun LocalMedia(onOpenMedia: (File) -> Unit, onBack: () -> Unit, kindFilter: FileKind? = null) {
    val context = LocalContext.current

    val readPermissions = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    fun hasPermission(): Boolean = readPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = hasPermission() }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(readPermissions)
    }

    if (!granted) {
        PermissionPrompt(onGrant = { permissionLauncher.launch(readPermissions) })
    } else {
        FileBrowser(onOpenMedia = onOpenMedia, onBack = onBack, kindFilter = kindFilter)
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.pick_permission_needed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.pick_grant)) }
    }
}

@Composable
private fun FileBrowser(onOpenMedia: (File) -> Unit, onBack: () -> Unit, kindFilter: FileKind? = null) {
    val root = remember {
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
    }
    var dir by remember { mutableStateOf(root) }

    // The folder's children as the browser's own [RemoteEntry], so the local tree
    // shows through the very same list the network browsers use -- breadcrumb,
    // posters, the detail sheet, 보기 옵션, sort, refresh -- rather than a plainer
    // one of its own. Every file is passed (not just media) so a sidecar poster
    // beside a film is found; the list itself shows only folders and media. Paths
    // are kept relative to the storage root so the breadcrumb reads from 내부
    // 저장소 down, not from the filesystem root.
    val entries = remember(dir, kindFilter) {
        dir.listFiles()?.mapNotNull { f ->
            if (f.isDirectory && !f.canRead()) return@mapNotNull null
            RemoteEntry(
                name = f.name,
                isDirectory = f.isDirectory,
                path = f.path.removePrefix(root.path).ifEmpty { "/${f.name}" },
                modified = f.lastModified().takeIf { it > 0 },
                size = if (f.isFile) f.length() else null,
            )
        }.orEmpty()
    }

    // Back goes up a folder while below the root; at the root it leaves to the
    // local landing.
    val atRoot = dir.path == root.path
    BackHandler(enabled = atRoot || dir.parentFile != null) {
        if (!atRoot) dir.parentFile?.let { if (it.path.length >= root.path.length) dir = it } else onBack()
    }

    fun fileFor(relPath: String) = File(root.path + relPath)

    RemoteBrowseList(
        rootLabel = "내부 저장소",
        path = dir.path.removePrefix(root.path),
        entries = entries,
        loading = false,
        error = null,
        onChangeSource = onBack,
        onNavigate = { rel -> dir = if (rel == "/" || rel.isEmpty()) root else fileFor(rel) },
        onEntry = { entry ->
            val target = fileFor(entry.path)
            if (entry.isDirectory) dir = target else onOpenMedia(target)
        },
        imageUriFor = { rel -> Uri.fromFile(fileFor(rel)) },
        listFolder = { rel ->
            withContext(Dispatchers.IO) {
                fileFor(rel).listFiles()?.mapNotNull { f ->
                    if (f.isDirectory && !f.canRead()) return@mapNotNull null
                    RemoteEntry(
                        name = f.name,
                        isDirectory = f.isDirectory,
                        path = f.path.removePrefix(root.path).ifEmpty { "/${f.name}" },
                        modified = f.lastModified().takeIf { it > 0 },
                        size = if (f.isFile) f.length() else null,
                    )
                }.orEmpty()
            }
        },
        onPlayFile = { v -> onOpenMedia(fileFor(v.path)) },
        rootIcon = R.drawable.ic_tile_app,
    )
}

/**
 * The centred spinner a network browser shows while a saved server reconnects,
 * in place of the connect form. A reconnect already has every field, so flashing
 * the form on the way in is just noise; the form returns only if the connect
 * fails, so credentials can still be fixed.
 */
@Composable
internal fun NetConnecting() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.ftp_connecting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

