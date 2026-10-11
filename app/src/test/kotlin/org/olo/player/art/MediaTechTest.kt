package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 파일명에서 읽는 기술 사양. 씬 이름의 관습적 표식을 뽑되, 없는 건 지어내지 않고 비운다.
 */
class MediaTechTest {

    @Test
    fun `a full scene name yields the expected chips`() {
        val m = MediaTech.parse("Motor City 2026 1080p BluRay x264 DTS-HD.MA.5.1-GROUP.mkv")
        assertEquals("1080p", m.resolution)
        assertEquals("H.264", m.videoCodec)
        assertEquals("DTS-HD 5.1", m.audio)
        assertEquals("BluRay", m.source)
        assertEquals("MKV", m.container)
        assertEquals(listOf("1080p", "H.264", "DTS-HD 5.1", "BluRay", "MKV"), m.chips())
    }

    @Test
    fun `4k hevc web-dl with atmos`() {
        val m = MediaTech.parse("Show.S01E01.2160p.WEB-DL.HEVC.HDR.DDP5.1.Atmos.mkv")
        assertEquals("4K", m.resolution)
        assertEquals("H.265", m.videoCodec)
        assertEquals("HDR", m.hdr)
        // Atmos가 DDP(E-AC3)보다 구체적이라 우선, 채널 5.1을 붙인다.
        assertEquals("Atmos 5.1", m.audio)
        assertEquals("WEB-DL", m.source)
    }

    @Test
    fun `dolby vision wins over plain hdr`() {
        val m = MediaTech.parse("Movie.2024.2160p.BluRay.REMUX.DoVi.HDR10.TrueHD.7.1.mkv")
        assertEquals("Dolby Vision", m.hdr)
        assertEquals("TrueHD 7.1", m.audio)
        assertEquals("BluRay", m.source)
    }

    @Test
    fun `h265 and 720p`() {
        val m = MediaTech.parse("Film 2019 720p HDTV x265 AAC.mp4")
        assertEquals("720p", m.resolution)
        assertEquals("H.265", m.videoCodec)
        assertEquals("AAC", m.audio)
        assertEquals("HDTV", m.source)
        assertEquals("MP4", m.container)
    }

    @Test
    fun `a bare name gives only the container`() {
        val m = MediaTech.parse("홈비디오.mkv")
        assertNull(m.resolution)
        assertNull(m.videoCodec)
        assertNull(m.audio)
        assertNull(m.source)
        assertEquals("MKV", m.container)
        assertEquals(listOf("MKV"), m.chips())
    }

    @Test
    fun `a year is not mistaken for an audio channel`() {
        // "2026"의 "2.0" 같은 오탐을 막는다(앞뒤가 숫자면 채널로 보지 않음).
        val m = MediaTech.parse("Clip.2026.1080p.x264.mkv")
        assertEquals("H.264", m.videoCodec)
        assertNull(m.audio) // 코덱 없음 → 채널만으로 음성 칩을 만들지 않는다
    }

    @Test
    fun `unknown extension yields no container`() {
        assertNull(MediaTech.parse("stream.strm").container)
    }

    @Test
    fun `chips are empty for a plain unknown`() {
        assertTrue(MediaTech.parse("foo.xyz").chips().isEmpty())
    }
}
