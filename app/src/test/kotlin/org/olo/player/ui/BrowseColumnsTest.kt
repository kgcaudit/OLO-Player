package org.olo.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The responsive column count for 격자·갤러리: a phone/cover width keeps 3 columns,
 * an unfolded/tablet width widens to 4~5 (confirmed Galaxy Z Fold two-spec design),
 * and it never drops below 3.
 */
class BrowseColumnsTest {

    // Galaxy Z Fold cover: ~467dp wide.
    @Test fun `cover keeps three columns`() {
        assertEquals(3, browseColumns(467f, gallery = true))
        assertEquals(3, browseColumns(467f, gallery = false))
    }

    // Galaxy Z Fold main (unfolded): ~969dp wide.
    @Test fun `unfolded widens gallery to five and grid denser`() {
        assertEquals(5, browseColumns(969f, gallery = true))
        assertEquals(6, browseColumns(969f, gallery = false))
    }

    // A mid width (a large phone landscape) sits between the two.
    @Test fun `mid width grows past three`() {
        assertEquals(4, browseColumns(720f, gallery = true))
    }

    @Test fun `never below three, even very narrow`() {
        assertEquals(3, browseColumns(200f, gallery = true))
        assertEquals(3, browseColumns(200f, gallery = false))
    }
}
