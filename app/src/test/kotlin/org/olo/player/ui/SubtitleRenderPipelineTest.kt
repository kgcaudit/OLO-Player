package org.olo.player.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.olo.player.subtitle.SubtitleCue
import org.olo.player.subtitle.SubtitleCues
import org.robolectric.RobolectricTestRunner

/**
 * 자막 렌더 준비 경로 대조(디코드→서식). 화면에 그려지기 직전까지의 두 단계를 고정한다.
 *  1) SAMI(.smi, 한국어권에 흔함)를 VTT로 바꿔 타임드 큐로 읽어내는 변환+파싱.
 *  2) 큐 텍스트의 <i>/<b>/<u>를 실제 그리기에 쓰는 AnnotatedString 스팬(기울임/굵게/밑줄)으로 세우는
 *     [buildSubtitleAnnotated] -- 오버레이가 이 문자열을 그대로 Text로 그린다.
 * 실제 픽셀 렌더는 Robolectric NATIVE 하니스로 PNG를 떠 눈으로 대조한다(스크래치패드, 커밋 안 함).
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleRenderPipelineTest {

    // ── SAMI → VTT → 큐: 다음 Sync가 이전 자막의 끝이 되고, <br>·엔티티가 풀리는지. ──
    @Test
    fun `SAMI is converted and read into timed cues`() {
        val sami = """
            <SAMI>
            <BODY>
            <SYNC Start=1000><P Class=KRCC>첫 줄<br>둘째 줄
            <SYNC Start=3000><P Class=KRCC>다음 &amp; 끝
            <SYNC Start=5000><P Class=KRCC>&nbsp;
            </BODY>
            </SAMI>
        """.trimIndent()

        val vtt = SamiSubtitles.toVtt(sami)
        assertTrue("SAMI로 인식돼 VTT로 변환돼야 한다", vtt != null && vtt.startsWith("WEBVTT"))

        val cues = SubtitleCues.parse(vtt!!)
        assertEquals(2, cues.size) // 마지막 공백(&nbsp;)은 '지우기' 마커라 큐가 아니다
        assertEquals(SubtitleCue(1000, 3000, "첫 줄\n둘째 줄"), cues[0])
        assertEquals(SubtitleCue(3000, 5000, "다음 & 끝"), cues[1])
    }

    @Test
    fun `non-SAMI text is rejected by the converter`() {
        assertNull(SamiSubtitles.toVtt("1\n00:00:01,000 --> 00:00:02,000\nhi"))
    }

    // ── 서식: 평문은 스팬이 없고, <i>/<b>/<u>는 해당 구간에 정확한 스타일이 걸린다. ──
    @Test
    fun `plain text carries no style spans`() {
        val a = buildSubtitleAnnotated("그냥 자막")
        assertEquals("그냥 자막", a.text)
        assertTrue("평문엔 스팬이 없어야 한다", a.spanStyles.isEmpty())
    }

    @Test
    fun `italic bold underline tags become the right spans`() {
        val a = buildSubtitleAnnotated("보통 <i>기울임</i> <b>굵게</b> <u>밑줄</u>")
        assertEquals("보통 기울임 굵게 밑줄", a.text)

        val italic = a.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertEquals("기울임", a.text.substring(italic.start, italic.end))

        val bold = a.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("굵게", a.text.substring(bold.start, bold.end))

        val underline = a.spanStyles.single { it.item.textDecoration == TextDecoration.Underline }
        assertEquals("밑줄", a.text.substring(underline.start, underline.end))
    }

    @Test
    fun `nested tags keep both styles on the overlap`() {
        val a = buildSubtitleAnnotated("<b><i>둘다</i></b>")
        assertEquals("둘다", a.text)
        val span = a.spanStyles.single()
        assertEquals(FontStyle.Italic, span.item.fontStyle)
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }
}
