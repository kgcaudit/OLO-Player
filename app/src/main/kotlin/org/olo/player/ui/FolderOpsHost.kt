package org.olo.player.ui

import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.art.LocalFileOps
import org.olo.player.art.MediaTagSaver
import org.olo.player.data.canManageAllFiles
import org.olo.player.data.externalStorageRoot
import org.olo.player.data.openAllFilesSettings
import org.olo.player.ui.components.CpField
import org.olo.player.ui.theme.OloTheme

/**
 * 로컬 폴더의 기본 작업 호스트: 폴더 열기 · 이름 변경 · 복사(재귀) · 이동 · 삭제. 탐색기 ⋮ 메뉴에서 연다.
 *
 * 파일 작업([FileOpsHost])과 같은 틀·같은 권한을 쓴다: 이름변경·복사·이동·삭제는 '모든 파일 접근'
 * (또는 API 29 이하 레거시)이 있어야 File로 바로 처리하고, 없으면 '권한 필요' 안내로 유도한다.
 * 복사·이동·삭제는 폴더 내용을 통째로(재귀) 다룬다. 삭제는 안의 항목 수를 세어 확인시킨다.
 * 성공하면 [onDone]으로 결과([OpsResult])를 알리고 창을 닫는다(결과 다이얼로그 없음 -> 바로 보임).
 * 실패·권한 안내·취소만 다이얼로그를 띄우고, 닫으면 [onClose]로 돌아간다.
 * 로컬 폴더 전용. 실제 쓰기·삭제는 기기에서만 검증된다.
 */
