package org.olo.player.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.data.AppPreferences
import org.olo.player.data.PlaylistStore
import org.olo.player.data.SavedItem

/**
 * The player's state and its window onto the settings.
 *
 * A thin stand-in for OLO Explorer's MainViewModel: it holds the one screen of
 * state the player has -- which playlist is open, if any -- and forwards the
 * media/subtitle preferences the player screen reads and writes. A playlist is
 * a list of [MediaEntry], so a local folder, a pasted web URL and an FTP folder
 * all open the same player.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val preferences = AppPreferences(app)
    private val playlist = PlaylistStore(app)

    /**
     * A media playlist open in the viewer: the items, and which one is showing.
     * One kind throughout -- video with video, sound with sound.
     *
     * [openId]는 '열기'가 일어날 때마다 새로 붙는 번호다. 같은 폴더의 다른 곡을 탭하면 아이템 목록은
     * 같고(형제 전체) index만 달라, 재생 엔진이 '같은 큐'로 보고 이동하지 않던 문제가 있었다(특히
     * 음악이 백그라운드로 계속 재생될 때). openId가 바뀌면 사용자가 직접 연 것이므로 그 index로
     * 이동·재생하고, 그대로면 회전·복귀 같은 재구성이라 재생을 건드리지 않는다.
     */
    data class MediaViewer(
        val items: List<MediaEntry>,
        val index: Int,
        val openId: Long = OPEN_SEQ.incrementAndGet(),
    )

    private companion object {
        val OPEN_SEQ = java.util.concurrent.atomic.AtomicLong(0L)
    }

    var mediaViewer by mutableStateOf<MediaViewer?>(null)
        private set

    /**
     * Whether the app is in picture-in-picture. The player screen reads it to drop
     * its own chrome while the small window is up (the system draws its own), and
     * MainActivity sets it from the PiP mode change.
     */
    var inPip by mutableStateOf(false)
        private set

    fun onPipModeChanged(value: Boolean) { inPip = value }

    /**
     * The chosen app theme -- "system", "light" or "dark" -- as observable state,
     * so setting it in 설정 › 일반 recomposes the whole app through OloPlayerTheme
     * at once. Seeded from preferences and written back on change.
     */
    var themeMode by mutableStateOf(preferences.themeMode())
        private set

    fun chooseTheme(mode: String) {
        themeMode = mode
        preferences.setThemeMode(mode)
    }

    /**
     * Opens a local [file] with the other media of its kind sitting beside it in
     * the same folder, as a playlist.
     *
     * Video with video, sound with sound: a folder holding both a film and its
     * soundtrack does not fold them into one playlist, and the tap says which
     * kind was meant. Ordered for playing by natural name. Always opens at least
     * the file itself.
     */
    fun openLocalMedia(file: File) {
        // 형제 모으기는 부모 폴더 listFiles -- 디스크 I/O라 탭 시점 메인 스레드에서 돌리면 큰 폴더에서
        // 화면이 잠깐 멎는다. IO에서 훑어 결과만 메인에서 상태에 반영한다(파일 존재 확인도 I/O라 함께).
        viewModelScope.launch {
            val viewer = withContext(Dispatchers.IO) {
                // 사라진 파일(삭제·이동)은 열지 않는다 -- 검은 재생창만 뜨던 걸 막는 안전장치(탭 지점에서도
                // 막지만, 다른 호출 경로를 위해 여기서도 확인).
                if (!file.exists()) return@withContext null
                val candidates = file.parentFile?.listFiles()?.filter { it.isFile }.orEmpty()
                val wantVideo = looksVideo(file.name)
                val siblings = candidates
                    .filter { looksMedia(it.name) && looksVideo(it.name) == wantVideo }
                    .sortedWith(compareBy(NaturalOrder) { it.name })
                    .map { localEntry(it) }
                val index = siblings.indexOfFirst { it.localFile?.path == file.path }.coerceAtLeast(0)
                if (siblings.isEmpty()) MediaViewer(listOf(localEntry(file)), 0) else MediaViewer(siblings, index)
            } ?: return@launch
            mediaViewer = viewer
            recordRecent(localEntry(file), "기기", local = true)
        }
    }

    /**
     * 다른 앱의 '연결 프로그램/열기'로 넘어온 미디어 URI를 연다(파일 탐색기·문서 공급자·링크).
     * file은 폴더의 형제까지 모아 열고(앱 안에서 연 것과 같은 경험), content·http(s)·ftp는 단일
     * 항목으로 연다 -- 형제 모으기·사이드카·태그 편집은 경로를 아는 로컬 파일에서만 되기 때문이다.
     * [displayName]은 호출부가 content URI에서 뽑아 넘긴 표시 이름(없으면 URI에서 추정).
     */
    fun openExternalUri(uri: Uri, displayName: String?) {
        when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { openLocalMedia(File(it)) }
            "content" -> {
                val name = displayName?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: "미디어"
                val entry = MediaEntry(uri, name, prefKey = uri.toString())
                mediaViewer = MediaViewer(listOf(entry), 0)
                recordRecent(entry, "열기", local = false)
            }
            else -> openNetworkUrl(uri.toString()) // http(s)·ftp 등
        }
    }

    /**
     * Opens a single network stream from a pasted URL (http(s) or ftp). It has
     * no siblings to gather -- one URL is one item.
     */
    fun openNetworkUrl(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val uri = Uri.parse(trimmed)
        val name = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.host ?: trimmed
        val entry = MediaEntry(uri, name, prefKey = trimmed)
        mediaViewer = MediaViewer(listOf(entry), 0)
        val source = if (uri.scheme.equals("ftp", true)) "FTP" else "URL"
        recordRecent(entry, source, local = false)
        playlist.recordUrl(entry.toSaved(source, local = false))
    }

    /** Opens a ready-made playlist (the FTP browser builds one from a folder). */
    fun openEntries(items: List<MediaEntry>, index: Int) {
        if (items.isEmpty()) return
        val at = index.coerceIn(0, items.size - 1)
        mediaViewer = MediaViewer(items, at)
        val entry = items[at]
        val source = if (entry.uri.scheme.equals("ftp", true)) "FTP" else "URL"
        recordRecent(entry, source, local = false)
        // A visited server: keep the host as a one-tap return point.
        entry.uri.host?.let { host ->
            playlist.recordServer(
                SavedItem(key = "${entry.uri.scheme}://$host", name = host, uri = "${entry.uri.scheme}://$host", source = source),
            )
        }
    }

    /** Reopens a saved shelf item (recent, URL, favourite, or visited server). */
    fun openSaved(item: SavedItem) {
        val uri = Uri.parse(item.uri)
        if (item.local) {
            val file = File(uri.path ?: return)
            if (file.exists()) { openLocalMedia(file); return }
            // 로컬 파일이 사라졌으면 아래 네트워크 경로로 떨어져 재생을 시도한다.
        }
        // 저장된 item.key를 prefKey로 그대로 써야 이어보기 위치가 맞는다. openNetworkUrl은
        // prefKey를 URI 문자열(FTP는 자격증명 포함)로 다시 만들어, 브라우즈에서 재생할 때
        // 쓴 credential-free 키와 어긋나 저장된 위치를 못 찾았다(최근 재생에선 처음부터 재생).
        val name = item.name.ifBlank { uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.host ?: item.uri }
        val entry = MediaEntry(uri, name, prefKey = item.key)
        mediaViewer = MediaViewer(listOf(entry), 0)
        // 같은 key로 recents 최상단 갱신(중복 생성 없이 제자리). URL 셸프엔 추가하지 않는다.
        recordRecent(entry, item.source, local = item.local)
    }

    // Playlist shelves, read straight through so a screen sees the latest.
    fun recents() = playlist.recents()
    fun urls() = playlist.urls()
    fun favorites() = playlist.favorites()
    fun servers() = playlist.servers()
    fun isFavorite(key: String) = playlist.isFavorite(key)
    fun toggleFavorite(item: SavedItem) = playlist.toggleFavorite(item)
    fun removeSaved(shelf: PlaylistStore.Shelf, key: String) = playlist.remove(shelf, key)
    fun clearShelf(shelf: PlaylistStore.Shelf) = playlist.clear(shelf)

    // '자주 들은' 스마트 목록: 재생 횟수로 집계·산출한다.
    /** 한 곡을 충분히 들었을 때(스킵 제외는 호출부가 임계로 판정) 재생 횟수를 1 올린다. */
    fun recordPlay(entry: MediaEntry) {
        val local = entry.localFile != null
        val source = when {
            local -> "기기"
            entry.uri.scheme?.lowercase() in setOf("ftp", "sftp", "ftps") -> "FTP"
            else -> "URL"
        }
        playlist.recordPlay(entry.toSaved(source, local))
    }
    fun mostPlayed(cap: Int = 30): List<SavedItem> = playlist.mostPlayed(cap)
    fun removePlay(key: String) = playlist.removePlay(key)

    private fun recordRecent(entry: MediaEntry, source: String, local: Boolean) {
        playlist.recordRecent(entry.toSaved(source, local))
    }

    private fun MediaEntry.toSaved(source: String, local: Boolean) = SavedItem(
        key = prefKey, name = name, uri = uri.toString(), source = source, local = local,
    )

    fun closeMediaViewer() {
        mediaViewer = null
    }

    private fun localEntry(file: File): MediaEntry =
        MediaEntry(Uri.fromFile(file), file.name, prefKey = file.path, localFile = file)

    /** Where a media item was last left, in milliseconds, or 0 to start over. */
    fun mediaPosition(entry: MediaEntry): Long = preferences.mediaPosition(entry.prefKey)

    /** The resume position for a saved shelf item, by its key. */
    fun savedPosition(key: String): Long = preferences.mediaPosition(key)

    // App-wide playback defaults the player reads on opening a queue.
    fun resumeEnabled(): Boolean = preferences.resumeEnabled()
    fun defaultSpeed(): Float = preferences.defaultSpeed()
    fun keepScreenOn(): Boolean = preferences.keepScreenOn()

    /** 파일별 마지막 재생 속도(배속), 저장 없으면 0f -- 호출부가 기본값으로 대체한다. */
    fun savedSpeed(entry: MediaEntry): Float = preferences.playbackSpeed(entry.prefKey)

    /** 파일별 재생 속도를 기억한다 -- 같은 파일을 다시 열면 그 배속으로 시작한다. */
    fun setMediaSpeed(entry: MediaEntry, speed: Float) {
        preferences.setPlaybackSpeed(entry.prefKey, speed)
    }

    /** Remembers where a media item was left, so it reopens there. */
    fun setMediaPosition(entry: MediaEntry, positionMs: Long) {
        preferences.setMediaPosition(entry.prefKey, positionMs)
    }

    /** How large the player draws subtitles, as a fraction of the screen. */
    fun subtitleScale(): Float = preferences.subtitleScale()

    /** What colour the player draws subtitles. */
    fun subtitleColor(): Int = preferences.subtitleColor()

    /** Remembers the subtitle size and colour, applied to every video. */
    fun setSubtitleStyle(scale: Float, color: Int) {
        preferences.setSubtitleStyle(scale, color)
    }

    /** The saved subtitle time nudge for an item, in milliseconds (0 = in sync). */
    fun subtitleDelay(entry: MediaEntry): Long = preferences.subtitleDelay(entry.prefKey)

    /** Remembers a subtitle time nudge, so the file reopens with it applied. */
    fun setSubtitleDelay(entry: MediaEntry, deltaMs: Long) {
        preferences.setSubtitleDelay(entry.prefKey, deltaMs)
    }

    /** Which subtitle an item was last watched with, or null for none saved. */
    fun subtitleChoice(entry: MediaEntry): String? = preferences.subtitleChoice(entry.prefKey)

    /** Remembers the subtitle an item is watched with, so it reopens the same. */
    fun setSubtitleChoice(entry: MediaEntry, token: String) {
        preferences.setSubtitleChoice(entry.prefKey, token)
    }
}

/**
 * Orders names the way a person reads them: the digits in a name compare as
 * numbers, so "ep2" comes before "ep10" rather than after it. Everything else
 * compares as lower-case text.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++
                // Compare the digit runs as numbers, ignoring leading zeros.
                val numA = a.substring(i, endA).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(j, endB).trimStart('0').ifEmpty { "0" }
                val byLength = numA.length - numB.length
                if (byLength != 0) return byLength
                val byValue = numA.compareTo(numB)
                if (byValue != 0) return byValue
                i = endA
                j = endB
            } else {
                val byChar = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
