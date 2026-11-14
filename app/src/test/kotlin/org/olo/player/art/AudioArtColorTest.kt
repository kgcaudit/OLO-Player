package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Test

/** 썸네일 여백색(커버 평균색)의 순수 계산을 고정한다 -- 투명 제외·빈 입력 대체·불투명 보장. */
class AudioArtColorTest {

    @Test
    fun `averages opaque pixels and returns opaque result`() {
        // 빨강 + 파랑 => 평균 (127,0,127), alpha는 항상 불투명(FF).
        val px = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        val argb = averageArgb(px)
        assertEquals(0xFF, (argb ushr 24) and 0xFF)
        assertEquals(127, (argb ushr 16) and 0xFF)
        assertEquals(0, (argb ushr 8) and 0xFF)
        assertEquals(127, argb and 0xFF)
    }

    @Test
    fun `skips near-transparent pixels`() {
        // 거의 투명한 흰색은 평균에서 빠지고, 불투명 빨강만 남는다.
        val px = intArrayOf(0xFFFF0000.toInt(), 0x05FFFFFF)
        val argb = averageArgb(px)
        assertEquals(0xFFFF0000.toInt(), argb)
    }

    @Test
    fun `empty or fully transparent falls back to neutral`() {
        assertEquals(NEUTRAL_BG, averageArgb(intArrayOf()))
        assertEquals(NEUTRAL_BG, averageArgb(intArrayOf(0x00000000, 0x0AFFFFFF)))
    }
}
