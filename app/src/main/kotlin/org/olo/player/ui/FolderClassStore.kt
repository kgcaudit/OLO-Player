package org.olo.player.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.olo.player.art.RemoteImage
import org.olo.player.ftp.RemoteEntry

/**
 * 네트워크 폴더 '분류'(단일영화/시리즈/모음/일반)를 디스크에 영구 캐시한다.
 *
 * 왜: 네트워크 탐색기는 폴더마다 LIST를 때려 '단일영화인지'를 판별하는데, 이 판별은 화면이
 * 살아있는 동안만 메모리에 남아 앱을 다시 켜면 NAS 전체를 다시 훑었다(Jellyfin/Kodi가 피하라는
 * '매번 재스캔' 안티패턴). 분류를 디스크에 남겨 두 번째 방문부터는 네트워크 LIST 없이 그린다.
 *
 * 무엇을: 성공한 판별만 저장한다(조회 실패는 저장 안 함 -> 일시적 실패가 굳지 않음). 키는
 * `prefKey@수정시각`이라, 폴더 내용이 바뀌어 mtime이 달라지면 자동으로 캐시 미스가 되어 재판별한다
 * (mtime을 주지 않는 서버는 아예 저장하지 않아, 그 소스는 종전처럼 세션 메모리 캐시만 쓴다).
 *
 * 보안: 미디어 URI에는 비밀번호가 섞여 있으므로 URI 자체는 저장하지 않는다. 자격증명 없는
 * 상대경로(사이드카·재생대상)만 저장하고, 읽을 때 imageUriFor로 다시 URI를 만든다.
 *
 * nfo 재현: 캐시 히트 시 .nfo 지연 로더는 되살리지 않는다(되살리려면 폴더를 다시 LIST해야 해
 * 캐시의 의미가 없다). 사이드카 이미지도 TMDB 매치도 없는, .nfo에만 포스터가 있는 희귀 폴더는
 * 재방문 때 제목(TMDB) 포스터로 떨어진다 -- 대개 결과가 같다.
 */
internal object FolderClassStore {
    private const val PREFS = "olo_folder_class"
    private const val CAP = 5000 // 최근 사용 순으로 이 개수까지만 유지(오래된 건 밀려남).

    // 디스크를 매 조회마다 읽지 않도록 메모리 거울(LRU). accessOrder=true라 가장 안 쓴 항목이 앞.
    private val mem = object : LinkedHashMap<String, String>(256, 0.75f, true) {}
    private var loaded = false

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        runCatching {
            val all = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            for ((k, v) in all) if (v is String) mem[k] = v
        }
        loaded = true
    }

    @Synchronized
    fun get(context: Context, key: String, imageUriFor: ((String) -> android.net.Uri?)?): FolderProbe? {
        ensureLoaded(context)
        val json = mem[key] ?: return null // get()이 accessOrder를 갱신(최근 사용 표시)
        return runCatching { parse(json, imageUriFor) }.getOrNull()
    }

    @Synchronized
    fun put(context: Context, key: String, probe: FolderProbe) {
        ensureLoaded(context)
        val json = serialize(probe)
        mem[key] = json
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(key, json)
        while (mem.size > CAP) {
            val eldest = mem.entries.iterator().next().key
            mem.remove(eldest)
            editor.remove(eldest)
        }
        editor.apply()
    }

    @Synchronized
    fun remove(context: Context, key: String) {
        ensureLoaded(context)
        mem.remove(key)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
    }

    private fun serialize(p: FolderProbe): String {
        val o = JSONObject()
        when (p) {
            is FolderProbe.Plain -> o.put("k", "plain")
            is FolderProbe.Videos -> o.put("k", "videos").put("c", p.count)
            is FolderProbe.Music -> o.put("k", "music").put("c", p.count)
            is FolderProbe.Mixed -> o.put("k", "mixed").put("v", p.videoCount).put("m", p.musicCount)
            is FolderProbe.Media -> {
                o.put("k", "media").put("pn", p.posterName).put("c", p.count)
                p.posterNameAlt?.let { o.put("pa", it) }
                p.play?.let { o.put("pp", it.path).put("pm", it.name) }
                p.sidecarPath?.let { o.put("sc", it) }
                if (p.subs.isNotEmpty()) {
                    val arr = JSONArray()
                    p.subs.forEach { arr.put(JSONObject().put("p", it.path).put("n", it.name)) }
                    o.put("su", arr)
                }
            }
        }
        return o.toString()
    }

    private fun parse(json: String, imageUriFor: ((String) -> android.net.Uri?)?): FolderProbe? {
        val o = JSONObject(json)
        return when (o.optString("k")) {
            "plain" -> FolderProbe.Plain
            "videos" -> FolderProbe.Videos(o.getInt("c"))
            "music" -> FolderProbe.Music(o.getInt("c"))
            "mixed" -> FolderProbe.Mixed(o.getInt("v"), o.getInt("m"))
            "media" -> {
                val play = if (o.has("pp")) RemoteEntry(o.optString("pm"), false, o.getString("pp")) else null
                val sc = if (o.has("sc")) o.getString("sc") else null
                val art = sc?.let { imageUriFor?.invoke(it) }?.let { RemoteImage(it) }
                val subs = o.optJSONArray("su")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val e = arr.getJSONObject(i)
                        RemoteEntry(e.optString("n"), isDirectory = false, path = e.getString("p"))
                    }
                } ?: emptyList()
                FolderProbe.Media(
                    posterName = o.getString("pn"),
                    posterNameAlt = if (o.has("pa")) o.getString("pa") else null,
                    art = art,
                    nfo = null,
                    play = play,
                    count = o.getInt("c"),
                    subs = subs,
                    sidecarPath = sc,
                )
            }
            else -> null
        }
    }
}
