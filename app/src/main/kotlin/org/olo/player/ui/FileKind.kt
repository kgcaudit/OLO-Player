package org.olo.player.ui

import androidx.annotation.DrawableRes
import org.olo.player.R

/**
 * What a file is, coarsely, from its extension, and the two-tone white tile glyph
 * that stands for it.
 *
 * Ported from OLO Explorer's kind table so the browse list reads as the same app:
 * a hue-distinct tile per kind (see [org.olo.player.ui.theme.OloColors]) carrying
 * [glyph]. The player still only *plays* a video or a sound ([looksMedia]); the
 * other kinds exist so a folder listing shows every file with the right tile
 * rather than a single grey icon. Anything unrecognised is [OTHER].
 */
enum class FileKind(@DrawableRes val glyph: Int) {
    FOLDER(R.drawable.ic_tile_folder),
    IMAGE(R.drawable.ic_tile_image),
    VIDEO(R.drawable.ic_tile_video),
    AUDIO(R.drawable.ic_tile_audio),
    DOCUMENT(R.drawable.ic_tile_document),
    ARCHIVE(R.drawable.ic_tile_archive),
    CODE(R.drawable.ic_tile_code),
    APP(R.drawable.ic_tile_app),
    OTHER(R.drawable.ic_tile_document),
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
    // (which decides the music controls). The two must agree. mka·weba(Matroska/WebM
    // 오디오), m4b(MP4 오디오북), oga(Ogg), amr은 ExoPlayer 기본 extractor가 실제로 재생한다.
    // (aiff·ape·dsd는 extractor가 없어 넣지 않는다 -- 넣으면 '열리지만 안 나는' 포맷이 된다.)
    for (e in "mp3 flac wav aac ogg oga m4a m4b wma opus mka weba amr".split(" ")) put(e, FileKind.AUDIO)
    for (e in "zip rar 7z tar gz bz2 xz tgz iso".split(" ")) put(e, FileKind.ARCHIVE)
    for (e in "pdf epub doc docx xls xlsx ppt pptx txt md rtf odt hwp srt smi ass vtt sub".split(" ")) put(e, FileKind.DOCUMENT)
    for (e in "kt java py js ts json xml yml yaml html css sh c cpp h rs go rb php".split(" ")) put(e, FileKind.CODE)
    for (e in "apk aab apks xapk".split(" ")) put(e, FileKind.APP)
}
