package org.olo.player.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.olo.player.ftp.RemoteEntry
import org.robolectric.RobolectricTestRunner

/**
 * The single-film-folder probe: a folder is treated as a film only when it holds
 * exactly one video, and any listing failure falls back to an ordinary folder --
 * so the shortcut never hides a real folder or surfaces an error of its own.
 */
@RunWith(RobolectricTestRunner::class)
class FilmFolderProbeTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun file(name: String) = RemoteEntry(name, false, "/m/$name")
    private fun dirEntry(name: String) = RemoteEntry(name, true, "/m/$name")
    private val folder = dirEntry("어느 영화 (2024)")

    @Test
    fun `one video is a film folder`() = runBlocking {
        val sub = listOf(file("movie.mkv"), file("poster.jpg"), file("movie.nfo"))
        val r = probeFilmFolder(ctx, folder, { sub }, null)
        assertTrue("expected Film, was $r", r is FolderProbe.Film)
        assertEquals("movie.mkv", (r as FolderProbe.Film).video.name)
    }

    @Test
    fun `two videos is a plain folder`() = runBlocking {
        val sub = listOf(file("cd1.mkv"), file("cd2.mkv"))
        assertEquals(FolderProbe.Plain, probeFilmFolder(ctx, folder, { sub }, null))
    }

    @Test
    fun `no video is a plain folder`() = runBlocking {
        val sub = listOf(file("readme.txt"), file("cover.jpg"))
        assertEquals(FolderProbe.Plain, probeFilmFolder(ctx, folder, { sub }, null))
    }

    @Test
    fun `a subfolder beside the one video still counts as a film folder`() = runBlocking {
        // A movie folder often has a Subs/ or Extras/ subfolder; one video still wins.
        val sub = listOf(file("movie.mp4"), dirEntry("Subs"))
        assertTrue(probeFilmFolder(ctx, folder, { sub }, null) is FolderProbe.Film)
    }

    @Test
    fun `a listing failure falls back to a plain folder`() = runBlocking {
        assertEquals(
            FolderProbe.Plain,
            probeFilmFolder(ctx, folder, { throw java.io.IOException("broken pipe") }, null),
        )
    }
}
