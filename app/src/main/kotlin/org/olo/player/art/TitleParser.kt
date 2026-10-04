package org.olo.player.art

/**
 * Reads a media file name -- and, when the file alone is thin, the folder it sits
 * in -- into a [MediaTitle] a poster lookup can act on. Scene names bury the real
 * title under dots, release tags and quality markers; drama names hide a season
 * and episode in a dozen shapes (S01E02, 1x02, 시즌1 2화, E02). We pull those out
 * so TMDB is queried with a clean title and, for drama, the exact episode.
 *
 * When the read is not confident -- no title left after cleaning -- we return
 * [MediaTitle.Unknown] rather than send TMDB a query that would match the wrong
 * work; a wrong poster is worse than no poster.
 */
object TitleParser {

    fun parse(fileName: String, folderName: String? = null): MediaTitle {
        val stem = stripExtension(fileName)

        val episode = readEpisode(stem)
        if (episode != null) {
            // The series title is whatever sits before the episode marker; a bare
            // "E02.mkv" leaves it empty, so the folder name carries the series.
            val series = cleanTitle(stem.substring(0, episode.markerStart))
                .ifBlank { folderName?.let { cleanTitle(stripYear(it).text) } ?: "" }
            if (series.isBlank()) return MediaTitle.Unknown
            return MediaTitle.Episode(series, episode.season, episode.episode)
        }

        // Not an episode: treat it as a movie. Prefer the year printed in the file;
        // fall back to a year on the folder (a "Movie (2010)" folder of parts).
        val fromFile = stripYear(stem)
        val year = fromFile.year ?: folderName?.let { stripYear(it).year }
        val title = cleanTitle(fromFile.text)
            .ifBlank { folderName?.let { cleanTitle(stripYear(it).text) } ?: "" }
        if (title.isBlank()) return MediaTitle.Unknown
        return MediaTitle.Movie(title, year)
    }

    // --- episode reading -------------------------------------------------------

    private data class EpisodeMark(val season: Int, val episode: Int, val markerStart: Int)

    // The episode markers, tried most-specific first so "S01E02" wins over a bare
    // "E02". Each capture is (season?, episode); the Korean forms and the bare E##
    // default the season to 1, which is what a single-season drama folder means.
    private fun readEpisode(stem: String): EpisodeMark? {
        SEASON_EPISODE.find(stem)?.let { m ->
            return EpisodeMark(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.range.first)
        }
        CROSS_EPISODE.find(stem)?.let { m ->
            return EpisodeMark(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.range.first)
        }
        KOREAN_EPISODE.find(stem)?.let { m ->
            val season = KOREAN_SEASON.find(stem)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val marker = KOREAN_SEASON.find(stem)?.range?.first ?: m.range.first
            return EpisodeMark(season, m.groupValues[1].toInt(), minOf(marker, m.range.first))
        }
        BARE_EPISODE.find(stem)?.let { m ->
            return EpisodeMark(1, m.groupValues[1].toInt(), m.range.first)
        }
        return null
    }

    // --- year reading ----------------------------------------------------------

    private data class Yeared(val text: String, val year: Int?)

    // Cut the title at its year (1900-2099), which in scene names sits right after
    // the real title and before the release tags. A year in parentheses wins; else
    // the first standalone year. A leading four digits that IS the title (a rare
    // "2012" movie) is kept by requiring a separator before the year we cut on.
    private fun stripYear(text: String): Yeared {
        PAREN_YEAR.find(text)?.let { m ->
            return Yeared(text.substring(0, m.range.first), m.groupValues[1].toInt())
        }
        LOOSE_YEAR.find(text)?.let { m ->
            return Yeared(text.substring(0, m.range.first), m.groupValues[1].toInt())
        }
        return Yeared(text, null)
    }

    // --- title cleaning --------------------------------------------------------

