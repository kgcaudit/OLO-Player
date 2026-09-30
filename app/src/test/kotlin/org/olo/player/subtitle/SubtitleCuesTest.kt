package org.olo.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The read from a subtitle file to timed cues, and picking the active one. Pinned
 * because a delay control is only as right as this: a mis-parsed timestamp or a
 * dropped line shows the wrong caption, or none, exactly when the reader is trying
 * to fix the timing.
 */
class SubtitleCuesTest {

    @Test
    fun `srt block -- index, comma millis, multi-line text`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            첫 줄
            둘째 줄

            2
            00:00:04,000 --> 00:00:05,000
            다음
        """.trimIndent()
        val cues = SubtitleCues.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(SubtitleCue(1000, 3500, "첫 줄\n둘째 줄"), cues[0])
        assertEquals(SubtitleCue(4000, 5000, "다음"), cues[1])
    }

    @Test
    fun `vtt with header, dot millis, and tags stripped`() {
        val vtt = """
            WEBVTT

            00:01.000 --> 00:02.000
            <b>강조</b> 텍스트
        """.trimIndent()
        val cues = SubtitleCues.parse(vtt)
        assertEquals(1, cues.size)
        assertEquals(SubtitleCue(1000, 2000, "강조 텍스트"), cues[0])
    }

    @Test
    fun `hours are read`() {
        val cues = SubtitleCues.parse("01:02:03,004 --> 01:02:04,000\n밤")
        assertEquals(3_723_004L, cues[0].startMs)
    }

    @Test
    fun `a short millis is padded, not read as units`() {
        // ".5" is half a second, not five milliseconds.
        val cues = SubtitleCues.parse("00:00:00.5 --> 00:00:01.0\nx")
        assertEquals(500L, cues[0].startMs)
    }

    @Test
    fun `active cue is the one covering the moment`() {
        val cues = listOf(SubtitleCue(1000, 2000, "가"), SubtitleCue(3000, 4000, "나"))
        assertEquals("가", SubtitleCues.activeText(cues, 1500))
        assertNull(SubtitleCues.activeText(cues, 2500))
        assertEquals("나", SubtitleCues.activeText(cues, 3000))
    }

    @Test
    fun `the end is exclusive`() {
        val cues = listOf(SubtitleCue(1000, 2000, "가"))
        assertNull(SubtitleCues.activeText(cues, 2000))
    }

    @Test
    fun `overlapping cues are joined`() {
        val cues = listOf(SubtitleCue(1000, 3000, "위"), SubtitleCue(1500, 2500, "아래"))
        assertEquals("위\n아래", SubtitleCues.activeText(cues, 2000))
    }

    @Test
    fun `a note block and empty text are skipped`() {
        val vtt = """
            WEBVTT

            NOTE this is a comment

            00:00:01,000 --> 00:00:02,000
        """.trimIndent()
        assertEquals(0, SubtitleCues.parse(vtt).size)
    }
}
