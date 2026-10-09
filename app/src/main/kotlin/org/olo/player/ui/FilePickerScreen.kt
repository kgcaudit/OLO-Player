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
import org.olo.player.data.SavedItem
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
internal fun LocalMedia(
    onOpenMedia: (File) -> Unit,
    onBack: () -> Unit,
    onChangeSource: () -> Unit = {},
    onGlobalSearch: (() -> Unit)? = null,
    onPlaylist: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    rootShelf: (@Composable () -> Unit)? = null,
    onIsFavorite: ((String) -> Boolean)? = null,
    onFavorite: ((SavedItem) -> Unit)? = null,
) {
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
        FileBrowser(
            onOpenMedia = onOpenMedia, onBack = onBack, onChangeSource = onChangeSource,
            onGlobalSearch = onGlobalSearch, onPlaylist = onPlaylist, onSettings = onSettings, rootShelf = rootShelf,
            onIsFavorite = onIsFavorite, onFavorite = onFavorite,
        )
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
private fun FileBrowser(
    onOpenMedia: (File) -> Unit,
    onBack: () -> Unit,
    onChangeSource: () -> Unit = {},
    onGlobalSearch: (() -> Unit)? = null,
    onPlaylist: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    rootShelf: (@Composable () -> Unit)? = null,
    onIsFavorite: ((String) -> Boolean)? = null,
    onFavorite: ((SavedItem) -> Unit)? = null,
) {
    val root = remember {
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
    }
    var dir by remember { mutableStateOf(root) }
    // 새로고침 틱: 같은 폴더를 다시 조회하려면 키가 바뀌어야 한다. onNavigate(현재경로)는
    // 같은 경로의 새 File이라 File.equals로 변화가 없어 재조회가 안 됐다(삭제한 항목이 목록에
    // 남던 원인). 이 틱을 올려 디스크를 다시 읽는다.
    var refreshTick by remember { mutableStateOf(0) }
    // 태그 편집 대상(로컬 음악 파일). ⋮ 메뉴의 '태그 편집'으로 열고, 저장 후 목록을 새로고침해
    // 바뀐 제목/커버가 바로 보이게 한다. 로컬에서만 쓰므로 네트워크 브라우저엔 없다.
    var tagEditFile by remember { mutableStateOf<File?>(null) }

    // The folder's children as the browser's own [RemoteEntry], so the local tree
    // shows through the very same list the network browsers use -- breadcrumb,
    // posters, the detail sheet, 보기 옵션, sort, refresh -- rather than a plainer
    // one of its own. Every file is passed (not just media) so a sidecar poster
    // beside a film is found; the list itself shows only folders and media. Paths
    // are kept relative to the storage root so the breadcrumb reads from 내부
    // 저장소 down, not from the filesystem root.
    val entries = remember(dir, refreshTick) {
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

    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
    RemoteBrowseList(
        rootLabel = "내부 저장소",
        path = dir.path.removePrefix(root.path),
        entries = entries,
        loading = false,
        error = null,
        onChangeSource = onChangeSource,
        onNavigate = { rel -> dir = if (rel == "/" || rel.isEmpty()) root else fileFor(rel) },
        onEntry = { entry ->
            val target = fileFor(entry.path)
            // 사라진 항목(삭제됨)을 탭하면 검은 재생창으로 넘어가지 않게 막고, 목록을 새로고침해
            // 남아 있던 항목을 치운다.
            when {
                entry.isDirectory && target.exists() -> dir = target
                !entry.isDirectory && target.exists() -> onOpenMedia(target)
                else -> refreshTick++
            }
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
        // 로컬은 재생 시점에 디스크에서 사이드카를 직접 스캔하므로 subs 인자는 쓰지 않는다.
        onPlayFile = { v, _ -> fileFor(v.path).let { if (it.exists()) onOpenMedia(it) else refreshTick++ } },
        onRefresh = { refreshTick++ },
        // 플레이어가 저장에 쓰는 키(로컬=절대 파일 경로)와 같게 -> 상세의 이어보기·자막·배속이 맞는다.
        prefKeyFor = { v -> fileFor(v.path).path },
        rootIcon = R.drawable.ic_tile_app,
        onGlobalSearch = onGlobalSearch,
        onPlaylist = onPlaylist,
        onSettings = onSettings,
        rootShelf = rootShelf,
        // 로컬 파일은 File 경로를 key로, file:// URI로 저장(열 때 File 재구성). source=기기.
        isFavorite = onIsFavorite?.let { f -> { v -> f(fileFor(v.path).path) } },
        onToggleFavorite = onFavorite?.let { f ->
            { v ->
                val file = fileFor(v.path)
                f(SavedItem(key = file.path, name = v.name, uri = Uri.fromFile(file).toString(), source = "기기", local = true))
            }
        },
        // 로컬 음악 파일만: ⋮ 메뉴 '태그 편집'으로 편집기를 연다(looksAudio 판별은 목록 쪽에서).
        onEditTags = { v -> fileFor(v.path).let { if (it.exists()) tagEditFile = it } },
    )
        // 태그 편집기(단일 파일). 저장/닫기 후 목록을 새로고침해 바뀐 제목·커버가 바로 보이게.
        tagEditFile?.let { f ->
            TagEditorHost(files = listOf(f), onClose = { tagEditFile = null; refreshTick++ })
        }
    }
}


