package org.olo.player.art

/**
 * One record from a TMDB search response, reduced to what choosing a match needs.
 * The client fills these from the JSON; the ranking below is pure so it can be
 * tested without a network.
 */
data class TmdbCandidate(
    val id: Int,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val popularity: Double,
)

/**
 * 포스터 변경 다이얼로그가 보여줄 한 검색 후보: 완성된 포스터 URL과 사람이 고를 때
 * 보는 제목·연도·개요. (랭킹용 [TmdbCandidate]와 달리 바로 화면에 뿌릴 표시값이다.)
 */
data class TmdbResult(
    // TMDB 작품 id -- 사용자가 이 후보를 고르면 override에 같이 눌러 둬, 상세정보가 제목
    // 재매칭이 아니라 이 id로 메타데이터를 바로 받아 포스터와 정보가 어긋나지 않게 한다.
    val id: Int,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val overview: String,
    val tv: Boolean,
)

/**
 * Picks the one search result that best fits the name we parsed. TMDB happily
 * returns a dozen near-namesakes (remakes, sequels, foreign cuts), so we score on
 * what a person would check: does the title actually match, does the year agree,
 * and only then how well-known it is. A weak best is dropped to null -- a kind
 * tile beats a confidently wrong poster.
 */
object TmdbMatch {

    fun best(query: String, year: Int?, candidates: List<TmdbCandidate>): TmdbCandidate? {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return null

        // (연도 스코프 단일작 우선 · 사용자 요청) 요청 연도와 '정확히 같은 연도'의 후보가 포스터와
        // 함께 딱 하나면 그걸 쓴다 -- 표기 차이로 점수가 낮거나 다른 해의 더 유명한 동명작이
        // 있어도. TMDB 검색은 제목 기반이라 그 해 단일 결과는 사실상 그 작품이다(예: "The
        // Uprising" 2026 한 편 ↔ 타 연도 동명작 다수). 같은 해에 둘 이상이면(예: 동일 연도
        // 'Obsession' 다수) 확신이 없으니 아래 점수제로 넘긴다. 반드시 점수제보다 먼저 둔다 --
        // 같은 해 후보는 연도 가점(+40)만으로 임계값을 넘어, 점수제에선 정확 제목의 '다른 해'
        // 동명작(예: 2019)에 밀려 엉뚱한 포스터가 붙을 수 있기 때문이다.
        if (year != null) {
            candidates.filter { it.year == year && it.posterPath != null }
                .singleOrNull()?.let { return it }
        }

        val scored = candidates
            .map { it to score(wanted, year, it) }
            .filter { it.second >= MIN_SCORE }
        // Highest score wins; popularity only breaks a tie so a blockbuster
        // namesake cannot outrank the title the person actually typed.
        scored.maxWithOrNull(compareBy({ it.second }, { it.first.popularity }))?.let { return it.first }

        // 임계값을 넘는 후보가 없어도, TMDB가 이 질의에 "단 하나"만 돌려줬고 그 후보에
        // 포스터가 있으면 그 하나를 쓴다. 특정적인 긴 제목이 한 건만 나오는 건 사실상 그
        // 작품이라(사용자 요청: "검색 1건이면 붙여라"), 표기 차이로 점수가 깎인 경우를
        // 구제한다 -- 예: 폴더 "스파이 X 패밀리 - 코드 화이트" ↔ TMDB "스파이 패밀리 코드
        // : 화이트". 다만 연도를 양쪽 다 아는데 3년 넘게 벌어지면 다른 작품이므로 버린다.
        val lone = candidates.singleOrNull() ?: return null
        if (lone.posterPath == null) return null
        val yearFarOff = year != null && lone.year != null && kotlin.math.abs(year - lone.year) > 3
        return if (yearFarOff) null else lone
    }

    // 포함 매칭의 최대 점수(두 제목이 거의 같은 길이일 때). 길이 비율로 가중해, 긴 제목 속에
    // 짧은 질의가 우연히 들어간 경우(예: "onslaught" ⊂ "Pickleball Pals III: The Onslaught Of
    // Justice")는 이보다 훨씬 낮게 준다.
    private const val CONTAIN_MAX = 55

    private fun score(wanted: String, year: Int?, candidate: TmdbCandidate): Int {
        val got = normalize(candidate.title)
        var score = when {
            got == wanted -> 100
            got.isNotEmpty() && (got.contains(wanted) || wanted.contains(got)) -> {
                // 포함은 "짧은 쪽이 긴 쪽에서 차지하는 비중"으로 가중한다. 비중이 클수록(두 제목이
                // 비슷한 길이) 55에 가깝고, 긴 제목에 짧은 단어가 박힌 경우는 작게 준다 -- 긴
                // 동명 제목이 정작 그 해 단일작보다 높은 점수로 이겨 엉뚱한 포스터가 붙던 걸 막는다.
                val shorter = minOf(wanted.length, got.length)
                val longer = maxOf(wanted.length, got.length).coerceAtLeast(1)
                Math.round(CONTAIN_MAX * (shorter.toFloat() / longer))
            }
            else -> 0
        }
        if (year != null && candidate.year != null) {
            val gap = kotlin.math.abs(year - candidate.year)
            score += when {
                gap == 0 -> 40
                // 나라마다 개봉연도가 달라 1~3년쯤 차이 나는 건 같은 작품으로 본다(사용자 요청).
                // 제목이 정확히 같으면 이 정도 차이는 매칭을 유지하고, 제목이 느슨히만 겹치면
                // (예: "Batman" ⊂ "Batman Begins") 여전히 임계값 아래로 떨어뜨린다.
                gap <= 3 -> 10
                else -> -30 // a different year is a different work
            }
        }
        // A result with art is the whole point of the lookup; nudge it ahead of a
        // posterless namesake when titles tie.
        if (candidate.posterPath != null) score += 5
        return score
    }

    // Fold titles to their comparable core: lowercase, accents and punctuation
    // dropped, spaces removed, so "Spider-Man" == "spiderman" == "Spider Man".
    //
    // 폴더명엔 못 쓰는 특수문자(콜론 등)는 여기서 이미 다 지워지므로 "귀멸의 칼날: 무한성편"과
    // "귀멸의 칼날 무한성편"이 같아진다. 추가로 극장판 표식("극장판"/"劇場版"/"the movie")은
    // 같은 작품을 다르게 보이게 하므로 제거한다 -- 이러면 "극장판 귀멸의 칼날: 무한성편"이
    // 폴더 "귀멸의 칼날 무한성편"과 정확히 일치한다(사용자 요청).
    private fun normalize(text: String): String =
        text.lowercase()
            .replace(NON_ALNUM, "")
            .let { MOVIE_MARKERS.fold(it) { acc, m -> acc.replace(m, "") } }

    private val NON_ALNUM = Regex("""[^\p{L}\p{Nd}]""")

    // 극장판/극장 개봉 표식(비알파벳 제거 뒤의 형태). "movie"는 실제 제목에 흔해 제외한다.
    private val MOVIE_MARKERS = listOf("극장판", "劇場版", "themovie", "theatrical")

    // Below this, the best result is not a real match (no title overlap and no
    // year agreement); return nothing rather than a guess.
    private const val MIN_SCORE = 40
}
