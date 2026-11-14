package org.olo.player.ui

import androidx.media3.common.C
import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 트랙 라벨: 언어 코드가 우리말로 뜨는지, 같은 언어 자막을 SDH/강제로 가르는지 고정한다.
 * (사용자 신고: "DA·FI·FR"처럼 코드만 뜨고, English 두 개가 일반/SDH 구분이 안 됨.)
 */
@RunWith(RobolectricTestRunner::class)
class TrackLabelsTest {

    @Test
    fun `language codes become Korean names`() {
        assertEquals("덴마크어", trackLanguageName("da"))
        assertEquals("핀란드어", trackLanguageName("fi"))
        assertEquals("프랑스어", trackLanguageName("fr"))
        assertEquals("영어", trackLanguageName("en"))
        assertEquals("영어", trackLanguageName("eng")) // 3글자 코드도
        assertEquals("일본어", trackLanguageName("ja"))
        assertEquals("한국어", trackLanguageName("ko"))
        assertEquals("중국어", trackLanguageName("zh"))
    }

    @Test
    fun `region tag resolves to its base language`() {
        assertEquals("포르투갈어", trackLanguageName("pt-BR"))
        assertEquals("영어", trackLanguageName("en-US"))
    }

    @Test
    fun `undetermined or empty is null`() {
        assertNull(trackLanguageName("und"))
        assertNull(trackLanguageName("zxx"))
        assertNull(trackLanguageName(""))
        assertNull(trackLanguageName(null))
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun fmt(role: Int = 0, selection: Int = 0, label: String? = null): Format =
        Format.Builder().setRoleFlags(role).setSelectionFlags(selection).setLabel(label).build()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test
    fun `SDH detected from role flag or label`() {
        assertEquals("SDH", subtitleKind(fmt(role = C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND)))
        assertEquals("SDH", subtitleKind(fmt(label = "English SDH")))
        assertEquals("SDH", subtitleKind(fmt(label = "English (hearing impaired)")))
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test
    fun `forced detected from selection flag or label`() {
        assertEquals("강제", subtitleKind(fmt(selection = C.SELECTION_FLAG_FORCED)))
        assertEquals("강제", subtitleKind(fmt(label = "Forced")))
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test
    fun `a plain subtitle has no kind`() {
        assertNull(subtitleKind(fmt(label = "English")))
        assertNull(subtitleKind(fmt()))
    }
}
