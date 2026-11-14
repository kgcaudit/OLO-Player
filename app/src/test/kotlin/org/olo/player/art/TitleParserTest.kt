package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The read from file name to [MediaTitle]. Pinned here because every wrong read
 * sends TMDB a query that fetches the wrong poster: a movie mistaken for an
 * episode, a release tag left in the title, a season lost.
 */
class TitleParserTest {

    @Test
    fun `scene movie -- dots, year, release tags`() {
        assertEquals(
            MediaTitle.Movie("Inception", 2010),
            TitleParser.parse("Inception.2010.1080p.BluRay.x264.mkv"),
        )
    }

    @Test
    fun `movie with year in parentheses`() {
        assertEquals(
            MediaTitle.Movie("The Matrix", 1999),
            TitleParser.parse("The Matrix (1999).mkv"),
        )
    }

    @Test
    fun `korean movie title survives`() {
        assertEquals(
            MediaTitle.Movie("기생충", 2019),
            TitleParser.parse("기생충.2019.mkv"),
        )
    }

    @Test
    fun `movie with no year keeps the whole cleaned title`() {
        assertEquals(
            MediaTitle.Movie("Inception", null),
            TitleParser.parse("Inception.1080p.x264.mkv"),
        )
    }

    @Test
    fun `SxxExx episode`() {
        assertEquals(
            MediaTitle.Episode("The Wire", 1, 1),
            TitleParser.parse("The.Wire.S01E01.720p.mkv"),
        )
    }

    @Test
    fun `cross form episode 1x02`() {
        assertEquals(
            MediaTitle.Episode("Breaking Bad", 1, 2),
            TitleParser.parse("Breaking Bad - 1x02 - Cat's in the Bag.mkv"),
        )
    }

    @Test
    fun `resolution is not mistaken for an episode`() {
        // 1920x1080 must not read as season 19 / 20 episode 108.
        assertEquals(
            MediaTitle.Movie("Dune", 2021),
            TitleParser.parse("Dune.2021.1920x1080.mkv"),
        )
    }

    @Test
    fun `korean episode counter -- N화 defaults to season 1`() {
        assertEquals(
            MediaTitle.Episode("오징어게임", 1, 3),
            TitleParser.parse("오징어게임 3화.mkv"),
        )
    }

    @Test
    fun `korean season and episode`() {
        assertEquals(
            MediaTitle.Episode("종이의집", 2, 5),
            TitleParser.parse("종이의집 시즌2 5화.mkv"),
        )
    }

    @Test
    fun `bare E-number takes the series from the folder`() {
        assertEquals(
            MediaTitle.Episode("Dark", 1, 5),
            TitleParser.parse("E05.mkv", folderName = "Dark (2017)"),
        )
    }

    @Test
    fun `folder year fills in when the file has none`() {
        assertEquals(
            MediaTitle.Movie("Interstellar", 2014),
            TitleParser.parse("Interstellar.mkv", folderName = "Interstellar (2014)"),
        )
    }

    @Test
    fun `an unreadable name is Unknown, not a bad guess`() {
        assertEquals(MediaTitle.Unknown, TitleParser.parse("E07.mkv"))
    }

    @Test
    fun `leading list index -- numeric dot -- is dropped from the title`() {
        // 시리즈 폴더의 "01. 극장판 …" 같은 앞머리 번호는 TMDB 검색어에서 빠져야 한다.
        assertEquals(
            MediaTitle.Movie("극장판 짱구는 못말려 액션가면 vs 그래그래 마왕", null),
            TitleParser.parse("01. 극장판 짱구는 못말려 액션가면 vs 그래그래 마왕"),
        )
    }

    @Test
    fun `leading index variants -- paren, hangul, letter, zero-padded space`() {
        assertEquals(MediaTitle.Movie("기생충", 2019), TitleParser.parse("1) 기생충 (2019)"))
        assertEquals(MediaTitle.Movie("기생충", 2019), TitleParser.parse("가. 기생충 (2019)"))
        assertEquals(MediaTitle.Movie("기생충", 2019), TitleParser.parse("A. 기생충 (2019)"))
        assertEquals(MediaTitle.Movie("기생충", 2019), TitleParser.parse("01 기생충 (2019)"))
    }

    @Test
    fun `a number that is the title is not mistaken for an index`() {
        // 구분자 없는 비패딩 숫자로 시작하는 진짜 제목은 보존한다.
        assertEquals(MediaTitle.Movie("12 Monkeys", 1995), TitleParser.parse("12 Monkeys (1995).mkv"))
        assertEquals(MediaTitle.Movie("300", 2006), TitleParser.parse("300 (2006).mkv"))
    }

    @Test
    fun `SxxExx wins over a bare E-number elsewhere in the name`() {
        assertEquals(
            MediaTitle.Episode("Show", 2, 4),
            TitleParser.parse("Show.S02E04.E-something.mkv"),
        )
    }
}
