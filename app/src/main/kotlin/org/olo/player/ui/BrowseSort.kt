package org.olo.player.ui

import org.olo.player.ftp.RemoteEntry

/** What a browse list is ordered by. */
enum class SortBy { NAME, DATE, SIZE, FORMAT }

/** How the browse list is laid out. */
enum class BrowseView { LIST, GRID, GALLERY }

/**
 * Orders a folder's entries for display. By default folders lead -- a person
 * browsing wants the way down the tree first, whatever the sort -- and within each
 * group the chosen key decides, with the direction flipping it. Folders-first can
 * be turned off, and then the key orders everything together.
 *
 * Pure so it is tested: a wrong order, or files creeping above folders, is a daily
 * annoyance the tests guard against.
 */
object BrowseSort {

    fun sort(
        entries: List<RemoteEntry>,
        by: SortBy,
        ascending: Boolean,
        foldersFirst: Boolean = true,
    ): List<RemoteEntry> {
        val within: Comparator<RemoteEntry> = when (by) {
            SortBy.NAME -> Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }
            // A missing date/size sorts as the smallest, so unknowns sink to the
            // bottom in the common newest/largest-first view.
            SortBy.DATE -> compareBy { it.modified ?: Long.MIN_VALUE }
            SortBy.SIZE -> compareBy { it.size ?: Long.MIN_VALUE }
            // By file type: the extension, then the name so one type stays natural.
            SortBy.FORMAT -> Comparator { a, b ->
                val byExt = extensionOf(a.name).compareTo(extensionOf(b.name))
                if (byExt != 0) byExt else NaturalOrder.compare(a.name, b.name)
            }
        }
        val directed = if (ascending) within else within.reversed()
        return if (foldersFirst) {
            // Folders first is not part of the direction: it holds whichever way
            // the files are sorted.
            entries.sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.then(directed))
        } else {
            entries.sortedWith(directed)
        }
    }

    // A folder has no extension, so it sorts before every file under 형식.
    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(dot + 1).lowercase() else ""
    }
}
