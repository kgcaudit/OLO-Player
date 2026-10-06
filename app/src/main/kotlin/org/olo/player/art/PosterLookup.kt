package org.olo.player.art

/**
 * 포스터 자동 해석의 결과 — URL 하나(String?)로는 "왜 못 찾았는지"를 구분할 수 없어, 빈 타일을
 * 두 가지로 보여 주려(후보 있음 vs 자료 없음) 사유까지 담는다.
 *
 *  - [Hit]       : 확신 매칭 성공 → 그 포스터 URL.
 *  - [Ambiguous] : TMDB 검색 결과는 있으나 확신 매칭 실패(동명작이 여럿). 수동 '포스터 변경'으로
 *                  고르면 찾을 가능성이 높으므로, 타일에서 선택을 유도한다.
 *  - [NoData]    : 모든 검색어가 0건 — TMDB에 자료가 없다. 지정해도 안 나올 수 있어 중립 표기.
 *  - [None]      : 포스터 끄기·키 없음·이름 불명 등 — 구분 표기 대상이 아님(종전처럼 kind 타일).
 */
sealed interface PosterLookup {
    data class Hit(val url: String) : PosterLookup
    data object Ambiguous : PosterLookup
    data object NoData : PosterLookup
    data object None : PosterLookup
}
