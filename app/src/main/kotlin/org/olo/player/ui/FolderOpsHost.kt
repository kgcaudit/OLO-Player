package org.olo.player.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.art.LocalFileOps
import org.olo.player.art.MediaTagSaver
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

    fun canWrite(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()

    fun openAllFilesSettings() {
        val uri = Uri.parse("package:${context.packageName}")
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)) }
            .onFailure { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
    }

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

    // ── 복사(같은 상위에 재귀 사본) ──
    fun doCopy() {
        val parent = folder.parentFile ?: run { fail("상위 폴더를 찾을 수 없습니다."); return }
        if (!canWrite()) { stage = "needPermission"; return }
        val newName = LocalFileOps.copyName(folder.name) { File(parent, it).exists() }
        stage = "busy"
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    folder.copyRecursively(File(parent, newName), overwrite = false)
                    MediaTagSaver.rescan(context, listOf(File(parent, newName)))
                    null
                }.getOrElse { "폴더를 복사하지 못했습니다(${it.message})." }
            }
            if (err == null) onDone(OpsResult("폴더 사본을 만들었습니다: $newName", parent, newName)) else fail(err)
        }
    }

    // ── 이동(SAF 폴더 선택 → 재귀 복사 → 원본 재귀 삭제) ──
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) { stage = "menu"; return@rememberLauncherForActivityResult }
        stage = "busy"
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    if (!copyDirToTree(context, folder, treeUri)) return@runCatching "대상 폴더에 복사하지 못했습니다."
                    if (folder.deleteRecursively()) { MediaTagSaver.rescan(context, listOf(folder)); null }
                    else "사본은 만들었으나 원본 폴더를 지우지 못했습니다(권한 제한)."
                }.getOrElse { "폴더를 옮기지 못했습니다(${it.message})." }
            }
            if (err == null) {
                // 대상이 내부저장소면 그 폴더로 들어가 옮긴 폴더를 강조. 외부 트리면 위치만 토스트로.
                val destDir = treeUriToFile(treeUri)
                onDone(
                    if (destDir != null) OpsResult("폴더를 옮겼습니다 · ${destDir.name}", destDir, folder.name)
                    else OpsResult("폴더를 옮겼습니다"),
                )
            } else {
                fail(err)
            }
        }
    }
    fun doMove() { if (!canWrite()) { stage = "needPermission"; return }; treePicker.launch(null) }

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
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    if (folder.deleteRecursively()) { MediaTagSaver.rescan(context, listOf(folder)); null }
                    else "삭제하지 못했습니다(권한 제한)."
                }.getOrElse { "삭제하지 못했습니다(${it.message})." }
            }
            // 삭제는 결과물이 없으니 이동·강조 없이 현재 폴더만 새로고침.
            if (err == null) onDone(OpsResult("폴더를 삭제했습니다.")) else fail(err)
        }
    }

    when (stage) {
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
                OloDialogButton("설정 열기", onClick = { openAllFilesSettings(); onClose() })
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
            val sub = if (itemCount > 0) "이 폴더와 안의 ${itemCount}개 항목이 모두 삭제됩니다." else "이 폴더와 안의 내용이 모두 삭제됩니다."
            Text(sub, color = Color(0xFFB23B3B), fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            Text("되돌릴 수 없습니다.", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
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
    OloActionRow(Icons.Filled.ContentCopy, "복사", "폴더 통째 사본(재귀)", onClick = onCopy)
    OloActionRow(Icons.AutoMirrored.Filled.DriveFileMove, "이동", "다른 위치로 옮기기", onClick = onMove)
    OloActionRow(Icons.Filled.Delete, "삭제", "폴더·내용 전부 삭제 (되돌릴 수 없음)", danger = true, onClick = onDelete)
}

// SAF로 고른 폴더(treeUri) 아래에 dir를 통째(재귀)로 복사한다. 성공 true. (원본 삭제는 호출부에서.)
private fun copyDirToTree(context: Context, dir: File, treeUri: Uri): Boolean = runCatching {
    val parentDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    copyInto(context, dir, parentDoc)
}.getOrDefault(false)

// parentDoc(문서 URI) 아래에 dir와 그 내용을 재귀로 만든다.
private fun copyInto(context: Context, dir: File, parentDoc: Uri): Boolean {
    val resolver = context.contentResolver
    val newDir = DocumentsContract.createDocument(resolver, parentDoc, DocumentsContract.Document.MIME_TYPE_DIR, dir.name) ?: return false
    dir.listFiles()?.forEach { child ->
        val ok = if (child.isDirectory) {
            copyInto(context, child, newDir)
        } else {
            val mime = mimeOf(child.name)
            val d = DocumentsContract.createDocument(resolver, newDir, mime, child.name) ?: return false
            (resolver.openOutputStream(d)?.use { out -> child.inputStream().use { it.copyTo(out) } }) != null
        }
        if (!ok) return false
    }
    return true
}

private fun mimeOf(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}
