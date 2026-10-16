package org.olo.player.art

import java.io.File
import java.io.RandomAccessFile

/**
 * 음악 파일의 태그를 보완해 읽는다 -- 특히 WAV처럼 표준 태그 컨테이너가 없어
 * [android.media.MediaMetadataRetriever]가 비워 오는 포맷을 위해.
 *
 * 두 가지를 담당한다:
 *  1) 파일명에서 "아티스트 - 제목"을 추정([guessTrackName]) -- 태그가 전혀 없어도 Now Playing이
 *     파일명만 덜렁 보이지 않게.
 *  2) WAV(RIFF) 안에 간혹 들어 있는 메타데이터를 직접 파싱([readWavTags]) -- 표준 INFO 청크
 *     (INAM/IART/IPRD)와, 태거가 심은 ID3v2 청크(id3 )의 제목·아티스트·앨범·앨범아트.
 *
 * 순수 함수([guessTrackName]·[parseId3v2])는 유닛 테스트로 고정하고, 파일 I/O는 얇게 감싼다.
 */

/** 컨테이너에서 긁어낸 원시 태그. 비는 값은 null, 앨범아트는 원본 바이트(디코딩은 호출부). */
data class AudioTagData(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val picture: ByteArray? = null,
) {
    val isEmpty: Boolean get() = title == null && artist == null && album == null && picture == null

    // ByteArray 때문에 equals/hashCode를 손으로 맞춘다(데이터 클래스 기본은 참조 비교라 테스트가
    // 흔들린다). 실사용엔 영향 없지만 테스트·중복 제거가 정확해진다.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioTagData) return false
        return title == other.title && artist == other.artist && album == other.album &&
            (picture?.contentEquals(other.picture) ?: (other.picture == null))
    }

    override fun hashCode(): Int {
        var r = title?.hashCode() ?: 0
        r = 31 * r + (artist?.hashCode() ?: 0)
        r = 31 * r + (album?.hashCode() ?: 0)
        r = 31 * r + (picture?.contentHashCode() ?: 0)
        return r
    }
}

/** 파일명 추정 결과: 아티스트(없으면 null)와 제목. */
data class TrackName(val artist: String?, val title: String)

/**
 * 태그가 없을 때 파일명에서 아티스트·제목을 추정한다. 앞 트랙번호("01." "07 -" "3)")를 떼고,
 * 처음 나오는 " - "를 기준으로 왼쪽=아티스트, 오른쪽=제목으로 본다. 구분자가 없으면 전체가
 * 제목(아티스트 없음). 확장자·밑줄은 정리한다. 어느 쪽도 비면 통째로 제목으로 둔다.
 */
fun guessTrackName(fileName: String): TrackName {
    var stem = fileName.substringBeforeLast('.').ifBlank { fileName }
    stem = stem.replace('_', ' ').replace(Regex("""\s+"""), " ").trim()
    // 앞 트랙번호: "01." "01)" "07 - " 같이 숫자 뒤에 구분자가 붙은 형태만 떼어, "1917" 같은
    // 숫자 제목을 깨뜨리지 않는다. 떼면 빈 문자열이 되는 경우는 원문을 둔다.
    stem = Regex("""^\s*\d{1,3}\s*[.)\-]\s*""").replaceFirst(stem, "").trim().ifBlank { stem }
    val sep = stem.indexOf(" - ")
    if (sep > 0) {
        val artist = stem.substring(0, sep).trim()
        val title = stem.substring(sep + 3).trim()
        if (artist.isNotEmpty() && title.isNotEmpty()) return TrackName(artist, title)
    }
    return TrackName(null, stem)
}

/**
 * WAV(RIFF) 파일에서 메타데이터를 직접 읽는다. RIFF 청크를 순회하며 (1) LIST→INFO의
 * INAM/IART/IPRD, (2) "id3 "/"ID3 " 청크의 ID3v2 태그를 모은다. WAV가 아니거나 아무 태그도
 * 없으면 null. 어떤 손상에도 예외를 던지지 않는다(재생엔 영향 없어야 하므로).
 */
