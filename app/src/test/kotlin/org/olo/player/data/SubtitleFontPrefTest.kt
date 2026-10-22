package org.olo.player.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The chosen subtitle font's remembered name: absent by default, round-trips, and
 * clears back to the default (null) -- the one bit 설정 reads to show which font is
 * set and the player reads to decide whether to load a custom typeface.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleFontPrefTest {

    private fun prefs() = AppPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun `default is null`() {
        assertNull(prefs().subtitleFontName())
    }

    @Test
    fun `set then get round-trips`() {
        val p = prefs()
        p.setSubtitleFontName("NanumGothic.ttf")
        assertEquals("NanumGothic.ttf", p.subtitleFontName())
    }

    @Test
    fun `null or blank clears back to default`() {
        val p = prefs()
        p.setSubtitleFontName("Pretendard.otf")
        p.setSubtitleFontName(null)
        assertNull(p.subtitleFontName())
        p.setSubtitleFontName("x")
        p.setSubtitleFontName("")
        assertNull(p.subtitleFontName())
    }
}
