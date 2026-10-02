package org.olo.player.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.olo.player.R
import org.olo.player.art.Posters
import org.olo.player.art.RemoteImage
import org.olo.player.art.SidecarArt
import org.olo.player.art.SidecarResolver
import org.olo.player.art.TmdbResult
import org.olo.player.data.AppPreferences
import org.olo.player.data.PosterOverride
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ui.components.CpDivider
import org.olo.player.ui.theme.OloTheme

/**
 * The one list every network browser draws, so FTP·SFTP·SMB·WebDAV read as one
 * screen, styled in one place after the OLO Explorer browser.
 *
 * The header (ported from OLO Explorer's PaneHeader) is a single row: a source
 * tile button that leaves to the source picker, a reverse-scrolling breadcrumb
 * whose ancestor crumbs jump to that folder, and a filter toggle. Below it, rows
 * with a kind tile, the name (folder Medium / file Normal) and a 날짜  ·  크기
 * line, hairline-divided, with a 폴더/파일 count at the foot. Only folders and
 * playable media show; a folder opens and a file plays through [onEntry].
 *
 * There is no "위로" row: an ancestor crumb (or system back) goes up. [onNavigate]
 * jumps to any folder on the path; [onChangeSource] leaves to pick another source.
 */
// SidecarResolver.nfoArt는 media3의 아직-불안정 API를 쓰는 @UnstableApi 선언이라,
// opt-in을 이 얇은 래퍼가 소비한다 -- 호출부(중첩 람다)는 안정 API로 부른다.
// media3의 마커는 androidx의 @RequiresOptIn이므로 kotlin의 @OptIn이 아니라
// androidx.annotation.OptIn을 써야 lint가 인정한다.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private suspend fun loadNfoArt(
    context: android.content.Context,
    entries: List<RemoteEntry>,
    name: String,
    build: (String) -> android.net.Uri?,
): Any? = SidecarResolver.nfoArt(context, entries, name, build)

// The browse options a folder is shown with -- global by default, or pinned to one
// folder via "이 폴더만". Encoded compactly so a per-folder pin is one small string.
private data class BrowseOpts(
    val view: BrowseView,
    val sortBy: SortBy,
    val asc: Boolean,
    val foldersFirst: Boolean,
    val showHidden: Boolean,
)

// 갤러리는 보기 모드에서 뺐으므로, 예전에 저장된 "갤러리" 값은 격자로 보정해 읽는다.
private fun BrowseView.coerced(): BrowseView = if (this == BrowseView.GALLERY) BrowseView.GRID else this

private fun globalBrowseOpts(prefs: AppPreferences) = BrowseOpts(
    view = runCatching { BrowseView.valueOf(prefs.browseView().uppercase()) }.getOrDefault(BrowseView.LIST).coerced(),
    sortBy = runCatching { SortBy.valueOf(prefs.browseSortBy().uppercase()) }.getOrDefault(SortBy.NAME),
    asc = prefs.browseSortAsc(),
    foldersFirst = prefs.browseFoldersFirst(),
    showHidden = prefs.browseShowHidden(),
)

private fun encodeBrowseOpts(o: BrowseOpts) = "${o.view.name}|${o.sortBy.name}|${o.asc}|${o.foldersFirst}|${o.showHidden}"

private fun decodeBrowseOpts(s: String): BrowseOpts? = runCatching {
    val p = s.split("|")
    BrowseOpts(BrowseView.valueOf(p[0]).coerced(), SortBy.valueOf(p[1]), p[2].toBoolean(), p[3].toBoolean(), p[4].toBoolean())
}.getOrNull()

// Column count for 격자·갤러리, adapted to width so a phone/cover (~467dp) keeps 3
// while an unfolded/tablet screen (~969dp) widens to 4~5 -- by a target cell width
// per mode, since a poster wants more room than a compact tile. Floored at 3.
internal fun browseColumns(widthDp: Float, gallery: Boolean): Int {
    val target = if (gallery) 175f else 130f
    val max = if (gallery) 5 else 6
    return (widthDp / target).toInt().coerceIn(3, max)
}

// A media folder shown as one poster card (구상안 ⑥): a folder of exactly one video is
// that film (tap plays it), a folder of several videos is a drama series (tap enters
// it). [Plain] = not a media folder, so it stays an ordinary clay folder.
internal sealed interface FolderProbe {
    data object Plain : FolderProbe
    // [play] != null → 단일영화(그 영상을 바로 재생), null → 시리즈(폴더로 진입).
    // [posterName](우선)·[posterNameAlt](보조)로 TMDB를 순서대로 질의하고, [art]는 로컬
    // 사이드카, [badge]로 폴더/시리즈를 구분한다. 폴더명·파일명이 각각 맞는 경우가 달라
    // (한국어 폴더명 vs 영문 파일명) 두 질의를 모두 시도한다.
    data class Media(
        val posterName: String,
        val posterNameAlt: String?,
        val art: Any?,
        val nfo: (suspend () -> Any?)?,
        val play: RemoteEntry?,
        // 폴더 안 영상 개수 -- 시리즈 부제("N개 영상")에 쓴다(단일영화는 1).
        val count: Int,
        // 단일영화([play]!=null)일 때, 그 영상 옆의 사이드카 자막 파일들. 바로재생 경로가
        // 외장 자막을 붙이는 데 쓴다(프로브가 이미 폴더를 나열했으니 여기서 같이 찾아 둔다).
        val subs: List<RemoteEntry> = emptyList(),
    ) : FolderProbe
}

