package org.olo.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.olo.player.ftp.RemoteEntry

/**
 * Ordering a folder for display. Pinned because two rules must hold together:
 * folders always lead, and the chosen key orders within each group -- a file must
 * never climb above a folder, whichever way the sort runs.
 */
class BrowseSortTest {

    private fun dir(name: String) = RemoteEntry(name, true, "/$name", modified = 0, size = null)
    private fun file(name: String, date: Long, size: Long) =
        RemoteEntry(name, false, "/$name", modified = date, size = size)

    private fun names(list: List<RemoteEntry>) = list.map { it.name }

    @Test
    fun `folders always lead, whatever the key or direction`() {
        val entries = listOf(file("b.mkv", 5, 5), dir("Z"), file("a.mkv", 1, 1), dir("A"))
        for (by in SortBy.entries) for (asc in listOf(true, false)) {
            val sorted = BrowseSort.sort(entries, by, asc)
            assertEquals("$by asc=$asc", listOf(true, true, false, false), sorted.map { it.isDirectory })
        }
    }

    @Test
    fun `name ascending is natural order`() {
        val entries = listOf(file("ep10.mkv", 1, 1), file("ep2.mkv", 1, 1))
        assertEquals(listOf("ep2.mkv", "ep10.mkv"), names(BrowseSort.sort(entries, SortBy.NAME, ascending = true)))
    }

    @Test
    fun `date descending is newest first`() {
        val entries = listOf(file("old.mkv", 100, 1), file("new.mkv", 200, 1))
        assertEquals(listOf("new.mkv", "old.mkv"), names(BrowseSort.sort(entries, SortBy.DATE, ascending = false)))
    }

    @Test
    fun `size descending is largest first`() {
        val entries = listOf(file("small.mkv", 1, 100), file("big.mkv", 1, 900))
        assertEquals(listOf("big.mkv", "small.mkv"), names(BrowseSort.sort(entries, SortBy.SIZE, ascending = false)))
    }

    @Test
    fun `direction flips the file order`() {
        val entries = listOf(file("a.mkv", 1, 1), file("b.mkv", 2, 2))
        assertEquals(
            names(BrowseSort.sort(entries, SortBy.NAME, ascending = true)).reversed(),
            names(BrowseSort.sort(entries, SortBy.NAME, ascending = false)),
        )
    }

    @Test
    fun `an unknown size sinks below a known one when largest-first`() {
        val entries = listOf(file("known.mkv", 1, 500), RemoteEntry("unknown.mkv", false, "/u", modified = 1, size = null))
        assertEquals(listOf("known.mkv", "unknown.mkv"), names(BrowseSort.sort(entries, SortBy.SIZE, ascending = false)))
    }

    @Test
    fun `format groups by extension then name`() {
        val entries = listOf(file("b.mkv", 1, 1), file("a.srt", 1, 1), file("a.mkv", 1, 1))
        assertEquals(
            listOf("a.mkv", "b.mkv", "a.srt"),
            names(BrowseSort.sort(entries, SortBy.FORMAT, ascending = true)),
        )
    }

    @Test
    fun `folders-first off interleaves folders and files by the key`() {
        // The folder name sorts after the file, so folders-first vs not differs.
        val entries = listOf(dir("z"), file("a.mkv", 1, 1))
        assertEquals(
            listOf("a.mkv", "z"),
            names(BrowseSort.sort(entries, SortBy.NAME, ascending = true, foldersFirst = false)),
        )
        assertEquals(
            listOf("z", "a.mkv"),
            names(BrowseSort.sort(entries, SortBy.NAME, ascending = true, foldersFirst = true)),
        )
    }
}
