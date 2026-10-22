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
                    run("이름을 바꿨습니다", null) { ops.rename(entry.path, to) }
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
                    if (moving) run("옮겼습니다", to) { ops.rename(entry.path, to) }
                    else run("복사했습니다", to) { ops.copy(entry.path, to) }
                },
                onCancel = { stage = "menu" },
                onMkdir = { path -> ops.mkdir(path) },
            )
        }
        "confirmDelete" -> OloCardDialog(
            title = "삭제", onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("삭제", onClick = { run("삭제했습니다", null) { ops.delete(entry.path, isDir = false) } })
            },
        ) {
            Text("'${entry.name}' 파일을 삭제할까요?", color = c.text, fontSize = 13.sp)
            Text("원격은 휴지통이 없어 바로 영구 삭제됩니다(되돌릴 수 없음).", color = c.muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
        "busy" -> OloCardDialog(title = "파일작업", onDismiss = {}, dismissLabel = null) {
            Text("처리 중…", color = c.muted, fontSize = 13.sp)
        }
        "result" -> OloCardDialog(title = "파일작업", onDismiss = onClose, dismissLabel = "확인") {
            Text(failMsg ?: "", color = c.text, fontSize = 13.sp)
        }
        else -> OloCardDialog(title = "파일작업", onDismiss = onClose, dismissLabel = "닫기") {
            Text(entry.name, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            OloActionRow(Icons.Filled.DriveFileRenameOutline, "이름 변경", "확장자는 유지 · 이름만 수정", onClick = { stage = "rename" })
            if (ops.caps.copy) OloActionRow(Icons.Filled.ContentCopy, "복사", "다른 폴더로 복사 (원본 유지)", onClick = { stage = "copy" })
            OloActionRow(Icons.AutoMirrored.Filled.DriveFileMove, "이동", "다른 폴더로 옮기기", onClick = { stage = "move" })
            OloActionRow(Icons.Filled.Delete, "삭제", "확인 후 영구 삭제 (되돌릴 수 없음)", danger = true, onClick = { stage = "confirmDelete" })
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
