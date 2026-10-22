package org.olo.player.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The per-folder "이 폴더만" browse override: it round-trips against a folder key,
 * clears back to null, and keeps folders independent -- so pinning one folder's
 * view never disturbs another's or the global choice.
 */
@RunWith(RobolectricTestRunner::class)
class FolderOptionsTest {

    private fun prefs() = AppPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun `absent by default`() {
        assertNull(prefs().folderOptions("NAS2e|/a"))
    }

    @Test
    fun `set then get round-trips`() {
        val p = prefs()
        p.setFolderOptions("NAS2e|/a", "GRID|NAME|true|true|false")
        assertEquals("GRID|NAME|true|true|false", p.folderOptions("NAS2e|/a"))
    }

    @Test
    fun `null clears the pin`() {
        val p = prefs()
        p.setFolderOptions("NAS2e|/a", "GALLERY|DATE|false|true|true")
        p.setFolderOptions("NAS2e|/a", null)
        assertNull(p.folderOptions("NAS2e|/a"))
    }

    @Test
    fun `folders are independent`() {
        val p = prefs()
        p.setFolderOptions("NAS2e|/a", "GRID|NAME|true|true|false")
        p.setFolderOptions("NAS2e|/b", "LIST|SIZE|false|false|false")
        assertEquals("GRID|NAME|true|true|false", p.folderOptions("NAS2e|/a"))
        assertEquals("LIST|SIZE|false|false|false", p.folderOptions("NAS2e|/b"))
        // Clearing one leaves the other.
        p.setFolderOptions("NAS2e|/a", null)
        assertNull(p.folderOptions("NAS2e|/a"))
        assertEquals("LIST|SIZE|false|false|false", p.folderOptions("NAS2e|/b"))
    }
}
