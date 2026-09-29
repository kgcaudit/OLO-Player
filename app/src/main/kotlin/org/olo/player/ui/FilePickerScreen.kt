package org.olo.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import org.olo.player.R

@Composable
internal fun OpenUrlDialog(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.url_title)) },
        text = {
            org.olo.player.ui.components.CpField(
                label = "주소",
                value = text,
                onValueChange = { text = it },
                placeholder = "http(s):// 또는 ftp://",
                keyboardType = KeyboardType.Uri,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onOpen(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.url_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.url_cancel)) }
        },
    )
}

@Composable
internal fun LocalMedia(onOpenMedia: (File) -> Unit, kindFilter: FileKind? = null) {
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
        FileBrowser(onOpenMedia = onOpenMedia, kindFilter = kindFilter)
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
private fun FileBrowser(onOpenMedia: (File) -> Unit, kindFilter: FileKind? = null) {
    val root = remember {
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
    }
    var dir by remember { mutableStateOf(root) }

    // Folders first, then media files, each in natural name order. Anything that
    // is neither a folder nor openable media is left out. A kind filter (video or
    // sound) narrows the files while still letting every folder be walked, so the
    // "비디오"/"오디오" categories browse the tree showing only their own kind.
    val entries = remember(dir, kindFilter) {
        val children = dir.listFiles()?.toList().orEmpty()
        val folders = children.filter { it.isDirectory && it.canRead() }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        val media = children
            .filter { it.isFile && looksMedia(it.name) && (kindFilter == null || kindOf(it.name, false) == kindFilter) }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        folders + media
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            dir.path,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (dir.path != root.path && dir.parentFile != null) {
                item {
                    EntryRow(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        label = stringResource(R.string.pick_up),
                        onClick = { dir.parentFile?.let { dir = it } },
                    )
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.pick_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            items(entries, key = { it.path }) { file ->
                EntryRow(
                    icon = {
                        Icon(
                            when (kindOf(file.name, file.isDirectory)) {
                                FileKind.FOLDER -> Icons.Filled.Folder
                                FileKind.AUDIO -> Icons.Filled.MusicNote
                                else -> Icons.Filled.Movie
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    label = file.name,
                    onClick = { if (file.isDirectory) dir = file else onOpenMedia(file) },
                )
            }
        }
    }
}

/** One tappable row: an icon, then a name. Shared by the local and FTP browsers. */
@Composable
internal fun EntryRow(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
