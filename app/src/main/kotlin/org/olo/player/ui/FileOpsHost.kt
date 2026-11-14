package org.olo.player.ui

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * 파일·폴더 작업이 성공했을 때의 결과. 작업이 끝나면 탐색기가 이 정보로 (1) 결과물이 놓인 폴더로
 * 화면을 옮기고 (2) 그 항목을 잠깐 강조하며 (3) [message]를 토스트로 알린다. 결과 다이얼로그를
 * 띄우지 않으므로(성공은 바로 보이게) 실패일 때만 안내창을 쓴다.
 *
 * @param destDir   결과물이 놓인 폴더(내부저장소 File). null이면 화면 이동 없이 현재 폴더만 새로고침
 *                  한다(삭제, 또는 SD·USB 등 앱이 바로 열 수 없는 외부 트리로의 이동).
 * @param resultName destDir 안에서 강조·스크롤할 결과물 이름. null이면 강조 없음(예: 삭제).
 */
data class OpsResult(
    val message: String,
    val destDir: File? = null,
    val resultName: String? = null,
    // 삭제 결과면 되돌릴 수 있는 휴지통 항목들. null이면 토스트만, 있으면 '되돌리기' 스낵바를 띄운다.
    val undo: List<org.olo.player.data.TrashEntry>? = null,
)

/**
 * 파일/폴더를 [destDir] 안으로 옮긴다. 같은 볼륨이면 rename으로 즉시 끝나고(큰 파일도 재복사 없이),
 * 볼륨이 다르면(rename 실패) 복사 후 원본을 지운다. 성공하면 null, 실패하면 안내 문구를 돌려준다.
 * '모든 파일 접근' 권한은 호출부가 먼저 확인한다(없으면 애초에 이 길로 오지 않는다).
 */
internal fun moveInto(context: Context, src: File, destDir: File): String? {
    val target = File(destDir, src.name)
    if (target.path == src.path) return "같은 폴더입니다."
    if (target.exists()) return "대상 폴더에 같은 이름이 이미 있습니다."
    return runCatching {
        if (src.renameTo(target)) {
            MediaTagSaver.rescan(context, listOf(src, target))
            return null
        }
        // 볼륨이 다르면 rename이 실패한다 -- 복사 후 원본 삭제로 대체.
        if (src.isDirectory) src.copyRecursively(target, overwrite = false) else src.copyTo(target, overwrite = false)
        val removed = if (src.isDirectory) src.deleteRecursively() else src.delete()
        if (removed) {
            MediaTagSaver.rescan(context, listOf(src, target))
            null
        } else {
            "사본은 만들었으나 원본을 지우지 못했습니다(권한 제한)."
        }
    }.getOrElse { "옮기지 못했습니다(${it.message})." }
}

/** 파일/폴더를 [destDir] 안으로 복사한다(원본 유지). 성공 null, 실패 문구. 일괄 복사에 쓴다. */
internal fun copyIntoFolder(context: Context, src: File, destDir: File): String? {
    val target = File(destDir, src.name)
    if (target.exists()) return "대상 폴더에 같은 이름이 이미 있습니다."
    return runCatching {
        if (src.isDirectory) src.copyRecursively(target, overwrite = false) else src.copyTo(target, overwrite = false)
        MediaTagSaver.rescan(context, listOf(target))
        null
    }.getOrElse { "복사하지 못했습니다(${it.message})." }
}

/**
 * 로컬 파일의 기본 작업 호스트: 이름 변경 · 복사 · 이동 · 삭제. 탐색기 ⋮ 메뉴에서 연다.
 *
 * 스코프 저장소(API 30+, targetSdk 35)에선 공유 저장소 파일의 수정·삭제에 사용자 동의가 필요해,
 * 태그 저장과 같은 MediaStore 동의 흐름(createWriteRequest/createDeleteRequest)을 재사용한다.
 * 이동은 플랫폼 SAF(폴더 선택)로 대상에 복사한 뒤 원본을 지운다(동의). API 29 이하는 File 직접.
 *
 * 성공하면 [onDone]으로 결과([OpsResult])를 알리고 창을 닫는다(결과 다이얼로그 없음 -> 바로 보임).
 * 실패·권한 안내·취소는 다이얼로그를 띄우고, 닫으면 [onClose]로 돌아간다.
 * 로컬 파일 전용(원격은 추후). 실제 권한·쓰기·삭제는 기기에서만 검증된다.
 */
