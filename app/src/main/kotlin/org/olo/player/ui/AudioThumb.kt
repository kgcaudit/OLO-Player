package org.olo.player.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import java.io.File
import org.olo.player.art.NEUTRAL_BG
import org.olo.player.art.TagReader
import org.olo.player.art.averageArgb

/**
 * 로컬 음악 파일의 브라우즈 썸네일: 내장 앨범아트(원본 바이트)와, 2:3 썸네일 박스에 정사각형으로
 * 왜곡 없이 얹을 때 둘레를 채울 여백색. 여백색은 커버의 평균색이라 표지마다 어울리는 바탕이 깔린다.
 */
data class AudioThumb(val bytes: ByteArray, val background: Color) {
    override fun equals(other: Any?): Boolean =
        other is AudioThumb && background == other.background && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + background.hashCode()
}

/**
 * 파일에 심긴 앨범아트를 읽어 썸네일(바이트 + 여백색)을 만든다. 아트가 없으면 null(호출부가
 * 기존 음표 타일로 그린다). 여백색은 작게 디코드한 비트맵의 평균색 -- 여백 바탕만 필요해 큰
 * 비트맵을 들 이유가 없다. 블로킹(디코드)이라 IO에서 호출한다.
 */
fun loadAudioThumb(file: File): AudioThumb? {
    val bytes = TagReader.readArtwork(file) ?: return null
    // 평균색만 뽑으므로 크게 줄여 디코드(메모리·시간 절약). 디코드 실패 시 중립 바탕.
    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
    val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
    val bg = if (bmp != null && bmp.width > 0 && bmp.height > 0) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        Color(averageArgb(px))
    } else {
        Color(NEUTRAL_BG)
    }
    return AudioThumb(bytes, bg)
}
