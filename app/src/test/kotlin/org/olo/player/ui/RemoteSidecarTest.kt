package org.olo.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 사이드카 매칭 고정. 미디어 파일을 옮기/이름변경/삭제할 때 같은 이름의 자막·포스터·nfo가 함께
 * 따라가야 자막·포스터가 고아가 되지 않는다 -- 그 '딸린 파일' 판별이 정확한지(다른 미디어·폴더
 * 공용 커버는 제외) 못박는다.
 */
class RemoteSidecarTest {

    @Test
    fun `same-basename subtitles poster and nfo are sidecars, other media and folder covers are not`() {
        val siblings = listOf(
            "Movie.2026.1080p.mkv",          // 자기 자신 -- 제외
            "Movie.2026.1080p.ko.srt",       // 사이드카(자막)
            "Movie.2026.1080p.en.srt",       // 사이드카(자막)
            "Movie.2026.1080p.smi",          // 사이드카(자막)
            "Movie.2026.1080p.nfo",          // 사이드카(메타)
            "Movie.2026.1080p-poster.jpg",   // 사이드카(포스터)
            "Movie.2026.1080p.mp3",          // 다른 미디어(사운드트랙) -- 제외
            "poster.jpg",                    // 폴더 공용 커버 -- 제외(기본이름으로 시작 안 함)
            "Other.Movie.mkv",               // 다른 작품 -- 제외
            "Other.Movie.srt",               // 다른 작품의 사이드카 -- 제외
        )
        val got = remoteSidecarNames("Movie.2026.1080p.mkv", siblings).toSet()
        assertEquals(
            setOf("Movie.2026.1080p.ko.srt", "Movie.2026.1080p.en.srt", "Movie.2026.1080p.smi", "Movie.2026.1080p.nfo", "Movie.2026.1080p-poster.jpg"),
            got,
        )
    }

    @Test
    fun `a sidecar belonging to a more specific other video is not bundled`() {
        // 같은 폴더에 "Movie"와 "Movie.Part2"가 있을 때, "Movie.Part2.srt"는 Part2의 것이므로
        // "Movie.mkv"를 옮길 때 딸려가면 안 된다(더 긴·구체적 이름이 임자).
        val siblings = listOf("Movie.mkv", "Movie.Part2.mkv", "Movie.srt", "Movie.Part2.srt")
        assertEquals(setOf("Movie.srt"), remoteSidecarNames("Movie.mkv", siblings).toSet())
        assertEquals(setOf("Movie.Part2.srt"), remoteSidecarNames("Movie.Part2.mkv", siblings).toSet())
    }

    @Test
    fun `underscore-joined companion is a sidecar`() {
        val got = remoteSidecarNames("Film.mkv", listOf("Film.mkv", "Film_kor.srt")).toSet()
        assertEquals(setOf("Film_kor.srt"), got)
    }
}
