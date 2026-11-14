package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 박스 블러가 치수를 보존하고, 뚜렷한 경계를 부드럽게(대비를 낮춰) 만드는지 고정한다. */
class CoverBackdropTest {

    @Test
    fun `blur preserves size and length`() {
        val w = 8; val h = 8
        val px = IntArray(w * h) { 0xFF808080.toInt() }
        val out = boxBlur(px, w, h, 2)
        assertEquals(px.size, out.size)
        // 균일한 입력은 블러 후에도 그대로(평균=자기자신).
        assertTrue(out.all { it == 0xFF808080.toInt() })
    }

    @Test
    fun `blur softens a hard edge`() {
        val w = 16; val h = 1 // 왼쪽 절반 검정, 오른쪽 절반 흰색의 한 줄.
        val px = IntArray(w) { if (it < w / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = boxBlur(px, w, h, 3)
        // 경계 바로 왼쪽 픽셀은 더 이상 순수 검정이 아니다(이웃 흰색이 섞여 밝아짐).
        val left = out[w / 2 - 1] and 0xFF
        val right = out[w / 2] and 0xFF
        assertTrue("경계 왼쪽이 밝아짐", left > 0)
        assertTrue("경계 오른쪽이 어두워짐", right < 255)
    }

    @Test
    fun `radius below one returns input unchanged`() {
        val px = intArrayOf(0xFF102030.toInt(), 0xFF405060.toInt())
        assertEquals(px, boxBlur(px, 2, 1, 0))
    }
}
