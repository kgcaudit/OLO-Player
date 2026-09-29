package org.olo.player.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import org.olo.player.data.AppPreferences

/**
 * The player's state and its window onto the settings.
 *
 * A thin stand-in for OLO Explorer's MainViewModel: it holds the one screen of
 * state the player has -- which playlist is open, if any -- and forwards the
 * media/subtitle preferences the player screen reads and writes. The reference
 * app's file-browser and FTP state does not come across; the file picker keeps
 * its own.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val preferences = AppPreferences(app)

    /**
     * A media playlist open in the viewer: the files, and which one is showing.
     * One kind throughout -- video with video, sound with sound.
     */
    data class MediaViewer(
        val items: List<File>,
        val index: Int,
    )

    var mediaViewer by mutableStateOf<MediaViewer?>(null)
        private set

    /**
     * Opens [file] in the player with the other media of its kind sitting beside
     * it in the same folder, as a playlist.
     *
     * Video with video, sound with sound: a folder holding both a film and its
     * soundtrack does not fold them into one playlist, and the tap says which
     * kind was meant. Ordered for playing by natural name. Always opens at least
     * the file itself.
     */
    fun openMedia(file: File) {
        val candidates = file.parentFile?.listFiles()?.filter { it.isFile }.orEmpty()
        val wantVideo = looksVideo(file.name)
        val items = candidates
            .filter { looksMedia(it.name) && looksVideo(it.name) == wantVideo }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        val index = items.indexOfFirst { it.path == file.path }.coerceAtLeast(0)
        mediaViewer = if (items.isEmpty()) MediaViewer(listOf(file), 0) else MediaViewer(items, index)
    }

    fun closeMediaViewer() {
        mediaViewer = null
    }

    /** Where a media file was last left, in milliseconds, or 0 to start over. */
    fun mediaPosition(file: File): Long = preferences.mediaPosition(file.path)

    /** Remembers where a media file was left, so it reopens there. */
    fun setMediaPosition(file: File, positionMs: Long) {
        preferences.setMediaPosition(file.path, positionMs)
    }

    /** How large the player draws subtitles, as a fraction of the screen. */
    fun subtitleScale(): Float = preferences.subtitleScale()

    /** What colour the player draws subtitles. */
    fun subtitleColor(): Int = preferences.subtitleColor()

    /** Remembers the subtitle size and colour, applied to every video. */
    fun setSubtitleStyle(scale: Float, color: Int) {
        preferences.setSubtitleStyle(scale, color)
    }

    /** Which subtitle a file was last watched with, or null for none saved. */
    fun subtitleChoice(file: File): String? = preferences.subtitleChoice(file.path)

    /** Remembers the subtitle a file is watched with, so it reopens the same. */
    fun setSubtitleChoice(file: File, token: String) {
        preferences.setSubtitleChoice(file.path, token)
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
