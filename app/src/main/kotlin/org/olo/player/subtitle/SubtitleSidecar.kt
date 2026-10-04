package org.olo.player.subtitle

import androidx.media3.common.MimeTypes

/**
 * 영상 옆에 파일로 놓인 자막(사이드카)을 알아보는 공통 지식: 어떤 확장자가 자막인지, 그
 * media3 MIME는 무엇인지, 어떤 자막 파일명이 이 영상의 것인지, 파일명 꼬리에서 읽는 대략의
 * 언어. 로컬 폴더 스캔과 네트워크(같은 폴더) 자막 붙이기가 같은 규칙을 쓰도록 한곳에 둔다.
 */
object SubtitleSidecar {

    /** media3가 스스로 읽는 자막 포맷 -> MIME. SAMI(.smi/.sami)는 못 읽어 VTT로 변환해 쓴다. */
    val MIME = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT,
        "webvtt" to MimeTypes.TEXT_VTT,
        "ass" to MimeTypes.TEXT_SSA,
        "ssa" to MimeTypes.TEXT_SSA,
        "ttml" to MimeTypes.APPLICATION_TTML,
        "dfxp" to MimeTypes.APPLICATION_TTML,
    )

    /** 변환이 필요한 SAMI 확장자. */
    val SAMI = setOf("smi", "sami")

    /** 자막으로 볼 확장자 전체(소문자). */
    val EXTENSIONS = MIME.keys + SAMI

    /**
     * 자막 이름이 영상의 것이라 볼 만큼 가까운가. 둘을 글자·숫자만 남겨, 한쪽이 다른 쪽의
     * 앞부분이거나(제목, 제목+언어 꼬리) 앞 열 글자 이상이 겹치면(제목+연도까지 같고 릴리스
     * 태그만 갈리는 경우) 같은 작품으로 본다. 같은 폴더의 다른 작품은 금세 갈라져 걸러진다.
     */
    fun nameMatches(videoBase: String, subtitleStem: String): Boolean {
        fun letters(text: String) = text.lowercase().filter { it.isLetterOrDigit() }
        val a = letters(videoBase)
        val b = letters(subtitleStem)
        if (a.length < 4 || b.length < 4) return a == b
        val common = a.commonPrefixWith(b).length
        return common >= minOf(a.length, b.length) || common >= 10
    }

    /** 파일명 꼬리 태그에서 읽는 대략의 언어(선택창 라벨용). */
    fun languageOf(tag: String): String? = when {
        tag.isEmpty() -> null
        tag.startsWith("ko") || tag.startsWith("kr") || tag.contains("kor") || tag.contains("한") -> "ko"
        tag.startsWith("en") || tag.contains("eng") -> "en"
        tag.startsWith("ja") || tag.startsWith("jp") || tag.contains("jpn") -> "ja"
        tag.startsWith("zh") || tag.contains("chi") || tag.contains("chs") || tag.contains("cht") -> "zh"
        else -> tag.take(8)
    }
}
