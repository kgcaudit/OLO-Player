package org.olo.player.art

import java.net.HttpURLConnection
import java.net.URL

/**
 * 아티스트+앨범(+노래 제목)으로 앨범아트 후보를 모아 주는 클라이언트(키 불필요). 영화의 TMDB
 * 포스터 선택과 같은 자리에서, 음악은 이걸로 커버 후보 그리드를 채운다.
 *
 * 두 축으로 찾는다 -- 앨범(아티스트+앨범)과 곡(아티스트+제목). 요즘 EP·디지털 싱글은 곡마다 커버가
 * 달라, 앨범만으로는 그 곡의 표지를 놓친다. 그래서 곡 검색을 더해 둘을 합친다(중복 커버는 한 번만).
 * 각 축에서 두 곳을 쓴다: iTunes(국가 스토어라 한·일 상용 음반에 빠르고 정확)와 MusicBrainz→
 * Cover Art Archive(원어 표기 커버리지 보강). 네트워크 비용을 묶으려 MB→CAA 조회는 상위 몇 개
 * release로 제한한다.
 *
 * 요청은 블로킹이라 호출부의 IO 디스패처에서 돈다. 실패(네트워크·JSON·차단)는 조용히 건너뛰어
 * 그때까지 모은 후보만 돌려준다 -- 커버 검색은 편의 기능이라 흐름을 깨지 않는다.
 */
class AlbumArtClient(
    // 기기/사용자 국가. iTunes 스토어 선택에 쓴다(KR·JP·US…). 빗나가면 호출부가 바꿔 재검색.
    private val country: String = "US",
    // MB에서 찾은 release 중 커버를 조회할 최대 개수(호출 폭주 방지). 앨범·곡 축에 각각 적용.
    private val maxReleases: Int = 4,
) {

    fun search(artist: String, album: String, title: String = ""): List<AlbumArtCandidate> {
        if (artist.isBlank() && album.isBlank() && title.isBlank()) return emptyList()
        val out = LinkedHashMap<String, AlbumArtCandidate>() // fullUrl 기준 중복 제거(순서 유지)
        fun add(list: List<AlbumArtCandidate>) = list.forEach { out.putIfAbsent(it.fullUrl, it) }

        // 1) iTunes 앨범 검색 -- 한 번의 호출로 국가 스토어의 앨범 아트워크.
        if (artist.isNotBlank() || album.isNotBlank()) {
            get(AlbumArtApi.itunesSearchUrl(artist, album, country))?.let { add(AlbumArtApi.parseItunes(it)) }
        }

        // 2) iTunes 곡 검색(entity=song) -- 싱글·EP처럼 곡마다 다른 커버를 잡는다.
        if (title.isNotBlank()) {
            get(AlbumArtApi.itunesSongSearchUrl(artist, title, country))?.let { add(AlbumArtApi.parseItunes(it)) }
        }

        // 3) MusicBrainz release 검색 -> 상위 MBID들 -> Cover Art Archive 커버(+앨범 단위 메타).
        if (artist.isNotBlank() || album.isNotBlank()) {
            get(AlbumArtApi.mbReleaseSearchUrl(artist, album))?.let { body ->
                for (hit in AlbumArtApi.parseMbReleases(body, maxReleases)) {
                    get(AlbumArtApi.caaReleaseUrl(hit.mbid))?.let {
                        add(AlbumArtApi.parseCoverArtArchive(it, hit.meta.album ?: album.ifBlank { null }, hit.meta.artist ?: artist.ifBlank { null }, hit.meta))
                    }
                }
            }
        }

        // 4) MusicBrainz 레코딩(곡) 검색 -> 곡이 든 release MBID들 -> Cover Art Archive 커버(+곡 단위 메타).
        if (title.isNotBlank()) {
            get(AlbumArtApi.mbRecordingSearchUrl(artist, title))?.let { body ->
                for (hit in AlbumArtApi.parseMbRecordings(body, maxReleases)) {
                    get(AlbumArtApi.caaReleaseUrl(hit.mbid))?.let {
                        add(AlbumArtApi.parseCoverArtArchive(it, hit.meta.title ?: hit.meta.album ?: title.ifBlank { null }, hit.meta.artist ?: artist.ifBlank { null }, hit.meta))
                    }
                }
            }
        }
        return out.values.toList()
    }

    private fun get(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        // MusicBrainz는 의미 있는 User-Agent가 없으면 차단한다. 모든 요청에 공통으로 붙인다.
        conn.setRequestProperty("User-Agent", AlbumArtApi.USER_AGENT)
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
