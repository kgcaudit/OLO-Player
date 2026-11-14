package org.olo.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A-B 구간 반복 탭의 3단계 전이를 고정한다: 지정 전→A, A만→B(반복), 그 외→해제. */
class AbTapCycleTest {

    @Test fun `first tap sets A`() {
        assertEquals(64_000L to -1L, abTapCycle(-1L, -1L, 64_000L))
    }

    @Test fun `second tap after A sets B when later`() {
        assertEquals(64_000L to 138_000L, abTapCycle(64_000L, -1L, 138_000L))
    }

    @Test fun `tap while looping clears`() {
        assertEquals(-1L to -1L, abTapCycle(64_000L, 138_000L, 90_000L))
    }

    @Test fun `second tap before A does not set B (clears)`() {
        // A가 찍힌 뒤 A보다 앞에서 누르면 B로 못 잡으니 해제한다(잘못된 구간 방지).
        assertEquals(-1L to -1L, abTapCycle(64_000L, -1L, 30_000L))
    }
}
