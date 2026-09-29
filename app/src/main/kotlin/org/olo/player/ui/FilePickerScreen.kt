package org.olo.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import org.olo.player.R

/**
 * A plain filesystem browser, the app's entry point: pick a video or a song and
 * the player opens it with the folder's other media of the same kind as a
 * playlist.
 *
 * It reads local media by java.io.File, so it asks for the storage read
 * permission first (the media-type reads on Android 13+, the single storage
 * read below that). Folders and media files show; everything else is hidden, so
 * the list is only what can be opened.
 */
@Composable
fun FilePickerScreen(onOpenMedia: (File) -> Unit) {
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Text(
                stringResource(R.string.pick_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (!granted) {
                PermissionPrompt(onGrant = { permissionLauncher.launch(readPermissions) })
            } else {
                FileBrowser(onOpenMedia = onOpenMedia)
            }
        }
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
private fun FileBrowser(onOpenMedia: (File) -> Unit) {
    val root = remember {
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
    }
    var dir by remember { mutableStateOf(root) }

    // Folders first, then media files, each in natural name order. Anything that
    // is neither a folder nor openable media is left out.
    val entries = remember(dir) {
        val children = dir.listFiles()?.toList().orEmpty()
        val folders = children.filter { it.isDirectory && it.canRead() }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        val media = children.filter { it.isFile && looksMedia(it.name) }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        folders + media
    }

    Column(Modifier.fillMaxSize()) {
        // The current path, and an "up" row when there is a parent above the root.
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
                        icon = { UpIcon() },
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
                        val kind = kindOf(file.name, file.isDirectory)
                        Icon(
                            when (kind) {
                                FileKind.FOLDER -> Icons.Filled.Folder
                                FileKind.AUDIO -> Icons.Filled.MusicNote
                                else -> Icons.Filled.Movie
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    label = file.name,
                    onClick = {
                        if (file.isDirectory) dir = file else onOpenMedia(file)
                    },
                )
            }
        }
    }
}

@Composable
private fun UpIcon() {
    Icon(
        Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EntryRow(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
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
            modifier = Modifier
                .padding(start = 16.dp)
                .background(androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}
