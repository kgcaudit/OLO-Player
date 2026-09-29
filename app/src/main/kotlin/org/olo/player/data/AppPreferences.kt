package org.olo.player.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The app's remembered settings, on top of SharedPreferences.
 *
 * Ported thin from OLO Explorer: only what the player asks for is kept here --
 * where each file was last left, which subtitle it was watched with, and the
 * subtitle look shared by every video. The reference app's comic/pdf/FTP state
 * is gone.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("olo_player", Context.MODE_PRIVATE)

    /**
     * How far into a video or a sound it was left, in milliseconds, so it
     * reopens where it stopped. Zero (or none) means start from the beginning.
     */
    fun mediaPosition(key: String): Long =
        prefs.getLong(mediaPositionKey(key), 0L).coerceAtLeast(0L)

    fun setMediaPosition(key: String, positionMs: Long) {
        val edit = prefs.edit()
        rememberMedia(edit, key)
        edit.putLong(mediaPositionKey(key), positionMs.coerceAtLeast(0L))
        edit.apply()
    }

    /**
     * Which subtitle a file was last watched with, so it comes back the same
     * rather than defaulting every time. "off" means subtitles were turned off;
     * anything else is a token naming the chosen track (see the player). Kept
     * beside the position under the same budget, and forgotten with it.
     */
    fun subtitleChoice(key: String): String? =
        prefs.getString(mediaSubtitleKey(key), null)?.ifEmpty { null }

    fun setSubtitleChoice(key: String, token: String) {
        val edit = prefs.edit()
        rememberMedia(edit, key)
        edit.putString(mediaSubtitleKey(key), token)
        edit.apply()
    }

    /**
     * Moves [key] to the front of the remembered-media list and drops the oldest
     * past the cap, forgetting its position and subtitle together so the two
     * never fall out of step over which files are still remembered.
     */
    private fun rememberMedia(edit: SharedPreferences.Editor, key: String) {
        val keys = (mediaKeys() - key).toMutableList()
        keys += key
        while (keys.size > MAX_REMEMBERED_MEDIA) {
            val dropped = keys.removeAt(0)
            edit.remove(mediaPositionKey(dropped))
            edit.remove(mediaSubtitleKey(dropped))
        }
        edit.putString(KEY_MEDIA_KEYS, keys.joinToString(KEY_SEPARATOR))
    }

    private fun mediaKeys(): List<String> =
        prefs.getString(KEY_MEDIA_KEYS, null)
            ?.split(KEY_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private fun mediaPositionKey(key: String) = "$KEY_MEDIA_POSITION${hash(key)}"

    private fun mediaSubtitleKey(key: String) = "$KEY_MEDIA_SUBTITLE${hash(key)}"

    /** How large the player draws subtitles, as a fraction of the screen. */
    fun subtitleScale(): Float =
        prefs.getFloat(KEY_SUBTITLE_SCALE, DEFAULT_SUBTITLE_SCALE)
            .coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE)

    /** What colour the player draws subtitles. */
    fun subtitleColor(): Int = prefs.getInt(KEY_SUBTITLE_COLOR, DEFAULT_SUBTITLE_COLOR)

    /** Remembers the subtitle size and colour, applied to every video. */
    fun setSubtitleStyle(scale: Float, color: Int) {
        prefs.edit()
            .putFloat(KEY_SUBTITLE_SCALE, scale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE))
            .putInt(KEY_SUBTITLE_COLOR, color)
            .apply()
    }

    // A 32-bit hashCode would risk two files whose hashes collide sharing -- and
    // overwriting -- each other's saved place, and the eviction list falling out
    // of step with the stored values. A SHA-256 prefix does not collide in any
    // collection a phone will ever hold.
    private fun hash(key: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .take(16).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_MEDIA_POSITION = "media_pos_"
        private const val KEY_MEDIA_SUBTITLE = "media_sub_"
        private const val KEY_MEDIA_KEYS = "media_pos_keys"
        private const val KEY_SUBTITLE_SCALE = "subtitle_scale"
        private const val KEY_SUBTITLE_COLOR = "subtitle_color"
        private const val KEY_SEPARATOR = "\n"

        const val DEFAULT_SUBTITLE_SCALE = 0.0533f
        const val MIN_SUBTITLE_SCALE = 0.03f
        const val MAX_SUBTITLE_SCALE = 0.12f
        val DEFAULT_SUBTITLE_COLOR = 0xFFFFFFFF.toInt()

        // The place is kept for the most recent files only; the oldest is
        // forgotten first, so the preferences file does not grow without end.
        private const val MAX_REMEMBERED_MEDIA = 300
    }
}
