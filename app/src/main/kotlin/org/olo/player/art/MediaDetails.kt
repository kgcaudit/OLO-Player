package org.olo.player.art

/**
 * 상세정보 화면이 보여 줄 "작품" 정보. 포스터 하나([TmdbResult])를 넘어, 사람이 상세에서
 * 기대하는 것들을 TMDB에서 한 번에 받아 담는다 -- 줄거리·평점·러닝타임·장르·관람등급·
 * 원제·배경·출연/감독. 탐색기에서도 보는 파일 사실(이름·용량·경로)과 달리, 이건 "무엇을
 * 보는가"를 설명하는 작품 메타다.
 *
 * 모든 필드는 없을 수 있다(키 없음·미매칭·해당 없음). 비면 화면이 그 조각을 통째로 접어
 * 빈 줄을 남기지 않는다.
 */
data class MediaDetails(
    /** 현지(한국어) 제목. 비면 원제를 쓴다. */
    val title: String,
    /** 원제 -- 현지 제목과 다를 때만 부제로 보여 준다. */
    val originalTitle: String?,
    val year: Int?,
    /** TMDB 평점(10점 만점). 0이면 평점 없음(null). */
    val rating: Double?,
    /** 러닝타임(분). 영화는 상영시간, 시리즈는 1화 평균. */
    val runtimeMinutes: Int?,
    val genres: List<String>,
    /** 관람등급(한국 우선, 없으면 미국). "15세" 같은 표기 그대로. */
    val certification: String?,
    val overview: String,
    val posterUrl: String?,
    /** 16:9 배경(백드롭). 아이덴티티 헤더의 바탕. */
    val backdropUrl: String?,
    val director: String?,
    val cast: List<CastMember>,
    val tv: Boolean,
)

/** 출연진 한 명: 이름과 (있으면) 얼굴 사진 URL. */
data class CastMember(val name: String, val profileUrl: String?)