fun readWavTags(file: File): AudioTagData? = runCatching {
    RandomAccessFile(file, "r").use { raf ->
        val len = raf.length()
        if (len < 12) return null
        val riff = readAscii(raf, 4)
        raf.skipBytes(4) // RIFF 전체 크기
        val wave = readAscii(raf, 4)
        if (riff != "RIFF" || wave != "WAVE") return null

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var picture: ByteArray? = null
        var guard = 0
        while (raf.filePointer + 8 <= len && guard++ < 4096) {
            val chunkId = readAscii(raf, 4)
            val size = readLeUInt(raf)
            if (size < 0) break
            val dataStart = raf.filePointer
            val dataEnd = (dataStart + size).coerceAtMost(len)
            when (chunkId) {
                "LIST" -> if (size >= 4) {
                    val listType = readAscii(raf, 4)
                    if (listType == "INFO") {
                        while (raf.filePointer + 8 <= dataEnd) {
                            val sid = readAscii(raf, 4)
                            val ssize = readLeUInt(raf)
                            if (ssize < 0 || raf.filePointer + ssize > dataEnd) break
                            val text = readAscii(raf, ssize.coerceAtMost(8192)).trim('\u0000', ' ')
                            if (ssize > 8192) raf.skipBytes(ssize - 8192)
                            when (sid) {
                                "INAM" -> if (title == null) title = text.ifBlank { null }
                                "IART" -> if (artist == null) artist = text.ifBlank { null }
                                "IPRD" -> if (album == null) album = text.ifBlank { null }
                            }
                            if (ssize % 2 == 1) raf.skipBytes(1) // 워드 정렬
                        }
                    }
                }
                "id3 ", "ID3 " -> {
                    val cap = size.coerceAtMost(8 * 1024 * 1024)
                    val buf = ByteArray(cap)
                    raf.readFully(buf)
                    val id3 = parseId3v2(buf)
                    if (title == null) title = id3.title
                    if (artist == null) artist = id3.artist
                    if (album == null) album = id3.album
                    if (picture == null) picture = id3.picture
                }
            }
            // 다음 청크로(워드 정렬). 전진이 없으면 손상으로 보고 중단.
            var next = dataStart + size
            if (size % 2 == 1) next += 1
            if (next <= dataStart) break
            if (next >= len) break
            raf.seek(next)
        }
        val tags = AudioTagData(title, artist, album, picture)
        if (tags.isEmpty) null else tags
    }
}.getOrNull()

/**
 * ID3v2(.3/.4) 태그 바이트에서 제목(TIT2)·아티스트(TPE1)·앨범(TALB)·앨범아트(APIC)를 뽑는다.
 * v2.2(3글자 프레임)와 unsynchronisation은 드물어 다루지 않는다 -- 못 읽으면 빈 값일 뿐 재생엔
 * 무관하다. 바이트 경계를 벗어나지 않게 모든 길이를 검사한다.
 */
fun parseId3v2(buf: ByteArray): AudioTagData {
    if (buf.size < 10 || buf[0] != 'I'.code.toByte() || buf[1] != 'D'.code.toByte() || buf[2] != '3'.code.toByte()) {
        return AudioTagData()
    }
    val major = buf[3].toInt() and 0xFF
    if (major < 3) return AudioTagData() // v2.2 이하 미지원
    val tagSize = synchsafe(buf, 6)
    val end = (10 + tagSize).coerceAtMost(buf.size)
    var pos = 10
    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var picture: ByteArray? = null
    var guard = 0
    while (pos + 10 <= end && guard++ < 2048) {
        val frameId = String(buf, pos, 4, Charsets.US_ASCII)
        if (frameId[0] == '\u0000') break
        val frameSize = if (major >= 4) synchsafe(buf, pos + 4) else beUInt(buf, pos + 4)
        pos += 10
        if (frameSize <= 0 || pos + frameSize > end) break
        when (frameId) {
            "TIT2" -> if (title == null) title = decodeTextFrame(buf, pos, frameSize)
            "TPE1" -> if (artist == null) artist = decodeTextFrame(buf, pos, frameSize)
            "TALB" -> if (album == null) album = decodeTextFrame(buf, pos, frameSize)
            "APIC" -> if (picture == null) picture = decodeApic(buf, pos, frameSize)
        }
        pos += frameSize
    }
    return AudioTagData(title, artist, album, picture)
}

