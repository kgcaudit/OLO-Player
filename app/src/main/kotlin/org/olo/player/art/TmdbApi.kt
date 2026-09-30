package org.olo.player.art

import java.net.URLEncoder

/**
 * The TMDB v3 request URLs and image URLs, built as pure strings so they can be
 * checked without a network. Nothing here holds the API key beyond the moment it
 * is placed in a query; the key itself lives in settings, never in the repo.
 */
object TmdbApi {

    const val API_BASE = "https://api.themoviedb.org/3"
    const val IMAGE_BASE = "https://image.tmdb.org/t/p/"

    /** Poster art size; w342 is the row/grid sweet spot -- sharp on a phone, small
     *  on the wire. */
    const val POSTER_SIZE = "w342"

    /** Episode still size; a 16:9 frame, narrower than a poster. */
    const val STILL_SIZE = "w300"

    fun searchMovieUrl(apiKey: String, title: String, year: Int?, language: String): String =
        buildString {
            append("$API_BASE/search/movie?api_key=").append(enc(apiKey))
            append("&query=").append(enc(title))
            append("&include_adult=false&language=").append(enc(language))
            if (year != null) append("&year=").append(year)
        }

    fun searchTvUrl(apiKey: String, title: String, language: String): String =
        buildString {
            append("$API_BASE/search/tv?api_key=").append(enc(apiKey))
            append("&query=").append(enc(title))
            append("&include_adult=false&language=").append(enc(language))
        }

    fun episodeUrl(apiKey: String, seriesId: Int, season: Int, episode: Int, language: String): String =
        "$API_BASE/tv/$seriesId/season/$season/episode/$episode" +
            "?api_key=${enc(apiKey)}&language=${enc(language)}"

    /** The full URL for a poster path ("/abc.jpg") from a search result, or null
     *  when the result carries no art. */
    fun posterUrl(path: String?, size: String = POSTER_SIZE): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE$size$it" }

    /** The full URL for an episode still path, or null when there is none. */
    fun stillUrl(path: String?, size: String = STILL_SIZE): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE$size$it" }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
}

/**
 * Stable keys for the on-disk match cache, so the same title resolves once and is
 * never re-queried. The key folds a title to its comparable core (see below) and
 * stamps the kind, season and episode, so a movie and a same-named series, or two
 * episodes of one drama, never collide.
 */
object PosterCacheKey {

    fun movie(title: String, year: Int?): String =
        "movie:${core(title)}:${year ?: ""}"

    fun series(title: String): String =
        "tv:${core(title)}"

    fun episode(series: String, season: Int, episode: Int): String =
        "tv:${core(series)}:s$season:e$episode"

    // Same folding as the ranker uses, so a key is stable across the punctuation
    // and case noise that file names carry.
    private fun core(text: String): String =
        text.lowercase().replace(NON_ALNUM, "")

    private val NON_ALNUM = Regex("""[^\p{L}\p{Nd}]""")
}
