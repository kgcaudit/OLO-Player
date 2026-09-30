package org.olo.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The breadcrumb trail the header hangs on: the root first, then one crumb per
 * folder, each carrying the absolute path it jumps to. Pinned here because a
 * wrong path on a crumb sends a tap to the wrong folder.
 */
class PathCrumbsTest {

    @Test
    fun `root only at the top`() {
        assertEquals(listOf(PathCrumb("NAS2e", "/")), pathCrumbs("NAS2e", "/"))
    }

    @Test
    fun `a deep path builds a crumb per folder with cumulative paths`() {
        val crumbs = pathCrumbs("NAS2e", "/HDD1/Database/ANIMATION")
        assertEquals(
            listOf(
                PathCrumb("NAS2e", "/"),
                PathCrumb("HDD1", "/HDD1"),
                PathCrumb("Database", "/HDD1/Database"),
                PathCrumb("ANIMATION", "/HDD1/Database/ANIMATION"),
            ),
            crumbs,
        )
    }

    @Test
    fun `the last crumb is the current folder`() {
        assertEquals("ANIMATION", pathCrumbs("NAS2e", "/HDD1/Database/ANIMATION").last().label)
        assertEquals("/HDD1/Database/ANIMATION", pathCrumbs("NAS2e", "/HDD1/Database/ANIMATION").last().path)
    }
}
