package org.olo.player.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 열 때마다 [PlayerViewModel.MediaViewer.openId]가 새로 붙는지 고정한다. 같은 폴더의 다른 곡을
 * 열면 items·index만 바뀌고 openId가 달라져야, 재생 엔진이 '사용자가 직접 골랐다'를 알아 그 곡으로
 * 이동한다(회전·복귀는 openId가 그대로라 재생을 건드리지 않는다).
 */
class MediaViewerOpenIdTest {

    @Test
    fun `each open gets a distinct id`() {
        val a = PlayerViewModel.MediaViewer(emptyList(), 0)
        val b = PlayerViewModel.MediaViewer(emptyList(), 0)
        assertNotEquals(a.openId, b.openId)
        assertTrue(b.openId > a.openId) // 단조 증가
    }
}
