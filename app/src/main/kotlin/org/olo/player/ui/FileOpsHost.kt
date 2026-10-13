package org.olo.player.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 * 로컬 파일의 기본 작업 호스트: 이름 변경 · 복사 · 이동 · 삭제. 탐색기 ⋮ 메뉴에서 연다.
 *
 * 스코프 저장소(API 30+, targetSdk 35)에선 공유 저장소 파일의 수정·삭제에 사용자 동의가 필요해,
 * 태그 저장과 같은 MediaStore 동의 흐름(createWriteRequest/createDeleteRequest)을 재사용한다.
 * 이동은 플랫폼 SAF(폴더 선택)로 대상에 복사한 뒤 원본을 지운다(동의). API 29 이하는 File 직접.
 *
 * 로컬 파일 전용(원격은 추후). 실제 권한·쓰기·삭제는 기기에서만 검증된다.
 */
@Composable
fun FileOpsHost(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OloTheme.colors

    // 단계: 메뉴 → (이름변경/삭제확인/권한안내) → 결과. busy는 비동기 처리 중 표시.
    var stage by remember { mutableStateOf("menu") }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf(file.nameWithoutExtension) }

    fun finish(msg: String) { resultMsg = msg; stage = "result" }

    // 파일을 직접 쓸 수 있는가 = API 29 이하(레거시) 또는 '모든 파일 접근' 허용. 삭제·이름변경·이동은
    // 이게 있어야 File로 바로(동의창 없이) 처리한다. 예전엔 권한이 없을 때 MediaStore 삭제 동의
    // (createDeleteRequest)를 썼는데, 일부 기기에서 그 호출이 앱을 그대로 죽였다(삭제 시 홈으로
    // 튕김). 그래서 그 경로를 걷어내고, 권한이 없으면 안내 다이얼로그로 유도한다.
    fun canWrite(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()

    fun openAllFilesSettings() {
        val uri = android.net.Uri.parse("package:${context.packageName}")
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)) }
            .onFailure { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
    }

    // ── 이름 변경 ──────────────────────────────────────────────
    fun doRename() {
        val newName = LocalFileOps.ensureExtension(renameText, file.name)
        if (!LocalFileOps.isValidName(newName)) { finish("이름이 올바르지 않습니다."); return }
        if (newName == file.name) { finish("이름이 그대로입니다."); return }
        val target = File(file.parentFile, newName)
        if (target.exists()) { finish("같은 이름의 파일이 이미 있습니다."); return }
        if (!canWrite()) { stage = "needPermission"; return }
        stage = "busy"
        scope.launch {
            finish(withContext(Dispatchers.IO) {
                runCatching {
                    if (file.renameTo(target)) { MediaTagSaver.rescan(context, listOf(file, target)); "이름을 바꿨습니다: $newName" }
                    else "이름을 바꾸지 못했습니다(권한 제한)."
                }.getOrElse { "이름을 바꾸지 못했습니다(${it.message})." }
            })
        }
    }

    // ── 삭제 ──────────────────────────────────────────────────
    fun doDelete() {
        if (!canWrite()) { stage = "needPermission"; return }
        stage = "busy"
        scope.launch {
            finish(withContext(Dispatchers.IO) {
                runCatching {
                    if (file.delete()) { MediaTagSaver.rescan(context, listOf(file)); "삭제했습니다." }
                    else "삭제하지 못했습니다(권한 제한)."
                }.getOrElse { "삭제하지 못했습니다(${it.message})." }
            })
        }
    }

    // ── 복사(같은 폴더 사본): 새 파일을 만드는 것이라 권한 없이도 MediaStore 삽입으로 된다. ──
    fun doCopy() {
        val parent = file.parentFile ?: run { finish("폴더를 찾을 수 없습니다."); return }
        val newName = LocalFileOps.copyName(file.name) { File(parent, it).exists() }
        stage = "busy"
        scope.launch { finish(withContext(Dispatchers.IO) { copySameFolder(context, file, newName) }) }
    }

    // ── 이동(SAF 폴더 선택 → 복사 → 원본 삭제) ──────────────────
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) { stage = "menu"; return@rememberLauncherForActivityResult }
        stage = "busy"
        scope.launch {
            finish(withContext(Dispatchers.IO) {
                runCatching {
                    copyToTree(context, file, treeUri) ?: return@runCatching "대상 폴더에 복사하지 못했습니다."
                    if (file.delete()) { MediaTagSaver.rescan(context, listOf(file)); "옮겼습니다." }
                    else "사본은 만들었으나 원본을 지우지 못했습니다(권한 제한)."
                }.getOrElse { "옮기지 못했습니다(${it.message})." }
            })
        }
    }
    // 이동은 원본 삭제까지 하므로 '모든 파일 접근'이 필요하다. 없으면 안내 후 중단.
    fun doMove() { if (!canWrite()) { stage = "needPermission"; return }; treePicker.launch(null) }

    when (stage) {
        "result" -> OloCardDialog(title = "파일 작업", onDismiss = onClose, dismissLabel = "확인") {
            Text(resultMsg ?: "", color = c.text, fontSize = 13.sp)
        }
        "busy" -> OloCardDialog(title = "파일 작업", onDismiss = {}, dismissLabel = null) {
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
            Text("되돌릴 수 없습니다.", color = Color(0xFFB23B3B), fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
        }
        else -> OloCardDialog(title = "파일 작업", onDismiss = onClose, dismissLabel = "닫기") {
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
    OpRow(Icons.Filled.DriveFileRenameOutline, "이름 변경", "확장자는 유지 · 이름만 수정", onClick = onRename)
    OpRow(Icons.Filled.ContentCopy, "복사", "같은 폴더에 사본 만들기", onClick = onCopy)
    OpRow(Icons.AutoMirrored.Filled.DriveFileMove, "이동", "다른 폴더로 옮기기", onClick = onMove)
    OpRow(Icons.Filled.Delete, "삭제", "확인 후 삭제 (되돌릴 수 없음)", danger = true, onClick = onDelete)
}

@Composable
private fun OpRow(icon: ImageVector, title: String, desc: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = OloTheme.colors
    val tone = if (danger) Color(0xFFB23B3B) else c.accent
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) tone else c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = c.muted, fontSize = 11.sp)
        }
    }
}

