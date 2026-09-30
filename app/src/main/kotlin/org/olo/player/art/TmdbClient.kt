package org.olo.player.art

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Turns a parsed [MediaTitle] into a TMDB image URL. Two intents are kept apart on
 * purpose:
 *
 *  - [poster] gives the 2:3 poster the browse rows and gallery show. A movie's own
 *    poster; a drama episode's *series* poster -- because a 16:9 still squashed
 *    into a 2:3 row breaks the column rhythm (a design decision confirmed from the
 *    mockup).
 *  - [still] gives the 16:9 episode frame, for the player/detail where a wide image
 *    fits. Reserved for later use.
 *
 * A movie is one search; a series poster is one search; a still is a search then an
 * episode lookup. Every failure -- no key, no network, no match, bad JSON --
 * returns null, because artwork is layered over browsing and must never break it.
 *
 * The requests block, so this runs on a background thread (the caller's IO
 * dispatcher). The API key is only ever placed in a request; it is never logged.
 */
class TmdbClient(
    private val apiKey: String,
    private val language: String = "ko-KR",
) {

    /** The 2:3 poster URL for a movie or a drama's series, or null. */
    fun poster(title: MediaTitle): String? {
        if (apiKey.isBlank()) return null
        return when (title) {
            is MediaTitle.Movie -> TmdbApi.posterUrl(movieMatch(title)?.posterPath)
            is MediaTitle.Episode -> TmdbApi.posterUrl(seriesMatch(title.series)?.posterPath)
            MediaTitle.Unknown -> null
        }
    }

    /** The 16:9 still URL for a specific episode, falling back to the series
     *  poster when that episode has no frame yet, or null. */
    fun still(episode: MediaTitle.Episode): String? {
        if (apiKey.isBlank()) return null
        val series = seriesMatch(episode.series) ?: return null
        val episodeJson = get(TmdbApi.episodeUrl(apiKey, series.id, episode.season, episode.episode, language))
        val stillPath = episodeJson?.let { runCatching { JSONObject(it).optStringOrNull("still_path") }.getOrNull() }
        return TmdbApi.stillUrl(stillPath) ?: TmdbApi.posterUrl(series.posterPath)
    }

    private fun movieMatch(movie: MediaTitle.Movie): TmdbCandidate? {
        val json = get(TmdbApi.searchMovieUrl(apiKey, movie.title, movie.year, language)) ?: return null
        return TmdbMatch.best(movie.title, movie.year, candidates(json, titleKey = "title", dateKey = "release_date"))
    }

    private fun seriesMatch(series: String): TmdbCandidate? {
        val json = get(TmdbApi.searchTvUrl(apiKey, series, language)) ?: return null
        return TmdbMatch.best(series, null, candidates(json, titleKey = "name", dateKey = "first_air_date"))
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
