package org.olo.player.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import org.olo.player.data.AppPreferences
import org.olo.player.data.PlaylistStore
import org.olo.player.data.SavedItem

/**
 * The player's state and its window onto the settings.
 *
 * A thin stand-in for OLO Explorer's MainViewModel: it holds the one screen of
 * state the player has -- which playlist is open, if any -- and forwards the
 * media/subtitle preferences the player screen reads and writes. A playlist is
 * a list of [MediaEntry], so a local folder, a pasted web URL and an FTP folder
 * all open the same player.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val preferences = AppPreferences(app)
    private val playlist = PlaylistStore(app)

    /**
     * A media playlist open in the viewer: the items, and which one is showing.
     * One kind throughout -- video with video, sound with sound.
     */
    data class MediaViewer(
        val items: List<MediaEntry>,
        val index: Int,
    )

    var mediaViewer by mutableStateOf<MediaViewer?>(null)
        private set

    /**
     * The chosen app theme -- "system", "light" or "dark" -- as observable state,
     * so setting it in 설정 › 일반 recomposes the whole app through OloPlayerTheme
     * at once. Seeded from preferences and written back on change.
     */
    var themeMode by mutableStateOf(preferences.themeMode())
        private set

    fun chooseTheme(mode: String) {
        themeMode = mode
        preferences.setThemeMode(mode)
    }

    /**
     * Opens a local [file] with the other media of its kind sitting beside it in
     * the same folder, as a playlist.
     *
     * Video with video, sound with sound: a folder holding both a film and its
     * soundtrack does not fold them into one playlist, and the tap says which
     * kind was meant. Ordered for playing by natural name. Always opens at least
     * the file itself.
     */
    fun openLocalMedia(file: File) {
        val candidates = file.parentFile?.listFiles()?.filter { it.isFile }.orEmpty()
        val wantVideo = looksVideo(file.name)
        val siblings = candidates
            .filter { looksMedia(it.name) && looksVideo(it.name) == wantVideo }
            .sortedWith(compareBy(NaturalOrder) { it.name })
            .map { localEntry(it) }
        val index = siblings.indexOfFirst { it.localFile?.path == file.path }.coerceAtLeast(0)
        mediaViewer = if (siblings.isEmpty()) {
            MediaViewer(listOf(localEntry(file)), 0)
        } else {
            MediaViewer(siblings, index)
        }
        recordRecent(localEntry(file), "기기", local = true)
    }

    /**
     * Opens a single network stream from a pasted URL (http(s) or ftp). It has
     * no siblings to gather -- one URL is one item.
     */
    fun openNetworkUrl(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val uri = Uri.parse(trimmed)
        val name = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.host ?: trimmed
        val entry = MediaEntry(uri, name, prefKey = trimmed)
        mediaViewer = MediaViewer(listOf(entry), 0)
        val source = if (uri.scheme.equals("ftp", true)) "FTP" else "URL"
        recordRecent(entry, source, local = false)
        playlist.recordUrl(entry.toSaved(source, local = false))
    }

    /** Opens a ready-made playlist (the FTP browser builds one from a folder). */
    fun openEntries(items: List<MediaEntry>, index: Int) {
        if (items.isEmpty()) return
        val at = index.coerceIn(0, items.size - 1)
        mediaViewer = MediaViewer(items, at)
        val entry = items[at]
        val source = if (entry.uri.scheme.equals("ftp", true)) "FTP" else "URL"
        recordRecent(entry, source, local = false)
        // A visited server: keep the host as a one-tap return point.
        entry.uri.host?.let { host ->
            playlist.recordServer(
                SavedItem(key = "${entry.uri.scheme}://$host", name = host, uri = "${entry.uri.scheme}://$host", source = source),
            )
        }
    }

    /** Reopens a saved shelf item (recent, URL, favourite, or visited server). */
    fun openSaved(item: SavedItem) {
        val uri = Uri.parse(item.uri)
        if (item.local) {
            val file = File(uri.path ?: return)
            if (file.exists()) openLocalMedia(file) else openNetworkUrl(item.uri)
        } else {
            openNetworkUrl(item.uri)
        }
    }

    // Playlist shelves, read straight through so a screen sees the latest.
    fun recents() = playlist.recents()
    fun urls() = playlist.urls()
    fun favorites() = playlist.favorites()
    fun servers() = playlist.servers()
    fun isFavorite(key: String) = playlist.isFavorite(key)
    fun toggleFavorite(item: SavedItem) = playlist.toggleFavorite(item)
    fun removeSaved(shelf: PlaylistStore.Shelf, key: String) = playlist.remove(shelf, key)
    fun clearShelf(shelf: PlaylistStore.Shelf) = playlist.clear(shelf)

    private fun recordRecent(entry: MediaEntry, source: String, local: Boolean) {
        playlist.recordRecent(entry.toSaved(source, local))
    }

    private fun MediaEntry.toSaved(source: String, local: Boolean) = SavedItem(
        key = prefKey, name = name, uri = uri.toString(), source = source, local = local,
    )

    fun closeMediaViewer() {
        mediaViewer = null
    }

    private fun localEntry(file: File): MediaEntry =
        MediaEntry(Uri.fromFile(file), file.name, prefKey = file.path, localFile = file)

    /** Where a media item was last left, in milliseconds, or 0 to start over. */
    fun mediaPosition(entry: MediaEntry): Long = preferences.mediaPosition(entry.prefKey)

    /** The resume position for a saved shelf item, by its key. */
    fun savedPosition(key: String): Long = preferences.mediaPosition(key)

    // App-wide playback defaults the player reads on opening a queue.
    fun resumeEnabled(): Boolean = preferences.resumeEnabled()
    fun defaultSpeed(): Float = preferences.defaultSpeed()
    fun keepScreenOn(): Boolean = preferences.keepScreenOn()

    /** Remembers where a media item was left, so it reopens there. */
    fun setMediaPosition(entry: MediaEntry, positionMs: Long) {
        preferences.setMediaPosition(entry.prefKey, positionMs)
    }

    /** How large the player draws subtitles, as a fraction of the screen. */
    fun subtitleScale(): Float = preferences.subtitleScale()

    /** What colour the player draws subtitles. */
    fun subtitleColor(): Int = preferences.subtitleColor()

    /** Remembers the subtitle size and colour, applied to every video. */
    fun setSubtitleStyle(scale: Float, color: Int) {
        preferences.setSubtitleStyle(scale, color)
    }

    /** Which subtitle an item was last watched with, or null for none saved. */
    fun subtitleChoice(entry: MediaEntry): String? = preferences.subtitleChoice(entry.prefKey)

    /** Remembers the subtitle an item is watched with, so it reopens the same. */
    fun setSubtitleChoice(entry: MediaEntry, token: String) {
        preferences.setSubtitleChoice(entry.prefKey, token)
    }
}

/**
 * Orders names the way a person reads them: the digits in a name compare as
 * numbers, so "ep2" comes before "ep10" rather than after it. Everything else
 * compares as lower-case text.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++
                // Compare the digit runs as numbers, ignoring leading zeros.
                val numA = a.substring(i, endA).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(j, endB).trimStart('0').ifEmpty { "0" }
                val byLength = numA.length - numB.length
                if (byLength != 0) return byLength
                val byValue = numA.compareTo(numB)
                if (byValue != 0) return byValue
                i = endA
                j = endB
            } else {
                val byChar = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
