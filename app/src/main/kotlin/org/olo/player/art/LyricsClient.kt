package org.olo.player.art

import java.net.HttpURLConnection
import java.net.URL

/**
 * LRCLIB에서 가사 원문을 받아 오는 클라이언트(키 불필요). 정확 조회(/get)를 먼저,
 * 빗나가면 검색(/search)으로 보조한다. 요청은 블로킹이라 호출부의 IO에서 돈다. 실패는 null.
 */
class LyricsClient {

    /** 아티스트·제목(+앨범·길이[초])로 가사 원문(동기화 우선). 없거나 실패면 null. */
    fun fetch(artist: String, title: String, album: String = "", durationSec: Int = 0): String? {
        if (artist.isBlank() && title.isBlank()) return null
        get(LrcLibApi.getUrl(artist, title, album, durationSec))?.let { body ->
            LrcLibApi.parseGet(body)?.let { return it }
        }
        return get(LrcLibApi.searchUrl(artist, title))?.let { LrcLibApi.parseSearch(it) }
    }

    /**
     * 저장용 후보 목록: 정확 조회(/get)를 맨 앞에 두고, 검색(/search)으로 대안을 덧붙인다.
     * 같은 가사(동기 또는 일반이 동일)는 하나로 합치고 최대 [limit]건만 남긴다. 실패·없음은 빈 목록.
     */
    fun search(artist: String, title: String, album: String = "", durationSec: Int = 0, limit: Int = 8): List<LyricsHit> {
        if (artist.isBlank() && title.isBlank()) return emptyList()
        val out = ArrayList<LyricsHit>()
        get(LrcLibApi.getUrl(artist, title, album, durationSec))?.let { body ->
            LrcLibApi.parseGetHit(body)?.let { out.add(it) }
        }
        get(LrcLibApi.searchUrl(artist, title))?.let { out.addAll(LrcLibApi.parseSearchHits(it)) }
        return out.distinctBy { it.synced ?: it.plain }.take(limit)
    }

    private fun get(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", LrcLibApi.USER_AGENT)
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
