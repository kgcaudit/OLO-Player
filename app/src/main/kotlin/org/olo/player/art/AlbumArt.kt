package org.olo.player.art

import java.net.URLEncoder
import org.json.JSONObject

/**
 * 한 앨범아트 후보: 격자용 썸네일과 적용할 원본(큰) 이미지, 그리고 어디서 왔는지.
 *
 * 영화의 TMDB 포스터 선택과 같은 흐름을 음악에 둔다 -- 아티스트+앨범으로 찾은 커버 후보들을
 * 그리드로 보여 주고, 하나를 골라 앨범아트로 반영한다. 출처(source)는 품질/신뢰의 참고용.
 */
data class AlbumArtCandidate(
    val thumbUrl: String,
    val fullUrl: String,
    val title: String?,
    val artist: String?,
    val source: String,
)

/**
 * 앨범아트 제공처의 요청 URL과 응답 파서. TMDB와 같은 결(순수 문자열/파싱이라 네트워크 없이
 * 검증 가능)로 둔다. 키가 필요 없는 두 곳만 쓴다:
 *
 *  - iTunes Search: 국가별 스토어(country=KR/JP/US…)라 한·일 상용 음반 커버리지가 좋다.
 *    아트워크 URL의 "100x100bb"를 원하는 px로 바꿔 고해상도를 받는다(비공식 관례).
 *  - Cover Art Archive(MusicBrainz): 원어 표기(한글·한자·가나)로 찾는 공개 커버 아카이브.
 *    release를 검색해 MBID를 얻고, 그 MBID의 커버 목록(250/500/1200 썸네일 + 원본)을 받는다.
 */
object AlbumArtApi {

    const val ITUNES_SEARCH = "https://itunes.apple.com/search"
    const val MB_RELEASE = "https://musicbrainz.org/ws/2/release"
    const val MB_RECORDING = "https://musicbrainz.org/ws/2/recording"
    const val CAA_BASE = "https://coverartarchive.org"

