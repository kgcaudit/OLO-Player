package org.olo.player.art

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** LRCLIB 후보 파싱과 가사 저장(앱 보관·사이드카)을 네트워크·기기 없이 고정한다. (org.json 때문에 Robolectric.) */
@RunWith(RobolectricTestRunner::class)
class LyricsHitAndStoreTest {

    @Test
    fun `parseGetHit keeps synced and plain`() {
        val body = """{"trackName":"곡","artistName":"가수","albumName":"앨범","duration":230.5,
            "syncedLyrics":"[00:01.00]가","plainLyrics":"가"}"""
        val hit = LrcLibApi.parseGetHit(body)!!
        assertEquals("곡", hit.trackName)
        assertEquals(230, hit.durationSec)
        assertTrue(hit.hasSynced)
        assertEquals("[00:01.00]가", hit.synced)
        assertEquals("가", hit.plain)
        assertEquals("[00:01.00]가", hit.best) // 저장은 동기 우선
    }

    @Test
    fun `parseGetHit on instrumental is null`() {
        val body = """{"trackName":"곡","artistName":"가수","instrumental":true,"syncedLyrics":"","plainLyrics":""}"""
        assertNull(LrcLibApi.parseGetHit(body))
    }

    @Test
    fun `parseSearchHits drops entries without lyrics`() {
        val body = """[
            {"trackName":"A","artistName":"X","syncedLyrics":"[00:00.00]a","plainLyrics":"a"},
            {"trackName":"B","artistName":"Y","syncedLyrics":"","plainLyrics":""},
            {"trackName":"C","artistName":"Z","plainLyrics":"c"}
        ]"""
        val hits = LrcLibApi.parseSearchHits(body)
        assertEquals(2, hits.size)
        assertEquals("A", hits[0].trackName)
        assertFalse(hits[1].hasSynced) // C: 일반만
        assertEquals("c", hits[1].plain)
    }

    @Test
    fun `app store round-trips by prefKey`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val key = "ftp://host/music/곡.flac"
        assertNull(LyricsStore.readAppStore(ctx, key))
        assertTrue(LyricsStore.writeAppStore(ctx, key, "[00:00.00]가사"))
        assertEquals("[00:00.00]가사", LyricsStore.readAppStore(ctx, key))
        // 다른 곡은 섞이지 않는다.
        assertNull(LyricsStore.readAppStore(ctx, "ftp://host/music/다른곡.flac"))
    }

    @Test
    fun `sidecar writes next to the song when the folder is writable`() {
        val dir = File.createTempFile("olo", "").let { it.delete(); it.mkdirs(); it }
        val audio = File(dir, "song.mp3").apply { writeText("x") }
        assertTrue(LyricsStore.writeSidecarIfPossible(audio, "[00:00.00]가"))
        val lrc = LyricsStore.sidecarFile(audio)
        assertEquals("song.lrc", lrc.name)
        assertEquals("[00:00.00]가", lrc.readText())
    }
}