// Lists [dir] once and decides whether it is a media folder (단일영화 or 시리즈). A
// successful listing yields [FolderProbe.Media] or [FolderProbe.Plain]; a listing
// FAILURE (dropped connection, contention during the initial probe storm, no
// permission) returns null -- "undetermined", so the caller does NOT cache it and
// retries later. (Caching a transient failure as Plain used to freeze a real
// single-film folder as a plain folder until app restart.)
internal suspend fun probeMediaFolder(
    context: android.content.Context,
    dir: RemoteEntry,
    list: suspend (String) -> List<RemoteEntry>,
    imageUriFor: ((String) -> android.net.Uri?)?,
): FolderProbe? {
    val sub = runCatching { list(dir.path) }.getOrNull() ?: return null
    val videos = sub.filter { !it.isDirectory && looksVideo(it.name) }
    if (videos.isEmpty()) return FolderProbe.Plain
    // 하위 폴더가 둘 이상이면 'MOVIE/DRAMA' 같은 카테고리(묶음) 폴더로 보고, 그 안에 섞여 있는
    // 흩어진 영상 하나 때문에 단일영화/시리즈로 오인하지 않는다. 영화 한 편 폴더는 보통 Subs
    // 정도의 하위폴더만 가지므로(≤1), 이 기준이 카테고리 폴더와 영화 폴더를 가른다.
    val subdirs = sub.count { it.isDirectory }
    if (subdirs >= 2) return FolderProbe.Plain
    // 포스터 해석의 대표 영상: 단일영화면 그 영상, 시리즈면 첫 에피소드(→ 시리즈 포스터).
    val rep = videos.first()
    val build = imageUriFor
    // The art: the folder's image sidecar first (free, name work only), else its .nfo
    // art lazily (a network read deferred to the thumbnail), else TMDB(포스터/시리즈).
    val art: Any? = if (build != null) {
        SidecarArt.pick(sub.map { it.name }, rep.name)
            ?.let { picked -> sub.firstOrNull { it.name == picked }?.path }
            ?.let { build(it) }
            ?.let { RemoteImage(it) }
    } else {
        null
    }
    val nfo: (suspend () -> Any?)? =
        if (build != null && art == null) ({ loadNfoArt(context, sub, rep.name, build) }) else null
    return if (videos.size == 1) {
        // 질의는 폴더명 우선, 파일명 보조. 폴더명이 보통 깔끔한 제목("귀멸의 칼날 무한성편")이라
        // 먼저 쓰되, 영문/원제만 TMDB에 잡히는 경우(예: 폴더 "96분" / 파일 "96.Minutes.2025")엔
        // 파일명으로 재시도해 둘 다 커버한다. 재생할 영상(play)·사이드카(art)는 대표 영상 기준.
        FolderProbe.Media(dir.name, posterNameAlt = rep.name, art = art, nfo = nfo, play = rep, count = videos.size, subs = subtitleSiblings(sub, rep.name))
    } else {
        // 시리즈: 폴더명을 시리즈 제목으로 TMDB TV 검색을 타게 "<폴더명> S01E01" 합성 질의를
        // 우선 쓰고, 빗나가면 대표 에피소드 파일명으로 재시도한다.
        FolderProbe.Media("${dir.name} S01E01", posterNameAlt = rep.name, art = art, nfo = nfo, play = null, count = videos.size)
    }
}

// Probes [dir] once (only while [active]) and remembers the result in [cache], so a
// folder is read at most once however the list recomposes or the view mode changes.
// Returns the media card when [dir] is a 단일영화/시리즈 folder, else null.
@Composable
private fun rememberMediaFolder(
    dir: RemoteEntry,
    active: Boolean,
    cache: SnapshotStateMap<String, FolderProbe>,
    attempts: MutableMap<String, Int>,
    probe: suspend (RemoteEntry) -> FolderProbe?,
): FolderProbe.Media? {
    LaunchedEffect(dir.path, active) {
        // 성공 판별은 캐시한다. 조회 실패(null)는 바로 캐시하지 않아 재시도하되, 느린 NAS에서
        // 매 스크롤마다 무한 재시도하며 게이트를 몰아치지 않도록 2회까지만 시도하고 포기한다
        // (Plain 고정). 일시적 실패는 복구되면서, 지속 실패는 더는 네트워크를 때리지 않는다.
        if (active && cache[dir.path] == null) {
            val r = probe(dir)
            if (r != null) {
                cache[dir.path] = r
            } else {
                val n = (attempts[dir.path] ?: 0) + 1
                attempts[dir.path] = n
                if (n >= 2) cache[dir.path] = FolderProbe.Plain
            }
        }
    }
    return if (active) cache[dir.path] as? FolderProbe.Media else null
}

