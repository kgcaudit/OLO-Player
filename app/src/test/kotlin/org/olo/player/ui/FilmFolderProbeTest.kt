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
    fun `a huge video category folder counts past the display cap`() = runBlocking {
        // 하위 폴더가 많아도 표기 상한(10)을 넘는지만 확인하면 되므로 개수는 '최소'값이면 충분.
        // 표기는 "영상 10+". (느린 원격 트리 보호 상한 MAX_NESTED_SCAN까지만 조회한다.)
        val root = dirEntry("ALL")
        val dirs = (1..30).map { d("f$it", "/m/ALL/f$it") }
        val tree = HashMap<String, List<RemoteEntry>>()
        tree["/m/ALL"] = dirs
        dirs.forEach { tree[it.path] = listOf(f("v.mkv", it.path + "/v.mkv")) }
        val r = probeMediaFolder(ctx, root, { tree[it] ?: emptyList() }, null)
        assertTrue("expected Videos, was $r", r is FolderProbe.Videos)
        r as FolderProbe.Videos
        assertTrue("표기 상한(10)을 넘김 → \"10+\"", r.count > COLLECTION_COUNT_CAP)
        assertEquals("영상 10+", collectionCountLabel(MediaCollection(videos = r.count, music = 0)))
    }

    @Test
    fun `count text caps at ten with a plus, common to video and music`() {
        assertEquals("10", collectionCountText(10))
        assertEquals("10+", collectionCountText(11))
        assertEquals("10+", collectionCountText(304))
        assertEquals("영상 10+ · 음악 1", collectionCountLabel(MediaCollection(videos = 39, music = 1)))
        assertEquals("음악 10+", collectionCountLabel(MediaCollection(videos = 0, music = 50)))
        assertEquals("영상 8", collectionCountLabel(MediaCollection(videos = 8, music = 0)))
    }

    @Test
    fun `no video is a plain folder`() = runBlocking {
        val sub = listOf(file("readme.txt"), file("cover.jpg"))
        assertEquals(FolderProbe.Plain, probeMediaFolder(ctx, folder, { sub }, null))
    }

    @Test
    fun `audio only is a music collection`() = runBlocking {
        // 영상 모음과 대칭: 음악만 든 폴더는 Music으로 표기(음악 글리프 + '음악 N').
        val sub = listOf(file("01.flac"), file("02.flac"), file("03.mp3"), file("cover.jpg"))
        val r = probeMediaFolder(ctx, dirEntry("앨범"), { sub }, null)
        assertTrue("expected Music, was $r", r is FolderProbe.Music)
        r as FolderProbe.Music
        assertEquals(3, r.count)
    }

    @Test
    fun `audio only in subfolders still marks a music collection`() = runBlocking {
        // 직속 음악이 없어도 하위 폴더에 음악이 있으면 음악 모음. count는 하위 1단계 합계.
        val root = dirEntry("MUSIC")
        val tree = mapOf(
            "/m/MUSIC" to listOf(d("앨범1", "/m/MUSIC/앨범1"), d("앨범2", "/m/MUSIC/앨범2")),
            "/m/MUSIC/앨범1" to listOf(f("a.mp3", "/m/MUSIC/앨범1/a.mp3"), f("b.mp3", "/m/MUSIC/앨범1/b.mp3")),
            "/m/MUSIC/앨범2" to listOf(f("c.flac", "/m/MUSIC/앨범2/c.flac")),
        )
        val r = probeMediaFolder(ctx, root, { tree[it] ?: emptyList() }, null)
        assertTrue("expected Music, was $r", r is FolderProbe.Music)
        r as FolderProbe.Music
        assertEquals(3, r.count)
    }

    @Test
    fun `video and audio together is a mixed collection`() = runBlocking {
        // 영상·음악이 한 폴더에 섞이면 영상 한 편(포스터)으로 위장하지 않고 혼합으로 묶어
        // 영상·음악 개수를 함께 표기한다(확정 글리프 다-2).
        val sub = listOf(
            file("concert.mkv"),
            file("track1.mp3"), file("track2.mp3"), file("track3.flac"),
            file("cover.jpg"),
        )
        val r = probeMediaFolder(ctx, dirEntry("공연 실황"), { sub }, null)
        assertTrue("expected Mixed, was $r", r is FolderProbe.Mixed)
        r as FolderProbe.Mixed
        assertEquals(1, r.videoCount)
        assertEquals(3, r.musicCount)
    }

    @Test
    fun `mixed media counted across subfolders`() = runBlocking {
        // 카테고리 폴더(직속 영상 없음, 하위 폴더 2곳): 하위 1단계의 영상·음악을 함께 세어 혼합으로
        // 본다. (직속 영상 1편 + 하위폴더 ≤1은 단일영화 지름길이 먼저 잡으므로 여기선 일부러
        // 카테고리 형태로 둔다.)
        val root = dirEntry("자료함")
        val tree = mapOf(
            "/m/자료함" to listOf(d("영상", "/m/자료함/영상"), d("음원", "/m/자료함/음원")),
            "/m/자료함/영상" to listOf(f("clip.mp4", "/m/자료함/영상/clip.mp4")),
            "/m/자료함/음원" to listOf(f("s1.m4a", "/m/자료함/음원/s1.m4a"), f("s2.m4a", "/m/자료함/음원/s2.m4a")),
        )
        val r = probeMediaFolder(ctx, root, { tree[it] ?: emptyList() }, null)
        assertTrue("expected Mixed, was $r", r is FolderProbe.Mixed)
        r as FolderProbe.Mixed
        assertEquals(1, r.videoCount)
        assertEquals(2, r.musicCount)
    }

    @Test
    fun `a lone video with a music subfolder stays a single film (fast path)`() = runBlocking {
        // 단일영화 지름길 보존: 직속 영상 1편 + 하위 폴더 ≤1이면 하위를 뒤지지 않아(영화 한 편마다
        // 네트워크를 때리지 않게) 음악 하위폴더가 있어도 단일영화로 본다. 흔치 않은 조합이라
        // 성능을 우선한다.
        val root = dirEntry("어느 영화 (2024)")
        val tree = mapOf(
            "/m/어느 영화 (2024)" to listOf(f("movie.mkv", "/m/어느 영화 (2024)/movie.mkv"), d("OST", "/m/어느 영화 (2024)/OST")),
            "/m/어느 영화 (2024)/OST" to listOf(f("theme.mp3", "/m/어느 영화 (2024)/OST/theme.mp3")),
        )
        val r = probeMediaFolder(ctx, root, { tree[it] ?: emptyList() }, null)
        assertTrue("expected Media, was $r", r is FolderProbe.Media && r.play != null)
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
