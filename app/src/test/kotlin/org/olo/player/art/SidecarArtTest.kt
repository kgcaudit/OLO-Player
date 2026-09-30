package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which artwork a folder already carries. Pinned because the priority is the whole
 * point: a folder of several films must give each its own file-named poster before
 * a shared folder cover, and the .nfo reader must find the poster a scraper wrote.
 */
class SidecarArtTest {

    @Test
    fun `file-named poster wins over the folder cover`() {
        val siblings = listOf("Dune.2021.mkv", "Dune.2021-poster.jpg", "poster.jpg")
        assertEquals("Dune.2021-poster.jpg", SidecarArt.pick(siblings, "Dune.2021.mkv"))
    }

    @Test
    fun `a same-name image is taken when there is no explicit poster`() {
        val siblings = listOf("Interstellar.mkv", "Interstellar.jpg")
        assertEquals("Interstellar.jpg", SidecarArt.pick(siblings, "Interstellar.mkv"))
    }

    @Test
    fun `falls back to a folder-level cover`() {
        val siblings = listOf("movie.mkv", "folder.jpg", "cover.png")
        // poster/folder/cover order: folder.jpg outranks cover.png.
        assertEquals("folder.jpg", SidecarArt.pick(siblings, "movie.mkv"))
    }

    @Test
    fun `matching ignores case but returns the real spelling`() {
        val siblings = listOf("Film.mkv", "POSTER.JPG")
        assertEquals("POSTER.JPG", SidecarArt.pick(siblings, "Film.mkv"))
    }

    @Test
    fun `no companion image means null`() {
        val siblings = listOf("Film.mkv", "Film.srt", "notes.txt")
        assertNull(SidecarArt.pick(siblings, "Film.mkv"))
    }

    @Test
    fun `a landscape fanart is not used as a poster`() {
        val siblings = listOf("Film.mkv", "fanart.jpg", "backdrop.jpg")
        assertNull(SidecarArt.pick(siblings, "Film.mkv"))
    }

    @Test
    fun `nfo file is found by film name, then the generic names`() {
        assertEquals("Dune.2021.nfo", SidecarArt.nfoFor(listOf("Dune.2021.mkv", "Dune.2021.nfo"), "Dune.2021.mkv"))
        assertEquals("movie.nfo", SidecarArt.nfoFor(listOf("Dune.2021.mkv", "movie.nfo"), "Dune.2021.mkv"))
        assertNull(SidecarArt.nfoFor(listOf("Dune.2021.mkv"), "Dune.2021.mkv"))
    }

    @Test
    fun `nfo poster aspect wins over other thumbs`() {
        val nfo = """
            <movie>
              <thumb aspect="banner">http://x/banner.jpg</thumb>
              <thumb aspect="poster">http://x/poster.jpg</thumb>
            </movie>
        """.trimIndent()
        assertEquals("http://x/poster.jpg", SidecarArt.fromNfo(nfo))
    }

    @Test
    fun `nfo art poster element is read`() {
        val nfo = "<movie><art><poster>/local/art.png</poster></art></movie>"
        assertEquals("/local/art.png", SidecarArt.fromNfo(nfo))
    }

    @Test
    fun `nfo with no artwork is null`() {
        assertNull(SidecarArt.fromNfo("<movie><title>Dune</title></movie>"))
    }
}