@Composable
fun RemoteBrowseList(
    rootLabel: String,
    path: String,
    entries: List<RemoteEntry>,
    loading: Boolean,
    error: String?,
    onChangeSource: () -> Unit,
    onNavigate: (String) -> Unit,
    onEntry: (RemoteEntry) -> Unit,
    imageUriFor: ((String) -> android.net.Uri?)? = null,
    // Lists a subfolder's entries, so a folder holding one film can be shown as that
    // film; null turns the single-film shortcut off (e.g. the file picker).
    listFolder: (suspend (String) -> List<RemoteEntry>)? = null,
    // Plays one file directly, for tapping such a single-film folder. 2nd arg: 그 영상 옆
    // 사이드카 자막 파일들(외장 자막으로 붙이도록 호출부가 URI를 만들어 쓴다).
    onPlayFile: ((RemoteEntry, List<RemoteEntry>) -> Unit)? = null,
    @androidx.annotation.DrawableRes rootIcon: Int = R.drawable.ic_tile_server,
    // The browse screen is the app root (no separate 홈): at a source's root the top
    // bar shows these global actions instead of 보기·정렬, and [rootShelf] (최근 재생)
    // is drawn above the list. Null = not wired (a plain list, e.g. a mockup).
    onGlobalSearch: (() -> Unit)? = null,
    onPlaylist: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    rootShelf: (@Composable () -> Unit)? = null,
    // 즐겨찾기: 파일의 현재 즐겨찾기 여부와 토글. 한 소스의 URI·키를 아는 각 브라우저가
    // 넘겨주고, 길게누름 상세 시트의 ⭐가 이를 쓴다. null이면 별이 숨겨진다.
    isFavorite: ((RemoteEntry) -> Boolean)? = null,
    onToggleFavorite: ((RemoteEntry) -> Unit)? = null,
) {
    val c = OloTheme.colors
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // The view, sort and folder options. They default to the person's global choice
    // (kept across folders and screens, shared by local and network), but a folder
    // can pin its own with "이 폴더만" -- then this folder alone follows the pin.
    val folderKey = "$rootLabel|$path"
    val initial = remember(folderKey) { prefs.folderOptions(folderKey)?.let { decodeBrowseOpts(it) } ?: globalBrowseOpts(prefs) }
    var scoped by remember(folderKey) { mutableStateOf(prefs.folderOptions(folderKey) != null) }
    var view by remember(folderKey) { mutableStateOf(initial.view) }
    var sortBy by remember(folderKey) { mutableStateOf(initial.sortBy) }
    var sortAsc by remember(folderKey) { mutableStateOf(initial.asc) }
    var foldersFirst by remember(folderKey) { mutableStateOf(initial.foldersFirst) }
    var showHidden by remember(folderKey) { mutableStateOf(initial.showHidden) }
    // Persist a change either to this folder's pin (이 폴더만) or to the global choice.
    fun persist() {
        val o = BrowseOpts(view, sortBy, sortAsc, foldersFirst, showHidden)
        if (scoped) {
            prefs.setFolderOptions(folderKey, encodeBrowseOpts(o))
        } else {
            prefs.setBrowseView(o.view.name.lowercase())
            prefs.setBrowseSortBy(o.sortBy.name.lowercase())
            prefs.setBrowseSortAsc(o.asc)
            prefs.setBrowseFoldersFirst(o.foldersFirst)
            prefs.setBrowseShowHidden(o.showHidden)
        }
    }
    // Pin the current options to this folder ("이 폴더만"), or drop the pin and fall
    // back to the global choice. Shared by the overflow toggle in the header.
    fun applyScope(thisFolder: Boolean) {
        if (thisFolder == scoped) return
        scoped = thisFolder
        if (thisFolder) {
            prefs.setFolderOptions(folderKey, encodeBrowseOpts(BrowseOpts(view, sortBy, sortAsc, foldersFirst, showHidden)))
        } else {
            prefs.setFolderOptions(folderKey, null)
            val g = globalBrowseOpts(prefs)
            view = g.view; sortBy = g.sortBy; sortAsc = g.asc; foldersFirst = g.foldersFirst; showHidden = g.showHidden
        }
    }
    // The file a long-press opened the detail sheet on, or null when it is closed.
    var detail by remember { mutableStateOf<RemoteEntry?>(null) }
    // The current folder's own name, so a bare "E05.mkv" can borrow its series from
    // the folder ("Dark (2017)") when TMDB is queried.
    val folderName = path.trimEnd('/').substringAfterLast('/').ifBlank { rootLabel }
    val postersOn = prefs.postersEnabled()
    // At a source's root the browse screen is the app's landing: the 최근 재생 shelf
    // shows above the list. The header itself is the SAME at root and in folders --
    // 전역 액션(전체검색·재생목록·설정)은 어디서나 ⋮ 안에 있어, 루트/폴더가 한 헤더로 통일된다.
    val atRoot = path.trim('/').isBlank()

    // The media-folder shortcut (단일영화/시리즈): on when posters are on and a lister is
    // available. Gated on posters because it is the same network work the person opted
    // into, reusing the art pipeline. Probes are cached per folder and run only for
    // folders currently on screen (via each cell).
    //
    // 폴더를 여는 중(loading)에는 탐색을 쉬게 해, 포스터 판별용 하위 폴더 list가 네트워크
    // 내비게이션 list와 한 연결에서 부딪혀 충돌·지연을 키우지 않도록 한다. 로딩이 끝나면
    // 그때 화면에 보이는 폴더만 판별한다(실제 네트워크 직렬화는 각 브라우저의 게이트가 담당).
    val mediaProbeActive = postersOn && listFolder != null && !loading
    // 폴더 판별 결과를 이 화면이 사는 동안 유지한다(절대경로 dir.path가 키라 폴더가 달라도
    // 안 섞인다). folderKey·entries로 키를 잡으면 하위 폴더에 들어갔다 뒤로 올 때 상위 목록이
    // 다시 조회되며 캐시가 통째로 비워져, 전 폴더를 처음부터 재판별하느라 한동안 포스터가
    // 사라져 보였다(사용자 보고). 유지하면 돌아왔을 때 판별 결과가 그대로 있어 바로 뜬다.
    val mediaCache = remember { mutableStateMapOf<String, FolderProbe>() }
    // 조회 실패 재시도 횟수 -- 느린 NAS에서 실패가 매 스크롤마다 무한 재시도되며 게이트(목록
    // 직렬화)를 몰아쳐 폴더 열기가 느려지던 걸 막는다. 2회까지만 재시도하고 포기(Plain 고정).
    val mediaAttempts = remember { HashMap<String, Int>() }
    val probeMedia: suspend (RemoteEntry) -> FolderProbe? = { d -> probeMediaFolder(context, d, listFolder!!, imageUriFor) }
    // 해석된 포스터 모델 캐시 -- 스크롤로 항목이 폐기됐다 다시 들어와도, 또 하위 폴더에 들어갔다
    // 뒤로 와도 포스터가 바로 보이게 한다(타일↔포스터 깜빡임·뒤로가기 후 포스터 증발 방지).
    // 키(제목 질의+폴더명)가 폴더를 구분하므로 화면 전체에서 하나로 들고 있어도 안 섞인다.
    val remoteArtCache = remember { mutableStateMapOf<String, Any?>() }
    // 폴더별 스크롤 위치 보존 -- 네트워크 브라우저는 경로를 제자리에서 바꿔 탐색하므로, 경로마다
    // LazyListState를 따로 들고 있어야 하위 폴더에 들어갔다 뒤로 와도 보던 지점으로 돌아온다
    // (폴더가 수백 개여도 맨 위로 튕기지 않음). remember로 이 화면이 살아있는 동안 유지.
    val listStates = remember { HashMap<String, androidx.compose.foundation.lazy.LazyListState>() }

    // 포스터 변경: 다이얼로그를 띄울 대상(없으면 닫힘)과, 저장 시 썸네일을 다시 그리게 하는
    // 틱. override는 미디어 URI를 키로 읽으므로, 같은 파일이면 최근 재생에도 그대로 반영된다.
    var posterEditFor by remember { mutableStateOf<RemoteEntry?>(null) }
    var overrideTick by remember { mutableStateOf(0) }
    fun uriKeyFor(entry: RemoteEntry): String? = imageUriFor?.invoke(entry.path)?.toString()
    fun overrideFor(entry: RemoteEntry): String? = uriKeyFor(entry)?.let { PosterOverride.get(context, it) }
    // 포스터 변경을 쓸 수 있는가: 미디어 URI를 알고(저장 키), 포스터가 의미 있는 대상일 때.
    val canChangePoster = imageUriFor != null && postersOn

    // The folder's own poster for a file, if any -- the first layer, ahead of TMDB.
    // Pure name work plus the screen's own URL builder, so it needs no network; a
    // hit here means [MediaThumbnail]/[PosterCell] never queries TMDB at all.
    fun sidecarFor(entry: RemoteEntry): Any? {
        if (!postersOn || entry.isDirectory) return null
        val build = imageUriFor ?: return null
        val picked = SidecarArt.pick(entries.map { it.name }, entry.name) ?: return null
        val artPath = entries.firstOrNull { it.name == picked }?.path ?: return null
        return build(artPath)?.let { RemoteImage(it) }
    }

    // The second layer for a file with no image sidecar: read its .nfo (a network
    // read, so a suspend the thumbnail runs only when it has no image sidecar).
    fun nfoArtFor(entry: RemoteEntry): (suspend () -> Any?)? {
        if (!postersOn || entry.isDirectory) return null
        val build = imageUriFor ?: return null
        return { loadNfoArt(context, entries, entry.name, build) }
    }

    // One poster card for 격자·갤러리: a 단일영화/시리즈 folder as its art, a media file as
    // its poster, else a plain folder tile. The ⋮ 칩(즐겨찾기·포스터 변경·상세)은 미디어
    // 항목에만 붙는다. (override를 먼저 읽어 사용자가 고른 포스터를 우선 적용.)
    @Composable
    fun PosterItem(entry: RemoteEntry, modifier: Modifier) {
        val media = rememberMediaFolder(entry, mediaProbeActive && entry.isDirectory, mediaCache, mediaAttempts, probeMedia)
        val ov = run { overrideTick; overrideFor(entry) }
        when {
            media != null -> {
                val favEntry = media.play
                PosterCell(
                    entry = entry, folderName = entry.name,
                    subtitle = mediaSubtitle(media, entry),
                    sidecar = media.art, enabled = postersOn,
                    onClick = { if (media.play != null && onPlayFile != null) onPlayFile.invoke(media.play, media.subs) else onEntry(entry) },
                    modifier = modifier, nfoArt = media.nfo, posterName = media.posterName,
                    posterNameAlt = media.posterNameAlt,
                    overrideUrl = ov,
                    folderBadge = if (media.play != null) FolderBadgeKind.FILM else FolderBadgeKind.SERIES,
                    artCache = remoteArtCache,
                    cornerMenu = {
                        ItemMenu(
                            chip = true,
                            favorite = favEntry != null && isFavorite?.invoke(favEntry) == true,
                            onToggleFavorite = if (favEntry != null && onToggleFavorite != null) ({ onToggleFavorite.invoke(favEntry) }) else null,
                            onChangePoster = if (canChangePoster) ({ posterEditFor = entry }) else null,
                            onDetail = if (favEntry != null) ({ detail = favEntry }) else null,
                        )
                    },
                )
            }
            !entry.isDirectory -> {
                val video = looksVideo(entry.name)
                PosterCell(
                    entry = entry, folderName = folderName,
                    subtitle = entrySubtitle(entry),
                    sidecar = sidecarFor(entry), enabled = postersOn,
                    onClick = { onEntry(entry) }, modifier = modifier,
                    onLongClick = { detail = entry }, nfoArt = nfoArtFor(entry), overrideUrl = ov, artCache = remoteArtCache,
                    cornerMenu = {
                        ItemMenu(
                            chip = true,
                            favorite = isFavorite?.invoke(entry) == true,
                            onToggleFavorite = onToggleFavorite?.let { fn -> { fn(entry) } },
                            onChangePoster = if (canChangePoster && video) ({ posterEditFor = entry }) else null,
                            onDetail = { detail = entry },
                        )
                    },
                )
            }
            else -> {
                PosterCell(
                    entry = entry, folderName = folderName,
                    subtitle = entrySubtitle(entry), sidecar = null, enabled = postersOn,
                    onClick = { onEntry(entry) }, modifier = modifier,
                )
            }
        }
    }

    // One 목록 row with the same media logic and a trailing ⋮ for media items.
    @Composable
    fun RowItem(entry: RemoteEntry) {
        val media = rememberMediaFolder(entry, mediaProbeActive && entry.isDirectory, mediaCache, mediaAttempts, probeMedia)
        val ov = run { overrideTick; overrideFor(entry) }
        if (media != null) {
            val favEntry = media.play
            BrowseRow(
                kind = FileKind.FOLDER, folder = true,
                name = entry.name, folderName = entry.name,
                subtitle = mediaSubtitle(media, entry),
                sidecar = media.art, enabled = postersOn,
                onClick = { if (media.play != null && onPlayFile != null) onPlayFile.invoke(media.play, media.subs) else onEntry(entry) },
                nfoArt = media.nfo, posterName = media.posterName, posterNameAlt = media.posterNameAlt,
                overrideUrl = ov, artCache = remoteArtCache,
                folderBadge = if (media.play != null) FolderBadgeKind.FILM else FolderBadgeKind.SERIES,
                trailing = {
                    ItemMenu(
                        chip = false,
                        favorite = favEntry != null && isFavorite?.invoke(favEntry) == true,
                        onToggleFavorite = if (favEntry != null && onToggleFavorite != null) ({ onToggleFavorite.invoke(favEntry) }) else null,
                        onChangePoster = if (canChangePoster) ({ posterEditFor = entry }) else null,
                        onDetail = if (favEntry != null) ({ detail = favEntry }) else null,
                    )
                },
            )
        } else {
            val isDir = entry.isDirectory
            val video = !isDir && looksVideo(entry.name)
            BrowseRow(
                kind = kindOf(entry.name, isDir), folder = isDir,
                name = entry.name, folderName = folderName,
                subtitle = entrySubtitle(entry),
                sidecar = sidecarFor(entry), enabled = postersOn,
                onClick = { onEntry(entry) },
                onLongClick = if (isDir) null else ({ detail = entry }),
                nfoArt = nfoArtFor(entry), overrideUrl = ov, artCache = remoteArtCache,
                trailing = if (isDir) null else ({
                    ItemMenu(
                        chip = false,
                        favorite = isFavorite?.invoke(entry) == true,
                        onToggleFavorite = onToggleFavorite?.let { fn -> { fn(entry) } },
                        onChangePoster = if (canChangePoster && video) ({ posterEditFor = entry }) else null,
                        onDetail = { detail = entry },
                    )
                }),
            )
        }
    }

    val visible = entries.filter {
        (it.isDirectory || looksMedia(it.name)) && (showHidden || !it.name.startsWith("."))
    }
    val filtered = if (searching && query.isNotBlank()) {
        visible.filter { it.name.contains(query, ignoreCase = true) }
    } else {
        visible
    }
    val shown = BrowseSort.sort(filtered, sortBy, sortAsc, foldersFirst)
    val folders = shown.count { it.isDirectory }
    val files = shown.size - folders

    Column(Modifier.fillMaxSize()) {
        BrowseHeader(
            rootLabel = rootLabel,
            path = path,
            searching = searching,
            query = query,
            onQuery = { query = it },
            onToggleSearch = { searching = !searching; if (!searching) query = "" },
            onRefresh = { onNavigate(path) },
            onChangeSource = onChangeSource,
            onNavigate = onNavigate,
            rootIcon = rootIcon,
            view = view,
            onView = { view = it; persist() },
            sortBy = sortBy,
            ascending = sortAsc,
            onSort = { sortBy = it; persist() },
            onDirection = { sortAsc = it; persist() },
            scoped = scoped,
            onScope = { applyScope(it) },
            foldersFirst = foldersFirst,
            onFoldersFirst = { foldersFirst = it; persist() },
            showHidden = showHidden,
            onShowHidden = { showHidden = it; persist() },
            onGlobalSearch = onGlobalSearch,
            onPlaylist = onPlaylist,
            onSettings = onSettings,
        )
        // 폴더 여는 중임을 알리는 가느다란 진행바 -- 눌렀는지·여는 중인지 보이게. 완료되면
        // 사라진다. (로컬은 즉시라 거의 안 뜨고, 네트워크 폴더 전환에서 보인다.)
        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = c.accent,
                trackColor = c.progressTrack,
            )
        }
        if (error != null) {
            Text(
                text = "접속 실패: $error",
                color = c.accent,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = browseColumns(maxWidth.value, view == BrowseView.GALLERY)
        val listState = listStates.getOrPut(folderKey) { androidx.compose.foundation.lazy.LazyListState() }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // 최근 재생 shelf at the source root (the app's landing), above the folders.
            if (atRoot && rootShelf != null) {
                item("rootShelf") { rootShelf() }
            }
            if (shown.isEmpty() && !loading) {
                item("empty") {
                    Text(
                        if (searching && query.isNotBlank()) "검색 결과가 없습니다." else "이 폴더에 미디어가 없습니다.",
                        color = c.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
            when (view) {
            BrowseView.GALLERY, BrowseView.GRID -> {
                // 격자·갤러리 모두 2:3 포스터 카드(밀도=열 수만 다름). 단일영화/시리즈 폴더도
                // 포스터로 보이고, 미디어 항목엔 우측하단 ⋮ 칩이 붙는다.
                val lines = shown.chunked(cols)
                items(lines.size, key = { "poster$it" }) { line ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        lines[line].forEach { entry -> PosterItem(entry, Modifier.weight(1f)) }
                        repeat(cols - lines[line].size) { Box(Modifier.weight(1f)) {} }
                    }
                }
            }
            BrowseView.LIST -> {
                itemsIndexed(shown, key = { _, e -> e.path }) { index, entry ->
                    RowItem(entry)
                    if (index < shown.lastIndex) CpDivider()
                }
            }
            }
            if (shown.isNotEmpty()) {
                item("count") {
                    CpDivider()
                    Text(
                        "폴더 ${folders}개 · 파일 ${files}개",
                        color = c.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
        }
    }

    detail?.let { entry ->
        MediaDetailSheet(
            entry = entry,
            folderName = folderName,
            sidecar = overrideFor(entry) ?: sidecarFor(entry),
            onPlay = { onEntry(entry); detail = null },
            onDismiss = { detail = null },
        )
    }

    // 포스터 변경 다이얼로그: 고른 포스터를 미디어 URI 기준 override로 저장하고 썸네일을
    // 다시 그린다(overrideTick). 되돌리면 자동 해석으로 복귀.
    posterEditFor?.let { entry ->
        PosterChangeDialog(
            name = entry.name,
            folderName = folderName,
            current = overrideFor(entry),
            onDismiss = { posterEditFor = null },
            onPick = { url ->
                PosterOverride.set(context, uriKeyFor(entry), url)
                overrideTick++
                posterEditFor = null
            },
        )
    }
}

/**
 * 썸네일/행의 ⋮ 메뉴: 즐겨찾기·포스터 변경·상세정보를 한곳에 모은다(구상안 ⑤). 포스터
 * 위에서는 반투명 칩([chip]=true), 목록 행 끝에서는 일반 아이콘 버튼. 넘어온 액션이 하나도
 * 없으면 아무것도 그리지 않는다(일반 폴더 등).
 */
@Composable
private fun ItemMenu(
    chip: Boolean,
    favorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onChangePoster: (() -> Unit)?,
    onDetail: (() -> Unit)?,
) {
    if (onToggleFavorite == null && onChangePoster == null && onDetail == null) return
    val c = OloTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        if (chip) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x80000000)).clickable { open = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.MoreVert, "더보기", tint = Color.White, modifier = Modifier.size(18.dp)) }
        } else {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Filled.MoreVert, "더보기", tint = c.muted)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onToggleFavorite != null) {
                DropdownMenuItem(
                    text = { Text(if (favorite) "즐겨찾기 해제" else "즐겨찾기") },
                    onClick = { open = false; onToggleFavorite() },
                    leadingIcon = { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = c.accent) },
                )
            }
            if (onChangePoster != null) {
                DropdownMenuItem(
                    text = { Text("포스터 변경") },
                    onClick = { open = false; onChangePoster() },
                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                )
            }
            if (onDetail != null) {
                DropdownMenuItem(
                    text = { Text("상세정보") },
                    onClick = { open = false; onDetail() },
                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                )
            }
        }
    }
}