// --- 내부 헬퍼 -------------------------------------------------------------

// ID3 텍스트 프레임: 첫 바이트가 인코딩(0=Latin-1, 1=UTF-16+BOM, 2=UTF-16BE, 3=UTF-8).
private fun decodeTextFrame(buf: ByteArray, start: Int, size: Int): String? {
    if (size < 1) return null
    val enc = buf[start].toInt() and 0xFF
    val from = start + 1
    val raw = buf.copyOfRange(from, (start + size).coerceAtMost(buf.size))
    val charset = when (enc) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16 // BOM 포함
        2 -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }
    // UTF-16(enc 1) 디코딩은 String()이 BOM을 이미 소비하므로, 여기선 널·공백만 다듬으면 된다.
    return runCatching { String(raw, charset) }.getOrNull()
        ?.trim('\u0000', ' ')
        ?.takeIf { it.isNotBlank() }
}

// APIC: 인코딩(1) + MIME(널종료 ASCII) + 그림종류(1) + 설명(인코딩별 널종료) + 그림바이트.
// 설명 널종료는 UTF-16이면 2바이트라, 여기선 앞쪽만 안전하게 건너뛰고 그림 바이트를 취한다.
private fun decodeApic(buf: ByteArray, start: Int, size: Int): ByteArray? {
    val end = (start + size).coerceAtMost(buf.size)
    if (end - start < 4) return null
    val enc = buf[start].toInt() and 0xFF
    var p = start + 1
    // MIME: ASCII 널종료
    while (p < end && buf[p].toInt() != 0) p++
    p++ // MIME 널
    if (p >= end) return null
    p++ // 그림 종류 1바이트
    // 설명: 인코딩별 널종료(UTF-16은 2바이트 널)
    if (enc == 1 || enc == 2) {
        while (p + 1 < end && !(buf[p].toInt() == 0 && buf[p + 1].toInt() == 0)) p += 2
        p += 2
    } else {
        while (p < end && buf[p].toInt() != 0) p++
        p++
    }
    if (p >= end) return null
    return buf.copyOfRange(p, end).takeIf { it.isNotEmpty() }
}

// ID3 동기안전(synchsafe) 32비트: 각 바이트 하위 7비트만 사용.
private fun synchsafe(buf: ByteArray, at: Int): Int {
    if (at + 4 > buf.size) return 0
    return ((buf[at].toInt() and 0x7F) shl 21) or
        ((buf[at + 1].toInt() and 0x7F) shl 14) or
        ((buf[at + 2].toInt() and 0x7F) shl 7) or
        (buf[at + 3].toInt() and 0x7F)
}

// 빅엔디언 부호없는 32비트(v2.3 프레임 크기).
private fun beUInt(buf: ByteArray, at: Int): Int {
    if (at + 4 > buf.size) return 0
    return ((buf[at].toInt() and 0xFF) shl 24) or
        ((buf[at + 1].toInt() and 0xFF) shl 16) or
        ((buf[at + 2].toInt() and 0xFF) shl 8) or
        (buf[at + 3].toInt() and 0xFF)
}

private fun readAscii(raf: RandomAccessFile, n: Int): String {
    if (n <= 0) return ""
    val b = ByteArray(n)
    raf.readFully(b)
    return String(b, Charsets.ISO_8859_1)
}

// RIFF 청크 크기: 리틀엔디언 부호없는 32비트. 2GB를 넘으면(사실상 없음) -1로 본다.
private fun readLeUInt(raf: RandomAccessFile): Int {
    val b = ByteArray(4)
    raf.readFully(b)
    val v = (b[0].toLong() and 0xFF) or
        ((b[1].toLong() and 0xFF) shl 8) or
        ((b[2].toLong() and 0xFF) shl 16) or
        ((b[3].toLong() and 0xFF) shl 24)
    return if (v in 0..Int.MAX_VALUE) v.toInt() else -1
}
