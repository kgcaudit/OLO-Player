package org.olo.player.ui

/**
 * What a file is, coarsely, from its extension.
 *
 * OLO Player only needs to tell a video from a sound (which player to open, and
 * which controls it gets), and to spot media at all in the file picker. So this
 * is the reference app's kind table trimmed to what the player asks of it: the
 * tile glyphs and the many document/code/archive kinds are gone, and anything
 * unrecognised is [OTHER].
 */
enum class FileKind {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    OTHER,
}

/**
 * The kind of a file, from its name.
 *
 * The extension and nothing else. A name with no extension, or one this table
 * has not seen, is [FileKind.OTHER].
 */
fun kindOf(name: String, isDirectory: Boolean): FileKind {
    if (isDirectory) return FileKind.FOLDER
    val cut = name.lastIndexOf('.')
    if (cut <= 0) return FileKind.OTHER
    return BY_EXTENSION[name.substring(cut + 1).lowercase()] ?: FileKind.OTHER
}

/** Whether the app's own player shows this file: a video or a sound. */
fun looksMedia(name: String): Boolean =
    kindOf(name, false).let { it == FileKind.VIDEO || it == FileKind.AUDIO }

/** A video, so the player shows a picture rather than only controls. */
fun looksVideo(name: String): Boolean = kindOf(name, false) == FileKind.VIDEO

private val BY_EXTENSION: Map<String, FileKind> = buildMap {
    for (e in "jpg jpeg png gif webp bmp heic heif tiff tif svg".split(" ")) put(e, FileKind.IMAGE)
    for (e in "mkv mp4 avi mov wmv flv webm m4v mpg mpeg ts m2ts".split(" ")) put(e, FileKind.VIDEO)
    // The sound extensions, kept in step with PlaybackService.AUDIO_EXTENSIONS
    // (which decides the music controls). The two must agree.
    for (e in "mp3 flac wav aac ogg m4a wma opus".split(" ")) put(e, FileKind.AUDIO)
}
