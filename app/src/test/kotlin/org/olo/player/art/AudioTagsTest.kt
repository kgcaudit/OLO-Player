package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WAV처럼 태그가 없는 음악을 보기 좋게 다루는 두 순수 로직: 파일명에서 아티스트·제목 추정과
 * WAV에 심긴 ID3v2 태그 파싱. 바이트 경계·구분자 처리를 못 박아, 손상 입력에도 빈 값으로만
 * 떨어지고 재생에는 영향이 없게 한다.
 */
class AudioTagsTest {

    // --- 파일명 추정 ----------------------------------------------------------

    @Test fun `artist - title splits on the first separator`() {
        assertEquals(TrackName("Daft Punk", "One More Time"), guessTrackName("Daft Punk - One More Time.wav"))
    }

    @Test fun `a leading track number with a separator is stripped`() {
        assertEquals(TrackName("Artist", "Song"), guessTrackName("01. Artist - Song.flac"))
        assertEquals(TrackName(null, "Song Name"), guessTrackName("07 - Song Name.mp3"))
    }

    @Test fun `underscores become spaces`() {
        assertEquals(TrackName("Artist", "Title"), guessTrackName("Artist_-_Title.wav"))
    }

    @Test fun `no separator means the whole name is the title`() {
        assertEquals(TrackName(null, "SongOnly"), guessTrackName("SongOnly.wav"))
    }

    @Test fun `a numeric title is not mistaken for a track number`() {
        // "1917" 뒤에 구분자가 없으므로 번호로 보지 않는다 -- 숫자 제목을 깨뜨리지 않는다.
        assertEquals(TrackName(null, "1917"), guessTrackName("1917.wav"))
    }

    @Test fun `a leading dash is not a separator`() {
        // " - "(공백-하이픈-공백)만 구분자로 본다. "- Title"은 앞에 공백이 없어 통째로 제목.
        assertEquals(TrackName(null, "- Title"), guessTrackName("- Title.wav"))
    }

    // --- ID3v2 파싱 -----------------------------------------------------------

    @Test fun `id3v2_3 text frames yield title artist album`() {
        val bytes = id3(0x03, textFrame("TIT2", "Song"), textFrame("TPE1", "Band"), textFrame("TALB", "Record"))
        assertEquals(AudioTagData("Song", "Band", "Record", null), parseId3v2(bytes))
    }

    @Test fun `id3v2_4 synchsafe frame sizes are read`() {
        val bytes = id3(0x04, textFrame("TIT2", "제목", synchsafeSize = true), textFrame("TPE1", "가수", synchsafeSize = true))
        val tags = parseId3v2(bytes)
        assertEquals("제목", tags.title)
        assertEquals("가수", tags.artist)
    }

    @Test fun `non-id3 bytes parse to empty`() {
        assertEquals(AudioTagData(), parseId3v2("RIFFxxxxWAVE".toByteArray(Charsets.US_ASCII)))
        assertEquals(AudioTagData(), parseId3v2(ByteArray(3)))
    }

    @Test fun `a truncated frame does not overrun`() {
        // 프레임 크기가 실제 버퍼를 넘으면 안전하게 멈춘다(제목만 읽히고 나머지는 무시).
        val good = textFrame("TIT2", "Only")
        val bogus = "TPE1".toByteArray(Charsets.US_ASCII) + beSize(9999) + byteArrayOf(0, 0, 0x03)
        val bytes = id3(0x03, good + bogus)
        assertEquals("Only", parseId3v2(bytes).title)
        assertNull(parseId3v2(bytes).artist)
    }

    // --- 테스트용 ID3 바이트 빌더 --------------------------------------------

    private fun id3(major: Int, vararg frames: ByteArray): ByteArray {
        val body = if (frames.isEmpty()) ByteArray(0) else frames.reduce { a, b -> a + b }
        return "ID3".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(major.toByte(), 0x00, 0x00) + synchsafeSize(body.size) + body
    }

    private fun textFrame(id: String, text: String, synchsafeSize: Boolean = false): ByteArray {
        val data = byteArrayOf(0x03) + text.toByteArray(Charsets.UTF_8) // 0x03 = UTF-8
        val size = if (synchsafeSize) synchsafeSize(data.size) else beSize(data.size)
        return id.toByteArray(Charsets.US_ASCII) + size + byteArrayOf(0, 0) + data
    }

    private fun beSize(n: Int) = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

    private fun synchsafeSize(n: Int) = byteArrayOf(
        ((n ushr 21) and 0x7F).toByte(), ((n ushr 14) and 0x7F).toByte(),
        ((n ushr 7) and 0x7F).toByte(), (n and 0x7F).toByte(),
    )
}
