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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val root = remember {
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
    }
    var dir by remember { mutableStateOf(root) }
    // 새로고침 틱: 같은 폴더를 다시 조회하려면 키가 바뀌어야 한다. onNavigate(현재경로)는
    // 같은 경로의 새 File이라 File.equals로 변화가 없어 재조회가 안 됐다(삭제한 항목이 목록에
    // 남던 원인). 이 틱을 올려 디스크를 다시 읽는다.
    var refreshTick by remember { mutableStateOf(0) }
    // 작업 결과물(방금 복사/이동/이름변경한 항목)의 상대경로. 결과 폴더로 이동한 뒤 이 항목을 잠깐
    // 강조·스크롤해 "어디에 생겼는지" 눈에 바로 들어오게 한다. 몇 초 뒤 자동으로 지운다(아래 효과).
    var highlightPath by remember { mutableStateOf<String?>(null) }
    // 작업으로 폴더 내용이 바뀔 때마다 올려, 목록의 폴더 판별·포스터 캐시를 비우게 한다 -- 파일을
    // 옮기면 출발 폴더의 포스터(단일 영화 등)가 상위 그리드에 옛 모습으로 남던 걸 막는다.
    var contentVersion by remember { mutableStateOf(0) }
    // 태그 편집 대상(로컬 음악 파일). ⋮ 메뉴의 '태그 편집'으로 열고, 저장 후 목록을 새로고침해
    // 바뀐 제목/커버가 바로 보이게 한다. 로컬에서만 쓰므로 네트워크 브라우저엔 없다.
    var tagEditFile by remember { mutableStateOf<File?>(null) }
    // 기본 파일 작업(복사·이동·이름변경·삭제) 대상. ⋮ 메뉴 '파일 작업'으로 열고, 끝나면 목록 새로고침.
    var fileOpsFile by remember { mutableStateOf<File?>(null) }
    // 폴더 작업(열기·이름변경·복사·이동·삭제) 대상. ⋮ 메뉴 '폴더 작업'으로 연다.
    var folderOpsFolder by remember { mutableStateOf<File?>(null) }

    // 다중 선택: 길게 눌러 진입, 탭으로 토글. 선택은 현재 폴더의 상대경로 집합으로 든다. batchStage는
    // 하단 바에서 고른 일괄 작업의 단계(폴더 선택기/삭제 확인/권한 안내/처리 중).
    val scope = rememberCoroutineScope()
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var batchStage by remember { mutableStateOf<String?>(null) } // null/move/copy/confirmDelete/needPermission/busy
    var showNewFolder by remember { mutableStateOf(false) } // 탐색기 ⋮ '새 폴더'

    fun canWrite(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun openAllFilesSettings() {
        val u = android.net.Uri.parse("package:${context.packageName}")
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, u)) }
            .onFailure { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
    }

    fun exitSelection() { selectionMode = false; selected = emptySet() }

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
    BackHandler(enabled = selectionMode || atRoot || dir.parentFile != null) {
        when {
            selectionMode -> exitSelection()
            !atRoot -> dir.parentFile?.let { if (it.path.length >= root.path.length) dir = it }
            else -> onBack()
        }
    }

    fun fileFor(relPath: String) = File(root.path + relPath)

    // 파일·폴더 작업이 성공하면: (1) 현재 폴더를 새로고침해 바뀐/사라진 항목을 바로 반영,
    // (2) 결과물이 지금 보고 있는 폴더에 생겼으면(같은 폴더 복사·이름변경) 그 항목을 강조·스크롤,
    // (3) 완료를 토스트로 알린다. 화면을 다른 폴더로 자동 전환하지는 않는다(사용자 요청) -- 다른
    // 폴더로 옮긴 경우엔 어디로 갔는지 토스트로만 알리고, 보던 폴더에 그대로 머문다.
    fun applyOpsResult(r: OpsResult) {
        val dest = r.destDir
        // 결과물이 '지금 보고 있는 폴더'에 있을 때만 강조(다른 폴더로 옮겼으면 화면에 없으니 강조 없음).
        highlightPath = if (dest != null && r.resultName != null && dest.path == dir.path) {
            File(dest, r.resultName).path.removePrefix(root.path).ifEmpty { "/${r.resultName}" }
        } else {
            null
        }
        refreshTick++
        contentVersion++ // 폴더 판별·포스터 캐시도 비워, 옮긴 폴더 포스터가 옛 모습으로 남지 않게.
        android.widget.Toast.makeText(context, r.message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // 강조는 잠깐만 -- 2.5초 뒤 저절로 꺼진다(계속 테두리가 남아 거슬리지 않게).
    LaunchedEffect(highlightPath) {
        if (highlightPath != null) {
            kotlinx.coroutines.delay(2500)
            highlightPath = null
        }
    }

    // ── 다중 선택 일괄 작업 ─────────────────────────────────────
    // 선택한 항목들을 IO에서 하나씩 처리하고(실패 수 집계), 끝나면 선택 해제 + 새로고침 + 토스트.
    fun runBatch(label: String, op: (File) -> String?) {
        val targets = selected.map { fileFor(it) }.filter { it.exists() }
        if (targets.isEmpty()) { exitSelection(); batchStage = null; return }
        batchStage = "busy"
        scope.launch {
            val fails = withContext(Dispatchers.IO) { targets.count { op(it) != null } }
            val done = targets.size - fails
            batchStage = null
            exitSelection()
            refreshTick++
            contentVersion++
            val msg = if (fails == 0) "${done}개 ${label}했습니다." else "${done}개 ${label}, ${fails}개 실패"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // 하단 바 액션: 권한 없으면 안내, 있으면 이동/복사는 폴더 선택기, 삭제는 확인 다이얼로그.
    fun onBatchMove() { if (!canWrite()) batchStage = "needPermission" else batchStage = "move" }
    fun onBatchCopy() { if (!canWrite()) batchStage = "needPermission" else batchStage = "copy" }
    fun onBatchDelete() { if (!canWrite()) batchStage = "needPermission" else batchStage = "confirmDelete" }

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
        // 방금 복사/이동/이름변경한 항목을 강조·스크롤(작업 완료 뒤 결과물을 바로 짚어 주기).
        highlightPath = highlightPath,
        // 작업으로 폴더 내용이 바뀌면 판별·포스터 캐시를 비우게 하는 신호.
        contentVersion = contentVersion,
        // 다중 선택: 길게 눌러 진입 + 탭 토글 + 전체선택/종료. 로컬 전용(원격 브라우저는 안 넘김).
        selectionMode = selectionMode,
        selectedPaths = selected,
        onLongPressEntry = { v -> selectionMode = true; selected = selected + v.path },
        onToggleSelect = { v ->
            selected = if (v.path in selected) selected - v.path else selected + v.path
            if (selected.isEmpty()) selectionMode = false
        },
        onExitSelection = { exitSelection() },
        onSelectAll = { selected = if (selected.size >= entries.size) emptySet() else entries.map { it.path }.toSet() },
        // 플레이어가 저장에 쓰는 키(로컬=절대 파일 경로)와 같게 -> 상세의 이어보기·자막·배속이 맞는다.
        prefKeyFor = { v -> fileFor(v.path).path },
        rootIcon = R.drawable.ic_tile_app,
        onGlobalSearch = onGlobalSearch,
        onPlaylist = onPlaylist,
        onSettings = onSettings,
        // ⋮ 메뉴 '새 폴더': 권한 있으면 이름 다이얼로그, 없으면 안내.
        onNewFolder = { if (canWrite()) showNewFolder = true else batchStage = "needPermission" },
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
        // 로컬 파일: ⋮ 메뉴 '파일 작업'으로 복사·이동·이름변경·삭제. 디렉터리는 목록 쪽에서 제외됨.
        onFileOps = { v -> fileFor(v.path).let { if (it.exists() && it.isFile) fileOpsFile = it } },
        // 로컬 폴더: ⋮ 메뉴 '폴더 작업'으로 열기·이름변경·복사·이동·삭제.
        onFolderOps = { v -> fileFor(v.path).let { if (it.exists() && it.isDirectory) folderOpsFolder = it } },
        // 로컬 음악 파일의 내장 앨범아트를 썸네일로(looksAudio 판별은 목록 쪽에서). IO에서 디코드.
        audioThumbFor = { v ->
            val f = fileFor(v.path)
            if (f.exists()) withContext(Dispatchers.IO) { loadAudioThumb(f) } else null
        },
    )
        // 태그 편집기(단일 파일). 저장/닫기 후 목록을 새로고침해 바뀐 제목·커버가 바로 보이게.
        tagEditFile?.let { f ->
            TagEditorHost(files = listOf(f), onClose = { tagEditFile = null; refreshTick++ })
        }
        // 파일 작업(복사·이동·이름변경·삭제). 성공하면 결과 위치로 이동·강조·토스트(applyOpsResult),
        // 실패/취소는 다이얼로그를 닫으며 현재 폴더만 새로고침.
        fileOpsFile?.let { f ->
            FileOpsHost(
                file = f,
                onDone = { r -> fileOpsFile = null; applyOpsResult(r) },
                onClose = { fileOpsFile = null; refreshTick++ },
            )
        }
        // 폴더 작업(열기·이름변경·복사·이동·삭제). 열기는 그 폴더로 진입, 성공은 결과 위치로 이동·강조.
        folderOpsFolder?.let { d ->
            FolderOpsHost(
                folder = d,
                onOpen = { if (d.isDirectory) dir = d },
                onDone = { r -> folderOpsFolder = null; applyOpsResult(r) },
                onClose = { folderOpsFolder = null; refreshTick++ },
            )
        }

        // 다중 선택 하단 고정 바(선택 모드일 때). 작업 중(폴더 선택기/다이얼로그)엔 가린다.
        if (selectionMode && batchStage == null) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                BatchActionBar(
                    enabled = selected.isNotEmpty(),
                    onMove = { onBatchMove() },
                    onCopy = { onBatchCopy() },
                    onDelete = { onBatchDelete() },
                )
            }
        }

        // 일괄 이동/복사: 앱 폴더 선택기로 대상 폴더를 고른다. 현재 폴더로는 못 보낸다(제자리).
        if (batchStage == "move" || batchStage == "copy") {
            val moving = batchStage == "move"
            FolderChooser(
                title = if (moving) "이동할 폴더 선택" else "복사할 폴더 선택",
                subtitle = "${selected.size}개 항목을 ${if (moving) "옮깁니다" else "복사합니다"}",
                startDir = dir,
                root = root,
                rootLabel = "내부 저장소",
                confirmLabel = if (moving) "여기로 이동" else "여기로 복사",
                canConfirm = { it.path != dir.path },
                onConfirm = { destDir ->
                    batchStage = null
                    if (moving) runBatch("이동") { moveInto(context, it, destDir) }
                    else runBatch("복사") { copyIntoFolder(context, it, destDir) }
                },
                onCancel = { batchStage = null },
            )
        }
        // 일괄 삭제 확인.
        if (batchStage == "confirmDelete") {
            BatchDeleteDialog(
                count = selected.size,
                onConfirm = { batchStage = null; runBatch("삭제") { f -> if ((if (f.isDirectory) f.deleteRecursively() else f.delete())) { org.olo.player.art.MediaTagSaver.rescan(context, listOf(f)); null } else "삭제 실패" } },
                onCancel = { batchStage = null },
            )
        }
        // 권한 안내(모든 파일 접근).
        if (batchStage == "needPermission") {
            BatchPermissionDialog(
                onOpenSettings = { batchStage = null; openAllFilesSettings() },
                onCancel = { batchStage = null },
            )
        }
        // 탐색기 '새 폴더': 현재 폴더에 만들고 새로고침 + 그 폴더를 강조.
        if (showNewFolder) {
            val rel = dir.path.removePrefix(root.path).trim('/')
            NewFolderDialog(
                locationLabel = if (rel.isEmpty()) "내부 저장소" else "내부 저장소/$rel",
                exists = { File(dir, it).exists() },
                onCreate = { nm ->
                    showNewFolder = false
                    val target = File(dir, nm)
                    if (target.mkdirs()) {
                        refreshTick++
                        contentVersion++
                        highlightPath = target.path.removePrefix(root.path).ifEmpty { "/$nm" }
                        android.widget.Toast.makeText(context, "폴더를 만들었습니다: $nm", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "폴더를 만들지 못했습니다.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onCancel = { showNewFolder = false },
            )
        }
    }
}


