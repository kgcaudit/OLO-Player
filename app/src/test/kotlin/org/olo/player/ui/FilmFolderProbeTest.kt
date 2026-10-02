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
 * The media-folder probe: one video is a 단일영화 (play != null, count 1, tap plays),
 * several videos are a 드라마 시리즈 (play == null, count = 영상 수, tap enters), no video is
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
        assertEquals(1, r.count)
    }

    @Test
    fun `a single-film folder carries its sidecar subtitle for direct play`() = runBlocking {
        // 바로재생 경로가 외장 자막을 붙일 수 있게, 프로브가 같은 폴더의 자막을 Media.subs에
        // 담아야 한다(영상과 이름이 맞는 것만; 포스터·nfo·다른 작품 자막은 제외).
        val sub = listOf(
            file("Dust.Bunny.2025.1080p.mkv"),
            file("Dust.Bunny.2025.1080p.ko.srt"),
            file("Dust.Bunny.2025.1080p.smi"),
            file("poster.jpg"),
            file("Other.Movie.srt"),
        )
        val r = probeMediaFolder(ctx, folder, { sub }, null)
        r as FolderProbe.Media
        val names = r.subs.map { it.name }.toSet()
        assertEquals(setOf("Dust.Bunny.2025.1080p.ko.srt", "Dust.Bunny.2025.1080p.smi"), names)
    }

    @Test
    fun `several videos is a series folder that is entered`() = runBlocking {
        val sub = listOf(file("S01E01.mkv"), file("S01E02.mkv"), file("S01E03.mkv"))
        val r = probeMediaFolder(ctx, folder, { sub }, null)
        assertTrue("expected Media, was $r", r is FolderProbe.Media)
        r as FolderProbe.Media
        assertNull("a series card enters the folder, not plays", r.play)
        assertEquals("세 편짜리 시리즈는 count 3", 3, r.count)
        // 시리즈 포스터는 폴더명을 시리즈 제목으로 TMDB TV 검색하도록 "<폴더명> S01E01" 합성 질의.
        assertEquals("어느 영화 (2024) S01E01", r.posterName)
    }

    @Test
    fun `a category folder of many subfolders with one stray video is plain`() = runBlocking {
        // MOVIE 같은 묶음 폴더: 하위 영화 폴더가 여럿인데 콘서트 영상 하나가 섞여 있어도
        // 단일영화로 오인하지 않는다.
        val sub = listOf(
            file("The.Faith.Tour.mkv"),
            dirEntry("러너(2026)"), dirEntry("더스트 버니(2026)"), dirEntry("사카린(2026)"),
        )
        assertEquals(FolderProbe.Plain, probeMediaFolder(ctx, folder, { sub }, null))
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
    fun `a listing failure is undetermined (null), not cached as plain`() = runBlocking {
        // 조회 실패는 null -- 호출부가 캐시하지 않고 나중에 재시도한다(Plain으로 굳지 않음).
        assertNull(probeMediaFolder(ctx, folder, { throw java.io.IOException("broken pipe") }, null))
    }
}
