package org.olo.player.art

/**
 * 태그 편집의 '순수부' -- 편집 모델과 체계적 절차(일괄 적용·파일명↔태그 변환)를 네트워크·파일 없이
 * 담는다. 실제 파일 읽기/쓰기(jaudiotagger)와 UI는 이 위에 얹는다.
 *
 * Mp3tag의 두 축을 옮겼다: ① 여러 파일을 고르면 서로 다른 값은 '유지'(Keep), 입력한 필드만
 * 전체에 적용(Set) ② 파일명↔태그를 플레이스홀더 패턴(%artist% - %title%)으로 변환. 모두 순수
 * 함수라 유닛 테스트로 고정한다.
 */

/** 편집 대상 태그 필드. [token]은 파일명 패턴의 플레이스홀더(%token%), [label]은 UI 표기. */
enum class TagField(val token: String, val label: String) {
    TITLE("title", "제목"),
    ARTIST("artist", "아티스트"),
    ALBUM("album", "앨범"),
    ALBUM_ARTIST("albumartist", "앨범 아티스트"),
    TRACK("track", "트랙"),
    DISC("disc", "디스크"),
    YEAR("year", "연도"),
    GENRE("genre", "장르"),
    COMPOSER("composer", "작곡가"),
    COMMENT("comment", "코멘트"),
    ;

    companion object {
        private val BY_TOKEN = entries.associateBy { it.token }
        fun byToken(token: String): TagField? = BY_TOKEN[token.lowercase()]
    }
}

/** 한 곡의 현재 태그(있는 필드만 담는다; 빈 문자열은 '있지만 빔'). */
data class TrackTags(val values: Map<TagField, String> = emptyMap()) {
    operator fun get(field: TagField): String? = values[field]
}

/** 한 필드에 대한 편집 의도: 그대로 두거나(Keep), 값으로 덮어쓴다(Set; 빈 문자열이면 지움). */
sealed interface FieldEdit {
    data object Keep : FieldEdit
    data class Set(val value: String) : FieldEdit
}

/** 저장 전 대조용: 한 곡·한 필드의 변경 전/후와 바뀜 여부. */
data class FieldChange(val field: TagField, val before: String?, val after: String?) {
    val changed: Boolean get() = (before ?: "") != (after ?: "")
}

object TagEdit {

    /**
     * 한 곡에 편집을 적용한 결과 태그. [edits]에 없는 필드는 Keep으로 보고 그대로 둔다. Set은
     * 값으로 덮어쓰되, 빈 문자열이면 그 필드를 지운다(Mp3tag에서 빈 값=삭제와 같은 규칙).
     */
    fun apply(current: TrackTags, edits: Map<TagField, FieldEdit>): TrackTags {
        if (edits.isEmpty()) return current
        val out = current.values.toMutableMap()
        for ((field, edit) in edits) {
            when (edit) {
                is FieldEdit.Keep -> Unit
                is FieldEdit.Set -> if (edit.value.isEmpty()) out.remove(field) else out[field] = edit.value
            }
        }
        return TrackTags(out)
    }

    /**
     * 일괄 편집 UI의 필드 초기값: 선택한 곡들의 그 필드 값이 모두 같으면 그 값, 서로 다르거나
     * 일부만 있으면 null('〈유지〉'로 표기). 빈 선택은 null.
     */
    fun commonValue(tracks: List<TrackTags>, field: TagField): String? {
        if (tracks.isEmpty()) return null
        val first = tracks.first()[field]
        return if (tracks.all { it[field] == first }) first else null
    }

    /** 곡별 변경 계획(변경 전/후). 저장 전 대조 표와 '바뀐 곡만 쓰기'에 쓴다. */
    fun plan(tracks: List<TrackTags>, edits: Map<TagField, FieldEdit>): List<List<FieldChange>> =
        tracks.map { current ->
            val after = apply(current, edits)
            edits.keys.map { field -> FieldChange(field, current[field], after[field]) }
        }

    /** 트랙 번호 자동 매기기: [start]부터 1씩, [count]개. UI의 '번호매기기' 도구가 쓴다. */
    fun trackNumbers(count: Int, start: Int = 1): List<String> =
        (0 until count).map { (start + it).toString() }
}

/**
 * 파일명 ↔ 태그 변환(Mp3tag Convert). 플레이스홀더 %token%로 파일명을 파싱해 태그를 뽑거나,
 * 태그로 파일명을 만든다. 알 수 없는 토큰은 '무시 자리'로 흘려 보낸다(파싱 시 비캡처).
 */
object TagFilename {

    private val PLACEHOLDER = Regex("""%([a-zA-Z_]+)%""")

    /**
     * [fileName](확장자 포함 가능)을 [pattern]으로 파싱해 필드값을 뽑는다. 패턴이 파일명과
     * 맞지 않으면 null. 캡처는 기본 비탐욕, 마지막 캡처만 탐욕으로 나머지를 담는다.
     */
    fun parse(fileName: String, pattern: String): Map<TagField, String>? {
        val stem = fileName.substringBeforeLast('.', fileName)
        val tokens = PLACEHOLDER.findAll(pattern).map { it.groupValues[1] }.toList()
        if (tokens.isEmpty()) return null
        // 패턴을 [리터럴][토큰][리터럴]… 순으로 정규식으로 바꾼다. 토큰은 캡처 그룹, 사이 글자는
        // 리터럴로 이스케이프. 마지막 토큰만 탐욕(.+)으로 꼬리까지 담는다.
        val sb = StringBuilder("^")
        var last = 0
        val fields = ArrayList<TagField?>()
        var seen = 0
        for (m in PLACEHOLDER.findAll(pattern)) {
            sb.append(Regex.escape(pattern.substring(last, m.range.first)))
            seen++
            val greedy = seen == tokens.size
            sb.append(if (greedy) "(.+)" else "(.+?)")
            fields.add(TagField.byToken(m.groupValues[1]))
            last = m.range.last + 1
        }
        sb.append(Regex.escape(pattern.substring(last))).append("$")
        val match = runCatching { Regex(sb.toString()).matchEntire(stem) }.getOrNull() ?: return null
        val out = LinkedHashMap<TagField, String>()
        fields.forEachIndexed { i, field ->
            if (field != null) {
                val v = match.groupValues[i + 1].trim()
                if (v.isNotEmpty()) out[field] = v
            }
        }
        return out.ifEmpty { null }
    }

    /**
     * [tags]로 [pattern]을 채워 파일명을 만든다(확장자 [ext]는 "mp3"처럼 점 없이). 값이 없는
     * 토큰·알 수 없는 토큰은 빈 문자열로, 파일명에 못 쓰는 문자는 안전 문자로 바꾸고 공백을 정리.
     */
    fun format(tags: TrackTags, pattern: String, ext: String): String {
        val body = PLACEHOLDER.replace(pattern) { m ->
            TagField.byToken(m.groupValues[1])?.let { tags[it] } ?: ""
        }
        val safe = body.replace(ILLEGAL, "_").replace(Regex("""\s+"""), " ").trim().trim('.')
        val name = safe.ifBlank { "untitled" }
        return if (ext.isBlank()) name else "$name.$ext"
    }

    // 파일명에 쓸 수 없는 문자(윈도/안드로이드 공통 안전선).
    private val ILLEGAL = Regex("""[/\\:*?"<>|]""")
}