/**
 * 포스터 변경 다이얼로그(구상안 ⑥): 파일명에서 해석한 제목·연도로 TMDB를 검색해 후보
 * 포스터를 보여 주고, 고르면 [onPick](URL)으로 override를 저장한다. 사용자가 제목·연도를
 * 고쳐 다시 검색할 수 있고, "자동으로 되돌리기"는 [onPick](null). 외부조사의 Plex Fix Match /
 * Jellyfin Identify 흐름을 따른다. TMDB 귀속 문구는 약관 요구사항.
 */
@Composable
private fun PosterChangeDialog(
    name: String,
    folderName: String?,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val c = OloTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { Posters.get(context) }
    val seed = remember(name, folderName) { repo.initialQuery(name, folderName) }
    var title by remember { mutableStateOf(seed.first) }
    var year by remember { mutableStateOf(seed.second?.toString() ?: "") }
    var results by remember { mutableStateOf<List<TmdbResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // 선택한 후보의 상세 보기(전체 제목·연도·전체 줄거리·큰 포스터). null이면 후보 목록.
    // 잘린 제목을 눌러 바로 지정하던 걸, 상세를 확인하고 "이 포스터로 지정"으로 확정하게 한다.
    var selected by remember { mutableStateOf<TmdbResult?>(null) }

    fun run() {
        loading = true
        selected = null
        scope.launch {
            results = repo.searchManual(title.trim(), year.trim().toIntOrNull())
            loading = false
        }
    }
    LaunchedEffect(Unit) { run() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = c.surface, shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("포스터 변경", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DialogField(title, { title = it }, "제목", Modifier.weight(1f))
                    DialogField(year, { year = it.filter(Char::isDigit).take(4) }, "연도", Modifier.width(72.dp))
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.accent).clickable { run() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Search, "검색", tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 400.dp)) {
                    val picked = selected
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 3.dp, color = c.accent)
                        }
                        picked != null -> PosterDetailBody(
                            result = picked,
                            onUse = { picked.posterUrl?.let { onPick(it) } },
                            onBack = { selected = null },
                        )
                        results.isEmpty() -> Text("검색 결과가 없습니다.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                        else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            results.forEach { r ->
                                Row(
                                    // 행을 누르면 상세 보기로 들어가 전체 정보를 확인한 뒤 지정한다.
                                    Modifier.fillMaxWidth().heightIn(min = 76.dp)
                                        .clickable { selected = r }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(Modifier.width(46.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(c.progressTrack), contentAlignment = Alignment.Center) {
                                        if (r.posterUrl != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(r.posterUrl).crossfade(true).build(),
                                                contentDescription = null, contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                            )
                                        } else {
                                            Icon(Icons.Filled.Image, null, tint = c.muted, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        // 목록에서도 제목을 2줄까지 보여 한 줄 말줄임을 완화한다.
                                        Text(r.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(r.year?.toString(), if (r.tv) "TV" else "영화").joinToString(" · "),
                                            color = c.muted, fontSize = 12.sp,
                                        )
                                        if (r.overview.isNotBlank()) {
                                            Text(r.overview, color = c.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Text("자세히", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                CpDivider()
                            }
                        }
                    }
                }
                // 상세 보기는 자체 버튼(지정·목록)을 갖는다. 목록일 때만 하단 되돌리기·닫기를 둔다.
                if (selected == null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current != null) {
                            Text("자동으로 되돌리기", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onPick(null) })
                        }
                        Spacer(Modifier.weight(1f))
                        Text("닫기", color = c.muted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onDismiss))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("데이터 제공: TMDB", color = c.muted, fontSize = 10.sp)
            }
        }
    }
}

