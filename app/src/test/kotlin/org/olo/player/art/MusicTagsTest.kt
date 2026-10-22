package org.olo.player.art

import android.media.MediaMetadataRetriever as MMR
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 태그 읽기 순수 매핑(키 추출기 → TagField)과 쓰기 계약 기본값을 네트워크·파일 없이 고정한다.
 * (한국어 태그가 그대로 접히는지, 빈 값 제외·연도 보완·지원 포맷 판정까지 확인.)
 */
class MusicTagsTest {

    @Test
    fun `map fields folds standard keys and drops blanks`() {
        val values = mapOf(
            MMR.METADATA_KEY_TITLE to "좋은 날",
            MMR.METADATA_KEY_ARTIST to "아이유",
            MMR.METADATA_KEY_ALBUM to "  ", // 공백 -> 제외
            MMR.METADATA_KEY_CD_TRACK_NUMBER to "3/12",
            MMR.METADATA_KEY_GENRE to "K-Pop",
        )
        val t = TagReader.mapFields { values[it] }
        assertEquals("좋은 날", t[TagField.TITLE])
        assertEquals("아이유", t[TagField.ARTIST])
        assertNull("공백은 제외", t[TagField.ALBUM])
        assertEquals("3/12", t[TagField.TRACK])
        assertEquals("K-Pop", t[TagField.GENRE])
    }

    @Test
    fun `year falls back to date prefix when year is missing`() {
        val withYear = TagReader.mapFields { if (it == MMR.METADATA_KEY_YEAR) "2010" else null }
        assertEquals("2010", withYear[TagField.YEAR])

        val onlyDate = TagReader.mapFields { if (it == MMR.METADATA_KEY_DATE) "20240131" else null }
        assertEquals("2024", onlyDate[TagField.YEAR])
    }

    @Test
    fun `writable extensions cover the main formats, writer defaults to unsupported`() {
        assertTrue("mp3" in TAG_WRITABLE_EXTENSIONS)
        assertTrue("flac" in TAG_WRITABLE_EXTENSIONS)
        assertTrue("m4a" in TAG_WRITABLE_EXTENSIONS)
        assertFalse("wma" in TAG_WRITABLE_EXTENSIONS)

        // 라이브러리 연결 전 기본 구현은 아무것도 쓰지 않는다.
        assertFalse(NoopTagWriter.supports(File("x.mp3")))
        assertEquals(TagWriteResult.Unsupported, NoopTagWriter.write(File("x.mp3"), emptyMap(), null))
    }
}