@Composable
fun FolderOpsHost(folder: File, onOpen: () -> Unit, onDone: (OpsResult) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OloTheme.colors

    var stage by remember { mutableStateOf("menu") }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf(folder.name) }
    var itemCount by remember { mutableIntStateOf(-1) } // 삭제 확인에 쓸 내부 항목 수(-1=셈 전/실패)

    // 실패 안내만 다이얼로그로(성공은 onDone으로 바로 닫고 탐색기가 새로고침·이동·강조).
    fun fail(msg: String) { resultMsg = msg; stage = "result" }

    fun canWrite(): Boolean = canManageAllFiles()

    // ── 이름 변경 ──
    fun doRename() {
        val newName = renameText.trim()
        if (!LocalFileOps.isValidName(newName)) { fail("이름이 올바르지 않습니다."); return }
        if (newName == folder.name) { fail("이름이 그대로입니다."); return }
        val parent = folder.parentFile ?: run { fail("상위 폴더를 찾을 수 없습니다."); return }
        val target = File(parent, newName)
        if (target.exists()) { fail("같은 이름이 이미 있습니다."); return }
        if (!canWrite()) { stage = "needPermission"; return }
        stage = "busy"
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    if (folder.renameTo(target)) { MediaTagSaver.rescan(context, listOf(folder, target)); null }
                    else "이름을 바꾸지 못했습니다(권한 제한)."
                }.getOrElse { "이름을 바꾸지 못했습니다(${it.message})." }
            }
            if (err == null) onDone(OpsResult("폴더 이름을 바꿨습니다: $newName", parent, newName)) else fail(err)
        }
    }

    // ── 복사(앱 자체 폴더 선택기 → 위치 지정 저장, 재귀 사본) ──
    fun doCopy() { if (!canWrite()) { stage = "needPermission"; return }; stage = "chooseFolderCopy" }

    fun copyTo(destDir: File) {
        stage = "busy"
        scope.launch {
            val sameParent = destDir.path == folder.parentFile?.path
            val name = if (sameParent || File(destDir, folder.name).exists()) {
                LocalFileOps.copyName(folder.name) { File(destDir, it).exists() }
            } else {
                folder.name
            }
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    folder.copyRecursively(File(destDir, name), overwrite = false)
                    MediaTagSaver.rescan(context, listOf(File(destDir, name)))
                    null
                }.getOrElse { "폴더를 복사하지 못했습니다(${it.message})." }
            }
            if (err == null) {
                onDone(OpsResult(if (sameParent) "폴더 사본을 만들었습니다: $name" else "폴더를 복사했습니다 · ${destDir.name}", destDir, name))
            } else {
                fail(err)
            }
        }
    }

    // ── 이동(앱 자체 폴더 선택기 → File 직접 이동) ──
    // 같은 볼륨이면 rename이라 통째로 즉시 옮겨진다(재귀 복사 없이).
    fun moveTo(destDir: File) {
        stage = "busy"
        scope.launch {
            val err = withContext(Dispatchers.IO) { moveInto(context, folder, destDir) }
            if (err == null) onDone(OpsResult("폴더를 옮겼습니다 · ${destDir.name}", destDir, folder.name)) else fail(err)
        }
    }
    fun doMove() { if (!canWrite()) { stage = "needPermission"; return }; stage = "chooseFolder" }

    // ── 삭제(내부 항목 수를 센 뒤 확인) ──
    fun askDelete() {
        if (!canWrite()) { stage = "needPermission"; return }
        stage = "busy"
        scope.launch {
            itemCount = withContext(Dispatchers.IO) { runCatching { folder.walkTopDown().count() - 1 }.getOrDefault(-1) }
            stage = "confirmDelete"
        }
    }
    fun doDelete() {
        stage = "busy"
        scope.launch {
            // 폴더 통째로 휴지통으로 이동(되돌리기 가능). 같은 볼륨이라 rename으로 즉시.
            val entry = withContext(Dispatchers.IO) { org.olo.player.data.OloTrash.trash(context, folder) }
            if (entry != null) onDone(OpsResult("폴더를 삭제했습니다.", undo = listOf(entry)))
            else fail("삭제하지 못했습니다(권한 제한).")
        }
    }

    when (stage) {
        "chooseFolder" -> {
            val root = remember { externalStorageRoot() }
            FolderChooser(
                title = "이동할 폴더 선택",
                subtitle = "${folder.name}/ 폴더를 옮깁니다",
                startDir = folder.parentFile ?: root,
                root = root,
                rootLabel = "내부 저장소",
                confirmLabel = "여기로 이동",
                // 지금 있는 상위, 자기 자신, 자기 하위로는 옮길 수 없다(제자리·순환 방지).
                canConfirm = { dest ->
                    dest.path != folder.parentFile?.path &&
                        dest.path != folder.path &&
                        !dest.path.startsWith(folder.path + "/")
                },
                onConfirm = { dest -> moveTo(dest) },
                onCancel = { stage = "menu" },
            )
        }
        "chooseFolderCopy" -> {
            val root = remember { externalStorageRoot() }
            FolderChooser(
                title = "복사할 폴더 선택",
                subtitle = "${folder.name}/ 폴더를 복사합니다",
                startDir = folder.parentFile ?: root,
                root = root,
                rootLabel = "내부 저장소",
                confirmLabel = "여기로 복사",
                // 자기 자신·자기 하위로는 복사 불가(순환 방지). 현재 상위는 '(사본)'으로 허용.
                canConfirm = { dest -> dest.path != folder.path && !dest.path.startsWith(folder.path + "/") },
                onConfirm = { dest -> copyTo(dest) },
                onCancel = { stage = "menu" },
            )
        }
        "result" -> OloCardDialog(title = "폴더작업", onDismiss = onClose, dismissLabel = "확인") {
            Text(resultMsg ?: "", color = c.text, fontSize = 13.sp)
        }
        "busy" -> OloCardDialog(title = "폴더작업", onDismiss = {}, dismissLabel = null) {
            Text("처리 중…", color = c.muted, fontSize = 13.sp)
        }
        "needPermission" -> OloCardDialog(
            title = "권한 필요",
            onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("설정 열기", onClick = { context.openAllFilesSettings(); onClose() })
            },
        ) {
            Text("폴더를 이름변경·복사·이동·삭제하려면 '모든 파일 접근' 권한이 필요합니다.", color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.padding(top = 4.dp))
            Text("설정에서 허용한 뒤 다시 시도해 주세요. (설정 › 고급에서도 켤 수 있어요.)", color = c.muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
        "rename" -> OloCardDialog(
            title = "폴더 이름 변경",
            onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("변경", onClick = ::doRename)
            },
        ) {
            Text(folder.name, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            CpField(label = "새 폴더 이름", value = renameText, onValueChange = { renameText = it })
        }
        "confirmDelete" -> OloCardDialog(
            title = "폴더 삭제",
            onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("삭제", onClick = ::doDelete)
            },
        ) {
            Text("'${folder.name}' 폴더를 삭제할까요?", color = c.text, fontSize = 13.sp)
            Spacer(Modifier.padding(top = 2.dp))
            val sub = if (itemCount > 0) "이 폴더와 안의 ${itemCount}개 항목이 함께 휴지통으로 갑니다." else "이 폴더와 안의 내용이 함께 휴지통으로 갑니다."
            Text(sub, color = c.text, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            Text("바로 되돌릴 수 있어요.", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        else -> OloCardDialog(title = "폴더작업", onDismiss = onClose, dismissLabel = "닫기") {
            FolderOpsMenuBody(
                name = folder.name,
                onOpen = { onOpen(); onClose() },
                onRename = { stage = "rename" },
                onCopy = { doCopy() },
                onMove = { doMove() },
                onDelete = { askDelete() },
            )
        }
    }
}

/** 폴더 작업 메뉴 알맹이(대조 렌더가 다이얼로그 밖에서도 같은 몸을 쓰도록 분리). */
@Composable
internal fun FolderOpsMenuBody(
    name: String,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Text("$name/", color = OloTheme.colors.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
    OloActionRow(Icons.Filled.FolderOpen, "폴더 열기", "안으로 이동", onClick = onOpen)
    OloActionRow(Icons.Filled.DriveFileRenameOutline, "이름 변경", "폴더 이름 수정", onClick = onRename)
    OloActionRow(Icons.Filled.ContentCopy, "복사", "위치를 골라 폴더 통째 복사", onClick = onCopy)
    OloActionRow(Icons.AutoMirrored.Filled.DriveFileMove, "이동", "다른 위치로 옮기기", onClick = onMove)
    OloActionRow(Icons.Filled.Delete, "삭제", "휴지통으로 이동 (되돌리기 가능)", danger = true, onClick = onDelete)
}

