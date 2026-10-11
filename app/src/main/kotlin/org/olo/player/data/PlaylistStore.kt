package org.olo.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One saved reference to a playable source, wherever it lives.
 *
 * The playlist tab's shelves (recents, pasted URLs, favourites, visited
 * servers) are all lists of these: enough to show a row and to reopen the
 * source. The resume position is *not* stored here -- it lives in
 * [AppPreferences] under [key], so a recents row and the player never disagree
 * on where a file was left.
 */
data class SavedItem(
    val key: String,
    val name: String,
    val uri: String,
    /** A short origin label for the row: 기기 / FTP / URL. */
    val source: String,
    val savedAt: Long = System.currentTimeMillis(),
    /** True only for a local file, so reopening can rebuild its java.io.File. */
    val local: Boolean = false,
    /** 재생 횟수(표시용). '자주 들은' 산출에서만 채워지고 다른 셸프엔 저장하지 않는다(기본 0). */
    val playCount: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("name", name).put("uri", uri)
        .put("source", source).put("savedAt", savedAt).put("local", local)

    companion object {
        fun fromJson(o: JSONObject) = SavedItem(
            key = o.getString("key"),
            name = o.getString("name"),
            uri = o.getString("uri"),
            source = o.optString("source", "URL"),
            savedAt = o.optLong("savedAt", 0L),
            local = o.optBoolean("local", false),
        )
    }
}

/**
 * The playlist tab's four shelves, on top of SharedPreferences.
 *
 * Why a list store beside [AppPreferences]'s scalars: recents, pasted URLs,
 * favourites and visited servers are ordered, capped lists, kept as JSON under
 * one key each. Recents and visited are recorded automatically as media and
 * servers are opened; URLs when one is pasted; favourites are toggled by the
 * user. Each keeps most-recent first and drops the oldest past its cap, so the
 * preferences file never grows without end.
 */
class PlaylistStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("olo_player", Context.MODE_PRIVATE)

    fun recents(): List<SavedItem> = read(KEY_RECENTS)
    fun urls(): List<SavedItem> = read(KEY_URLS)
    fun favorites(): List<SavedItem> = read(KEY_FAVORITES)
    fun servers(): List<SavedItem> = read(KEY_SERVERS)

    /** Records a just-opened source at the front of recents (dedup by key). */
    fun recordRecent(item: SavedItem) = pushFront(KEY_RECENTS, item, CAP_RECENTS)

    /** Records a pasted URL, so it can be reopened without retyping. */
    fun recordUrl(item: SavedItem) = pushFront(KEY_URLS, item, CAP_URLS)

    /** Records a visited server/folder, so the next visit is one tap. */
    fun recordServer(item: SavedItem) = pushFront(KEY_SERVERS, item, CAP_SERVERS)

    fun isFavorite(key: String): Boolean = favorites().any { it.key == key }

    /** Adds to favourites, or removes it if already there; returns the new state. */
    fun toggleFavorite(item: SavedItem): Boolean {
        val list = favorites().toMutableList()
        val existing = list.indexOfFirst { it.key == item.key }
        return if (existing >= 0) {
            list.removeAt(existing); write(KEY_FAVORITES, list); false
        } else {
            list.add(0, item); write(KEY_FAVORITES, list.take(CAP_FAVORITES)); true
        }
    }

    fun remove(shelf: Shelf, key: String) {
        val k = shelf.key
        write(k, read(k).filterNot { it.key == key })
    }

    fun clear(shelf: Shelf) = prefs.edit().remove(shelf.key).apply()

    // ---- 재생 횟수('자주 들은' 스마트 목록) ----
    // 리스트가 아니라 key→{메타+count+lastAt} 맵으로 둔다(중복 없이 바로 증가). 셸프 JSON(배열)과
    // 포맷이 달라 별도 키·전용 메서드로 다룬다.

    /** 한 곡을 '충분히 들었을 때'(호출부가 임계 판정) 재생 횟수를 1 올린다. 메타는 최신값으로 갱신. */
    fun recordPlay(item: SavedItem) {
        val o = runCatching { JSONObject(prefs.getString(KEY_PLAYS, "{}")) }.getOrElse { JSONObject() }
        val prev = o.optJSONObject(item.key)
        val count = (prev?.optInt("count", 0) ?: 0) + 1
        o.put(
            item.key,
            JSONObject().put("name", item.name).put("uri", item.uri).put("source", item.source)
                .put("local", item.local).put("count", count).put("lastAt", System.currentTimeMillis()),
        )
        prefs.edit().putString(KEY_PLAYS, trimPlays(o).toString()).apply()
    }

    /** 많이 들은 순(동률이면 최근 들은 순) 상위 [cap]곡. [SavedItem.playCount]에 횟수를 채워 돌려준다. */
    fun mostPlayed(cap: Int = 30): List<SavedItem> {
        val o = runCatching { JSONObject(prefs.getString(KEY_PLAYS, "{}")) }.getOrElse { JSONObject() }
        return playList(o)
            .sortedWith(compareByDescending<SavedItem> { it.playCount }.thenByDescending { it.savedAt })
            .take(cap)
    }

    /** '자주 들은'에서 한 곡을 뺀다(횟수 기록 삭제). */
    fun removePlay(key: String) {
        val o = runCatching { JSONObject(prefs.getString(KEY_PLAYS, "{}")) }.getOrElse { JSONObject() }
        o.remove(key)
        prefs.edit().putString(KEY_PLAYS, o.toString()).apply()
    }

    /** 재생 횟수 기록 전체 삭제. */
    fun clearPlays() = prefs.edit().remove(KEY_PLAYS).apply()

    private fun playList(o: JSONObject): List<SavedItem> =
        o.keys().asSequence().mapNotNull { k ->
            o.optJSONObject(k)?.let { e ->
                runCatching {
                    SavedItem(
                        key = k, name = e.optString("name"), uri = e.optString("uri"),
                        source = e.optString("source", "기기"), savedAt = e.optLong("lastAt", 0L),
                        local = e.optBoolean("local", false), playCount = e.optInt("count", 0),
                    )
                }.getOrNull()
            }
        }.filter { it.playCount > 0 }.toList()

    // 맵이 한없이 커지지 않게: [CAP_PLAYS]를 넘으면 적게 들은(동률이면 오래된) 항목부터 버린다.
    private fun trimPlays(o: JSONObject): JSONObject {
        if (o.length() <= CAP_PLAYS) return o
        val keep = playList(o)
            .sortedWith(compareByDescending<SavedItem> { it.playCount }.thenByDescending { it.savedAt })
            .take(CAP_PLAYS).map { it.key }.toSet()
        val out = JSONObject()
        for (k in keep) o.optJSONObject(k)?.let { out.put(k, it) }
        return out
    }

    enum class Shelf(val key: String) {
        RECENTS(KEY_RECENTS), URLS(KEY_URLS), FAVORITES(KEY_FAVORITES), SERVERS(KEY_SERVERS),
    }

    private fun pushFront(key: String, item: SavedItem, cap: Int) {
        val list = read(key).filterNot { it.key == item.key }.toMutableList()
        list.add(0, item)
        write(key, list.take(cap))
    }

    private fun read(key: String): List<SavedItem> =
        runCatching {
            val arr = JSONArray(prefs.getString(key, "[]"))
            // 행 단위로 건너뛴다 -- 한 항목이 깨져도(필드 누락 등) 셸프 전체가 날아가지 않도록.
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o -> runCatching { SavedItem.fromJson(o) }.getOrNull() }
            }
        }.getOrDefault(emptyList())

    private fun write(key: String, items: List<SavedItem>) {
        val arr = JSONArray().apply { items.forEach { put(it.toJson()) } }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val KEY_RECENTS = "pl_recents"
        private const val KEY_URLS = "pl_urls"
        private const val KEY_FAVORITES = "pl_favorites"
        private const val KEY_SERVERS = "pl_servers"
        private const val KEY_PLAYS = "pl_plays"
        private const val CAP_PLAYS = 300
        private const val CAP_RECENTS = 60
        private const val CAP_URLS = 60
        private const val CAP_FAVORITES = 120
        private const val CAP_SERVERS = 40
    }
}
