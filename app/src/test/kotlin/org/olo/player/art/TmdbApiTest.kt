package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The request/image URLs and the cache keys. Pinned because a mis-encoded query
 * silently returns the wrong (or no) results, and a colliding cache key would
 * serve one work's poster for another.
 */
class TmdbApiTest {

    @Test
    fun `movie search encodes the query and carries the year`() {
        val url = TmdbApi.searchMovieUrl("KEY", "The Matrix", 1999, "ko-KR")
        assertTrue(url.startsWith("https://api.themoviedb.org/3/search/movie?"))
        assertTrue(url.contains("api_key=KEY"))
        assertTrue(url.contains("query=The+Matrix"))
        assertTrue(url.contains("year=1999"))
        assertTrue(url.contains("language=ko-KR"))
    }

    @Test
    fun `movie search omits the year when unknown`() {
        val url = TmdbApi.searchMovieUrl("KEY", "Akira", null, "ko-KR")
        assertTrue(!url.contains("year="))
    }

    @Test
    fun `tv search has no year param`() {
        val url = TmdbApi.searchTvUrl("KEY", "The Wire", "en-US")
        assertTrue(url.startsWith("https://api.themoviedb.org/3/search/tv?"))
        assertTrue(!url.contains("year="))
    }

    @Test
    fun `episode url embeds series, season and episode`() {
        val url = TmdbApi.episodeUrl("KEY", 1438, 1, 1, "ko-KR")
        assertTrue(url.contains("/tv/1438/season/1/episode/1"))
    }

    @Test
    fun `poster and still URLs build from a path`() {
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", TmdbApi.posterUrl("/abc.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w300/s.jpg", TmdbApi.stillUrl("/s.jpg"))
    }

    @Test
    fun `a null or blank image path is no URL`() {
        assertNull(TmdbApi.posterUrl(null))
        assertNull(TmdbApi.posterUrl(""))
        assertNull(TmdbApi.stillUrl(" "))
    }

    @Test
    fun `cache keys fold punctuation and case but keep kind, year, episode apart`() {
        assertEquals(PosterCacheKey.movie("Spider-Man", 2002), PosterCacheKey.movie("spider man", 2002))
        // A movie and a same-named series must not collide.
        assertTrue(PosterCacheKey.movie("Fargo", 1996) != PosterCacheKey.series("Fargo"))
        // Two episodes of one drama must not collide.
        assertTrue(
            PosterCacheKey.episode("Dark", 1, 1) != PosterCacheKey.episode("Dark", 1, 2),
        )
        // The same movie without a year is a different key than with one.
        assertTrue(PosterCacheKey.movie("Akira", null) != PosterCacheKey.movie("Akira", 1988))
    }
}