/**
 * 포스터 변경 창의 '상세 보기': 고른 후보의 큰 포스터·전체 제목·연도·전체 줄거리를 보여
 * 주고 "이 포스터로 지정"으로 확정한다(목록의 잘린 제목만 보고 오지정하던 걸 막는다).
 * 포스터가 없는 후보는 지정 버튼을 비활성화한다. "← 목록"으로 후보 목록으로 돌아간다.
 */
@Composable
private fun PosterDetailBody(result: TmdbResult, onUse: () -> Unit, onBack: () -> Unit) {
    val c = OloTheme.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(108.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(c.progressTrack), contentAlignment = Alignment.Center) {
                if (result.posterUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(result.posterUrl).crossfade(true).build(),
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    )
                } else {
                    Icon(Icons.Filled.Image, null, tint = c.muted, modifier = Modifier.size(32.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(result.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(result.year?.toString(), if (result.tv) "TV" else "영화").joinToString(" · "),
                    color = c.muted, fontSize = 13.sp,
                )
            }
        }
        if (result.overview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(result.overview, color = c.text, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val canUse = result.posterUrl != null
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (canUse) c.accent else c.progressTrack)
                    .clickable(enabled = canUse, onClick = onUse)
                    .heightIn(min = 46.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (canUse) "이 포스터로 지정" else "포스터 없음",
                    color = if (canUse) Color.White else c.muted,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(c.progressTrack).clickable(onClick = onBack)
                    .heightIn(min = 46.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.text, modifier = Modifier.size(18.dp))
                Text("목록", color = c.text, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DialogField(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier) {
    val c = OloTheme.colors
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(c.progressTrack).heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, color = c.muted, fontSize = 14.sp)
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 미접속(등록·접속 중·오류) 상태를 전체화면이 아니라 "가운데 팝업 카드"로 띄우는 틀.
 * 뒤에는 [backdrop](위치 목록)이 흐리게 깔리고, 스크림이나 카드의 ←·X를 누르면 [onLeave]
 * (이전 메뉴=위치 전환기)로 빠져나간다. 전체 창을 점유하던 종전 방식은 절전 서버가 깨는
 * 동안 "다른 위치로 이동 불가·좌상단 뒤로가기는 앱 종료"가 되던 문제가 있어 팝업으로 바꾼다.
 */
@Composable
internal fun NetConnectScaffold(
    title: String,
    connecting: Boolean,
    onLeave: () -> Unit,
    backdrop: (@Composable () -> Unit)?,
    form: @Composable () -> Unit,
) {
    val scrimClick = remember { MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 뒤 배경: 위치 목록(없으면 테마 배경). 스크림이 클릭을 받으므로 시각적 맥락용이다.
        if (backdrop != null) backdrop() else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        // 스크림: 카드 바깥을 누르면 이전 메뉴(위치)로. 리플 없이 눌림만 처리한다.
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = scrimClick, indication = null, onClick = onLeave),
        )
        // 카드 본문이 화면을 넘지 않도록 상단바·여백만큼 뺀 높이로 제한하고 넘치면 스크롤.
        val bodyMax = maxHeight - 112.dp
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
            ) {
                Column {
                    NetCardTopBar(title, onLeave)
                    if (connecting) {
                        NetConnectingBody()
                    } else {
                        Column(Modifier.heightIn(max = bodyMax).verticalScroll(rememberScrollState())) { form() }
                    }
                }
            }
        }
    }
}

/** 팝업 카드 상단: ←(이전 메뉴) · 제목 · X(닫기). ←·X 모두 [onLeave]로 같은 동작. */
@Composable
private fun NetCardTopBar(title: String, onLeave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onLeave) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "위치 목록", tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        IconButton(onClick = onLeave) {
            Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 접속 중 카드 본문: 스피너 + 안내(절전 서버 대기, 기다리는 동안 ←로 다른 위치 가능). */
@Composable
private fun NetConnectingBody() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ftp_connecting), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            "절전 중인 서버가 깨어나는 데 시간이 걸릴 수 있습니다.\n기다리는 동안 ←로 다른 위치를 열 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The path header, one row: source tile button, breadcrumb (or filter field), then
 * the inline controls -- 보기(목록·격자·갤러리) and 정렬(이름·날짜·크기·형식) as compact
 * icon menus to the left of 검색, and an overflow ⋮ for the less-used 이 폴더만·폴더
 * 먼저·숨김 파일·새로고침. The controls sit on the row (no second row), after the
 * Samsung My Files pattern, so they cost no vertical space.
 */
@Composable
private fun BrowseHeader(
    rootLabel: String,
    path: String,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit,
    onChangeSource: () -> Unit,
    onNavigate: (String) -> Unit,
    @androidx.annotation.DrawableRes rootIcon: Int,
    view: BrowseView,
    onView: (BrowseView) -> Unit,
    sortBy: SortBy,
    ascending: Boolean,
    onSort: (SortBy) -> Unit,
    onDirection: (Boolean) -> Unit,
    scoped: Boolean,
    onScope: (Boolean) -> Unit,
    foldersFirst: Boolean,
    onFoldersFirst: (Boolean) -> Unit,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    onGlobalSearch: (() -> Unit)? = null,
    onPlaylist: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The source button: what the pane is pointed at, with a chevron to say it
            // is a choice. Tapping it opens the source switcher (위치 · 최근 재생).
            IconButton(onClick = onChangeSource) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(rootIcon),
                        contentDescription = "소스 변경",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // 루트든 폴더든 한 헤더: 경로·보기·정렬·검색(폴더 필터)·⋮. 검색 중에만 필터
            // 입력칸으로 바뀐다. 전역 액션(전체검색·재생목록·설정)은 어디서나 ⋮ 안에 있어,
            // 두 화면이 서로 다른 헤더를 쓸 이유가 없다.
            if (searching) {
                FilterField(query = query, onQuery = onQuery, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Filled.Close, contentDescription = "검색 닫기", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Breadcrumb(rootLabel = rootLabel, path = path, onNavigate = onNavigate, modifier = Modifier.weight(1f))
                ViewMenuButton(view = view, onView = onView)
                SortMenuButton(sortBy = sortBy, ascending = ascending, onSort = onSort, onDirection = onDirection, scoped = scoped, onScope = onScope, foldersFirst = foldersFirst, onFoldersFirst = onFoldersFirst)
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "검색", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OverflowMenuButton(
                    showHidden = showHidden, onShowHidden = onShowHidden,
                    onRefresh = onRefresh,
                    onGlobalSearch = onGlobalSearch,
                    onPlaylist = onPlaylist, onSettings = onSettings,
                )
            }
        }
    }
}

