package org.olo.player.ui

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 포스터 판별의 백오프 재시도([probeWithBackoff]) 고정. 서버 폴더에서 LIST 폭주로 조회가
 * 간헐적으로 실패할 때, 바로 포기해 폴더를 Plain으로 굳히는 대신 몇 번 더(간격을 늘려) 조회해
 * 일시적 실패를 넘기는지 -- 그리고 진짜로 계속 실패하면 정해진 횟수에서 멈추는지 확인한다.
 * (실제 증상: 서버 영화 폴더에서 처음 몇 개만 포스터가 붙고 중간부터 끊김.)
 */
class RemoteProbeBackoffTest {

    private val media: FolderProbe = FolderProbe.Videos(1)

    @Test
    fun `succeeds on the first try without sleeping`() = runTest {
        var calls = 0
        val sleeps = mutableListOf<Long>()
        val r = probeWithBackoff(sleep = { sleeps += it }) { calls++; media }
        assertSame(media, r)
        assertEquals("성공하면 한 번만 조회한다", 1, calls)
        assertEquals("성공하면 대기(백오프)가 없다", emptyList<Long>(), sleeps)
    }

    @Test
    fun `a transient failure then success recovers instead of giving up`() = runTest {
        var calls = 0
        val r = probeWithBackoff(sleep = {}) { calls++; if (calls < 3) null else media }
        assertSame("두 번 실패 뒤 세 번째에 성공하면 그 결과를 돌려준다", media, r)
        assertEquals(3, calls)
    }

    @Test
    fun `persistent failure stops after the retry budget`() = runTest {
        var calls = 0
        val sleeps = mutableListOf<Long>()
        val r = probeWithBackoff(retries = 4, backoffMs = 300L, sleep = { sleeps += it }) { calls++; null }
        assertNull("끝까지 실패하면 null(이후 호출부가 '진짜 실패'로 센다)", r)
        assertEquals("재시도 예산만큼만 조회한다", 4, calls)
        // 간격이 점점 길어져 NAS를 몰아치지 않는다: 300·600·900ms (조회 사이 3번).
        assertEquals(listOf(300L, 600L, 900L), sleeps)
    }
}
