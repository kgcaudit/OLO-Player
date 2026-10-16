package org.olo.player.subtitle

/** One timed caption: when it appears, when it clears, and the text to show. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Reads an external subtitle's text into timed cues, so the player can draw them
 * itself with a delay applied -- something media3's own renderer cannot do. Handles
 * the two formats these files almost always are: SubRip (.srt) and WebVTT (.vtt,
 * which is also what a .smi is converted to). A block is a timing line
 * (HH:MM:SS,mmm --> HH:MM:SS,mmm) and the lines under it; an index number, the
 * WEBVTT header and NOTE blocks, and inline tags are dropped.
 *
 * Pure so it is tested without a device: a wrong time or a swallowed line shows the
 * wrong caption, or none.
 */
object SubtitleCues {

    fun parse(content: String): List<SubtitleCue> {
        val out = ArrayList<SubtitleCue>()
        // Blocks are separated by a blank line; \r\n and \r are normalised first.
        val blocks = content.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n[ \t]*\n"))
        for (block in blocks) {
            val lines = block.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) continue // WEBVTT header, NOTE, or a stray block
            val (start, end) = parseTiming(lines[timingIndex]) ?: continue
            val text = lines.drop(timingIndex + 1).joinToString("\n") { stripTags(it) }.trim()
            if (text.isNotEmpty()) out += SubtitleCue(start, end, text)
        }
        out.sortBy { it.startMs }
        return out
    }

    /**
     * The caption to show at [atMs] (the play position already shifted by the
     * delay), or null when none is active. Overlapping cues are joined so a line
     * pinned top and one pinned bottom both show.
     */
    fun activeText(cues: List<SubtitleCue>, atMs: Long): String? {
        val active = cues.filter { atMs >= it.startMs && atMs < it.endMs }
        return if (active.isEmpty()) null else active.joinToString("\n") { it.text }
    }

    // "HH:MM:SS,mmm --> HH:MM:SS,mmm" or with '.' (VTT); hours optional. Only the
    // two endpoints are read; VTT cue settings after the second stamp are ignored.
    private fun parseTiming(line: String): Pair<Long, Long>? {
        val matches = STAMP.findAll(line).toList()
        if (matches.size < 2) return null
        val start = toMs(matches[0]) ?: return null
        val end = toMs(matches[1]) ?: return null
        return start to end
    }

    private fun toMs(m: MatchResult): Long? {
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLongOrNull() ?: return null
        val s = m.groupValues[3].toLongOrNull() ?: return null
        // Milliseconds may be 1-3 digits; pad so ".5" is 500ms, not 5ms.
        val ms = m.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((h * 60 + min) * 60 + s) * 1000 + ms
    }

    // 인라인 태그를 지우되 기본 서식 태그(<i>/<b>/<u>)만 남긴다 -- 외부 자막을 앱이 직접
    // 그릴 때 기울임·굵게·밑줄을 살리기 위함. 위치·글꼴·색 등 나머지 태그는 그대로 제거한다.
    private fun stripTags(text: String): String =
        TAG.replace(text) { m -> if (STYLE_TAG.matches(m.value)) m.value.lowercase() else "" }

    private val STAMP = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
    private val TAG = Regex("""<[^>]*>""")
    private val STYLE_TAG = Regex("""</?[ibu]>""", RegexOption.IGNORE_CASE)
}