// 같은 폴더에 사본. API 30+는 MediaStore에 삽입(원본과 같은 RELATIVE_PATH), 그 이하는 File 복사.
private fun copySameFolder(context: Context, file: File, newName: String): String = runCatching {
    val parent = file.parentFile ?: return "폴더를 찾을 수 없습니다."
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val srcUri = MediaTagSaver.mediaUri(context, file)
        if (srcUri != null) {
            val rel = queryRelativePath(context, srcUri)
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                if (rel != null) put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val dest = context.contentResolver.insert(collection, values) ?: return "복사하지 못했습니다."
            context.contentResolver.openOutputStream(dest)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: return "복사하지 못했습니다."
            context.contentResolver.update(dest, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return "사본을 만들었습니다: $newName"
        }
    }
    val target = File(parent, newName)
    file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
    MediaTagSaver.rescan(context, listOf(target))
    "사본을 만들었습니다: $newName"
}.getOrElse { "복사하지 못했습니다(${it.message})." }

// SAF로 고른 폴더(treeUri)에 파일을 복사한다. 성공 시 대상 Uri, 실패 null.
private fun copyToTree(context: Context, file: File, treeUri: Uri): Uri? = runCatching {
    val parentDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    val mime = mimeOf(file.name)
    val dest = DocumentsContract.createDocument(context.contentResolver, parentDoc, mime, file.name) ?: return null
    context.contentResolver.openOutputStream(dest)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return null
    dest
}.getOrNull()

private fun queryRelativePath(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { cur ->
        if (cur.moveToFirst()) cur.getString(0) else null
    }
}.getOrNull()

private fun mimeOf(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}
