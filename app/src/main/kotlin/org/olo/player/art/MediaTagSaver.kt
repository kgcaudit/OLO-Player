package org.olo.player.art

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * 로컬 음악 파일에 태그를 '저장'하는 안드로이드 쪽 배선. 포맷별 태그 쓰기는 [JAudioTagWriter]가,
 * 저장소 권한·쓰기 경로는 여기가 맡는다.
 *
 * 저장 전략(안드로이드 버전별):
 *  - API 30+ (targetSdk 35의 현실): 앱 소유가 아닌 공유 저장소 파일은 [MediaStore.createWriteRequest]
 *    로 사용자 동의를 받은 뒤, content URI로 되쓴다(URI→캐시 임시→editInPlace→스트림 되쓰기).
 *  - API 29 이하: 레거시 저장소에서 File 직접 쓰기([JAudioTagWriter.write], 임시+원자적 교체).
 *
 * 쓰기 후 [rescan]으로 MediaStore를 갱신해 다른 앱·우리 라이브러리가 바뀐 태그를 바로 본다.
 * 실제 권한·쓰기·재스캔은 기기에서만 검증된다.
 */
object MediaTagSaver {

    /** 로컬 파일의 MediaStore 오디오 content URI(없으면 null). 경로(DATA)로 찾는다. */
    fun audioUri(context: Context, file: File): Uri? = runCatching {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA}=?"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(file.absolutePath), null,
        )?.use { cur ->
            if (cur.moveToFirst()) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cur.getLong(0)) else null
        }
    }.getOrNull()

    /** 여러 파일에 대한 쓰기 동의 요청(API 30+). 반환 IntentSender를 런처로 띄워 동의를 받는다. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun writeRequest(context: Context, uris: List<Uri>): IntentSender =
        MediaStore.createWriteRequest(context.contentResolver, uris).intentSender

    /** 동의가 끝난 content URI에 되쓴다: URI→캐시 임시(확장자 유지)→editInPlace→스트림 되쓰기. */
    fun applyToUri(context: Context, uri: Uri, displayName: String, edits: Map<TagField, FieldEdit>, artwork: ArtworkEdit?): TagWriteResult {
        if (edits.isEmpty() && artwork == null) return TagWriteResult.Ok
        val ext = displayName.substringAfterLast('.', "")
        if (ext.lowercase() !in TAG_WRITABLE_EXTENSIONS) return TagWriteResult.Unsupported
        val tmp = File(context.cacheDir, "olotag_${System.nanoTime()}.$ext")
        val result = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: error("원본을 열 수 없음")
            JAudioTagWriter.editInPlace(tmp, edits, artwork)
            context.contentResolver.openOutputStream(uri, "rwt")?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                ?: error("대상을 쓸 수 없음")
            TagWriteResult.Ok as TagWriteResult
        }.getOrElse { TagWriteResult.Failed(it.message ?: it.toString()) }
        runCatching { tmp.delete() }
        return result
    }

    /** API 29 이하: 레거시 File 직접 쓰기(임시+원자적 교체는 JAudioTagWriter가 담당). */
    fun applyToFile(file: File, edits: Map<TagField, FieldEdit>, artwork: ArtworkEdit?): TagWriteResult =
        JAudioTagWriter.write(file, edits, artwork)

    /** 바뀐 파일을 MediaStore에 다시 스캔시켜, 앱·다른 앱이 새 태그/커버를 바로 보게 한다. */
    fun rescan(context: Context, files: List<File>) {
        runCatching { MediaScannerConnection.scanFile(context, files.map { it.absolutePath }.toTypedArray(), null, null) }
    }

    /** 이 버전에서 쓰기 동의(createWriteRequest)가 필요한가 = API 30+. */
    val needsWriteRequest: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}
