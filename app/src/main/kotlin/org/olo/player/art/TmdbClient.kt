package org.olo.player.art

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Turns a parsed [MediaTitle] into a TMDB image URL, doing the one or two GETs it
 * takes: a movie is one search; a drama episode is a series search then an episode
 * lookup for the still, falling back to the series poster when an episode has no
 * frame. Every failure -- no key, no network, no match, bad JSON -- returns null,
 * because artwork is a nice-to-have layered over browsing and must never break it.
 *
 * The requests block, so this runs on a background thread (the caller's IO
 * dispatcher). The API key is only ever placed in a request; it is never logged.
 */
class TmdbClient(
    private val apiKey: String,
    private val language: String = "ko-KR",
) {

    /** The poster (movie / series) or still (episode) URL, or null when there is
     *  nothing confident to show. */
    fun artworkUrl(title: MediaTitle): String? {
        if (apiKey.isBlank()) return null
        return when (title) {
            is MediaTitle.Movie -> moviePoster(title)
            is MediaTitle.Episode -> episodeArt(title)
            MediaTitle.Unknown -> null
        }
    }

    private fun moviePoster(movie: MediaTitle.Movie): String? {
        val json = get(TmdbApi.searchMovieUrl(apiKey, movie.title, movie.year, language)) ?: return null
        val best = TmdbMatch.best(movie.title, movie.year, candidates(json, titleKey = "title", dateKey = "release_date"))
        return TmdbApi.posterUrl(best?.posterPath)
    }

    private fun episodeArt(ep: MediaTitle.Episode): String? {
        val search = get(TmdbApi.searchTvUrl(apiKey, ep.series, language)) ?: return null
        val series = TmdbMatch.best(ep.series, null, candidates(search, titleKey = "name", dateKey = "first_air_date"))
            ?: return null
        // The episode's own still is the point; if that episode has no frame yet,
        // the series poster still tells the person what they are opening.
        val episodeJson = get(TmdbApi.episodeUrl(apiKey, series.id, ep.season, ep.episode, language))
        val still = episodeJson?.let { runCatching { JSONObject(it).optStringOrNull("still_path") }.getOrNull() }
        return TmdbApi.stillUrl(still) ?: TmdbApi.posterUrl(series.posterPath)
    }

    // Map a search response's results[] into ranking candidates. Movies key the
    // title as "title"/"release_date", series as "name"/"first_air_date"; the rest
    // is shared, so one reader serves both.
    private fun candidates(body: String, titleKey: String, dateKey: String): List<TmdbCandidate> =
        runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            (0 until results.length()).mapNotNull { i ->
                val o = results.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optInt("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                TmdbCandidate(
                    id = id,
                    title = o.optString(titleKey, ""),
                    year = o.optStringOrNull(dateKey)?.take(4)?.toIntOrNull(),
                    posterPath = o.optStringOrNull("poster_path"),
                    popularity = o.optDouble("popularity", 0.0),
                )
            }
        }.getOrDefault(emptyList())

    private fun get(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifBlank { null }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
