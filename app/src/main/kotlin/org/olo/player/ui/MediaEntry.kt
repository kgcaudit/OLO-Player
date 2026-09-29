package org.olo.player.ui

import android.net.Uri
import java.io.File

/**
 * One item in a playlist, wherever it lives.
 *
 * The player began life playing only local files, so its playlist was a list of
 * java.io.File. Network streaming (http, ftp) does not have a File behind it, so
 * a source is described here instead: the uri media3 plays, a name to show, a
 * stable key for remembering the place, and -- only for a local file -- the File
 * itself, which the sidecar-subtitle scan, the tag reader and the lyric loader
 * need and which a network source simply does without.
 */
data class MediaEntry(
    /** What the player plays: file://, http(s):// or ftp:// (ftp carries creds). */
    val uri: Uri,
    /** The name shown in the title bar and the queue. */
    val name: String,
    /**
     * The key the saved position and subtitle choice hang on. For a network
     * source this is the uri without any credentials, so a password never
     * reaches the preferences file.
     */
    val prefKey: String = uri.toString(),
    /** The file behind a local source; null for a network one. */
    val localFile: File? = null,
) {
    /** The name without its extension, for the queue and the lyrics screen. */
    val nameWithoutExtension: String get() = name.substringBeforeLast('.', name)

    /** Whether this opens as a song rather than a film, from its name. */
    val isAudio: Boolean get() = kindOf(name, false) == FileKind.AUDIO
}
