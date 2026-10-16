package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 기본 파일 작업의 이름 규칙(사본 생성·확장자 유지·유효성)을 고정한다 -- 기기 없이 검증 가능한 핵심. */
class LocalFileOpsTest {

    @Test fun `copyName appends 사본 and keeps extension`() {
        val n = LocalFileOps.copyName("곡.flac") { false }
        assertEquals("곡 (사본).flac", n)
    }

    @Test fun `copyName avoids collision with increasing number`() {
        val taken = setOf("곡 (사본).flac", "곡 (사본 2).flac")
        val n = LocalFileOps.copyName("곡.flac") { it in taken }
        assertEquals("곡 (사본 3).flac", n)
    }

    @Test fun `copyName handles no-extension names`() {
        assertEquals("README (사본)", LocalFileOps.copyName("README") { false })
    }

    @Test fun `copyName treats leading-dot name as having no base extension`() {
        // ".hidden" -> lastIndexOf('.')==0, so dot>0 is false: 확장자 없음으로 다룬다.
        assertEquals(".hidden (사본)", LocalFileOps.copyName(".hidden") { false })
    }

    @Test fun `ensureExtension adds missing extension`() {
        assertEquals("새이름.mp3", LocalFileOps.ensureExtension("새이름", "old.mp3"))
    }

    @Test fun `ensureExtension keeps existing extension`() {
        assertEquals("새이름.mp3", LocalFileOps.ensureExtension("새이름.mp3", "old.mp3"))
        assertEquals("새이름.MP3", LocalFileOps.ensureExtension("새이름.MP3", "old.mp3")) // 대소문자 그대로
    }

    @Test fun `ensureExtension on extensionless original leaves name as typed`() {
        assertEquals("folderlike", LocalFileOps.ensureExtension("folderlike", "noext"))
    }

    @Test fun `isValidName rejects empty and path separators`() {
        assertTrue(LocalFileOps.isValidName("정상 이름.flac"))
        assertFalse(LocalFileOps.isValidName("   "))
        assertFalse(LocalFileOps.isValidName("a/b"))
        assertFalse(LocalFileOps.isValidName("a\\b"))
        assertFalse(LocalFileOps.isValidName("."))
        assertFalse(LocalFileOps.isValidName(".."))
    }
}
