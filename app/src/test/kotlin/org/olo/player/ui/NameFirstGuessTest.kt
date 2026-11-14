package org.olo.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 이름 우선 포스터 추정 고정. 네트워크에서 폴더 LIST를 기다리지 않고 '이름만으로' 포스터를 붙일지
 * 가르는 판단이라, 연도 있는 영화 제목엔 붙이고(영화 배지) 연도 없는 평범한 폴더엔 안 붙여
 * (null) 포스터가 잘못 떴다 사라지는 깜빡임이 없도록 못박는다.
 */
class NameFirstGuessTest {

    @Test
    fun `movie folder with a year gets a film poster guess`() {
        val g = nameFirstGuess("메이데이(2026)", folderName = "MOVIE")
        assertEquals(NameGuess("메이데이(2026)", FolderBadgeKind.FILM), g)
    }

    @Test
    fun `another year-tagged title also guesses`() {
        val g = nameFirstGuess("강령사바(2026)", folderName = "MOVIE")
        assertEquals(FolderBadgeKind.FILM, g?.badge)
        assertEquals("강령사바(2026)", g?.posterName)
    }

    @Test
    fun `a plain folder name without a year is left alone`() {
        assertNull("연도 없는 평범한 폴더엔 포스터를 먼저 붙이지 않는다", nameFirstGuess("Downloads", folderName = null))
        assertNull(nameFirstGuess("Documents", folderName = null))
        assertNull(nameFirstGuess("backup_2", folderName = null))
    }

    @Test
    fun `an episode-marked folder name guesses a series`() {
        val g = nameFirstGuess("Breaking.Bad.S01E01", folderName = null)
        assertEquals(FolderBadgeKind.SERIES, g?.badge)
        assertEquals("Breaking.Bad.S01E01 S01E01", g?.posterName)
    }
}