    // MusicBrainz는 의미 있는 User-Agent를 요구한다(없으면 차단). 앱·연락처를 담는다.
    const val USER_AGENT = "OLO-Player/1.0 ( https://github.com/kgcaudit/OLO-Player )"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** iTunes 앨범 검색 URL. 국가 스토어(country)로 한·일 결과를 그대로 받는다. */
    fun itunesSearchUrl(artist: String, album: String, country: String, limit: Int = 12): String {
        val term = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" ")
        return buildString {
            append(ITUNES_SEARCH).append("?term=").append(enc(term))
            append("&media=music&entity=album&limit=").append(limit)
            if (country.isNotBlank()) append("&country=").append(enc(country))
        }
    }

    /** iTunes 곡 검색 URL(entity=song). 싱글·EP는 곡으로 찾아야 그 릴리스 커버가 나온다
     *  (musicTrack은 뮤직비디오가 섞여 song을 쓴다). 각 곡 결과가 그 릴리스의 아트워크를 든다. */
    fun itunesSongSearchUrl(artist: String, title: String, country: String, limit: Int = 12): String {
        val term = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        return buildString {
            append(ITUNES_SEARCH).append("?term=").append(enc(term))
            append("&media=music&entity=song&limit=").append(limit)
            if (country.isNotBlank()) append("&country=").append(enc(country))
        }
    }

    /** MusicBrainz 레코딩(곡) 검색 URL. 곡이 든 릴리스들을 함께 받아(inc 없이도 releases 포함)
     *  그 release MBID로 Cover Art Archive를 조회한다 -- 싱글·EP 커버를 곡으로 찾는 경로. */
    fun mbRecordingSearchUrl(artist: String, title: String, limit: Int = 8): String {
        val query = buildList {
            if (title.isNotBlank()) add("recording:\"$title\"")
            if (artist.isNotBlank()) add("artist:\"$artist\"")
        }.joinToString(" AND ")
        return "$MB_RECORDING/?query=${enc(query)}&fmt=json&limit=$limit"
    }

    /** iTunes 아트워크 URL의 크기 토큰("600x600bb")을 [size]px로 바꾼다. 패턴이 없으면 원본 그대로. */
    fun upscaleItunesArtwork(url: String, size: Int): String =
        url.replace(ARTWORK_SIZE, "${size}x${size}bb")

    /** MusicBrainz release 검색 URL. 앨범명+아티스트로 질의하고 JSON으로 받는다. */
    fun mbReleaseSearchUrl(artist: String, album: String, limit: Int = 8): String {
        val query = buildList {
            if (album.isNotBlank()) add("release:\"$album\"")
            if (artist.isNotBlank()) add("artist:\"$artist\"")
        }.joinToString(" AND ")
        return "$MB_RELEASE/?query=${enc(query)}&fmt=json&limit=$limit"
    }

    /** 한 release(MBID)의 커버 목록 JSON URL. */
    fun caaReleaseUrl(mbid: String): String = "$CAA_BASE/release/$mbid"

    // iTunes 응답 → 후보들. artworkUrl100을 썸네일/원본 px로 각각 키워 쓴다.
    fun parseItunes(body: String, thumbPx: Int = 200, fullPx: Int = 600): List<AlbumArtCandidate> =
        runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            (0 until results.length()).mapNotNull { i ->
                val o = results.optJSONObject(i) ?: return@mapNotNull null
                val art = o.optString("artworkUrl100", "").ifBlank { return@mapNotNull null }
                AlbumArtCandidate(
                    thumbUrl = upscaleItunesArtwork(art, thumbPx),
                    fullUrl = upscaleItunesArtwork(art, fullPx),
                    title = o.optString("collectionName", "").ifBlank { null },
                    artist = o.optString("artistName", "").ifBlank { null },
                    source = "iTunes",
                )
            }
        }.getOrDefault(emptyList())

    // MusicBrainz release 검색 응답 → MBID 목록(상위 [limit]개). 각 MBID로 CAA를 다시 조회한다.
    fun parseMbReleaseIds(body: String, limit: Int = 5): List<String> =
        runCatching {
            val releases = JSONObject(body).optJSONArray("releases") ?: return emptyList()
            (0 until releases.length()).mapNotNull { i ->
                releases.optJSONObject(i)?.optString("id", "")?.ifBlank { null }
            }.take(limit)
        }.getOrDefault(emptyList())

    // MusicBrainz 레코딩(곡) 검색 응답 → 그 곡이 든 release MBID들(중복 제거, 상위 [limit]개).
    // 싱글·EP는 recording에서 release로 올라가 CAA를 조회한다.
    fun parseMbRecordingReleaseIds(body: String, limit: Int = 5): List<String> =
        runCatching {
            val recordings = JSONObject(body).optJSONArray("recordings") ?: return emptyList()
            val ids = LinkedHashSet<String>()
            for (i in 0 until recordings.length()) {
                val rels = recordings.optJSONObject(i)?.optJSONArray("releases") ?: continue
                for (j in 0 until rels.length()) {
                    rels.optJSONObject(j)?.optString("id", "")?.ifBlank { null }?.let { ids.add(it) }
                    if (ids.size >= limit) break
                }
                if (ids.size >= limit) break
            }
            ids.toList()
        }.getOrDefault(emptyList())

    // Cover Art Archive 한 release 응답 → 후보들. front(앞표지)를 우선하고, 500px 썸네일 +
    // 1200px(없으면 원본)를 쓴다. title/artist는 CAA가 주지 않으므로 호출부가 채운다.
    fun parseCoverArtArchive(body: String, title: String?, artist: String?): List<AlbumArtCandidate> =
        runCatching {
            val images = JSONObject(body).optJSONArray("images") ?: return emptyList()
            val all = (0 until images.length()).mapNotNull { i ->
                val o = images.optJSONObject(i) ?: return@mapNotNull null
                val thumbs = o.optJSONObject("thumbnails")
                val thumb = thumbs?.optStringOrNull("500") ?: thumbs?.optStringOrNull("large")
                    ?: thumbs?.optStringOrNull("250") ?: o.optStringOrNull("image") ?: return@mapNotNull null
                val full = thumbs?.optStringOrNull("1200") ?: o.optStringOrNull("image") ?: thumb
                Triple(o.optBoolean("front", false), thumb, full)
            }
            // 앞표지를 먼저, 그다음 나머지. 같은 release의 여러 장(뒷면·속지 등)도 후보로 둔다.
            all.sortedByDescending { it.first }.map { (_, thumb, full) ->
                AlbumArtCandidate(thumbUrl = thumb, fullUrl = full, title = title, artist = artist, source = "CoverArtArchive")
            }
        }.getOrDefault(emptyList())

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifBlank { null }

    // iTunes 아트워크 경로의 크기 토큰(예: "100x100bb", "170x170bb").
    private val ARTWORK_SIZE = Regex("""\d+x\d+bb""")
}
