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

    /** 아이덴티티 헤더의 배경(백드롭). 폭이 넓은 16:9라 포스터보다 큰 크기를 쓴다. */
    const val BACKDROP_SIZE = "w780"

    /** 출연진 얼굴 사진. 작은 원형 썸네일이라 가장 작은 인물 크기로 충분하다. */
    const val PROFILE_SIZE = "w185"

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

    // 상세정보 한 번의 호출로 작품 메타 + 출연/제작 + 관람등급을 함께 받는다
    // (append_to_response). 호출 수를 늘리지 않으려 credits·등급을 끼워 넣는다.
    fun movieDetailsUrl(apiKey: String, id: Int, language: String): String =
        "$API_BASE/movie/$id?api_key=${enc(apiKey)}&language=${enc(language)}" +
            "&append_to_response=credits,release_dates"

    fun tvDetailsUrl(apiKey: String, id: Int, language: String): String =
        "$API_BASE/tv/$id?api_key=${enc(apiKey)}&language=${enc(language)}" +
            "&append_to_response=credits,content_ratings"

    /** The full URL for a poster path ("/abc.jpg") from a search result, or null
     *  when the result carries no art. */
    fun posterUrl(path: String?, size: String = POSTER_SIZE): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE$size$it" }

    /** The full URL for an episode still path, or null when there is none. */
    fun stillUrl(path: String?, size: String = STILL_SIZE): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE$size$it" }

    /** 백드롭(배경) 경로의 전체 URL, 없으면 null. */
    fun backdropUrl(path: String?, size: String = BACKDROP_SIZE): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE$size$it" }

    /** 출연진 얼굴 사진 경로의 전체 URL, 없으면 null. */
    fun profileUrl(path: String?, size: String = PROFILE_SIZE): String? =
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
