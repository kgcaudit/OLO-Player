package org.olo.player.art

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 음악 파일에 박혀 있는 앨범 아트를 꺼내 온다(최근 재생 썸네일용). 영화가 포스터를
 * 쓰듯 음악은 자기 앨범 커버를 쓰는 게 자연스러워서다.
 *
 * 로컬(file/content)만 시도한다 — [MediaMetadataRetriever]는 로컬 소스에서만 임베드
 * 그림을 안정적으로 읽고, FTP/SMB 같은 네트워크 URI는 열지 못하므로 바로 null을 돌려
 * 호출부가 음표 타일로 폴백하게 한다. Coil이 ByteArray를 바로 로드하므로 그대로 반환한다.
 */
object AlbumArt {
    suspend fun embedded(context: Context, uriStr: String?): ByteArray? {
        if (uriStr.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != null && scheme != "file" && scheme != "content") return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context.applicationContext, uri)
                    mmr.embeddedPicture
                } finally {
                    mmr.release()
                }
            }.getOrNull()
        }
    }
}
