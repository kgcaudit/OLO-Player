package org.olo.player.art

/**
 * 기본 파일 작업(복사·이동·이름 변경·삭제)의 '순수 규칙'. 실제 쓰기·권한·MediaStore 동의는
 * UI 호스트(FileOpsHost)가 안드로이드 API로 처리하고, 여기선 기기가 필요 없는 이름 규칙만 둔다
 * -- 유닛 테스트로 고정하기 위함. (외부 조사: 스코프 저장소에선 공유 파일 수정·삭제에 사용자
 * 동의가 필요하고, 복사/이동도 같은 제약을 받는다 -- 그래서 '기본 기능'으로 좁혀 담는다.)
 */
object LocalFileOps {

    /**
     * 같은 폴더에 둘 사본 이름. "곡.flac" → "곡 (사본).flac", 이미 있으면 "곡 (사본 2).flac"…
     * 확장자는 그대로 두고 충돌만 피한다([exists]로 폴더 안 존재를 물어본다).
     */
    fun copyName(name: String, exists: (String) -> Boolean): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else "" // 점 포함(".flac")
        val first = "$base (사본)$ext"
        if (!exists(first)) return first
        var n = 2
        while (exists("$base (사본 $n)$ext")) n++
        return "$base (사본 $n)$ext"
    }

    /**
     * 이름 변경 결과 이름 정규화: 사용자가 확장자를 빼먹으면 원래 확장자를 붙여 준다(실수로 포맷을
     * 잃지 않도록). 이미 같은 확장자를 쓰면 그대로.
     */
    fun ensureExtension(newBase: String, original: String): String {
        val trimmed = newBase.trim()
        val origExt = original.substringAfterLast('.', "")
        if (origExt.isEmpty()) return trimmed
        return if (trimmed.endsWith(".$origExt", ignoreCase = true)) trimmed else "$trimmed.$origExt"
    }

    /** 이름 유효성: 빈값·경로구분자·상위참조 금지. */
    fun isValidName(name: String): Boolean {
        val t = name.trim()
        return t.isNotEmpty() && !t.contains('/') && !t.contains('\\') && t != "." && t != ".."
    }
}
