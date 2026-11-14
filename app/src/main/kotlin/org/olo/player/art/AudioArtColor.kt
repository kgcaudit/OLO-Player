package org.olo.player.art

/**
 * 내장 앨범아트를 브라우즈 썸네일(2:3 박스)에 왜곡 없이 정사각형으로 얹을 때, 그 둘레 여백을
 * 채울 '합리적인 색'을 커버 자체에서 뽑는다. 커버의 평균색이라 어떤 표지든 그와 어울리는 바탕이
 * 깔려, 흰/검 고정 여백보다 커버가 자연스럽게 떠 보인다.
 *
 * 순수 함수로 두어(비트맵 디코드는 호출부) 네트워크·안드로이드 없이 검증한다.
 */

/**
 * ARGB 픽셀 배열의 평균색을 '불투명' ARGB로 돌려준다. 거의 투명한 픽셀(alpha<16)은 평균에서
 * 빼(투명 테두리가 여백색을 흐리지 않게), 유효 픽셀이 없으면 중립 어두운 회색으로 대체한다.
 */
fun averageArgb(pixels: IntArray): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    for (p in pixels) {
        val a = (p ushr 24) and 0xFF
        if (a < 16) continue
        r += (p ushr 16) and 0xFF
        g += (p ushr 8) and 0xFF
        b += p and 0xFF
        n++
    }
    if (n == 0L) return NEUTRAL_BG
    val rr = (r / n).toInt()
    val gg = (g / n).toInt()
    val bb = (b / n).toInt()
    return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
}

// 평균을 낼 픽셀이 없을 때(전부 투명 등)의 중립 바탕색.
const val NEUTRAL_BG: Int = 0xFF2A2A2A.toInt()
