package org.olo.player.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.art.LocalFileOps
import org.olo.player.ftp.RemoteEntry
import org.olo.player.net.RemoteOps
import org.olo.player.net.remoteJoin
import org.olo.player.net.remoteNameOf
import org.olo.player.net.remoteParentOf
import org.olo.player.ui.components.CpField
import org.olo.player.ui.theme.OloTheme

/**
 * 미디어 파일의 사이드카(딸린 파일) 이름들 -- 같은 폴더에서 '영상 기본이름'으로 시작하는 비미디어
 * 파일(자막 .srt/.smi/.ass, 포스터/썸네일, .nfo 등). 화면엔 안 보이지만, 파일을 옮기/이름변경/삭제
 * 하면 함께 처리해야 자막·포스터가 고아가 되지 않는다. 다른 미디어(사운드트랙 등)는 사이드카가
 * 아니므로 제외한다. 폴더 공용 커버(poster.jpg 등)는 기본이름으로 시작하지 않아 자연히 빠진다.
 */
internal fun remoteSidecarNames(videoName: String, siblingNames: List<String>): List<String> {
    val base = videoName.substringBeforeLast('.', missingDelimiterValue = videoName)
    if (base.isEmpty()) return emptyList()
    fun matches(b: String, nm: String): Boolean {
        val stem = nm.substringBeforeLast('.', missingDelimiterValue = nm) // 마지막 확장자를 뗀 몸통
        return stem == b || stem.startsWith("$b.") || nm.startsWith("$b-") || nm.startsWith("${b}_")
    }
    // 한 폴더에 여러 영상이 있고 이름이 겹칠 때(예: "Movie" vs "Movie.Part2"), 사이드카가 더 구체적인
    // (더 긴) 다른 영상 이름에 맞으면 그쪽 것이므로 이 영상의 사이드카에서 뺀다 -- 오동반 방지.
    val otherBases = siblingNames.filter { it != videoName && looksMedia(it) }
        .map { it.substringBeforeLast('.', missingDelimiterValue = it) }
    return siblingNames.filter { nm ->
        nm != videoName && !looksMedia(nm) && matches(base, nm) &&
            otherBases.none { ob -> ob.length > base.length && matches(ob, nm) }
    }
}

/**
 * 네트워크 파일/폴더 작업 호스트: 이름변경 · (복사: 지원 서버만) · 이동 · 삭제. 탐색기 ⋮ 메뉴에서 연다.
 *
 * 로컬 [FileOpsHost]/[FolderOpsHost]의 네트워크 짝. File 대신 [RemoteOps](프로토콜별 구현)로 돌고,
 * 이동/복사 대상은 [RemoteFolderChooser](네트워크 폴더 훑기)로 고른다. 복사는 WebDAV만 네이티브라
 * [RemoteOps.caps].copy가 true일 때만 메뉴에 뜬다. 삭제는 원격엔 휴지통이 없어 '확인 후 영구 삭제'다.
 * 성공하면 [onDone](안내 문구)으로 알리고 창을 닫는다 -- 호출부가 목록을 새로고침한다.
 */