/** 보기 모드를 나타내는 OLO 메뉴 글리프. 목록·격자는 OLO-Design 라인 글리프로 통일하고,
 *  갤러리는 전용 글리프가 없어 Material 폴백을 쓴다. tint는 호출부가 행 색/강조색으로 지정. */
@Composable
private fun ViewModeGlyph(view: BrowseView, contentDescription: String?, tint: Color) {
    when (view) {
        BrowseView.LIST -> Icon(painterResource(R.drawable.ic_menu_view_list), contentDescription, tint = tint)
        BrowseView.GRID -> Icon(painterResource(R.drawable.ic_menu_view_grid), contentDescription, tint = tint)
        BrowseView.GALLERY -> Icon(Icons.Filled.PhotoLibrary, contentDescription, tint = tint)
    }
}

/** 보기: a compact button showing the current mode, opening a 목록·격자·갤러리 menu. */
@Composable
private fun ViewMenuButton(view: BrowseView, onView: (BrowseView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            ViewModeGlyph(view, "보기 방식", MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // 격자가 포스터를 보여주면서 갤러리와 사실상 겹쳐, 보기 모드는 목록·격자 둘로 둔다.
            ViewItem("목록", BrowseView.LIST, view) { onView(BrowseView.LIST); open = false }
            ViewItem("격자", BrowseView.GRID, view) { onView(BrowseView.GRID); open = false }
        }
    }
}

