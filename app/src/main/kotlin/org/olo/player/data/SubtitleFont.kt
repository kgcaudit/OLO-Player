package org.olo.player.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * The person's chosen subtitle font (TTF/OTF). The picked file is copied once into
 * app storage rather than held as a content URI, so it survives the picker's grant
 * being revoked and the player can load it with [Typeface.createFromFile] without
 * any permission. [AppPreferences.subtitleFontName] remembers its display name;
 * this object owns the file itself.
 */
object SubtitleFont {

    private fun file(context: Context) = File(context.filesDir, "subtitle_font")

    /**
     * Copies the picked font into app storage and validates it is a real font.
     * Returns the display name on success, or null (leaving no file) on failure --
     * a stream that will not decode as a typeface is rejected rather than kept.
     */
    fun install(context: Context, uri: Uri): String? = runCatching {
        val target = file(context)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open font stream" }
            target.outputStream().use { input.copyTo(it) }
        }
        if (typeface(context) == null) {
            clear(context)
            null
        } else {
            displayName(context, uri) ?: "사용자 글꼴"
        }
    }.getOrElse { clear(context); null }

    /** The installed subtitle [Typeface], or null when none is set (use the default). */
    fun typeface(context: Context): Typeface? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { Typeface.createFromFile(f) }.getOrNull()
    }

    /** Removes the installed font, so subtitles fall back to the default. */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** The human name of a picked document, from the content resolver. */
    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
