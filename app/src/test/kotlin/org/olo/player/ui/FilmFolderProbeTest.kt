package org.olo.player.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.olo.player.ftp.RemoteEntry
import org.robolectric.RobolectricTestRunner

/**
 * The media-folder probe: one video is a 단일영화 (play != null, "폴더" 배지, tap plays),
 * several videos are a 드라마 시리즈 (play == null, "시리즈" 배지, tap enters), no video is
 * an ordinary folder, and any listing failure falls back to Plain -- so the shortcut
 * never hides a real folder or surfaces an error of its own.
 */
@RunWith(RobolectricTestRunner::class)
class FilmFolderProbeTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun file(name: String) = RemoteEntry(name, false, "/m/$name")
    private fun dirEntry(name: String) = RemoteEntry(name, true, "/m/$name")
    private val folder = dirEntry("어느 영화 (2024)")

    @Test
    fun `one video is a film folder that plays`() = runBlocking {
        val sub = listOf(file("movie.mkv"), file("poster.jpg"), file("movie.nfo"))
        val r = probeMediaFolder(ctx, folder, { sub }, null)
        assertTrue("expected Media, was $r", r is FolderProbe.Media)
        r as FolderProbe.Media
        assertNotNull("single film should be playable", r.play)
        assertEquals("movie.mkv", r.play?.name)
        assertEquals("폴더", r.badge)
    }

    @Test
    fun `several videos is a series folder that is entered`() = runBlocking {
        val sub = listOf(file("S01E01.mkv"), file("S01E02.mkv"), file("S01E03.mkv"))
        val r = probeMediaFolder(ctx, folder, { sub }, null)
        assertTrue("expected Media, was $r", r is FolderProbe.Media)
        r as FolderProbe.Media
        assertNull("a series card enters the folder, not plays", r.play)
        assertEquals("시리즈", r.badge)
        assertEquals("S01E01.mkv", r.posterName)
    }

    @Test
    fun `no video is a plain folder`() = runBlocking {
        val sub = listOf(file("readme.txt"), file("cover.jpg"))
        assertEquals(FolderProbe.Plain, probeMediaFolder(ctx, folder, { sub }, null))
    }

    @Test
    fun `a subfolder beside the one video still counts as a film folder`() = runBlocking {
        // A movie folder often has a Subs/ or Extras/ subfolder; one video still wins.
        val sub = listOf(file("movie.mp4"), dirEntry("Subs"))
        val r = probeMediaFolder(ctx, folder, { sub }, null)
        assertTrue(r is FolderProbe.Media && r.play != null)
    }

    @Test
    fun `a listing failure falls back to a plain folder`() = runBlocking {
        assertEquals(
            FolderProbe.Plain,
            probeMediaFolder(ctx, folder, { throw java.io.IOException("broken pipe") }, null),
        )
    }
}