@Composable
private fun ViewItem(label: String, mode: BrowseView, current: BrowseView, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.Normal) },
        onClick = onClick,
        leadingIcon = { ViewModeGlyph(mode, null, LocalContentColor.current) },
        trailingIcon = { if (mode == current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
    )
}

/**
 * 정렬: 이름·날짜·크기·형식 + 오름/내림, 그리고 맨 아래 "이 폴더만"(이 정렬·보기 설정을
 * 이 폴더에만 적용). 범위 토글은 정렬 기준을 어디에 적용할지를 정하는 것이라 ⋮가 아니라
 * 정렬 메뉴에 둔다(사용자 요청). 고른 정렬 키·범위에는 체크가 붙는다.
 */
@Composable
private fun SortMenuButton(
    sortBy: SortBy,
    ascending: Boolean,
    onSort: (SortBy) -> Unit,
    onDirection: (Boolean) -> Unit,
    scoped: Boolean,
    onScope: (Boolean) -> Unit,
    foldersFirst: Boolean,
    onFoldersFirst: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "정렬", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortItem("이름", SortBy.NAME, sortBy) { onSort(SortBy.NAME); open = false }
            SortItem("날짜", SortBy.DATE, sortBy) { onSort(SortBy.DATE); open = false }
            SortItem("크기", SortBy.SIZE, sortBy) { onSort(SortBy.SIZE); open = false }
            SortItem("형식", SortBy.FORMAT, sortBy) { onSort(SortBy.FORMAT); open = false }
            HorizontalDivider()
            // 방향 글리프(↑/↓)가 곧 현재 상태 표시라, 중복되던 꼬리 화살표 텍스트는 뺀다.
            DropdownMenuItem(
                text = { Text(if (ascending) "오름차순" else "내림차순", fontWeight = FontWeight.Normal) },
                onClick = { onDirection(!ascending); open = false },
                leadingIcon = { Icon(painterResource(if (ascending) R.drawable.ic_menu_sort_asc else R.drawable.ic_menu_sort_desc), contentDescription = null) },
            )
            HorizontalDivider()
            // 정렬·보기 설정을 이 폴더에만 적용(핀 고정). 켜면 이 폴더만 따로, 끄면 전역을 따른다.
            CheckItem("이 폴더만", scoped, R.drawable.ic_menu_scope_folder) { onScope(!scoped) }
            // 폴더 먼저도 정렬 규칙이라 ⋮가 아니라 여기에 둔다(이 폴더만 바로 아래).
            CheckItem("폴더 먼저", foldersFirst, R.drawable.ic_menu_folders_first) { onFoldersFirst(!foldersFirst) }
        }
    }
}

@Composable
private fun SortItem(label: String, key: SortBy, current: SortBy, onClick: () -> Unit) {
    val glyph = when (key) {
        SortBy.NAME -> R.drawable.ic_menu_sort_name
        SortBy.DATE -> R.drawable.ic_menu_sort_date
        SortBy.SIZE -> R.drawable.ic_menu_sort_size
        SortBy.FORMAT -> R.drawable.ic_menu_sort_type
    }
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.Normal) },
        onClick = onClick,
        leadingIcon = { Icon(painterResource(glyph), contentDescription = null) },
        trailingIcon = { if (key == current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
    )
}

/** ⋮ : 새로고침·전체검색·재생목록·숨긴 파일·설정 (사용자 지정 순서). 정렬 규칙인 "폴더
 *  먼저"와 적용 범위인 "이 폴더만"은 정렬 메뉴로 옮겼다. */
