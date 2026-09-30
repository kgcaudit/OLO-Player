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
        val scored = candidates
            .map { it to score(wanted, year, it) }
            .filter { it.second >= MIN_SCORE }
        // Highest score wins; popularity only breaks a tie so a blockbuster
        // namesake cannot outrank the title the person actually typed.
        return scored.maxWithOrNull(
            compareBy({ it.second }, { it.first.popularity }),
        )?.first
    }

    private fun score(wanted: String, year: Int?, candidate: TmdbCandidate): Int {
        val got = normalize(candidate.title)
        var score = when {
            got == wanted -> 100
            got.contains(wanted) || wanted.contains(got) -> 55
            else -> 0
        }
        if (year != null && candidate.year != null) {
            val gap = kotlin.math.abs(year - candidate.year)
            score += when {
                gap == 0 -> 40
                gap == 1 -> 10 // a release-vs-listing off-by-one is common
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
    private fun normalize(text: String): String =
        text.lowercase()
            .replace(NON_ALNUM, "")

    private val NON_ALNUM = Regex("""[^\p{L}\p{Nd}]""")

    // Below this, the best result is not a real match (no title overlap and no
    // year agreement); return nothing rather than a guess.
    private const val MIN_SCORE = 40
}
