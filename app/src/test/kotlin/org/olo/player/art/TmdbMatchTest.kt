package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How the one poster is chosen from a search response. Pinned because the tempting
 * failure is quiet: TMDB returns a popular namesake, and without title+year
 * scoring we would proudly show the wrong film's poster.
 */
class TmdbMatchTest {

    private fun c(id: Int, title: String, year: Int?, poster: String? = "/p$id.jpg", pop: Double = 1.0) =
        TmdbCandidate(id, title, year, poster, pop)

    @Test
    fun `exact title and year beats a more popular namesake`() {
        val wanted = c(1, "The Matrix", 1999, pop = 5.0)
        val blockbuster = c(2, "The Matrix Resurrections", 2021, pop = 90.0)
        assertEquals(wanted, TmdbMatch.best("The Matrix", 1999, listOf(blockbuster, wanted)))
    }

    @Test
    fun `punctuation and case do not matter`() {
        val hit = c(1, "Spider-Man", 2002)
        assertEquals(hit, TmdbMatch.best("spiderman", 2002, listOf(hit)))
    }

    @Test
    fun `a different year is treated as a different work`() {
        val original = c(1, "The Grudge", 2004, pop = 3.0)
        val remake = c(2, "The Grudge", 2020, pop = 3.0)
        assertEquals(remake, TmdbMatch.best("The Grudge", 2020, listOf(original, remake)))
    }

    @Test
    fun `no plausible match returns null`() {
        val unrelated = c(1, "Frozen", 2013, pop = 80.0)
        assertNull(TmdbMatch.best("기생충", 2019, listOf(unrelated)))
    }

    @Test
    fun `a loose title match at the wrong year is rejected`() {
        // "Batman" only sits inside "Batman Begins", and the file says 2022 while
        // that film is 2005 -- a partial title at a different year is not enough to
        // claim a poster. The wrong-year penalty must pull it under the threshold.
        val looseWrongYear = c(1, "Batman Begins", 2005, pop = 50.0)
        assertNull(TmdbMatch.best("Batman", 2022, listOf(looseWrongYear)))
    }

    @Test
    fun `year off by one still matches -- release vs listing`() {
        val hit = c(1, "Some Film", 2019)
        assertEquals(hit, TmdbMatch.best("Some Film", 2018, listOf(hit)))
    }

    @Test
    fun `same title a few years apart matches across regions`() {
        // 나라마다 개봉연도가 달라도(폴더 2023 / TMDB 2022) 같은 작품으로 매칭.
        val hit = c(1, "거울 속 외딴 성", 2022)
        assertEquals(hit, TmdbMatch.best("거울 속 외딴 성", 2023, listOf(hit)))
    }

    @Test
    fun `theatrical prefix and colon do not block a match`() {
        // 폴더엔 콜론을 못 써 "귀멸의 칼날 무한성편"이지만 TMDB는 "극장판 귀멸의 칼날: 무한성편".
        // 극장판 표식과 특수문자를 무시하고 동일 작품으로 본다.
        val hit = c(1, "극장판 귀멸의 칼날: 무한성편", 2025)
        assertEquals(hit, TmdbMatch.best("귀멸의 칼날 무한성편", 2025, listOf(hit)))
    }

    @Test
    fun `a lone search hit with a poster is used despite a notation gap`() {
        // 폴더 "극장판 스파이 X 패밀리 - 코드 화이트"(2024) ↔ TMDB "극장판 스파이 패밀리 코드
        // : 화이트"(2023). X·구분자 표기 차이로 점수는 임계값 아래지만, TMDB가 돌려준 유일한
        // 후보라 채택한다(검색 결과가 1건이면 그게 그 작품이다).
        val only = c(1, "극장판 스파이 패밀리 코드 : 화이트", 2023)
        assertEquals(only, TmdbMatch.best("극장판 스파이 X 패밀리 코드 화이트", 2024, listOf(only)))
    }

    @Test
    fun `a lone hit at a clearly different year is still rejected`() {
        // 유일한 후보라도 연도가 4년 이상 벌어지면 다른 작품으로 보고 버린다.
        val only = c(1, "느슨한 제목", 2021, pop = 3.0)
        assertNull(TmdbMatch.best("완전히 다른 영화", 2015, listOf(only)))
    }

    @Test
    fun `a lone hit without a poster is not used`() {
        // 붙일 포스터가 없으면 유일한 후보여도 의미가 없다.
        val only = c(1, "느슨한 제목", 2021, poster = null)
        assertNull(TmdbMatch.best("완전히 다른 영화", 2024, listOf(only)))
    }

    @Test
    fun `a spacing difference in the title does not block a match`() {
        // 폴더 "수플레섬의"(붙임) ↔ TMDB "수플레 섬의"(띄움). 공백을 무시하므로 같은 작품.
        // (실제 실패는 TMDB 검색이 0건을 주던 것이라 TmdbClient가 앞머리로 다시 찾는다.)
        val hit = c(1, "극장판 엉덩이 탐정: 수플레 섬의 비밀", 2021)
        assertEquals(hit, TmdbMatch.best("극장판 엉덩이 탐정 수플레섬의 비밀", 2021, listOf(hit)))
    }

    @Test
    fun `with titles tied, the one that has a poster wins`() {
        val noArt = c(1, "Twins", null, poster = null, pop = 9.0)
        val withArt = c(2, "Twins", null, poster = "/t.jpg", pop = 1.0)
        assertEquals(withArt, TmdbMatch.best("Twins", null, listOf(noArt, withArt)))
    }

    @Test
    fun `empty query never matches`() {
        assertNull(TmdbMatch.best("", null, listOf(c(1, "Anything", 2000))))
    }

    // --- 동일 연도 단일작 우선(사용자 요청) --------------------------------------

    @Test
    fun `same-year single beats a wrong-year exact namesake`() {
        // "The Uprising" 2026 한 편 + 다른 해 동명작(더 유명) → 2026 단일작이 붙어야 한다.
        val older = c(1, "The Uprising", 2019, pop = 50.0)
        val thisYear = c(2, "The Uprising", 2026, pop = 1.0)
        assertEquals(thisYear, TmdbMatch.best("The Uprising", 2026, listOf(older, thisYear)))
    }

    @Test
    fun `same-year single is taken even when the title is localized`() {
        // 그 해 후보가 하나뿐이면 표기(현지화 제목)로 점수가 낮아도 채택한다.
        val localized = c(1, "봉기", 2026)
        val other = c(2, "Uprising Dawn", 2010)
        assertEquals(localized, TmdbMatch.best("The Uprising", 2026, listOf(localized, other)))
    }

    @Test
    fun `multiple same-year matches are resolved by score, not left blank`() {
        // 같은 해에 제목이 겹치는 후보가 여럿이면 단일작 구제는 건너뛰되, 점수제가 최상위를 고른다.
        val a = c(1, "Obsession: Dark", 2026, pop = 9.0)
        val b = c(2, "The Obsession", 2026, pop = 1.0)
        assertEquals(a, TmdbMatch.best("Obsession", 2026, listOf(a, b)))
    }

    @Test
    fun `only different-year namesakes stay unmatched`() {
        // 요청 연도 후보가 없고 다른 해 동명작만 있으면 연도 벌점으로 임계값 미달 → null(빈 타일).
        val x = c(1, "Obsession: Dark", 2019)
        val y = c(2, "The Obsession", 2004)
        assertNull(TmdbMatch.best("Obsession", 2026, listOf(x, y)))
    }
}
