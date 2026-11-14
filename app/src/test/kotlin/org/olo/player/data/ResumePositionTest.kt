package org.olo.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 이어보기 위치 저장 규칙. 뷰어(생명주기·주기 저장)와 서비스(onTaskRemoved)가 같은 규칙을
 * 공유하므로, 그 규칙을 한 곳에 못 박는다: 끝 1초 이내는 '다 봤다'로 보고 처음(0)으로 되돌리고,
 * 그 밖에는 현재 위치를 쓰되 음수는 0으로, 길이를 모르면(≤0) 위치만 쓴다.
 */
class ResumePositionTest {

    @Test fun `mid-playback keeps the current position`() {
        assertEquals(42_000L, resumePositionToSave(42_000L, 120_000L))
    }

    @Test fun `within a second of the end resets to the start -- reads as finished`() {
        assertEquals(0L, resumePositionToSave(119_500L, 120_000L))
        assertEquals(0L, resumePositionToSave(120_000L, 120_000L))
    }

    @Test fun `just outside the end grace still keeps its place`() {
        // 끝에서 1초보다 더 남았으면(여기선 2초) 아직 덜 본 것 -- 위치 유지.
        assertEquals(118_000L, resumePositionToSave(118_000L, 120_000L))
    }

    @Test fun `an unknown duration keeps the position -- cannot judge the end yet`() {
        assertEquals(30_000L, resumePositionToSave(30_000L, 0L))
        assertEquals(30_000L, resumePositionToSave(30_000L, -1L))
    }

    @Test fun `a negative position is floored to zero`() {
        assertEquals(0L, resumePositionToSave(-5L, 0L))
    }

    @Test fun `the start of a long file is kept, not mistaken for finished`() {
        assertEquals(0L, resumePositionToSave(0L, 120_000L))
        assertEquals(1_000L, resumePositionToSave(1_000L, 120_000L))
    }
}
