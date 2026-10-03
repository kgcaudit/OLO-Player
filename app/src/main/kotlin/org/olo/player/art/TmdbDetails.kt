package org.olo.player.art

import org.json.JSONObject

/**
 * TMDB 상세 응답(JSON)을 [MediaDetails]로 접는 순수 파서. 네트워크를 섞지 않아 응답
 * 모양을 유닛테스트로 고정할 수 있다 -- 상세 화면의 "틀린 정보"는 조용히 번지므로
 * (키 하나가 영화/시리즈에서 달라 엉뚱한 칸에 들어가는 식) 파싱을 못으로 박아 둔다.
 *
 * 영화와 시리즈는 키가 다르다(title/name, release_date/first_air_date, runtime/
 * episode_run_time, release_dates/content_ratings). 한 파서가 [tv] 플래그로 양쪽을
 * 읽어, 호출부는 어느 쪽이든 같은 [MediaDetails]를 받는다.
 */
object TmdbDetails {

    // 관람등급은 한국을 먼저, 없으면 미국을 쓴다(국내 사용자 기준, 그다음 가장 흔한 표기).
    private val CERT_COUNTRIES = listOf("KR", "US")

    // 출연진은 상위 몇 명만 -- 상세 카드의 가로 줄에 들어갈 만큼.
    private const val MAX_CAST = 10

    fun parse(body: String, tv: Boolean): MediaDetails? = runCatching {
        val o = JSONObject(body)
        val title = o.stringOrNull(if (tv) "name" else "title")
            ?: o.stringOrNull(if (tv) "original_name" else "original_title")
            ?: return null
        val original = o.stringOrNull(if (tv) "original_name" else "original_title")
        MediaDetails(
            title = title,
            // 원제는 현지 제목과 실제로 다를 때만 의미가 있다(같으면 부제로 중복 표기 안 함).
            originalTitle = original?.takeIf { it != title },
            year = o.stringOrNull(if (tv) "first_air_date" else "release_date")?.take(4)?.toIntOrNull(),
            rating = o.optDouble("vote_average", 0.0).takeIf { it > 0.0 },
            runtimeMinutes = runtimeOf(o, tv),
            genres = namesFrom(o.optJSONArray("genres")),
            certification = certificationOf(o, tv),
            overview = o.optString("overview", ""),
            posterUrl = TmdbApi.posterUrl(o.stringOrNull("poster_path")),
            backdropUrl = TmdbApi.backdropUrl(o.stringOrNull("backdrop_path")),
            director = directorOf(o, tv),
            cast = castOf(o),
            tv = tv,
        )
    }.getOrNull()

    private fun runtimeOf(o: JSONObject, tv: Boolean): Int? =
        if (tv) {
            o.optJSONArray("episode_run_time")?.takeIf { it.length() > 0 }?.optInt(0, 0)?.takeIf { it > 0 }
        } else {
            o.optInt("runtime", 0).takeIf { it > 0 }
        }

    private fun namesFrom(arr: org.json.JSONArray?): List<String> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.stringOrNull("name") }
    }

    // 영화: credits.crew에서 job=Director. 시리즈: created_by(제작자)를 먼저, 없으면 crew Director.
    private fun directorOf(o: JSONObject, tv: Boolean): String? {
        if (tv) {
            val creators = namesFrom(o.optJSONArray("created_by"))
            if (creators.isNotEmpty()) return creators.joinToString(", ")
        }
        val crew = o.optJSONObject("credits")?.optJSONArray("crew") ?: return null
        return (0 until crew.length())
            .mapNotNull { crew.optJSONObject(it) }
            .firstOrNull { it.optString("job") == "Director" }
            ?.stringOrNull("name")
    }

    private fun castOf(o: JSONObject): List<CastMember> {
        val cast = o.optJSONObject("credits")?.optJSONArray("cast") ?: return emptyList()
        return (0 until cast.length())
            .mapNotNull { cast.optJSONObject(it) }
            .mapNotNull { m ->
                val name = m.stringOrNull("name") ?: return@mapNotNull null
                CastMember(name, TmdbApi.profileUrl(m.stringOrNull("profile_path")))
            }
            .take(MAX_CAST)
    }

    // 관람등급: 영화는 release_dates.results[country].release_dates[].certification,
    // 시리즈는 content_ratings.results[country].rating. 한국→미국 순으로 첫 비지 않은 값.
    private fun certificationOf(o: JSONObject, tv: Boolean): String? {
        val results = (if (tv) o.optJSONObject("content_ratings") else o.optJSONObject("release_dates"))
            ?.optJSONArray("results") ?: return null
        val byCountry = (0 until results.length())
            .mapNotNull { results.optJSONObject(it) }
            .associateBy { it.optString("iso_3166_1") }
        for (country in CERT_COUNTRIES) {
            val entry = byCountry[country] ?: continue
            val cert = if (tv) {
                entry.stringOrNull("rating")
            } else {
                entry.optJSONArray("release_dates")
                    ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.stringOrNull("certification") } }
                    ?.firstOrNull()
            }
            if (cert != null) return cert
        }
        return null
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifBlank { null }
}
