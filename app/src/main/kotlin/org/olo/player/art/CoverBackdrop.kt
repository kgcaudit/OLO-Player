package org.olo.player.art

import android.graphics.Bitmap

/**
 * 재생 화면 배경(블러된 커버)을 만든다. 종전엔 40px로 줄여 화면 전체로 ~50배 확대만 해, 실제
 * 블러가 아니라 업스케일된 격자가 '깨져' 보였다(특히 태블릿·폴더블). 이제 적당한 해상도(≈260px)로
 * 줄인 뒤 '진짜 블러'(박스 블러 3패스 ≈ 가우시안)를 약하게 적용한다 -- 블러가 고주파를 없애므로
 * 화면 크기로 확대해도 매끈하면서, 앨범의 형태·색 흐름(윤곽)은 은은히 남긴다. 한때 r24에 하드웨어
 * 블러까지 덧대 윤곽이 완전히 사라졌는데, 과블러로 배경이 밋밋해져 이를 되돌렸다(세기↓·HW블러 제거,
 * 호출부). 전 버전에서 동작하는 순수 계산이라(안드로이드 효과 비의존) 유닛 테스트로 고정한다.
 */

/** 커버 → 블러된 배경 비트맵. [basePx]는 블러 작업 해상도(최대 변), [radius]는 블러 세기. */
fun backdropFromCover(cover: Bitmap, basePx: Int = 260, radius: Int = 5): Bitmap? = runCatching {
    val cw = cover.width.coerceAtLeast(1)
    val ch = cover.height.coerceAtLeast(1)
    val ratio = cw.toFloat() / ch
    val w = if (ratio >= 1f) basePx else (basePx * ratio).toInt().coerceAtLeast(1)
    val h = if (ratio >= 1f) (basePx / ratio).toInt().coerceAtLeast(1) else basePx
    val base = Bitmap.createScaledBitmap(cover, w, h, true)
    val px = IntArray(w * h)
    base.getPixels(px, 0, w, 0, 0, w, h)
    if (base !== cover) base.recycle()
    val blurred = boxBlur(px, w, h, radius.coerceAtMost(minOf(w, h) / 2).coerceAtLeast(1))
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(blurred, 0, w, 0, 0, w, h) }
}.getOrNull()

/**
 * ARGB 픽셀 배열에 박스 블러를 3패스(≈가우시안) 적용해 새 배열을 돌려준다. 가로·세로로 분리해
 * 각 픽셀을 [-radius, +radius] 창의 평균으로 바꾼다(가장자리는 좌표를 가둬 처리). 순수 함수.
 */
internal fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
    if (radius < 1 || w <= 0 || h <= 0 || pixels.size < w * h) return pixels
    var cur = pixels.copyOf()
    val buf = IntArray(pixels.size)
    repeat(3) {
        blurHorizontal(cur, buf, w, h, radius)
        blurVertical(buf, cur, w, h, radius)
    }
    return cur
}

private fun blurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val win = 2 * r + 1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            var a = 0; var rr = 0; var gg = 0; var bb = 0
            for (k in -r..r) {
                val xx = (x + k).coerceIn(0, w - 1)
                val p = src[row + xx]
                a += (p ushr 24) and 0xFF
                rr += (p ushr 16) and 0xFF
                gg += (p ushr 8) and 0xFF
                bb += p and 0xFF
            }
            dst[row + x] = pack(a / win, rr / win, gg / win, bb / win)
        }
    }
}

private fun blurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val win = 2 * r + 1
    for (x in 0 until w) {
        for (y in 0 until h) {
            var a = 0; var rr = 0; var gg = 0; var bb = 0
            for (k in -r..r) {
                val yy = (y + k).coerceIn(0, h - 1)
                val p = src[yy * w + x]
                a += (p ushr 24) and 0xFF
                rr += (p ushr 16) and 0xFF
                gg += (p ushr 8) and 0xFF
                bb += p and 0xFF
            }
            dst[y * w + x] = pack(a / win, rr / win, gg / win, bb / win)
        }
    }
}

private fun pack(a: Int, r: Int, g: Int, b: Int): Int =
    (a shl 24) or (r shl 16) or (g shl 8) or b
