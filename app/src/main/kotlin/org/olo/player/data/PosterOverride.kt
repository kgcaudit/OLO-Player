package org.olo.player.data

import android.content.Context

/**
 * 사용자가 직접 고른 포스터를 기억하는 작은 저장소.
 *
 * 자동 포스터(파일명·연도 해석 → TMDB)가 틀릴 때, 상세/썸네일의 ⋮ "포스터 변경"에서
 * 고른 TMDB 포스터 URL을 **미디어 URI 기준**으로 눌러 둔다. URI를 키로 쓰는 이유: 브라우즈의
 * [org.olo.player.ftp.RemoteEntry]든 최근 재생의 [SavedItem]이든 같은 파일이면 같은 미디어
 * URI를 가지므로, 한 곳에서 고친 포스터가 다른 목록에도 그대로 반영된다. (파일명·시리즈
 * 해석과 무관한 안정적 식별자.)
 *
 * 앱-전용 SharedPreferences에만 저장되고 평문 비밀번호 등 민감정보는 담지 않는다(미디어
 * URI와 포스터 URL뿐). TMDB 호출을 대신하지 않고, 자동 해석보다 우선할 뿐이다.
 */
object PosterOverride {

    private const val FILE = "olo_poster_override"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** [uri]에 대해 사용자가 고른 포스터 URL, 없으면 null. [uri]가 비면 항상 null. */
    fun get(context: Context, uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return prefs(context).getString(uri, null)?.ifBlank { null }
    }

    /** 포스터를 눌러 두거나([url] 지정), 되돌린다([url]=null → 자동 해석으로 복귀). */
    fun set(context: Context, uri: String?, url: String?) {
        if (uri.isNullOrBlank()) return
        prefs(context).edit().apply {
            if (url.isNullOrBlank()) remove(uri) else putString(uri, url)
        }.apply()
    }
}
