package org.olo.player.art

/**
 * What a file name (helped by its folder) tells us about the work on screen,
 * reduced to just what a poster lookup needs. A movie is a title and maybe a
 * year; a drama episode is a series, a season and an episode number; anything we
 * cannot read confidently is [Unknown] so the caller falls back to a kind tile
 * rather than guessing a wrong poster.
 */
sealed interface MediaTitle {
    data class Movie(val title: String, val year: Int?) : MediaTitle
    data class Episode(val series: String, val season: Int, val episode: Int) : MediaTitle
    data object Unknown : MediaTitle
}
