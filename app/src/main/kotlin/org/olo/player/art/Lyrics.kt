package org.olo.player.art

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * 가사 모델과 파서(순수부). LRC(동기화) 텍스트와 일반(비동기) 텍스트를 함께 다룬다 -- 내장
 * 태그·사이드카 .lrc·온라인(LRCLIB) 어디서 와도 같은 [Lyrics]로 모은다. 네트워크·파일 없이
 * 검증 가능하게 둔다.
 */

/** 한 줄의 가사. 동기화면 [timeMs]에 불린다. 비동기(일반 텍스트)면 [timeMs]=[NO_SYNC]. */
data class LrcLine(val timeMs: Long, val text: String)

/** 동기화 시각이 없음을 나타내는 값(일반 가사). */
const val NO_SYNC: Long = -1L

/** 한 곡의 가사. [synced]면 시각에 맞춰 현재 줄을 짚고 탭하면 그 시점으로 이동한다. */
data class Lyrics(val lines: List<LrcLine>, val synced: Boolean) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

// [mm:ss] / [mm:ss.xx] (소수 앞 점·콜론 허용)와, 전체를 당기고 미는 offset 태그.
private val LRC_TIME = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val LRC_OFFSET = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
// 줄 맨 앞 메타/태그 대괄호(동기화 시각이 아닌 [ar:],[ti:] 등) -- 일반 가사에서 걷어낸다.
private val BRACKET_TAG = Regex("""^\s*\[[^\]]*]\s*""")

object LyricsParser {

    /**
     * 가사 텍스트를 [Lyrics]로. LRC 타임스탬프가 하나라도 있으면 동기화 가사(시각순 정렬, 한 줄에
     * 여러 시각이면 각각 한 줄), 없으면 일반 가사(줄 단위, 앞뒤 빈 줄 제거)로 본다.
     */
    fun parse(text: String): Lyrics {
        val synced = parseSynced(text)
        if (synced.isNotEmpty()) return Lyrics(synced, synced = true)
        val plain = text.lineSequence()
            .map { it.replace(BRACKET_TAG, "").trim() }
            .toList()
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .map { LrcLine(NO_SYNC, it) }
        return Lyrics(plain, synced = false)
    }

    private fun parseSynced(text: String): List<LrcLine> {
        var offset = 0L
        val out = mutableListOf<LrcLine>()
        for (raw in text.lineSequence()) {
            LRC_OFFSET.find(raw)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0L }
            val stamps = LRC_TIME.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val words = raw.substring(stamps.last().range.last + 1).trim()
            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toLong()
                val seconds = stamp.groupValues[2].toLong()
                val fraction = stamp.groupValues[3]
                val fractionMs = when (fraction.length) {
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    3 -> fraction.toLong()
                    else -> 0L
                }
                val time = minutes * 60_000L + seconds * 1_000L + fractionMs - offset
                out.add(LrcLine(time.coerceAtLeast(0L), words))
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** 동기화 가사에서 [positionMs]에 불릴 줄의 인덱스(아직 첫 줄 전이면 -1). 비동기면 -1. */
    fun currentIndex(lyrics: Lyrics, positionMs: Long): Int =
        if (!lyrics.synced) -1 else lyrics.lines.indexOfLast { it.timeMs <= positionMs }
}

/**
 * LRCLIB(lrclib.net) 요청 URL과 응답 파서. 키가 필요 없는 공개 가사 DB -- 아티스트·제목(+앨범·길이)로
 * 조회하면 syncedLyrics(LRC)/plainLyrics를 준다. TMDB·커버와 같은 결(순수 문자열/파싱)로 둔다.
 */
object LrcLibApi {
    const val BASE = "https://lrclib.net/api"
    const val USER_AGENT = "OLO-Player/1.0 ( https://github.com/kgcaudit/OLO-Player )"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** 정확 조회(/get): 아티스트·제목(+앨범·길이[초]). 길이는 0 이하면 뺀다. */
    fun getUrl(artist: String, title: String, album: String = "", durationSec: Int = 0): String =
        buildString {
            append(BASE).append("/get?track_name=").append(enc(title))
            append("&artist_name=").append(enc(artist))
            if (album.isNotBlank()) append("&album_name=").append(enc(album))
            if (durationSec > 0) append("&duration=").append(durationSec)
        }

    /** 검색(/search): 아티스트·제목으로 후보 목록. 정확 조회가 빗나갈 때 보조. */
    fun searchUrl(artist: String, title: String): String =
        "$BASE/search?track_name=${enc(title)}&artist_name=${enc(artist)}"

    /** /get 응답 → 가사 원문(동기화 우선, 없으면 일반). 연주곡·없음은 null. */
    fun parseGet(body: String): String? = runCatching {
        lyricsOf(JSONObject(body))
    }.getOrNull()

    /** /search 응답(배열) → 첫 유효 후보의 가사 원문(동기화 우선). */
    fun parseSearch(body: String): String? = runCatching {
        val arr = JSONArray(body)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            lyricsOf(o)?.let { return it }
        }
        null
    }.getOrNull()

    private fun lyricsOf(o: JSONObject): String? {
        val synced = o.optString("syncedLyrics", "").ifBlank { null }
        if (synced != null) return synced
        return o.optString("plainLyrics", "").ifBlank { null }
    }
}
