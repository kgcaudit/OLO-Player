package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 가사 파서·LRCLIB URL/파싱(순수부)을 네트워크 없이 고정한다. (org.json 때문에 Robolectric 러너.) */
@RunWith(RobolectricTestRunner::class)
class LyricsTest {

    @Test
    fun `parse synced lrc sorts by time and splits repeated stamps`() {
        val lrc = """
            [ti:좋은 날]
            [ar:아이유]
            [00:12.50]첫 줄
            [00:10.00][01:00.00]반복되는 후렴
        """.trimIndent()
        val ly = LyricsParser.parse(lrc)
        assertTrue(ly.synced)
        // 시각순 정렬: 10.0 → 12.5 → 60.0. 반복 스탬프는 각각 한 줄.
        assertEquals(listOf(10_000L, 12_500L, 60_000L), ly.lines.map { it.timeMs })
        assertEquals("반복되는 후렴", ly.lines.first().text)
    }

    @Test
    fun `current index follows playback and is -1 before first line`() {
        val ly = LyricsParser.parse("[00:05.00]A\n[00:10.00]B")
        assertEquals(-1, LyricsParser.currentIndex(ly, 0L))
        assertEquals(0, LyricsParser.currentIndex(ly, 6_000L))
        assertEquals(1, LyricsParser.currentIndex(ly, 12_000L))
    }

    @Test
    fun `plain lyrics have no sync and trim surrounding blanks`() {
        val ly = LyricsParser.parse("\n\n가사 첫 줄\n둘째 줄\n\n")
        assertFalse(ly.synced)
        assertEquals(listOf("가사 첫 줄", "둘째 줄"), ly.lines.map { it.text })
        assertTrue(ly.lines.all { it.timeMs == NO_SYNC })
        assertEquals(-1, LyricsParser.currentIndex(ly, 99_000L)) // 비동기는 짚지 않는다
    }

    @Test
    fun `offset tag shifts every line earlier`() {
        val ly = LyricsParser.parse("[offset:+500]\n[00:10.00]줄")
        assertEquals(9_500L, ly.lines.single().timeMs)
    }

    @Test
    fun `lrclib get url carries encoded track artist and optional duration`() {
        val url = LrcLibApi.getUrl(artist = "아이유", title = "밤편지", album = "Palette", durationSec = 221)
        assertTrue(url.startsWith("https://lrclib.net/api/get?"))
        assertTrue(url.contains("track_name="))
        assertTrue(url.contains("&artist_name="))
        assertTrue(url.contains("&album_name="))
        assertTrue(url.contains("&duration=221"))
        assertTrue("한글 인코딩", url.contains("%EB%B0%A4%ED%8E%B8%EC%A7%80")) // 밤편지
        assertFalse(url.contains("밤편지"))
    }

    @Test
    fun `lrclib get url omits duration when not positive`() {
        assertFalse(LrcLibApi.getUrl("a", "b", durationSec = 0).contains("duration"))
    }

    @Test
    fun `parse get prefers synced over plain`() {
        val body = """{"syncedLyrics":"[00:01.00]hi","plainLyrics":"hi","instrumental":false}"""
        assertEquals("[00:01.00]hi", LrcLibApi.parseGet(body))
    }

    @Test
    fun `parse get falls back to plain, and instrumental yields null`() {
        assertEquals("hi\nthere", LrcLibApi.parseGet("""{"plainLyrics":"hi\nthere"}"""))
        assertEquals(null, LrcLibApi.parseGet("""{"instrumental":true}"""))
    }

    @Test
    fun `parse search returns first candidate with lyrics`() {
        val body = """[{"plainLyrics":""},{"syncedLyrics":"[00:02.00]x"}]"""
        assertEquals("[00:02.00]x", LrcLibApi.parseSearch(body))
    }

    @Test
    fun `parsers return null on garbage, never throw`() {
        assertEquals(null, LrcLibApi.parseGet("nope"))
        assertEquals(null, LrcLibApi.parseSearch("{}"))
    }
}
