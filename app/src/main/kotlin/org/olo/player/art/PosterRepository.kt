package org.olo.player.art

import android.content.Context
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.olo.player.BuildConfig
import org.olo.player.data.AppPreferences

/**
 * The one place the UI asks "what poster, if any, for this file?". It gates on the
 * opt-in setting, reads the name into a [MediaTitle], and resolves it through TMDB
 * -- once. A hit is remembered on disk so the same title never queries twice; a
 * miss is remembered only for the session, so a transient network failure is not
 * frozen into a permanent "no poster".
 *
 * Folders are never given a poster here (the browse gallery is for files); the
 * caller keeps a folder on its clay tile.
 */
class PosterRepository(
    appContext: Context,
    private val prefs: AppPreferences,
) {
    // A hit lives across launches: the resolved image URL, keyed by the title's
    // stable core. Coil caches the image bytes; this only spares the API call.
    private val hits = appContext.getSharedPreferences("olo_tmdb_cache", Context.MODE_PRIVATE)

    // A miss lives for this launch only, so re-entering a folder after the network
    // comes back can still find a poster.
    private val misses = Collections.synchronizedSet(mutableSetOf<String>())

    /** The poster URL for a media file, or null when posters are off, there is no
     *  key, the name is unreadable, or TMDB has no confident match. */
    suspend fun posterUrl(name: String, folderName: String?): String? {
        if (!prefs.postersEnabled()) return null
        val key = effectiveKey()
        if (key.isBlank()) return null

        val title = TitleParser.parse(name, folderName)
        val cacheKey = cacheKeyFor(title) ?: return null

        hits.getString(cacheKey, null)?.let { return it.ifEmpty { null } }
        if (cacheKey in misses) return null

        val url = withContext(Dispatchers.IO) { TmdbClient(key).poster(title) }
        if (url != null) hits.edit().putString(cacheKey, url).apply() else misses += cacheKey
        return url
    }

    /** The 16:9 still URL for a drama episode (for the detail sheet / player),
     *  falling back to the series poster when that episode has no frame. Null for a
     *  movie or an unreadable name -- the caller shows the 2:3 poster instead. */
    suspend fun stillUrl(name: String, folderName: String?): String? {
        if (!prefs.postersEnabled()) return null
        val key = effectiveKey()
        if (key.isBlank()) return null
        val episode = TitleParser.parse(name, folderName) as? MediaTitle.Episode ?: return null
        return withContext(Dispatchers.IO) { TmdbClient(key).still(episode) }
    }

    /** 포스터 변경 다이얼로그용 수동 검색: 사용자가 입력한 제목·연도로 영화·TV를 모두
     *  찾아 합친다(포스터 있는 것 먼저, 제목·연도 중복 제거, 최대 12개). 자동 매칭과 달리
     *  best 하나로 줄이지 않고, 사람이 눈으로 고르도록 여러 후보를 보여 준다. */
    suspend fun searchManual(query: String, year: Int?): List<TmdbResult> {
        val key = effectiveKey()
        if (key.isBlank() || query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val client = TmdbClient(key)
            val merged = client.search(query, year, tv = false) + client.search(query, null, tv = true)
            merged
                .distinctBy { "${it.title.lowercase()}|${it.year}|${it.tv}" }
                .sortedByDescending { it.posterUrl != null }
                .take(12)
        }
    }

    /** 다이얼로그가 열릴 때 쓸 초기 검색어: 파일명을 해석한 제목·연도(영상 종류도). */
    fun initialQuery(name: String, folderName: String?): Pair<String, Int?> =
        when (val t = TitleParser.parse(name, folderName)) {
            is MediaTitle.Movie -> t.title to t.year
            is MediaTitle.Episode -> t.series to null
            MediaTitle.Unknown -> name.substringBeforeLast('.').replace(Regex("[._]"), " ").trim() to null
        }

    /** The default key from the (git-ignored) build config, unless the person set
     *  their own in settings. Blank means posters cannot load. */
    private fun effectiveKey(): String =
        prefs.tmdbApiKey().ifBlank { BuildConfig.TMDB_API_KEY }

    // An episode is cached under its series (the poster is the series'), so every
    // episode of one drama shares a single lookup.
    private fun cacheKeyFor(title: MediaTitle): String? = when (title) {
        is MediaTitle.Movie -> PosterCacheKey.movie(title.title, title.year)
        is MediaTitle.Episode -> PosterCacheKey.series(title.series)
        MediaTitle.Unknown -> null
    }
}

/** Process-wide access to the one [PosterRepository]; its caches are shared by
 *  every browse screen, so a poster fetched on one is instant on the next. */
object Posters {
    @Volatile private var instance: PosterRepository? = null

    fun get(context: Context): PosterRepository =
        instance ?: synchronized(this) {
            instance ?: PosterRepository(
                context.applicationContext,
                AppPreferences(context.applicationContext),
            ).also { instance = it }
        }
}