@Composable
private fun OverflowMenuButton(
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onGlobalSearch: (() -> Unit)? = null,
    onPlaylist: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "더보기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("새로고침", fontWeight = FontWeight.Normal) },
                onClick = { open = false; onRefresh() },
                leadingIcon = { Icon(painterResource(R.drawable.ic_menu_refresh), contentDescription = null) },
            )
            HorizontalDivider()
            // 전역 액션: 루트든 폴더든 한곳(⋮)에서 닿도록 둔다. 헤더의 돋보기는 현재 폴더
            // 이름 필터, 여기 전체검색은 소스·최근을 가로지르는 검색 -- 역할이 다르다.
            if (onGlobalSearch != null) {
                DropdownMenuItem(
                    text = { Text("전체검색", fontWeight = FontWeight.Normal) },
                    onClick = { open = false; onGlobalSearch() },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_menu_search), contentDescription = null) },
                )
            }
            if (onPlaylist != null) {
                DropdownMenuItem(
                    text = { Text("재생목록", fontWeight = FontWeight.Normal) },
                    onClick = { open = false; onPlaylist() },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_menu_playlist), contentDescription = null) },
                )
            }
            // 숨긴 파일 토글(체크는 열린 채 갱신). 문구는 "숨긴 파일"로 축약.
            CheckItem("숨긴 파일", showHidden, R.drawable.ic_menu_hidden) { onShowHidden(!showHidden) }
            if (onSettings != null) {
                DropdownMenuItem(
                    text = { Text("설정", fontWeight = FontWeight.Normal) },
                    onClick = { open = false; onSettings() },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_menu_settings), contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, @DrawableRes icon: Int, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.Normal) },
        onClick = onToggle,
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        trailingIcon = { if (checked) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
    )
}

/** The current folder's name filter, shown in place of the breadcrumb. */
@Composable
private fun FilterField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = OloTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.progressTrack)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text("이름 검색", color = c.muted, fontSize = 14.sp)
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The path as tappable crumbs (ported from OLO Explorer): the root (server) then
 * each folder. Reverse-scrolling so a long path rests on the current folder;
 * ancestor crumbs are accent and jump there, the current folder is ink and inert.
 */
/** One breadcrumb: a label and the path tapping it goes to. */
internal data class PathCrumb(val label: String, val path: String)

/**
 * The path as crumbs: the root (server) first at "/", then one per folder, each
 * carrying the absolute path it jumps to. Pure so it can be tested; the last
 * crumb is the current folder.
 */
internal fun pathCrumbs(rootLabel: String, path: String): List<PathCrumb> {
    val segments = path.split("/").filter { it.isNotBlank() }
    return buildList {
        add(PathCrumb(rootLabel, "/"))
        var acc = ""
        for (seg in segments) {
            acc += "/$seg"
            add(PathCrumb(seg, acc))
        }
    }
}

@Composable
private fun Breadcrumb(rootLabel: String, path: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val crumbs = pathCrumbs(rootLabel, path)
    Row(
        modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        crumbs.forEachIndexed { i, crumb ->
            val label = crumb.label
            val crumbPath = crumb.path
            if (i > 0) {
                Text("/", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outlineVariant)
            }
            val last = i == crumbs.lastIndex
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !last) { onNavigate(crumbPath) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * One browse row (ported from OLO Explorer's EntryRow): the kind tile, the name
 * (a folder in Medium, a file in Normal -- the first folder/file cue, with the
 * tile hue and folder glyph), and an optional 날짜  ·  크기 line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseRow(
    kind: FileKind,
    folder: Boolean,
    name: String,
    folderName: String?,
    subtitle: String?,
    sidecar: Any?,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    nfoArt: (suspend () -> Any?)? = null,
    posterName: String? = null,
    posterNameAlt: String? = null,
    overrideUrl: String? = null,
    artCache: SnapshotStateMap<String, Any?>? = null,
    folderBadge: FolderBadgeKind? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = OloTheme.colors
    // 썸네일이 고정 2:3 박스(66dp 높이)라, 포스터 유무와 무관하게 행 높이가 일정하다.
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 80.dp)
            .padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MediaThumbnail(kind = kind, folder = folder, name = name, folderName = folderName, sidecar = sidecar, enabled = enabled, nfoArt = nfoArt, posterName = posterName, posterNameAlt = posterNameAlt, overrideUrl = overrideUrl, artCache = artCache, folderBadge = folderBadge)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = c.text,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = if (folder) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

// 미디어 폴더의 둘째 줄: 단일영화는 그 영상의 날짜·크기, 시리즈는 폴더 날짜에 "N개 영상"을
// 덧붙여 "여러 편이 든 폴더(→ 진입)"임을 글로도 알려 준다.
private fun mediaSubtitle(media: FolderProbe.Media, entry: RemoteEntry): String? =
    if (media.play != null) {
        entrySubtitle(media.play)
    } else {
        listOfNotNull(entrySubtitle(entry), "${media.count}개 영상").joinToString("  ·  ").ifBlank { null }
    }

/** The second line: 날짜, and 크기 for a file, each shown only when known. */
private fun entrySubtitle(entry: RemoteEntry): String? {
    val date = entry.modified?.takeIf { it > 0 }?.let { formatDate(it) }
    val size = entry.size?.takeIf { it >= 0 && !entry.isDirectory }?.let { humanSize(it) }
    return when {
        date != null && size != null -> "$date  ·  $size"
        date != null -> date
        size != null -> size
        else -> null
    }
}

internal fun formatDate(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))

internal fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

/**
 * 같은 폴더(이미 나열된 [entries]) 안에서 [videoName] 영상의 사이드카 자막 파일들을 찾아
 * [MediaEntry.ExternalSub] 목록으로 돌려준다. 네트워크 소스는 재생 시점에 디스크가 없어, 폴더를
 * 이미 들고 있는 브라우저가 여기서 이름으로 짝지어 MediaEntry에 실어 넘긴다([uriFor]로 각 자막의
 * 재생 URI를 만든다). 매칭 규칙은 로컬 스캔과 동일하게 [SubtitleSidecar]를 쓴다.
 */
fun remoteSubsFor(
    entries: List<RemoteEntry>,
    videoName: String,
    uriFor: (String) -> Uri,
): List<MediaEntry.ExternalSub> =
    subtitleSiblings(entries, videoName).map { MediaEntry.ExternalSub(uriFor(it.path), it.name) }

/**
 * [entries] 중 [videoName] 영상의 사이드카로 볼 자막 파일들(폴더 제외, 자막 확장자, 이름이
 * 영상과 충분히 가까운 것). remoteSubsFor와 단일영화 폴더 프로브가 같은 규칙을 쓰도록 공유한다.
 */
internal fun subtitleSiblings(entries: List<RemoteEntry>, videoName: String): List<RemoteEntry> {
    val base = videoName.substringBeforeLast('.', videoName)
    return entries
        .filter { !it.isDirectory }
        .filter { it.name.substringAfterLast('.', "").lowercase() in org.olo.player.subtitle.SubtitleSidecar.EXTENSIONS }
        .filter { org.olo.player.subtitle.SubtitleSidecar.nameMatches(base, it.name.substringBeforeLast('.', it.name)) }
}
