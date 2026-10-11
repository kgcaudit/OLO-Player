package org.olo.player.art

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 파일 자체에서 읽은 기초 정보: 재생시간(ms)·가로·세로. 못 읽은 값은 null. */
data class MediaProbeInfo(val durationMs: Long?, val width: Int?, val height: Int?) {
    val isEmpty: Boolean get() = durationMs == null && width == null && height == null
    /** "1920×1080"처럼, 둘 다 있을 때만. */
    val resolution: String? get() = if (width != null && height != null && width > 0 && height > 0) "${width}×${height}" else null
}

/**
 * 영상·음성의 실제 재생시간과 해상도를 [MediaMetadataRetriever]로 읽는다. 파일명 태그에 기대지
 * 않고 컨테이너에서 직접 읽으므로, 이름에 1080p 같은 표식이 없어도 채워진다.
 *
 * 열 수 있는 것만: file·content(로컬)·http(s)(웹). FTP/SFTP/SMB/WebDAV는 리트리버가 못 열어
 * null을 돌려준다(그때 화면은 종전처럼 파일명 기반 기술정보만 보여 준다). 어떤 손상·실패에도
 * 예외를 던지지 않는다 -- 상세정보는 부가 정보라 실패가 치명적이면 안 된다.
 */
suspend fun probeMediaInfo(context: Context, uri: Uri): MediaProbeInfo? {
    val scheme = uri.scheme?.lowercase()
    if (scheme !in PROBEABLE) return null
    return withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            if (scheme == "http" || scheme == "https") {
                r.setDataSource(uri.toString(), HashMap<String, String>())
            } else {
                r.setDataSource(context, uri)
            }
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            MediaProbeInfo(dur, w, h).takeIf { !it.isEmpty }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}

private val PROBEABLE = setOf("file", "content", "http", "https")
