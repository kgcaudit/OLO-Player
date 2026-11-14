package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 태그 편집 순수 로직: 일괄 적용(Keep/Set)·공통값·변경 계획·번호매기기, 그리고 파일명↔태그
 * 변환(플레이스홀더). 한국어 값도 그대로 다뤄지는지 확인.
 */
class TagEditTest {

    private fun tags(vararg p: Pair<TagField, String>) = TrackTags(mapOf(*p))

    @Test
    fun `set overwrites, keep leaves, empty set clears`() {
        val cur = tags(TagField.TITLE to "옛 제목", TagField.ALBUM to "옛 앨범", TagField.YEAR to "2020")
        val edits = mapOf(
            TagField.ALBUM to FieldEdit.Set("새 앨범"),
            TagField.TITLE to FieldEdit.Keep,
            TagField.YEAR to FieldEdit.Set(""), // 지움
        )
        val out = TagEdit.apply(cur, edits)
        assertEquals("옛 제목", out[TagField.TITLE])   // Keep
        assertEquals("새 앨범", out[TagField.ALBUM])   // Set 덮어쓰기
        assertNull("빈 Set은 삭제", out[TagField.YEAR])
    }

    @Test
    fun `common value is the shared value or null when differing`() {
        val list = listOf(
            tags(TagField.ARTIST to "아이유", TagField.ALBUM to "A"),
            tags(TagField.ARTIST to "아이유", TagField.ALBUM to "B"),
        )
        assertEquals("아이유", TagEdit.commonValue(list, TagField.ARTIST)) // 모두 같음
        assertNull("서로 달라 〈유지〉", TagEdit.commonValue(list, TagField.ALBUM))
    }

    @Test
    fun `plan reports before and after per track`() {
        val list = listOf(
            tags(TagField.ALBUM to "Blase Boys"),
            tags(), // 앨범 없음
        )
        val edits = mapOf(TagField.ALBUM to FieldEdit.Set("Blasé Boys Club"))
        val plan = TagEdit.plan(list, edits)
        assertEquals("Blase Boys", plan[0][0].before)
        assertEquals("Blasé Boys Club", plan[0][0].after)
        assertTrue(plan[0][0].changed)
        assertNull(plan[1][0].before)
        assertEquals("Blasé Boys Club", plan[1][0].after)
    }

    @Test
    fun `track numbering counts from start`() {
        assertEquals(listOf("1", "2", "3"), TagEdit.trackNumbers(3))
        assertEquals(listOf("5", "6"), TagEdit.trackNumbers(2, start = 5))
    }

    @Test
    fun `filename to tag parses placeholder pattern`() {
        val out = TagFilename.parse("03 Duke Dumont - Ocean Drive.mp3", "%track% %artist% - %title%")
        assertEquals("03", out?.get(TagField.TRACK))
        assertEquals("Duke Dumont", out?.get(TagField.ARTIST))
        assertEquals("Ocean Drive", out?.get(TagField.TITLE))
    }

    @Test
    fun `filename to tag handles korean and returns null on mismatch`() {
        val out = TagFilename.parse("아이유 - 좋은 날.flac", "%artist% - %title%")
        assertEquals("아이유", out?.get(TagField.ARTIST))
        assertEquals("좋은 날", out?.get(TagField.TITLE))
        // 구분자가 없어 패턴과 안 맞으면 null.
        assertNull(TagFilename.parse("NoSeparatorHere.mp3", "%artist% - %title%"))
    }

    @Test
    fun `tag to filename fills pattern and sanitizes`() {
        val t = tags(TagField.TRACK to "3", TagField.ARTIST to "아이유", TagField.TITLE to "좋은/날")
        // 슬래시는 안전 문자로, 확장자는 붙는다.
        assertEquals("03 아이유 - 좋은_날.flac", formatPadded(t))
    }

    // 트랙 2자리 패딩은 UI가 패턴을 만들 때 넣는 몫이라, 여기선 기본 포맷만 확인한다.
    private fun formatPadded(t: TrackTags): String =
        TagFilename.format(t.copy(values = t.values + (TagField.TRACK to t[TagField.TRACK]!!.padStart(2, '0'))), "%track% %artist% - %title%", "flac")

    @Test
    fun `unknown token is ignored on parse and blanked on format`() {
        // %dummy%는 알 수 없는 토큰 -> 파싱 땐 자리만 흘리고, 포맷 땐 빈칸.
        val out = TagFilename.parse("X - Hello.mp3", "%dummy% - %title%")
        assertEquals("Hello", out?.get(TagField.TITLE))
        assertNull(out?.get(TagField.ARTIST))
        assertEquals("Hello.mp3", TagFilename.format(tags(TagField.TITLE to "Hello"), "%dummy%%title%", "mp3"))
    }
}
