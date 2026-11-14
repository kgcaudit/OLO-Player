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
    fun poster(title: MediaTitle): String? = (posterLookup(title) as? PosterLookup.Hit)?.url

    /**
     * 포스터 해석 결과를 사유까지 담아 돌려준다(Hit/Ambiguous/NoData/None). 빈 타일을 "후보
     * 있음"과 "자료 없음"으로 구분해 그리기 위해 [poster]와 달리 사유를 보존한다.
     */
    fun posterLookup(title: MediaTitle): PosterLookup {
        if (apiKey.isBlank()) return PosterLookup.None
        return when (title) {
            is MediaTitle.Movie -> lookupFrom(movieOutcome(title))
            is MediaTitle.Episode -> lookupFrom(seriesOutcome(title.series))
            MediaTitle.Unknown -> PosterLookup.None
        }
    }

    // 매칭 결과 → 표시 사유. 포스터가 있으면 Hit; 후보는 봤는데 확신 매칭이 없었으면 Ambiguous
    // (동명작 다수 — 수동 선택 여지); 어느 검색어도 결과가 0건이었으면 NoData(자료 없음).
    private fun lookupFrom(o: Outcome): PosterLookup {
        val path = o.match?.posterPath
        return when {
            path != null -> TmdbApi.posterUrl(path)?.let { PosterLookup.Hit(it) } ?: PosterLookup.NoData
            o.sawResults -> PosterLookup.Ambiguous
            else -> PosterLookup.NoData
        }
    }

    /**
     * 사용자가 포스터를 직접 고를 수 있도록, 제목 검색 결과를 후보 목록으로 돌려준다
     * (자동 매칭 best 하나가 아니라 여러 개). [tv]면 TV 시리즈, 아니면 영화 검색.
     * 각 후보는 포스터 URL·제목·연도·개요를 담는다. 실패하면 빈 목록.
     */
    fun search(query: String, year: Int?, tv: Boolean): List<TmdbResult> {
        if (apiKey.isBlank() || query.isBlank()) return emptyList()
        val url = if (tv) TmdbApi.searchTvUrl(apiKey, query, language) else TmdbApi.searchMovieUrl(apiKey, query, year, language)
        val body = get(url) ?: return emptyList()
        return runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            (0 until results.length()).mapNotNull { i ->
                val o = results.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optInt("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                val title = o.optString(if (tv) "name" else "title", "").ifBlank { return@mapNotNull null }
                TmdbResult(
                    id = id,
                    title = title,
                    year = o.optStringOrNull(if (tv) "first_air_date" else "release_date")?.take(4)?.toIntOrNull(),
                    posterUrl = TmdbApi.posterUrl(o.optStringOrNull("poster_path")),
                    overview = o.optString("overview", ""),
                    tv = tv,
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 상세정보용 작품 메타(줄거리·평점·러닝타임·장르·등급·출연 등). 포스터와 같은 방식으로
     * 작품을 먼저 찾고(movieMatch/seriesMatch), 그 id로 상세를 한 번 받아 [MediaDetails]로
     * 접는다. 포스터 해석과 같은 매칭을 타므로 상세가 그리드 포스터와 같은 작품을 가리킨다.
     * 실패(키 없음·미매칭·네트워크·JSON)는 모두 null -- 상세는 그때 파일 정보만 보여 준다.
     */
    fun details(title: MediaTitle): MediaDetails? {
        if (apiKey.isBlank()) return null
        return when (title) {
            is MediaTitle.Movie -> {
                val id = movieMatch(title)?.id ?: return null
                get(TmdbApi.movieDetailsUrl(apiKey, id, language))?.let { TmdbDetails.parse(it, tv = false) }
            }
            is MediaTitle.Episode -> {
                val id = seriesMatch(title.series)?.id ?: return null
                get(TmdbApi.tvDetailsUrl(apiKey, id, language))?.let { TmdbDetails.parse(it, tv = true) }
            }
            MediaTitle.Unknown -> null
        }
    }

    /**
     * 사용자가 포스터 변경에서 고른 작품의 상세를, 제목 재매칭 없이 그 TMDB id로 바로 받는다
     * (포스터 변경 → 메타데이터 연동). [tv]면 TV, 아니면 영화. 실패는 null.
     */
    fun detailsById(id: Int, tv: Boolean): MediaDetails? {
        if (apiKey.isBlank()) return null
        val url = if (tv) TmdbApi.tvDetailsUrl(apiKey, id, language) else TmdbApi.movieDetailsUrl(apiKey, id, language)
        return get(url)?.let { TmdbDetails.parse(it, tv) }
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

    // 매칭 결과 + "검색 결과를 하나라도 봤는지". 후자로 '자료 없음(0건)'과 '후보 있음(미확정)'을
    // 가른다.
    private data class Outcome(val match: TmdbCandidate?, val sawResults: Boolean)

    private fun movieOutcome(movie: MediaTitle.Movie): Outcome {
        // 연도를 API 필터로 넘기지 않는다 -- 나라마다 개봉연도가 달라 TMDB가 그 연도의 작품을
        // 빼버려(예: 일본 2022/국내 2023) 매칭이 통째로 실패할 수 있다. 후보를 넓게 받고
        // 연도 근접도는 TmdbMatch가 점수로 가린다. 전체 제목이 0건이면(붙여쓴 합성 부제가
        // TMDB 검색 토큰과 안 맞는 경우) 앞머리로 다시 넓게 찾는다(queryVariants 참고).
        var saw = false
        for (q in queryVariants(movie.title)) {
            val json = get(TmdbApi.searchMovieUrl(apiKey, q, null, language)) ?: continue
            val cands = candidates(json, titleKey = "title", dateKey = "release_date")
            if (cands.isNotEmpty()) saw = true
            TmdbMatch.best(movie.title, movie.year, cands)?.let { return Outcome(it, true) }
        }
        return Outcome(null, saw)
    }

    private fun seriesOutcome(series: String): Outcome {
        var saw = false
        for (q in queryVariants(series)) {
            val json = get(TmdbApi.searchTvUrl(apiKey, q, language)) ?: continue
            val cands = candidates(json, titleKey = "name", dateKey = "first_air_date")
            if (cands.isNotEmpty()) saw = true
            TmdbMatch.best(series, null, cands)?.let { return Outcome(it, true) }
        }
        return Outcome(null, saw)
    }

    private fun movieMatch(movie: MediaTitle.Movie): TmdbCandidate? = movieOutcome(movie).match
    private fun seriesMatch(series: String): TmdbCandidate? = seriesOutcome(series).match

    // TMDB 검색은 공백(토큰) 경계에 민감해, 폴더가 "수플레섬의"처럼 붙여 쓰면 저장된
    // "수플레 섬의"를 못 찾아 0건이 된다(로컬 점수는 공백을 무시해 괜찮지만, 애초에 후보가
    // 안 와서 소용없다). 그래서 전체 제목으로 먼저 찾고, 실패하면 잘 띄어쓴 앞머리(보통
    // 시리즈명)로 점점 짧혀 다시 찾는다. 넓은 검색이 올바른 작품을 포함하기만 하면, 공백을
    // 무시하는 TmdbMatch가 전체 제목 기준으로 정확히 집어낸다. 호출 폭주를 막으려 최대 2개.
    private fun queryVariants(title: String): List<String> {
        // 전체 제목으로 먼저 찾고, 실패 시 앞 3단어(보통 시리즈명)로 한 번만 더 찾는다.
        // 네트워크 부담을 줄이려 최대 2회로 제한한다(미매칭 항목이 많은 폴더에서 호출 폭주 방지).
        val words = title.trim().split(WHITESPACE).filter { it.isNotBlank() }
        val variants = linkedSetOf(title)
        if (words.size > 3) variants.add(words.take(3).joinToString(" "))
        return variants.toList()
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
        val WHITESPACE = Regex("""\s+""")
    }
}
