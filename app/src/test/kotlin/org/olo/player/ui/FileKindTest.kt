package org.olo.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The extension → [FileKind] mapping the browse tiles hang on. If an extension
 * lands on the wrong kind, the row shows the wrong tile (and video/audio decide
 * which player opens), so the mapping is pinned here.
 */
class FileKindTest {

    @Test
    fun `a folder is FOLDER whatever its name`() {
        assertEquals(FileKind.FOLDER, kindOf("movie.mkv", isDirectory = true))
        assertEquals(FileKind.FOLDER, kindOf("HDD1", isDirectory = true))
    }

    @Test
    fun `extensions map to their kind`() {
        assertEquals(FileKind.VIDEO, kindOf("여행 영상.mkv", false))
        assertEquals(FileKind.VIDEO, kindOf("trailer.MP4", false)) // case-insensitive
        assertEquals(FileKind.AUDIO, kindOf("좋아하는 곡.flac", false))
        assertEquals(FileKind.IMAGE, kindOf("poster.jpg", false))
        assertEquals(FileKind.ARCHIVE, kindOf("backup.zip", false))
        assertEquals(FileKind.DOCUMENT, kindOf("note.txt", false))
        assertEquals(FileKind.CODE, kindOf("Main.kt", false))
        assertEquals(FileKind.APP, kindOf("app.apk", false))
    }

    @Test
    fun `no extension or unknown is OTHER`() {
        assertEquals(FileKind.OTHER, kindOf("README", false))
        assertEquals(FileKind.OTHER, kindOf("archive.tar.xyz", false))
    }

    @Test
    fun `looksMedia is only video and audio`() {
        assertEquals(true, looksMedia("a.mkv"))
        assertEquals(true, looksMedia("a.mp3"))
        assertEquals(false, looksMedia("a.jpg")) // an image is not played
        assertEquals(false, looksMedia("a.txt"))
    }
}
