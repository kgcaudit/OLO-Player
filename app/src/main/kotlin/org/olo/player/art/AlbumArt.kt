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
    // 이 커버를 찾아준 출처 레코드에서 함께 받은 곡/앨범 메타(없으면 null). 커버를 고르면 이걸로
    // 부족한 태그를 '보강'한다 -- 출처마다 가진 필드가 달라(앨범 커버=앨범/연도/장르, 곡 커버=
    // 제목/트랙까지) 있는 것만 담는다.
    val meta: SourceMeta? = null,
)

/**
 * 커버 출처 레코드에서 뽑은 곡/앨범 메타. 태그 보강의 '제안값'이 된다. 출처가 주지 않는 필드는
 * null로 두고(그 칸은 제안하지 않음), [toFields]로 편집 가능한 TagField 맵으로 편다.
 */
data class SourceMeta(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val track: String? = null, // "1" 또는 "1/4"
    val disc: String? = null,
    val year: String? = null,
    val genre: String? = null,
) {
    fun isEmpty(): Boolean =
        title == null && artist == null && album == null && albumArtist == null &&
            track == null && disc == null && year == null && genre == null

    /** 제공된 필드만 TagField→값으로. 순서는 편집 시트의 필드 순서와 같게 둔다. */
    fun toFields(): Map<TagField, String> = buildMap {
        title?.let { put(TagField.TITLE, it) }
        artist?.let { put(TagField.ARTIST, it) }
        album?.let { put(TagField.ALBUM, it) }
        albumArtist?.let { put(TagField.ALBUM_ARTIST, it) }
        track?.let { put(TagField.TRACK, it) }
        disc?.let { put(TagField.DISC, it) }
        year?.let { put(TagField.YEAR, it) }
        genre?.let { put(TagField.GENRE, it) }
    }
}

