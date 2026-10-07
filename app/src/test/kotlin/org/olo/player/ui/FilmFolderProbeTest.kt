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
 * several videos OF ONE WORK are a 드라마 시리즈 (play == null, count = 영상 수, tap enters).
 * 서로 다른 영상이 여럿이거나 하위 폴더 안에 영상이 있으면 '영상 모음 폴더'(Videos, count =
 * 직속+하위1단계 영상, 한 작품 포스터로 위장 안 함)로, 영상이 전혀 없으면 Plain으로 본다.
 * 조회 실패는 null로 떨어뜨려(Plain 캐시 금지) 지름길이 실제 폴더를 가리거나 자체 오류를
 * 내지 않게 한다.
 */
@RunWith(RobolectricTestRunner::class)
class FilmFolderProbeTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun file(name: String) = RemoteEntry(name, false, "/m/$name")
    private fun dirEntry(name: String) = RemoteEntry(name, true, "/m/$name")
    // 경로를 명시해, 하위 폴더 스캔(한 단계)을 경로별 리스트로 흉내 낼 수 있게 한다.
    private fun f(name: String, path: String) = RemoteEntry(name, false, path)
    private fun d(name: String, path: String) = RemoteEntry(name, true, path)
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
    fun `a category folder whose subfolders hold videos is a video collection`() = runBlocking {
        // MOVIE 같은 묶음 폴더: 하위 영화 폴더가 여럿이면 한 영화(포스터)로 위장하지 않고 '영상
        // 있는 폴더'로 표기한다. 직속 콘서트 영상 1 + 하위 폴더 3곳의 영상까지 세어 count에 반영.
        val root = dirEntry("MOVIE")
        val tree = mapOf(
            "/m/MOVIE" to listOf(
                f("The.Faith.Tour.mkv", "/m/MOVIE/The.Faith.Tour.mkv"),
                d("러너(2026)", "/m/MOVIE/러너"), d("더스트 버니(2026)", "/m/MOVIE/더스트"), d("사카린(2026)", "/m/MOVIE/사카린"),
            ),
            "/m/MOVIE/러너" to listOf(f("runner.mkv", "/m/MOVIE/러너/runner.mkv")),
            "/m/MOVIE/더스트" to listOf(f("dust.mkv", "/m/MOVIE/더스트/dust.mkv")),
            "/m/MOVIE/사카린" to listOf(f("sac.mkv", "/m/MOVIE/사카린/sac.mkv"), f("sac.srt", "/m/MOVIE/사카린/sac.srt")),
        )
        val r = probeMediaFolder(ctx, root, { tree[it] ?: emptyList() }, null)
        assertTrue("expected Videos, was $r", r is FolderProbe.Videos)
        r as FolderProbe.Videos
        assertEquals("직속 1 + 하위 3", 4, r.count)
    }

    @Test
    fun `several distinct movies in one folder is a video collection, not a series`() = runBlocking {
        // Download 폴더(사용자 사례): 서로 다른 영화가 직속으로 여럿이면 한 작품(포스터)으로
        // 위장하거나 시리즈(겹장)로 보지 않고 '영상 N' 모음으로 표기한다.
        val sub = listOf(
            file("Teenage Sex and Death at Camp Miasma (2024).mkv"),
            file("The Matrix (1999).mkv"),
            file("Oppenheimer (2023).mkv"),
        )
        val r = probeMediaFolder(ctx, dirEntry("Download"), { sub }, null)
        assertTrue("expected Videos, was $r", r is FolderProbe.Videos)
        r as FolderProbe.Videos
        assertEquals(3, r.count)
    }

    @Test
    fun `videos only in subfolders still mark the folder as having videos`() = runBlocking {
        // 직속 영상이 없어도 하위 폴더에 영상이 있으면 '영상 있는 폴더'. count는 하위 1단계 합계.
        val root = dirEntry("DRAMA")
        val tree = mapOf(
            "/m/DRAMA" to listOf(d("시즌1", "/m/DRAMA/시즌1"), d("시즌2", "/m/DRAMA/시즌2")),
            "/m/DRAMA/시즌1" to listOf(f("e01.mkv", "/m/DRAMA/시즌1/e01.mkv"), f("e02.mkv", "/m/DRAMA/시즌1/e02.mkv")),
            "/m/DRAMA/시즌2" to listOf(f("e01.mkv", "/m/DRAMA/시즌2/e01.mkv")),
        )
        val r = probeMediaFolder(ctx, root, { tree[it] ?: emptyList() }, null)
        assertTrue("expected Videos, was $r", r is FolderProbe.Videos)
        r as FolderProbe.Videos
        assertEquals(3, r.count)
    }

    @Test
    fun `a huge category folder caps the nested scan and flags it`() = runBlocking {
        // 느린 원격 트리 보호: 하위 폴더가 상한(24)을 넘으면 거기까지만 세고 capped=true로 'N+'.
        val root = dirEntry("ALL")
        val dirs = (1..30).map { d("f$it", "/m/ALL/f$it") }
        val tree = HashMap<String, List<RemoteEntry>>()
        tree["/m/ALL"] = dirs
        dirs.forEach { tree[it.path] = listOf(f("v.mkv", it.path + "/v.mkv")) }
        val r = probeMediaFolder(ctx, root, { tree[it] ?: emptyList() }, null)
        assertTrue("expected Videos, was $r", r is FolderProbe.Videos)
        r as FolderProbe.Videos
        assertTrue("상한 초과 표식", r.capped)
        assertEquals("상한 24개까지만 집계", 24, r.count)
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
