package org.olo.player.art

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LRCLIB 자유텍스트(q=) 검색 URL이 공백·유니코드를 인코딩하는지 고정한다(가사 적중률 개선의 핵심 경로). */
class LyricsQueryUrlTest {

    @Test fun `free-text query url encodes spaces and unicode`() {
        val url = LrcLibApi.searchQueryUrl("tuki 晩餐歌")
        assertTrue(url.startsWith("https://lrclib.net/api/search?q="))
        assertTrue(url.contains("tuki"))
        assertFalse(url.contains(" ")) // 공백은 +/%20 로 인코딩
        assertFalse(url.contains("晩")) // 유니코드는 % 인코딩
    }
}
