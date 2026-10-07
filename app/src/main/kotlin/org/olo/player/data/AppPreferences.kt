package org.olo.player.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The app's remembered settings, on top of SharedPreferences.
 *
 * Ported thin from OLO Explorer: only what the player asks for is kept here --
 * where each file was last left, which subtitle it was watched with, and the
 * subtitle look shared by every video. The reference app's comic/pdf/FTP state
 * is gone.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("olo_player", Context.MODE_PRIVATE)

    /**
     * How far into a video or a sound it was left, in milliseconds, so it
     * reopens where it stopped. Zero (or none) means start from the beginning.
     */
    fun mediaPosition(key: String): Long =
        prefs.getLong(mediaPositionKey(key), 0L).coerceAtLeast(0L)

    fun setMediaPosition(key: String, positionMs: Long) {
        val edit = prefs.edit()
        rememberMedia(edit, key)
        edit.putLong(mediaPositionKey(key), positionMs.coerceAtLeast(0L))
        edit.apply()
    }

    /**
     * How far a file's external subtitle is nudged in time, in milliseconds --
     * positive shows it later, negative earlier -- so a subtitle that runs out of
     * sync stays fixed the next time the file is opened. Zero is in sync.
     */
    fun subtitleDelay(key: String): Long = prefs.getLong(mediaDelayKey(key), 0L)

    fun setSubtitleDelay(key: String, deltaMs: Long) {
        val edit = prefs.edit()
        // 위치·자막선택과 같은 예산(remembered-media)에 등록해, 지연값만 영구 누적되지 않고
        // 파일이 잊히면 함께 지워지도록 한다(종전엔 등록 없이 직접 써 영원히 남았다).
        rememberMedia(edit, key)
        edit.putLong(mediaDelayKey(key), deltaMs)
        edit.apply()
    }

    private fun mediaDelayKey(key: String) = "$KEY_MEDIA_DELAY${hash(key)}"

    /**
     * Which subtitle a file was last watched with, so it comes back the same
     * rather than defaulting every time. "off" means subtitles were turned off;
     * anything else is a token naming the chosen track (see the player). Kept
     * beside the position under the same budget, and forgotten with it.
     */
    fun subtitleChoice(key: String): String? =
        prefs.getString(mediaSubtitleKey(key), null)?.ifEmpty { null }

    fun setSubtitleChoice(key: String, token: String) {
        val edit = prefs.edit()
        rememberMedia(edit, key)
        edit.putString(mediaSubtitleKey(key), token)
        edit.apply()
    }

    /**
     * Moves [key] to the front of the remembered-media list and drops the oldest
     * past the cap, forgetting its position and subtitle together so the two
     * never fall out of step over which files are still remembered.
     */
    private fun rememberMedia(edit: SharedPreferences.Editor, key: String) {
        val keys = (mediaKeys() - key).toMutableList()
        keys += key
        while (keys.size > MAX_REMEMBERED_MEDIA) {
            val dropped = keys.removeAt(0)
            edit.remove(mediaPositionKey(dropped))
            edit.remove(mediaSubtitleKey(dropped))
            edit.remove(mediaDelayKey(dropped))
        }
        edit.putString(KEY_MEDIA_KEYS, keys.joinToString(KEY_SEPARATOR))
    }

    private fun mediaKeys(): List<String> =
        prefs.getString(KEY_MEDIA_KEYS, null)
            ?.split(KEY_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private fun mediaPositionKey(key: String) = "$KEY_MEDIA_POSITION${hash(key)}"

    private fun mediaSubtitleKey(key: String) = "$KEY_MEDIA_SUBTITLE${hash(key)}"

    // ---- App-wide defaults (the settings tree) -------------------------------
    // Why here: these are the values a fresh file opens with, shared with the
    // player's live sheet. Each is a plain scalar with a sensible default, so
    // reading one before it is ever set still gives the app's intended baseline.

    /** App theme: "system" (follow the OS), "light" or "dark". */
    fun themeMode(): String = prefs.getString(KEY_THEME, "system") ?: "system"
    fun setThemeMode(v: String) = prefs.edit().putString(KEY_THEME, v).apply()

    fun resumeEnabled(): Boolean = prefs.getBoolean(KEY_RESUME, true)
    fun setResumeEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_RESUME, v).apply()

    fun autoPlayNext(): Boolean = prefs.getBoolean(KEY_AUTO_NEXT, true)
    fun setAutoPlayNext(v: Boolean) = prefs.edit().putBoolean(KEY_AUTO_NEXT, v).apply()

    fun backgroundPlay(): Boolean = prefs.getBoolean(KEY_BG_PLAY, false)
    fun setBackgroundPlay(v: Boolean) = prefs.edit().putBoolean(KEY_BG_PLAY, v).apply()

    fun keepScreenOn(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN, true)
    fun setKeepScreenOn(v: Boolean) = prefs.edit().putBoolean(KEY_KEEP_SCREEN, v).apply()

    /** Rewind/forward step in seconds (10/15/30). */
    fun seekIntervalSec(): Int = prefs.getInt(KEY_SEEK_STEP, 10).coerceIn(5, 60)
    fun setSeekIntervalSec(v: Int) = prefs.edit().putInt(KEY_SEEK_STEP, v).apply()

    /** The speed a fresh video/song starts at. */
    fun defaultSpeed(): Float = prefs.getFloat(KEY_DEFAULT_SPEED, 1f).coerceIn(0.25f, 4f)
    fun setDefaultSpeed(v: Float) = prefs.edit().putFloat(KEY_DEFAULT_SPEED, v.coerceIn(0.25f, 4f)).apply()

    fun gestureSpeed(): Boolean = prefs.getBoolean(KEY_GESTURE_SPEED, true)
    fun setGestureSpeed(v: Boolean) = prefs.edit().putBoolean(KEY_GESTURE_SPEED, v).apply()

    fun doubleTapSeek(): Boolean = prefs.getBoolean(KEY_DOUBLE_TAP, true)
    fun setDoubleTapSeek(v: Boolean) = prefs.edit().putBoolean(KEY_DOUBLE_TAP, v).apply()

    /** Preferred decoder: "auto", "hw" or "sw". */
    fun decoder(): String = prefs.getString(KEY_DECODER, "auto") ?: "auto"
    fun setDecoder(v: String) = prefs.edit().putString(KEY_DECODER, v).apply()

    fun subtitleEnabled(): Boolean = prefs.getBoolean(KEY_SUB_ON, true)
    fun setSubtitleEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_SUB_ON, v).apply()

    /** 선호 자막 언어 ISO 코드("" = 자동). 내장 자막이 여러 개면 이 언어 트랙을 우선 선택한다
     *  -- 오디오의 선호 언어와 같은 방식(저장된 파일별 선택이 있으면 그게 우선). */
    fun preferredSubtitleLang(): String = prefs.getString(KEY_SUB_LANG, "") ?: ""
    fun setPreferredSubtitleLang(v: String) = prefs.edit().putString(KEY_SUB_LANG, v).apply()

    fun subtitleOutline(): Boolean = prefs.getBoolean(KEY_SUB_OUTLINE, true)
    fun setSubtitleOutline(v: Boolean) = prefs.edit().putBoolean(KEY_SUB_OUTLINE, v).apply()

    /** 자막 굵게: 얇은 사용자 글꼴도 강제로 볼드로 그려 영상 위 가독성을 높인다. 외부 자막은
     *  오버레이의 글자 두께로, 내장/ASS는 CaptionStyleCompat의 볼드 타입페이스로 적용된다. */
    fun subtitleBold(): Boolean = prefs.getBoolean(KEY_SUB_BOLD, false)
    fun setSubtitleBold(v: Boolean) = prefs.edit().putBoolean(KEY_SUB_BOLD, v).apply()

    /** 자막 줄 간격 배수(1.0=기본 행간, 1.35=35% 여유). media3 SubtitleView엔 줄 간격 API가
     *  없어, 일반 텍스트 자막(SRT/VTT/SMI)을 앱이 직접 그릴 때만 적용된다. */
    fun subtitleLineSpacing(): Float =
        prefs.getFloat(KEY_SUB_LINESPACING, DEFAULT_SUBTITLE_LINESPACING)
            .coerceIn(MIN_SUBTITLE_LINESPACING, MAX_SUBTITLE_LINESPACING)
    fun setSubtitleLineSpacing(v: Float) =
        prefs.edit().putFloat(KEY_SUB_LINESPACING, v.coerceIn(MIN_SUBTITLE_LINESPACING, MAX_SUBTITLE_LINESPACING)).apply()

    /** 자막 파일 디코딩 문자셋("" = 자동 감지). 레거시 SRT/SMI가 □□□로 깨질 때 수동 지정.
     *  "utf-8"/"euc-kr"/"shift-jis"/"gb18030" 등 Charset 이름을 그대로 쓴다. */
    fun subtitleEncoding(): String = prefs.getString(KEY_SUB_ENCODING, "") ?: ""
    fun setSubtitleEncoding(v: String) = prefs.edit().putString(KEY_SUB_ENCODING, v).apply()

    /** SSA/ASS·내장 자막의 색·굵기·위치 등 자막 자체의 스타일을 그대로 적용할지.
     *  끄면 사용자 설정(색·크기)으로 통일. media3 SubtitleView.setApplyEmbeddedStyles에 대응. */
    fun subtitleEmbeddedStyles(): Boolean = prefs.getBoolean(KEY_SUB_EMBEDDED_STYLES, true)
    fun setSubtitleEmbeddedStyles(v: Boolean) = prefs.edit().putBoolean(KEY_SUB_EMBEDDED_STYLES, v).apply()

    /** Subtitle anchor: "bottom" (default) or "top". */
    fun subtitlePosition(): String = prefs.getString(KEY_SUB_POS, "bottom") ?: "bottom"
    fun setSubtitlePosition(v: String) = prefs.edit().putString(KEY_SUB_POS, v).apply()

    /**
     * The display name of the chosen subtitle font, or null for the default. The
     * font file itself is copied into app storage (see [org.olo.player.data.SubtitleFont]);
     * this only remembers its name to show in 설정 and to mark one as chosen.
     */
    fun subtitleFontName(): String? = prefs.getString(KEY_SUB_FONT, null)
    fun setSubtitleFontName(v: String?) =
        prefs.edit().apply { if (v.isNullOrBlank()) remove(KEY_SUB_FONT) else putString(KEY_SUB_FONT, v) }.apply()

    // ---- 목록 (list view) ----
    /** Aggregated-library layout: "list" (one column) or "grid" (adaptive). */
    fun listView(): String = prefs.getString(KEY_LIST_VIEW, "list") ?: "list"
    fun setListView(v: String) = prefs.edit().putString(KEY_LIST_VIEW, v).apply()

    /** Sort order: "date" (newest first), "name" (natural) or "size" (largest). */
    fun listSort(): String = prefs.getString(KEY_LIST_SORT, "date") ?: "date"
    fun setListSort(v: String) = prefs.edit().putString(KEY_LIST_SORT, v).apply()

    fun listThumbnails(): Boolean = prefs.getBoolean(KEY_LIST_THUMBS, true)
    fun setListThumbnails(v: Boolean) = prefs.edit().putBoolean(KEY_LIST_THUMBS, v).apply()

    // ---- 포스터·썸네일 (TMDB) ----
    // Off by default and opt-in: turning it on sends file names to TMDB to fetch a
    // poster, which the person consents to once (see the first-run notice).

    /** Whether TMDB posters are shown at all. Default off; consent is explicit. */
    fun postersEnabled(): Boolean = prefs.getBoolean(KEY_POSTERS_ON, false)
    fun setPostersEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_POSTERS_ON, v).apply()

    /** A personal TMDB API key that overrides the build's default; blank = use the
     *  default (which may itself be empty, in which case posters cannot load). */
    fun tmdbApiKey(): String = prefs.getString(KEY_TMDB_KEY, "")?.trim().orEmpty()
    fun setTmdbApiKey(v: String) = prefs.edit().putString(KEY_TMDB_KEY, v.trim()).apply()

    /** Whether the person has seen the one-time notice explaining that enabling
     *  posters sends file names to TMDB. Gates the consent dialog, shown once. */
    fun posterNoticeSeen(): Boolean = prefs.getBoolean(KEY_POSTER_NOTICE, false)
    fun setPosterNoticeSeen(v: Boolean) = prefs.edit().putBoolean(KEY_POSTER_NOTICE, v).apply()

    /** Browse view mode: "list" (default), "grid" (kind tiles) or "gallery"
     *  (posters). Kept so the chosen view survives leaving a folder, shared by the
     *  local and network browsers. */
    fun browseView(): String = prefs.getString(KEY_BROWSE_VIEW, "list") ?: "list"
    fun setBrowseView(v: String) = prefs.edit().putString(KEY_BROWSE_VIEW, v).apply()

    /** Whether folders are listed before files (default on). */
    fun browseFoldersFirst(): Boolean = prefs.getBoolean(KEY_BROWSE_FOLDERS_FIRST, true)
    fun setBrowseFoldersFirst(v: Boolean) = prefs.edit().putBoolean(KEY_BROWSE_FOLDERS_FIRST, v).apply()

    /** Whether dot-files/folders are shown (default off). */
    fun browseShowHidden(): Boolean = prefs.getBoolean(KEY_BROWSE_HIDDEN, false)
    fun setBrowseShowHidden(v: Boolean) = prefs.edit().putBoolean(KEY_BROWSE_HIDDEN, v).apply()

    /** How a browse list is ordered: "name" (default), "date" or "size". Shared by
     *  the local and network browsers so both order the same way. */
    fun browseSortBy(): String = prefs.getString(KEY_BROWSE_SORT, "name") ?: "name"
    fun setBrowseSortBy(v: String) = prefs.edit().putString(KEY_BROWSE_SORT, v).apply()

    /** The browse sort direction: true = ascending (default), false = descending. */
    fun browseSortAsc(): Boolean = prefs.getBoolean(KEY_BROWSE_SORT_ASC, true)
    fun setBrowseSortAsc(v: Boolean) = prefs.edit().putBoolean(KEY_BROWSE_SORT_ASC, v).apply()

    /**
     * Per-folder view/sort override for "이 폴더만": an opaque encoded string kept
     * against a folder's key, or null when that folder follows the global options.
     * Stored as one small JSON map so a handful of overrides cost a single entry.
     */
    fun folderOptions(key: String): String? = folderOptionMap()[key]

    fun setFolderOptions(key: String, value: String?) {
        val map = folderOptionMap().toMutableMap()
        if (value == null) map.remove(key) else map[key] = value
        prefs.edit().putString(KEY_FOLDER_OPTS, org.json.JSONObject(map.toMap<String, Any?>()).toString()).apply()
    }

    private fun folderOptionMap(): Map<String, String> = runCatching {
        val s = prefs.getString(KEY_FOLDER_OPTS, "").orEmpty()
        if (s.isBlank()) return emptyMap()
        val o = org.json.JSONObject(s)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    // ---- 오디오 ----
    /** Extra loudness in millibels (0 = off), applied by a LoudnessEnhancer. */
    fun audioBoostMb(): Int = prefs.getInt(KEY_AUDIO_BOOST, 0).coerceIn(0, 2000)
    fun setAudioBoostMb(v: Int) = prefs.edit().putInt(KEY_AUDIO_BOOST, v.coerceIn(0, 2000)).apply()

    /** Preferred audio language as an ISO code ("" = automatic). */
    fun preferredAudioLang(): String = prefs.getString(KEY_AUDIO_LANG, "") ?: ""
    fun setPreferredAudioLang(v: String) = prefs.edit().putString(KEY_AUDIO_LANG, v).apply()

    // ---- 네트워크 ----
    /** Larger streaming buffer for shaky connections (else the media3 default). */
    fun netBufferLarge(): Boolean = prefs.getBoolean(KEY_NET_BUFFER, false)
    fun setNetBufferLarge(v: Boolean) = prefs.edit().putBoolean(KEY_NET_BUFFER, v).apply()

    /** 서버 연결 제한시간(초). 절전 NAS가 깨는 데 걸리는 시간을 감안해 기본 30초. */
    fun connectTimeoutSec(): Int = prefs.getInt(KEY_CONNECT_TIMEOUT, 30)
    fun setConnectTimeoutSec(v: Int) = prefs.edit().putInt(KEY_CONNECT_TIMEOUT, v).apply()

    /** How large the player draws subtitles, as a fraction of the screen. */
    fun subtitleScale(): Float =
        prefs.getFloat(KEY_SUBTITLE_SCALE, DEFAULT_SUBTITLE_SCALE)
            .coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE)

    /** What colour the player draws subtitles. */
    fun subtitleColor(): Int = prefs.getInt(KEY_SUBTITLE_COLOR, DEFAULT_SUBTITLE_COLOR)

    /** Remembers the subtitle size and colour, applied to every video. */
    fun setSubtitleStyle(scale: Float, color: Int) {
        prefs.edit()
            .putFloat(KEY_SUBTITLE_SCALE, scale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE))
            .putInt(KEY_SUBTITLE_COLOR, color)
            .apply()
    }

    // A 32-bit hashCode would risk two files whose hashes collide sharing -- and
    // overwriting -- each other's saved place, and the eviction list falling out
    // of step with the stored values. A SHA-256 prefix does not collide in any
    // collection a phone will ever hold.
    private fun hash(key: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .take(16).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_MEDIA_POSITION = "media_pos_"
        private const val KEY_MEDIA_SUBTITLE = "media_sub_"
        private const val KEY_MEDIA_DELAY = "media_subdelay_"
        private const val KEY_MEDIA_KEYS = "media_pos_keys"
        private const val KEY_SUBTITLE_SCALE = "subtitle_scale"
        private const val KEY_SUBTITLE_COLOR = "subtitle_color"
        private const val KEY_SEPARATOR = "\n"

        // App-wide defaults (settings tree)
        private const val KEY_THEME = "set_theme"
        private const val KEY_RESUME = "set_resume"
        private const val KEY_AUTO_NEXT = "set_auto_next"
        private const val KEY_BG_PLAY = "set_bg_play"
        private const val KEY_KEEP_SCREEN = "set_keep_screen"
        private const val KEY_SEEK_STEP = "set_seek_step"
        private const val KEY_DEFAULT_SPEED = "set_default_speed"
        private const val KEY_GESTURE_SPEED = "set_gesture_speed"
        private const val KEY_DOUBLE_TAP = "set_double_tap"
        private const val KEY_DECODER = "set_decoder"
        private const val KEY_SUB_ON = "set_sub_on"
        private const val KEY_SUB_OUTLINE = "set_sub_outline"
        private const val KEY_SUB_POS = "set_sub_pos"
        private const val KEY_SUB_FONT = "set_sub_font"
        private const val KEY_SUB_LANG = "set_sub_lang"
        private const val KEY_SUB_LINESPACING = "set_sub_linespacing"
        private const val KEY_SUB_ENCODING = "set_sub_encoding"
        private const val KEY_SUB_EMBEDDED_STYLES = "set_sub_embedded_styles"
        private const val KEY_SUB_BOLD = "set_sub_bold"
        private const val KEY_LIST_VIEW = "set_list_view"
        private const val KEY_LIST_SORT = "set_list_sort"
        private const val KEY_LIST_THUMBS = "set_list_thumbs"
        private const val KEY_POSTERS_ON = "set_posters_on"
        private const val KEY_TMDB_KEY = "set_tmdb_key"
        private const val KEY_POSTER_NOTICE = "set_poster_notice"
        private const val KEY_BROWSE_VIEW = "set_browse_view"
        private const val KEY_BROWSE_FOLDERS_FIRST = "set_browse_folders_first"
        private const val KEY_BROWSE_HIDDEN = "set_browse_hidden"
        private const val KEY_BROWSE_SORT = "set_browse_sort"
        private const val KEY_BROWSE_SORT_ASC = "set_browse_sort_asc"
        private const val KEY_FOLDER_OPTS = "set_folder_opts"
        private const val KEY_AUDIO_BOOST = "set_audio_boost"
        private const val KEY_AUDIO_LANG = "set_audio_lang"
        private const val KEY_NET_BUFFER = "set_net_buffer"
        private const val KEY_CONNECT_TIMEOUT = "set_connect_timeout"

        const val DEFAULT_SUBTITLE_SCALE = 0.0533f
        const val MIN_SUBTITLE_SCALE = 0.03f
        const val MAX_SUBTITLE_SCALE = 0.12f

        // 줄 간격 배수: 1.0(기본 행간)~2.0(두 줄 간격). 기본 1.35는 가독성과 화면 점유의 절충.
        const val DEFAULT_SUBTITLE_LINESPACING = 1.35f
        const val MIN_SUBTITLE_LINESPACING = 1.0f
        const val MAX_SUBTITLE_LINESPACING = 2.0f
        val DEFAULT_SUBTITLE_COLOR = 0xFFFFFFFF.toInt()
        // "원문": 색을 고정하지 않고 자막 파일 자체의 색상 정보를 그대로 쓴다는 센티넬.
        // 투명(0x00000000)이라 어떤 실제 자막 색상과도 겹치지 않아 안전한 표식이다.
        const val SUBTITLE_COLOR_ORIGINAL = 0

        // The place is kept for the most recent files only; the oldest is
        // forgotten first, so the preferences file does not grow without end.
        private const val MAX_REMEMBERED_MEDIA = 300
    }
}

/**
 * 저장할 이어보기 위치(ms)를 공통 규칙으로 계산한다 -- UI(뷰어)와 서비스(onTaskRemoved)가 같은
 * 규칙으로 쓰도록 한 곳에 둔다. 끝에서 [endGraceMs] 이내면 "다 봤다"로 보고 0(처음)으로
 * 되돌리고, 그 밖에는 현재 위치를 음수 없이 그대로 쓴다. duration을 아직 모르면(≤0) 위치만 쓴다.
 */
fun resumePositionToSave(positionMs: Long, durationMs: Long, endGraceMs: Long = 1_000L): Long =
    if (durationMs > 0 && positionMs >= durationMs - endGraceMs) 0L else positionMs.coerceAtLeast(0L)