@Composable
fun FileOpsHost(file: File, onDone: (OpsResult) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OloTheme.colors

    // 단계: 메뉴 → (이름변경/삭제확인/권한안내) → 결과(실패만). busy는 비동기 처리 중 표시.
    var stage by remember { mutableStateOf("menu") }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf(file.nameWithoutExtension) }

    // 실패 안내만 다이얼로그로(성공은 onDone으로 바로 닫고 탐색기가 새로고침·이동·강조).
    fun fail(msg: String) { resultMsg = msg; stage = "result" }

    // 파일을 직접(동의창 없이) 쓸 수 있는가 -- 삭제·이름변경·이동·복사가 이 판정으로 갈린다.
    // 판정·설정 열기는 data.StorageAccess에 공통화되어 있다(예전엔 호스트마다 복사돼 있었다).
    fun canWrite(): Boolean = canManageAllFiles()

    // ── 이름 변경 ──────────────────────────────────────────────
    fun doRename() {
        val newName = LocalFileOps.ensureExtension(renameText, file.name)
        if (!LocalFileOps.isValidName(newName)) { fail("이름이 올바르지 않습니다."); return }
        if (newName == file.name) { fail("이름이 그대로입니다."); return }
        val parent = file.parentFile ?: run { fail("폴더를 찾을 수 없습니다."); return }
        val target = File(parent, newName)
        if (target.exists()) { fail("같은 이름의 파일이 이미 있습니다."); return }
        if (!canWrite()) { stage = "needPermission"; return }
        stage = "busy"
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    if (file.renameTo(target)) { MediaTagSaver.rescan(context, listOf(file, target)); null }
                    else "이름을 바꾸지 못했습니다(권한 제한)."
                }.getOrElse { "이름을 바꾸지 못했습니다(${it.message})." }
            }
            if (err == null) onDone(OpsResult("이름을 바꿨습니다: $newName", parent, newName)) else fail(err)
        }
    }

    // ── 삭제(휴지통으로 이동 → 되돌리기 가능) ────────────────────
    fun doDelete() {
        if (!canWrite()) { stage = "needPermission"; return }
        stage = "busy"
        scope.launch {
            val entry = withContext(Dispatchers.IO) { org.olo.player.data.OloTrash.trash(context, file) }
            if (entry != null) onDone(OpsResult("삭제했습니다.", undo = listOf(entry)))
            else fail("삭제하지 못했습니다(권한 제한).")
        }
    }

    // ── 복사(앱 자체 폴더 선택기 → 위치 지정 저장, 원본 유지) ─────
    // 폴더를 고른다. 현재 폴더를 고르면 '(사본)'으로 제자리 복제, 다른 폴더면 그 폴더에 복사한다.
    // 대상 폴더를 훑고 거기에 쓰려면 '모든 파일 접근'이 필요하므로 먼저 확인한다.
    fun doCopy() { if (!canWrite()) { stage = "needPermission"; return }; stage = "chooseFolderCopy" }

    fun copyTo(destDir: File) {
        stage = "busy"
        scope.launch {
            val sameFolder = destDir.path == file.parentFile?.path
            // 같은 폴더이거나 이름이 겹치면 '(사본)'으로, 아니면 원래 이름 그대로.
            val name = if (sameFolder || File(destDir, file.name).exists()) {
                LocalFileOps.copyName(file.name) { File(destDir, it).exists() }
            } else {
                file.name
            }
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    file.copyTo(File(destDir, name), overwrite = false)
                    MediaTagSaver.rescan(context, listOf(File(destDir, name)))
                    null
                }.getOrElse { "복사하지 못했습니다(${it.message})." }
            }
            if (err == null) {
                onDone(OpsResult(if (sameFolder) "사본을 만들었습니다: $name" else "복사했습니다 · ${destDir.name}", destDir, name))
            } else {
                fail(err)
            }
        }
    }

    // ── 이동(앱 자체 폴더 선택기 → File 직접 이동) ──────────────
    // 대상 폴더로 옮긴 뒤 완료. 같은 볼륨이면 rename이라 즉시 끝난다.
    fun moveTo(destDir: File) {
        stage = "busy"
        scope.launch {
            val err = withContext(Dispatchers.IO) { moveInto(context, file, destDir) }
            if (err == null) onDone(OpsResult("옮겼습니다 · ${destDir.name}", destDir, file.name)) else fail(err)
        }
    }
    // 이동은 원본 삭제까지 하므로 '모든 파일 접근'이 필요하다. 없으면 안내, 있으면 폴더 선택기로.
    fun doMove() { if (!canWrite()) { stage = "needPermission"; return }; stage = "chooseFolder" }

    when (stage) {
        "chooseFolder" -> {
            val root = remember { externalStorageRoot() }
            FolderChooser(
                title = "이동할 폴더 선택",
                subtitle = "${file.name} 을(를) 옮깁니다",
                startDir = file.parentFile ?: root,
                root = root,
                rootLabel = "내부 저장소",
                confirmLabel = "여기로 이동",
                // 원본이 있는 그 폴더로는 옮길 수 없다(제자리).
                canConfirm = { it.path != file.parentFile?.path },
                onConfirm = { dest -> moveTo(dest) },
                onCancel = { stage = "menu" },
            )
        }
        "chooseFolderCopy" -> {
            val root = remember { externalStorageRoot() }
            FolderChooser(
                title = "복사할 폴더 선택",
                subtitle = "${file.name} 을(를) 복사합니다",
                startDir = file.parentFile ?: root,
                root = root,
                rootLabel = "내부 저장소",
                confirmLabel = "여기로 복사",
                // 복사는 어디든 가능(현재 폴더를 고르면 '(사본)'으로 제자리 복제).
                canConfirm = { true },
                onConfirm = { dest -> copyTo(dest) },
                onCancel = { stage = "menu" },
            )
        }
        "result" -> OloCardDialog(title = "파일작업", onDismiss = onClose, dismissLabel = "확인") {
            Text(resultMsg ?: "", color = c.text, fontSize = 13.sp)
        }
        "busy" -> OloCardDialog(title = "파일작업", onDismiss = {}, dismissLabel = null) {
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
            Text("파일을 삭제·이름변경·이동하려면 '모든 파일 접근' 권한이 필요합니다.", color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.padding(top = 4.dp))
            Text("설정에서 허용한 뒤 다시 시도해 주세요. (설정 › 고급에서도 켤 수 있어요.)", color = c.muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
        "rename" -> OloCardDialog(
            title = "이름 변경",
            onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("변경", onClick = ::doRename)
            },
        ) {
            Text(file.name, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            CpField(label = "새 이름", value = renameText, onValueChange = { renameText = it })
            Spacer(Modifier.padding(top = 2.dp))
            Text("확장자(.${file.extension})는 자동으로 유지됩니다.", color = c.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
        "confirmDelete" -> OloCardDialog(
            title = "삭제",
            onDismiss = onClose,
            actions = {
                OloDialogButton("취소", onClick = onClose, primary = false)
                OloDialogButton("삭제", onClick = ::doDelete)
            },
        ) {
            Text("'${file.name}'을(를) 삭제할까요?", color = c.text, fontSize = 13.sp)
            Spacer(Modifier.padding(top = 4.dp))
            Text("휴지통으로 옮깁니다 · 바로 되돌릴 수 있어요.", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        else -> OloCardDialog(title = "파일작업", onDismiss = onClose, dismissLabel = "닫기") {
            FileOpsMenuBody(
                name = file.name,
                onRename = { stage = "rename" },
                onCopy = { doCopy() },
                onMove = { doMove() },
                onDelete = { stage = "confirmDelete" },
            )
        }
    }
}

/** 파일 작업 메뉴 알맹이(대조 렌더가 다이얼로그 밖에서도 같은 몸을 쓰도록 분리). */
@Composable
internal fun FileOpsMenuBody(
    name: String,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = OloTheme.colors
    Text(name, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
    OloActionRow(Icons.Filled.DriveFileRenameOutline, "이름 변경", "확장자는 유지 · 이름만 수정", onClick = onRename)
    OloActionRow(Icons.Filled.ContentCopy, "복사", "위치를 골라 복사 (원본 유지)", onClick = onCopy)
    OloActionRow(Icons.AutoMirrored.Filled.DriveFileMove, "이동", "다른 폴더로 옮기기", onClick = onMove)
    OloActionRow(Icons.Filled.Delete, "삭제", "휴지통으로 이동 (되돌리기 가능)", danger = true, onClick = onDelete)
}
