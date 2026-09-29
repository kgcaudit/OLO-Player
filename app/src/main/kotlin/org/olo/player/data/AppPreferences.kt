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

    // ---- App-wide defaults (the settings tree) -------------------------------
    // Why here: these are the values a fresh file opens with, shared with the
    // player's live sheet. Each is a plain scalar with a sensible default, so
    // reading one before it is ever set still gives the app's intended baseline.

    fun resumeEnabled(): Boolean = prefs.getBoolean(KEY_RESUME, true)
    fun setResumeEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_RESUME, v).apply()

    fun autoPlayNext(): Boolean = prefs.getBoolean(KEY_AUTO_NEXT, true)
    fun setAutoPlayNext(v: Boolean) = prefs.edit().putBoolean(KEY_AUTO_NEXT, v).apply()

    fun backgroundPlay(): Boolean = prefs.getBoolean(KEY_BG_PLAY, false)
    fun setBackgroundPlay(v: Boolean) = prefs.edit().putBoolean(KEY_BG_PLAY, v).apply()

    fun keepScreenOn(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN, true)
    fun setKeepScreenOn(v: Boolean) = prefs.edit().putBoolean(KEY_KEEP_SCREEN, v).apply()

    /** Rewind/forward step in seconds (10/15/30). */
    fun seekIntervalSec(): Int = prefs.getInt(KEY_SEEK_STEP, 10).coerceIn(5, 60)
    fun setSeekIntervalSec(v: Int) = prefs.edit().putInt(KEY_SEEK_STEP, v).apply()

    /** The speed a fresh video/song starts at. */
    fun defaultSpeed(): Float = prefs.getFloat(KEY_DEFAULT_SPEED, 1f).coerceIn(0.25f, 4f)
    fun setDefaultSpeed(v: Float) = prefs.edit().putFloat(KEY_DEFAULT_SPEED, v.coerceIn(0.25f, 4f)).apply()

    fun gestureSpeed(): Boolean = prefs.getBoolean(KEY_GESTURE_SPEED, true)
    fun setGestureSpeed(v: Boolean) = prefs.edit().putBoolean(KEY_GESTURE_SPEED, v).apply()

    fun doubleTapSeek(): Boolean = prefs.getBoolean(KEY_DOUBLE_TAP, true)
    fun setDoubleTapSeek(v: Boolean) = prefs.edit().putBoolean(KEY_DOUBLE_TAP, v).apply()

    /** Preferred decoder: "auto", "hw" or "sw". */
    fun decoder(): String = prefs.getString(KEY_DECODER, "auto") ?: "auto"
    fun setDecoder(v: String) = prefs.edit().putString(KEY_DECODER, v).apply()

    fun subtitleEnabled(): Boolean = prefs.getBoolean(KEY_SUB_ON, true)
    fun setSubtitleEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_SUB_ON, v).apply()

    fun subtitleOutline(): Boolean = prefs.getBoolean(KEY_SUB_OUTLINE, true)
    fun setSubtitleOutline(v: Boolean) = prefs.edit().putBoolean(KEY_SUB_OUTLINE, v).apply()

    /** Subtitle anchor: "bottom" (default) or "top". */
    fun subtitlePosition(): String = prefs.getString(KEY_SUB_POS, "bottom") ?: "bottom"
    fun setSubtitlePosition(v: String) = prefs.edit().putString(KEY_SUB_POS, v).apply()

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

        // App-wide defaults (settings tree)
        private const val KEY_RESUME = "set_resume"
        private const val KEY_AUTO_NEXT = "set_auto_next"
        private const val KEY_BG_PLAY = "set_bg_play"
        private const val KEY_KEEP_SCREEN = "set_keep_screen"
        private const val KEY_SEEK_STEP = "set_seek_step"
        private const val KEY_DEFAULT_SPEED = "set_default_speed"
        private const val KEY_GESTURE_SPEED = "set_gesture_speed"
        private const val KEY_DOUBLE_TAP = "set_double_tap"
        private const val KEY_DECODER = "set_decoder"
        private const val KEY_SUB_ON = "set_sub_on"
        private const val KEY_SUB_OUTLINE = "set_sub_outline"
        private const val KEY_SUB_POS = "set_sub_pos"

        const val DEFAULT_SUBTITLE_SCALE = 0.0533f
        const val MIN_SUBTITLE_SCALE = 0.03f
        const val MAX_SUBTITLE_SCALE = 0.12f
        val DEFAULT_SUBTITLE_COLOR = 0xFFFFFFFF.toInt()

        // The place is kept for the most recent files only; the oldest is
        // forgotten first, so the preferences file does not grow without end.
        private const val MAX_REMEMBERED_MEDIA = 300
    }
}
