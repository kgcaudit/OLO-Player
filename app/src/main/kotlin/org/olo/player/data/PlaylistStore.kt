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
            (0 until arr.length()).map { SavedItem.fromJson(arr.getJSONObject(it)) }
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
        private const val CAP_RECENTS = 60
        private const val CAP_URLS = 60
        private const val CAP_FAVORITES = 120
        private const val CAP_SERVERS = 40
    }
}