@Composable
fun RemoteFileOpsHost(
    ops: RemoteOps,
    entry: RemoteEntry,
    rootLabel: String,
    listFolder: suspend (String) -> List<RemoteEntry>,
    onDone: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = OloTheme.colors
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf("menu") }
    var failMsg by remember { mutableStateOf<String?>(null) }

    val dot = entry.name.lastIndexOf('.')
    val baseName = if (dot > 0) entry.name.substring(0, dot) else entry.name
    val ext = if (dot > 0) entry.name.substring(dot) else ""
    var renameText by remember { mutableStateOf(baseName) }
    val parent = remoteParentOf(entry.path)

    // 이 미디어 파일에 딸린 사이드카(자막·포스터·nfo)를 폴더에서 찾는다 -- 옮기/이름변경/삭제 때 함께.
    suspend fun collectSidecars(): List<RemoteEntry> {
        val siblings = runCatching { listFolder(parent) }.getOrDefault(emptyList())
        val names = remoteSidecarNames(entry.name, siblings.map { it.name }).toSet()
        return siblings.filter { it.name in names }
    }

    fun fail(msg: String) { failMsg = msg; stage = "result" }
    fun run(label: String, dest: String?, block: suspend () -> Unit) {
        stage = "busy"
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { block() }.exceptionOrNull() }
            if (err == null) onDone(if (dest != null) "$label · ${remoteNameOf(dest)}" else label)
            else fail("$label 실패 (${err.message})")
        }
    }

    when (stage) {
        "rename" -> OloCardDialog(
            title = "이름 변경", onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("변경", onClick = {
                    val nm = renameText.trim()
                    if (!LocalFileOps.isValidName(nm)) { fail("이름이 올바르지 않습니다."); return@OloDialogButton }
                    val to = remoteJoin(parent, nm + ext)
                    if (to == entry.path) { fail("이름이 그대로입니다."); return@OloDialogButton }
                    run("이름을 바꿨습니다", null) {
                        val sidecars = collectSidecars()
                        ops.rename(entry.path, to)
                        // 사이드카도 같은 새 기본이름으로(접미사 유지): "old.ko.srt" -> "new.ko.srt"
                        sidecars.forEach { sc ->
                            val suffix = sc.name.substring(baseName.length)
                            runCatching { ops.rename(sc.path, remoteJoin(parent, nm + suffix)) }
                        }
                    }
                })
            },
        ) {
            Text(entry.name, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            CpField(label = "새 이름", value = renameText, onValueChange = { renameText = it })
            if (ext.isNotEmpty()) Text("확장자($ext)는 유지됩니다.", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        "move", "copy" -> {
            val moving = stage == "move"
            RemoteFolderChooser(
                title = if (moving) "이동할 폴더 선택" else "복사할 폴더 선택",
                subtitle = "${entry.name} 을(를) ${if (moving) "옮깁니다" else "복사합니다"}",
                startPath = parent, rootPath = "/", rootLabel = rootLabel,
                confirmLabel = if (moving) "여기로 이동" else "여기로 복사",
                listFolder = listFolder,
                canConfirm = { it != parent }, // 제자리 방지(같은 이름 충돌도 피함)
                onConfirm = { destDir ->
                    val to = remoteJoin(destDir, entry.name)
                    if (moving) run("옮겼습니다", to) {
                        val sidecars = collectSidecars()
                        ops.rename(entry.path, to)
                        sidecars.forEach { sc -> runCatching { ops.rename(sc.path, remoteJoin(destDir, sc.name)) } }
                    } else run("복사했습니다", to) {
                        val sidecars = collectSidecars()
                        ops.copy(entry.path, to)
                        sidecars.forEach { sc -> runCatching { ops.copy(sc.path, remoteJoin(destDir, sc.name)) } }
                    }
                },
                onCancel = { stage = "menu" },
                onMkdir = { path -> ops.mkdir(path) },
            )
        }
        "confirmDelete" -> OloCardDialog(
            title = "삭제", onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("삭제", onClick = {
                    run("삭제했습니다", null) {
                        val sidecars = collectSidecars()
                        ops.delete(entry.path, isDir = false)
                        sidecars.forEach { sc -> runCatching { ops.delete(sc.path, isDir = false) } }
                    }
                })
            },
        ) {
            Text("'${entry.name}' 파일을 삭제할까요?", color = c.text, fontSize = 13.sp)
            Text("딸린 자막·포스터도 함께, 원격은 휴지통이 없어 바로 영구 삭제됩니다(되돌릴 수 없음).", color = c.muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
        "busy" -> OloCardDialog(title = "파일작업", onDismiss = {}, dismissLabel = null) {
            Text("처리 중…", color = c.muted, fontSize = 13.sp)
        }
        "result" -> OloCardDialog(title = "파일작업", onDismiss = onClose, dismissLabel = "확인") {
            Text(failMsg ?: "", color = c.text, fontSize = 13.sp)
        }
        else -> OloCardDialog(title = "파일작업", onDismiss = onClose, dismissLabel = "닫기") {
            Text(entry.name, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            OloActionRow(Icons.Filled.DriveFileRenameOutline, "이름 변경", "확장자 유지 · 자막·포스터도 같은 이름으로", onClick = { stage = "rename" })
            if (ops.caps.copy) OloActionRow(Icons.Filled.ContentCopy, "복사", "자막·포스터와 함께 다른 폴더로", onClick = { stage = "copy" })
            OloActionRow(Icons.AutoMirrored.Filled.DriveFileMove, "이동", "자막·포스터와 함께 다른 폴더로", onClick = { stage = "move" })
            OloActionRow(Icons.Filled.Delete, "삭제", "자막·포스터와 함께 영구 삭제", danger = true, onClick = { stage = "confirmDelete" })
        }
    }
}

/** 네트워크 폴더 작업 호스트: 열기 · 이름변경 · (복사) · 이동 · 삭제(재귀, 영구). */
@Composable
fun RemoteFolderOpsHost(
    ops: RemoteOps,
    folder: RemoteEntry,
    rootLabel: String,
    listFolder: suspend (String) -> List<RemoteEntry>,
    onOpen: () -> Unit,
    onDone: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = OloTheme.colors
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf("menu") }
    var failMsg by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf(folder.name) }
    val parent = remoteParentOf(folder.path)

    fun fail(msg: String) { failMsg = msg; stage = "result" }
    fun run(label: String, dest: String?, block: suspend () -> Unit) {
        stage = "busy"
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { block() }.exceptionOrNull() }
            if (err == null) onDone(if (dest != null) "$label · ${remoteNameOf(dest)}" else label)
            else fail("$label 실패 (${err.message})")
        }
    }
    // 자기 자신·자기 하위로는 옮기/복사할 수 없다(순환), 지금 상위로도 못 간다(제자리).
    fun validDest(dest: String): Boolean =
        dest != parent && dest != folder.path && !dest.startsWith(folder.path.trimEnd('/') + "/")

    when (stage) {
        "rename" -> OloCardDialog(
            title = "폴더 이름 변경", onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("변경", onClick = {
                    val nm = renameText.trim()
                    if (!LocalFileOps.isValidName(nm)) { fail("이름이 올바르지 않습니다."); return@OloDialogButton }
                    val to = remoteJoin(parent, nm)
                    if (to == folder.path) { fail("이름이 그대로입니다."); return@OloDialogButton }
                    run("폴더 이름을 바꿨습니다", null) { ops.rename(folder.path, to) }
                })
            },
        ) {
            Text(folder.name, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            CpField(label = "새 폴더 이름", value = renameText, onValueChange = { renameText = it })
        }
        "move", "copy" -> {
            val moving = stage == "move"
            RemoteFolderChooser(
                title = if (moving) "이동할 폴더 선택" else "복사할 폴더 선택",
                subtitle = "${folder.name}/ 폴더를 ${if (moving) "옮깁니다" else "복사합니다"}",
                startPath = parent, rootPath = "/", rootLabel = rootLabel,
                confirmLabel = if (moving) "여기로 이동" else "여기로 복사",
                listFolder = listFolder, canConfirm = { validDest(it) },
                onConfirm = { destDir ->
                    val to = remoteJoin(destDir, folder.name)
                    if (moving) run("폴더를 옮겼습니다", to) { ops.rename(folder.path, to) }
                    else run("폴더를 복사했습니다", to) { ops.copy(folder.path, to) }
                },
                onCancel = { stage = "menu" },
                onMkdir = { path -> ops.mkdir(path) },
            )
        }
        "confirmDelete" -> OloCardDialog(
            title = "폴더 삭제", onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("삭제", onClick = { run("폴더를 삭제했습니다", null) { ops.delete(folder.path, isDir = true) } })
            },
        ) {
            Text("'${folder.name}' 폴더를 삭제할까요?", color = c.text, fontSize = 13.sp)
            Text("폴더와 안의 내용이 함께 바로 영구 삭제됩니다(되돌릴 수 없음).", color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        }
        "busy" -> OloCardDialog(title = "폴더작업", onDismiss = {}, dismissLabel = null) {
            Text("처리 중…", color = c.muted, fontSize = 13.sp)
        }
        "result" -> OloCardDialog(title = "폴더작업", onDismiss = onClose, dismissLabel = "확인") {
            Text(failMsg ?: "", color = c.text, fontSize = 13.sp)
        }
        else -> OloCardDialog(title = "폴더작업", onDismiss = onClose, dismissLabel = "닫기") {
            Text("${folder.name}/", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            OloActionRow(Icons.Filled.FolderOpen, "폴더 열기", "안으로 이동", onClick = { onOpen(); onClose() })
            OloActionRow(Icons.Filled.DriveFileRenameOutline, "이름 변경", "폴더 이름 수정", onClick = { stage = "rename" })
            if (ops.caps.copy) OloActionRow(Icons.Filled.ContentCopy, "복사", "다른 위치로 폴더 통째 복사", onClick = { stage = "copy" })
            OloActionRow(Icons.AutoMirrored.Filled.DriveFileMove, "이동", "다른 위치로 옮기기", onClick = { stage = "move" })
            OloActionRow(Icons.Filled.Delete, "삭제", "확인 후 영구 삭제 (되돌릴 수 없음)", danger = true, onClick = { stage = "confirmDelete" })
        }
    }
}
