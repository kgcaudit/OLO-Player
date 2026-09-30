package org.olo.player.art

/**
 * Finds the artwork a folder already ships beside its media -- the poster.jpg a
 * person dropped in, the "<film>-poster.jpg" a scraper wrote, the URL inside a
 * Kodi .nfo. This art is the person's own choice and costs no network, so the
 * layered strategy prefers it over a TMDB guess.
 *
 * Pure name work: given the names in a folder it returns which sibling to load
 * (the caller builds the URL for that name in the folder's own scheme), and given
 * a .nfo's text it returns the artwork reference inside. It never opens anything.
 */
object SidecarArt {

    private val IMAGE_EXTS = listOf("jpg", "jpeg", "png", "webp")

    // Folder-level covers, most poster-like first. A landscape "fanart"/"backdrop"
    // is deliberately left out -- it would not sit right in a 2:3 slot.
    private val FOLDER_ART = listOf("poster", "folder", "cover", "movie", "default", "thumb")

    /**
     * The best companion image among [siblingNames] for [mediaFileName], or null.
     * Art named for the file itself ("Dune.2021-poster.jpg", "Dune.2021.jpg") wins
     * over a shared folder cover, so a folder of several films gives each its own.
     * The returned name is the sibling's real spelling, ready to build a URL from.
     */
    fun pick(siblingNames: List<String>, mediaFileName: String): String? {
        val byLower = HashMap<String, String>(siblingNames.size)
        // First spelling wins, so a stable choice when a folder holds duplicates
        // differing only in case.
        for (name in siblingNames) byLower.putIfAbsent(name.lowercase(), name)

        val base = stem(mediaFileName).lowercase()
        for (key in candidates(base)) byLower[key]?.let { return it }
        return null
    }

    /** The .nfo a scraper would have written for [mediaFileName], if it is in the
     *  folder ("<film>.nfo", else "movie.nfo"/"tvshow.nfo"), or null. */
    fun nfoFor(siblingNames: List<String>, mediaFileName: String): String? {
        val byLower = HashMap<String, String>(siblingNames.size)
        for (name in siblingNames) byLower.putIfAbsent(name.lowercase(), name)
        val base = stem(mediaFileName).lowercase()
        for (key in listOf("$base.nfo", "movie.nfo", "tvshow.nfo")) byLower[key]?.let { return it }
        return null
    }

    /**
     * The poster reference inside a Kodi .nfo's text: the poster `<thumb>` when one
     * is marked, else the first `<thumb>`, else an `<art><poster>`. The value is a
     * URL or a path the caller resolves. Null when there is nothing arty in it.
     */
    fun fromNfo(nfoText: String): String? {
        POSTER_THUMB.find(nfoText)?.groupValues?.get(1)?.trim()?.ifEmpty { null }?.let { return it }
        ART_POSTER.find(nfoText)?.groupValues?.get(1)?.trim()?.ifEmpty { null }?.let { return it }
        ANY_THUMB.find(nfoText)?.groupValues?.get(1)?.trim()?.ifEmpty { null }?.let { return it }
        return null
    }

    // The candidate image names, highest priority first: the file's own poster,
    // then its own thumb, then a same-name image, then the folder covers.
    private fun candidates(base: String): List<String> = buildList {
        for (ext in IMAGE_EXTS) add("$base-poster.$ext")
        for (ext in IMAGE_EXTS) add("$base-thumb.$ext")
        for (ext in IMAGE_EXTS) add("$base.$ext")
        for (art in FOLDER_ART) for (ext in IMAGE_EXTS) add("$art.$ext")
    }

    private fun stem(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    // <thumb aspect="poster" ...>value</thumb> -- the poster aspect wins.
    private val POSTER_THUMB = Regex("""<thumb[^>]*aspect\s*=\s*["']poster["'][^>]*>([^<]*)</thumb>""", RegexOption.IGNORE_CASE)
    // <art><poster>value</poster></art>
    private val ART_POSTER = Regex("""<poster>([^<]*)</poster>""", RegexOption.IGNORE_CASE)
    // any <thumb>value</thumb>, the fallback.
    private val ANY_THUMB = Regex("""<thumb[^>]*>([^<]*)</thumb>""", RegexOption.IGNORE_CASE)
}
