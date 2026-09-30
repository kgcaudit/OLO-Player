package org.olo.player.ui

import org.olo.player.ftp.RemoteEntry

/** What a browse list is ordered by. */
enum class SortBy { NAME, DATE, SIZE }

/**
 * Orders a folder's entries for display. Folders always come before files -- a
 * person browsing wants the way down the tree first, whatever the sort -- and
 * within each group the chosen key decides, with the direction flipping it.
 *
 * Pure so it is tested: a wrong order, or files creeping above folders, is a daily
 * annoyance the tests guard against.
 */
object BrowseSort {

    fun sort(entries: List<RemoteEntry>, by: SortBy, ascending: Boolean): List<RemoteEntry> {
        val within: Comparator<RemoteEntry> = when (by) {
            SortBy.NAME -> Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }
            // A missing date/size sorts as the smallest, so unknowns sink to the
            // bottom in the common newest/largest-first view.
            SortBy.DATE -> compareBy { it.modified ?: Long.MIN_VALUE }
            SortBy.SIZE -> compareBy { it.size ?: Long.MIN_VALUE }
        }
        val directed = if (ascending) within else within.reversed()
        // Folders first is not part of the direction: it holds whichever way the
        // files are sorted.
        return entries.sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.then(directed))
    }
}