    // Turn the raw stem left of the year/marker into a plain title: a leading
    // list-index dropped, bracketed release-group tags dropped, dots and
    // underscores made spaces, trailing quality/codec tags removed, whitespace
    // collapsed. What survives is what a person would call the work.
    private fun cleanTitle(raw: String): String {
        var s = stripLeadingIndex(raw)
        s = BRACKETED.replace(s, " ")
        s = s.replace('.', ' ').replace('_', ' ').replace('-', ' ')
        s = TAGS.replace(s, " ")
        return s.trim().replace(WHITESPACE, " ").trim()
    }

    // 시리즈 폴더가 흔히 쓰는 앞머리 일련번호(01. / 1) / 가. / A. / 01 )를 떼어, 번호가
    // TMDB 검색어에 섞여 매칭을 흐리지 않게 한다. 번호 뒤에 구분자(점·괄호) 또는 공백이
    // 붙은 형태만 지우고, 떼면 제목이 통째로 사라지는 경우(제목이 번호뿐)는 원문을 둔다 --
    // "12 Monkeys"·"300"·"1917"처럼 숫자로 시작하는 진짜 제목을 깨뜨리지 않기 위해,
    // 구분자 없는 비(非)0 패딩 숫자(예: "3 ")는 건드리지 않는다.
    private fun stripLeadingIndex(raw: String): String {
        val stripped = raw
            .let { LEADING_NUM_INDEX.replaceFirst(it, "") } // "01." "12)" "1 ."
            .let { LEADING_ALPHA_INDEX.replaceFirst(it, "") } // "가. " "A) "
            .let { LEADING_PAD_NUM.replaceFirst(it, "") } // 패딩 번호 "01 " "007 "
        return stripped.ifBlank { raw }
    }

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        // A dot in the first character (".hidden") or none at all is not an
        // extension; only cut a real trailing suffix.
        return if (dot > 0 && dot > name.length - 6) name.substring(0, dot) else name
    }

    private val SEASON_EPISODE = Regex("""[Ss](\d{1,2})[\s._-]?[Ee](\d{1,3})""")
    private val CROSS_EPISODE = Regex("""(?<!\d)(\d{1,2})[xX](\d{2,3})(?!\d)""")
    private val KOREAN_EPISODE = Regex("""(\d{1,3})\s*화""")
    private val KOREAN_SEASON = Regex("""시즌\s*(\d{1,2})""")
    private val BARE_EPISODE = Regex("""(?<![A-Za-z0-9])[Ee](\d{1,3})(?!\d)""")
    private val PAREN_YEAR = Regex("""[(\[]((?:19|20)\d{2})[)\]]""")
    private val LOOSE_YEAR = Regex("""(?<![\d(\[])((?:19|20)\d{2})(?![\d)\]])""")
    // 앞머리 일련번호. 숫자형은 구분자(.·))가 있어야 떼므로 "12 Monkeys"는 안전하고,
    // 문자·한글 한 글자형은 구분자 뒤 공백까지 있어야 떼어 "A.I."를 깨지 않는다. 패딩
    // 숫자형은 0으로 시작해 사실상 색인이므로 공백만 있어도 뗀다.
    private val LEADING_NUM_INDEX = Regex("""^\s*\d{1,3}\s*[.)]\s*""")
    private val LEADING_ALPHA_INDEX = Regex("""^\s*(?:[A-Za-z]|[가-힣])[.)]\s+""")
    private val LEADING_PAD_NUM = Regex("""^\s*0\d{1,2}\s+""")
    private val BRACKETED = Regex("""[\[(][^\]\)]*[\])]""")
    private val WHITESPACE = Regex("""\s+""")
    private val TAGS = Regex(
        """(?i)\b(?:""" +
            "2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|h264|h265|av1|xvid|divx|" +
            "bluray|blu-ray|brrip|bdrip|webrip|web-dl|webdl|web|hdrip|dvdrip|dvd|hdtv|" +
            "remux|repack|proper|extended|unrated|imax|aac|ac3|dts|dd5|ddp5|flac|" +
            "10bit|8bit|multi|dual" +
            """)\b.*$""",
    )
}