/** MusicBrainz 검색 한 건: 커버를 조회할 release MBID와, 그 레코드에서 뽑은 메타. */
data class MbHit(val mbid: String, val meta: SourceMeta)

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

    // iTunes 응답 → 후보들. artworkUrl100을 썸네일/원본 px로 각각 키워 쓰고, 같은 결과에 실린
    // 메타(곡/앨범)를 [AlbumArtCandidate.meta]에 담아 보강에 쓴다.
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
                    meta = itunesMeta(o),
                )
            }
        }.getOrDefault(emptyList())

    // 한 iTunes 결과에서 보강용 메타를 뽑는다. kind=song이면 곡 단위(제목·트랙·디스크까지),
    // 앨범 결과면 앨범 단위(앨범·아티스트·연도·장르). 없는 필드는 null로 둔다.
    private fun itunesMeta(o: JSONObject): SourceMeta {
        val artist = o.optString("artistName", "").ifBlank { null }
        val genre = o.optString("primaryGenreName", "").ifBlank { null }
        val year = yearOf(o.optString("releaseDate", ""))
        return if (o.optString("kind") == "song") {
            val tn = o.optInt("trackNumber", 0)
            val tc = o.optInt("trackCount", 0)
            SourceMeta(
                title = o.optString("trackName", "").ifBlank { null },
                artist = artist,
                album = o.optString("collectionName", "").ifBlank { null },
                albumArtist = o.optString("collectionArtistName", "").ifBlank { null },
                track = if (tn > 0) (if (tc > 0) "$tn/$tc" else "$tn") else null,
                disc = o.optInt("discNumber", 0).takeIf { it > 0 }?.toString(),
                year = year,
                genre = genre,
            )
        } else {
            SourceMeta(album = o.optString("collectionName", "").ifBlank { null }, artist = artist, year = year, genre = genre)
        }
    }

    // ISO 날짜("2024-01-31T…")에서 앞 4자리 연도. 숫자가 아니면 null.
    private fun yearOf(date: String): String? =
        date.takeIf { it.length >= 4 && it.take(4).all(Char::isDigit) }?.take(4)

    // MusicBrainz release 검색 응답 → (MBID, 앨범 단위 메타) 목록(상위 [limit]개). 각 MBID로 CAA 조회.
    fun parseMbReleases(body: String, limit: Int = 5): List<MbHit> =
        runCatching {
            val releases = JSONObject(body).optJSONArray("releases") ?: return emptyList()
            (0 until releases.length()).mapNotNull { i ->
                val o = releases.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "").ifBlank { return@mapNotNull null }
                MbHit(id, SourceMeta(
                    artist = mbArtistCredit(o),
                    album = o.optString("title", "").ifBlank { null },
                    year = yearOf(o.optString("date", "")),
                ))
            }.take(limit)
        }.getOrDefault(emptyList())

    // MusicBrainz 레코딩(곡) 검색 응답 → (release MBID, 곡 단위 메타) 목록(중복 MBID 제거, 상위
    // [limit]개). 싱글·EP는 recording에서 release로 올라가 CAA를 조회하고, 곡 제목·아티스트·앨범·
    // 연도를 보강 메타로 싣는다.
    fun parseMbRecordings(body: String, limit: Int = 5): List<MbHit> =
        runCatching {
            val recordings = JSONObject(body).optJSONArray("recordings") ?: return emptyList()
            val seen = HashSet<String>()
            val out = ArrayList<MbHit>()
            for (i in 0 until recordings.length()) {
                val rec = recordings.optJSONObject(i) ?: continue
                val title = rec.optString("title", "").ifBlank { null }
                val artist = mbArtistCredit(rec)
                val rels = rec.optJSONArray("releases") ?: continue
                for (j in 0 until rels.length()) {
                    val rel = rels.optJSONObject(j) ?: continue
                    val id = rel.optString("id", "").ifBlank { null } ?: continue
                    if (!seen.add(id)) continue
                    out.add(MbHit(id, SourceMeta(
                        title = title,
                        artist = artist,
                        album = rel.optString("title", "").ifBlank { null },
                        year = yearOf(rel.optString("date", "")),
                    )))
                    if (out.size >= limit) break
                }
                if (out.size >= limit) break
            }
            out
        }.getOrDefault(emptyList())

    // artist-credit[0].name(원어 표기 아티스트). 없으면 null.
    private fun mbArtistCredit(o: JSONObject): String? =
        o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name", "")?.ifBlank { null }

    // Cover Art Archive 한 release 응답 → 후보들. front(앞표지)를 우선하고, 500px 썸네일 +
    // 1200px(없으면 원본)를 쓴다. title/artist는 CAA가 주지 않으므로 호출부가 채운다.
    fun parseCoverArtArchive(body: String, title: String?, artist: String?, meta: SourceMeta? = null): List<AlbumArtCandidate> =
        runCatching {
            val images = JSONObject(body).optJSONArray("images") ?: return emptyList()
            val all = (0 until images.length()).mapNotNull { i ->
                val o = images.optJSONObject(i) ?: return@mapNotNull null
                val thumbs = o.optJSONObject("thumbnails")
                // 격자 썸네일은 작게 -- CAA 이미지는 archive.org에 있어 첫 로딩이 느리므로, 250px를
                // 먼저 써 체감 로딩을 빠르게 한다(적용되는 원본 full은 1200px 그대로).
                val thumb = thumbs?.optStringOrNull("250") ?: thumbs?.optStringOrNull("small")
                    ?: thumbs?.optStringOrNull("500") ?: thumbs?.optStringOrNull("large")
                    ?: o.optStringOrNull("image") ?: return@mapNotNull null
                val full = thumbs?.optStringOrNull("1200") ?: o.optStringOrNull("image") ?: thumb
                Triple(o.optBoolean("front", false), thumb, full)
            }
            // 앞표지를 먼저, 그다음 나머지. 같은 release의 여러 장(뒷면·속지 등)도 후보로 둔다.
            // 메타는 CAA가 주지 않으므로 이 release를 찾아준 MB 검색 결과([meta])를 그대로 싣는다.
            all.sortedByDescending { it.first }.map { (_, thumb, full) ->
                AlbumArtCandidate(thumbUrl = thumb, fullUrl = full, title = title, artist = artist, source = "CoverArtArchive", meta = meta)
            }
        }.getOrDefault(emptyList())

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifBlank { null }

    // iTunes 아트워크 경로의 크기 토큰(예: "100x100bb", "170x170bb").
    private val ARTWORK_SIZE = Regex("""\d+x\d+bb""")
}
