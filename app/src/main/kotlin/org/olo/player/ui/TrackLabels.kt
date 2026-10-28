package org.olo.player.ui

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/**
 * 자막·음성 트랙을 사람이 알아볼 이름으로 바꾸는 순수 도우미. 트랙 목록에서 "DA·FI·FR"처럼
 * 코드만 뜨거나 같은 언어 자막 둘(일반/SDH)이 구분 안 되던 문제를 여기서 해결한다. media3
 * 타입을 받긴 하지만 로직은 단순해 유닛테스트로 고정한다.
 */

// 언어를 특정할 수 없는 코드들(표시하지 않고 호출부가 "알 수 없음"으로). und=미정, mis=기타,
// mul=다중, zxx=언어 없음.
private val UNDETERMINED_LANGS = setOf("und", "mis", "mul", "zxx")

/**
 * 트랙 언어 코드를 우리말 이름으로(da→덴마크어, fi→핀란드어, fr→프랑스어, en→영어,
 * ja→일본어). 2·3글자 ISO 코드와 지역표기(pt-BR 등)를 [Locale]로 풀어 거의 모든 언어를
 * 한국어로 보여 준다. 끝내 못 풀면 대문자 코드라도 돌려주고(식별 가능), 미정 코드는 null.
 */
internal fun trackLanguageName(language: String?): String? {
    val raw = language?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val key = raw.lowercase(Locale.ROOT)
    if (key.substringBefore('-') in UNDETERMINED_LANGS) return null
    val name = runCatching {
        Locale.forLanguageTag(key.replace('_', '-')).getDisplayLanguage(Locale.KOREAN)
    }.getOrNull()
    // Locale이 못 풀면 입력 코드를 그대로 돌려주므로, 코드와 같으면 대문자 코드로 식별만 남긴다.
    return name?.takeIf { it.isNotBlank() && !it.equals(key, ignoreCase = true) }
        ?: raw.uppercase(Locale.ROOT)
}

/**
 * 같은 언어 자막이 여러 개일 때 구분이 안 되던 걸 표시로 가른다: 청각장애인용(SDH)·강제
 * (영상 내 외국어 등 꼭 필요한 부분만). media3 역할/선택 플래그를 먼저 보고, 없으면 트랙
 * 이름(label)에 박힌 표기로 보강한다(MKV는 종종 이름에만 "SDH"를 적는다). 일반 자막은 null.
 */
@UnstableApi
internal fun subtitleKind(format: Format): String? {
    val label = format.label?.lowercase(Locale.ROOT).orEmpty()
    val sdh = format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0 ||
        "sdh" in label || "hearing" in label || "청각" in label
    val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 ||
        "forced" in label || "강제" in label
    return when {
        sdh -> "SDH"
        forced -> "강제"
        else -> null
    }
}
