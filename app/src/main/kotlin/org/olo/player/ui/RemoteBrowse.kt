package org.olo.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.R
import org.olo.player.art.RemoteImage
import org.olo.player.art.SidecarArt
import org.olo.player.art.SidecarResolver
import org.olo.player.data.AppPreferences
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

private fun globalBrowseOpts(prefs: AppPreferences) = BrowseOpts(
    view = runCatching { BrowseView.valueOf(prefs.browseView().uppercase()) }.getOrDefault(BrowseView.LIST),
    sortBy = runCatching { SortBy.valueOf(prefs.browseSortBy().uppercase()) }.getOrDefault(SortBy.NAME),
    asc = prefs.browseSortAsc(),
    foldersFirst = prefs.browseFoldersFirst(),
    showHidden = prefs.browseShowHidden(),
)

private fun encodeBrowseOpts(o: BrowseOpts) = "${o.view.name}|${o.sortBy.name}|${o.asc}|${o.foldersFirst}|${o.showHidden}"

private fun decodeBrowseOpts(s: String): BrowseOpts? = runCatching {
    val p = s.split("|")
    BrowseOpts(BrowseView.valueOf(p[0]), SortBy.valueOf(p[1]), p[2].toBoolean(), p[3].toBoolean(), p[4].toBoolean())
}.getOrNull()

// A folder probed for the single-film shortcut (Step 2 구상안): a folder that holds
// exactly one video is shown as that film -- its poster on the card, a tap plays it
// straight away -- while a 폴더 badge keeps it readable as a folder. [Plain] means
// probed and it is not a single-film folder, so it stays an ordinary folder.
internal sealed interface FolderProbe {
    data object Plain : FolderProbe
    data class Film(val video: RemoteEntry, val art: Any?, val nfo: (suspend () -> Any?)?) : FolderProbe
}

// Lists [dir] once and decides whether it is a single-film folder. Any failure (a
// dropped connection, no permission) falls back to [Plain] so the folder simply
// reads as a folder -- probing never surfaces an error of its own.
internal suspend fun probeFilmFolder(
    context: android.content.Context,
    dir: RemoteEntry,
    list: suspend (String) -> List<RemoteEntry>,
    imageUriFor: ((String) -> android.net.Uri?)?,
): FolderProbe {
    val sub = runCatching { list(dir.path) }.getOrNull() ?: return FolderProbe.Plain
    val videos = sub.filter { !it.isDirectory && looksVideo(it.name) }
    if (videos.size != 1) return FolderProbe.Plain
    val video = videos.first()
    val build = imageUriFor
    // The film's own art: the folder's image sidecar first (free, name work only),
    // else its .nfo art lazily (a network read deferred to the thumbnail), else TMDB.
    val art: Any? = if (build != null) {
        SidecarArt.pick(sub.map { it.name }, video.name)
            ?.let { picked -> sub.firstOrNull { it.name == picked }?.path }
            ?.let { build(it) }
            ?.let { RemoteImage(it) }
    } else {
        null
    }
    val nfo: (suspend () -> Any?)? =
        if (build != null && art == null) ({ loadNfoArt(context, sub, video.name, build) }) else null
    return FolderProbe.Film(video, art, nfo)
}

// Probes [dir] once (only while [active]) and remembers the result in [cache], so a
// folder is read at most once however the list recomposes or the view mode changes.
// Returns the film when [dir] is a single-film folder, else null.
@Composable
private fun rememberFilmFolder(
    dir: RemoteEntry,
    active: Boolean,
    cache: SnapshotStateMap<String, FolderProbe>,
    probe: suspend (RemoteEntry) -> FolderProbe,
): FolderProbe.Film? {
    LaunchedEffect(dir.path, active) {
        if (active && cache[dir.path] == null) cache[dir.path] = probe(dir)
    }
    return if (active) cache[dir.path] as? FolderProbe.Film else null
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
    // Plays one file directly, for tapping such a single-film folder.
    onPlayFile: ((RemoteEntry) -> Unit)? = null,
    @androidx.annotation.DrawableRes rootIcon: Int = R.drawable.ic_tile_server,
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
    // Whether the 보기 옵션 dialog is open.
    var showOptions by remember { mutableStateOf(false) }
    // The file a long-press opened the detail sheet on, or null when it is closed.
    var detail by remember { mutableStateOf<RemoteEntry?>(null) }
    // The current folder's own name, so a bare "E05.mkv" can borrow its series from
    // the folder ("Dark (2017)") when TMDB is queried.
    val folderName = path.trimEnd('/').substringAfterLast('/').ifBlank { rootLabel }
    val postersOn = prefs.postersEnabled()

    // The single-film shortcut: on when posters are on and a lister is available.
    // Gated on posters because it is network work of the same kind the person opted
    // into for posters, and it reuses the same art pipeline. Probes are cached per
    // folder and run only for folders currently on screen (via each cell).
    val filmActive = postersOn && listFolder != null && onPlayFile != null
    // Keyed on the listing too, so an in-place 새로고침 (a re-list of the same
    // folder) re-probes instead of showing a stale film/plain result.
    val filmCache = remember(folderKey, entries) { mutableStateMapOf<String, FolderProbe>() }
    val probeFilm: suspend (RemoteEntry) -> FolderProbe = { d -> probeFilmFolder(context, d, listFolder!!, imageUriFor) }

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
            onViewOptions = { showOptions = true },
            onRefresh = { onNavigate(path) },
            onChangeSource = onChangeSource,
            onNavigate = onNavigate,
            rootIcon = rootIcon,
        )
        if (error != null) {
            Text(
                text = "접속 실패: $error",
                color = c.accent,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
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
            BrowseView.GALLERY -> {
                // Every entry a 2:3 poster card, folders and files alike: a folder is a
                // clay card with the folder glyph and a 폴더 badge, a film its poster
                // (or a hue tile until it resolves). So 갤러리 reads distinctly from
                // 목록 even in a folder-only directory, three cards wide.
                val lines = shown.chunked(3)
                items(lines.size, key = { "gallery$it" }) { line ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        lines[line].forEach { entry ->
                            val film = rememberFilmFolder(entry, filmActive && entry.isDirectory, filmCache, probeFilm)
                            if (film != null) {
                                // A single-film folder: the film's poster on the card,
                                // a tap plays it, the 폴더 badge still marks it a folder.
                                PosterCell(
                                    entry = entry,
                                    folderName = entry.name,
                                    subtitle = entrySubtitle(film.video),
                                    sidecar = film.art,
                                    enabled = postersOn,
                                    onClick = { onPlayFile?.invoke(film.video) },
                                    modifier = Modifier.weight(1f),
                                    nfoArt = film.nfo,
                                    posterName = film.video.name,
                                )
                            } else {
                                PosterCell(
                                    entry = entry,
                                    folderName = folderName,
                                    subtitle = entrySubtitle(entry),
                                    sidecar = sidecarFor(entry),
                                    enabled = postersOn,
                                    onClick = { onEntry(entry) },
                                    modifier = Modifier.weight(1f),
                                    onLongClick = if (entry.isDirectory) null else ({ detail = entry }),
                                    nfoArt = nfoArtFor(entry),
                                )
                            }
                        }
                        repeat(3 - lines[line].size) { Box(Modifier.weight(1f)) {} }
                    }
                }
            }
            BrowseView.GRID -> {
                // Every entry a compact icon-tile cell, folders and files alike.
                val cellLines = shown.chunked(3)
                items(cellLines.size, key = { "grid$it" }) { line ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        cellLines[line].forEach { entry ->
                            // 격자는 포스터 없는 아이콘 뷰라, 단일영상 바로가기를 적용하면
                            // 일반 폴더와 구분되지 않은 채 탭하면 재생돼 버린다(진입 불가).
                            // 그래서 격자에서는 폴더를 평소대로 열고, 바로가기는 포스터·배지로
                            // 식별되는 목록·갤러리에서만 쓴다.
                            GridCell(
                                entry = entry,
                                subtitle = entrySubtitle(entry),
                                onClick = { onEntry(entry) },
                                modifier = Modifier.weight(1f),
                                onLongClick = if (entry.isDirectory) null else ({ detail = entry }),
                            )
                        }
                        repeat(3 - cellLines[line].size) { Box(Modifier.weight(1f)) {} }
                    }
                }
            }
            BrowseView.LIST -> {
                itemsIndexed(shown, key = { _, e -> e.path }) { index, entry ->
                    val film = rememberFilmFolder(entry, filmActive && entry.isDirectory, filmCache, probeFilm)
                    if (film != null) {
                        // A single-film folder: the row shows the film's poster and a
                        // tap plays it, while the folder name stays in folder weight.
                        BrowseRow(
                            kind = FileKind.FOLDER,
                            folder = true,
                            name = entry.name,
                            folderName = entry.name,
                            subtitle = entrySubtitle(film.video),
                            sidecar = film.art,
                            enabled = postersOn,
                            onClick = { onPlayFile?.invoke(film.video) },
                            nfoArt = film.nfo,
                            posterName = film.video.name,
                        )
                    } else {
                        BrowseRow(
                            kind = kindOf(entry.name, entry.isDirectory),
                            folder = entry.isDirectory,
                            name = entry.name,
                            folderName = folderName,
                            subtitle = entrySubtitle(entry),
                            sidecar = sidecarFor(entry),
                            enabled = postersOn,
                            onClick = { onEntry(entry) },
                            onLongClick = if (entry.isDirectory) null else ({ detail = entry }),
                            nfoArt = nfoArtFor(entry),
                        )
                    }
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

    detail?.let { entry ->
        MediaDetailSheet(
            entry = entry,
            folderName = folderName,
            sidecar = sidecarFor(entry),
            onPlay = { onEntry(entry); detail = null },
            onDismiss = { detail = null },
        )
    }

    if (showOptions) {
        BrowseOptionsDialog(
            scoped = scoped,
            view = view,
            sortBy = sortBy,
            ascending = sortAsc,
            foldersFirst = foldersFirst,
            showHidden = showHidden,
            onScope = { thisFolder ->
                if (thisFolder == scoped) return@BrowseOptionsDialog
                scoped = thisFolder
                if (thisFolder) {
                    // Pin the current options to this folder.
                    prefs.setFolderOptions(folderKey, encodeBrowseOpts(BrowseOpts(view, sortBy, sortAsc, foldersFirst, showHidden)))
                } else {
                    // Drop the pin and fall back to the global options.
                    prefs.setFolderOptions(folderKey, null)
                    val g = globalBrowseOpts(prefs)
                    view = g.view; sortBy = g.sortBy; sortAsc = g.asc; foldersFirst = g.foldersFirst; showHidden = g.showHidden
                }
            },
            onView = { view = it; persist() },
            onSort = { sortBy = it; persist() },
            onDirection = { sortAsc = it; persist() },
            onFoldersFirst = { foldersFirst = it; persist() },
            onShowHidden = { showHidden = it; persist() },
            onDismiss = { showOptions = false },
        )
    }
}

/** The simple top bar for the connect form / connecting state: back + title. */
@Composable
internal fun NetTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** The path header: source tile button, breadcrumb (or filter field), filter toggle. */
@Composable
private fun BrowseHeader(
    rootLabel: String,
    path: String,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onViewOptions: () -> Unit,
    onRefresh: () -> Unit,
    onChangeSource: () -> Unit,
    onNavigate: (String) -> Unit,
    @androidx.annotation.DrawableRes rootIcon: Int,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The source button: what the pane is pointed at (a server), and a
            // chevron to say it is a choice. Tapping it leaves to pick a source.
            IconButton(onClick = onChangeSource) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        // On the header (no coloured tile behind it) the white glyph
                        // is tinted to a header colour so it is not invisible on ivory.
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
            if (searching) {
                FilterField(query = query, onQuery = onQuery, modifier = Modifier.weight(1f))
            } else {
                Breadcrumb(rootLabel = rootLabel, path = path, onNavigate = onNavigate, modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onToggleSearch) {
                Icon(
                    if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (searching) "검색 닫기" else "검색",
                    tint = if (searching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The overflow: 보기 옵션 (sort + layout) and 새로고침, after OLO Explorer.
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "메뉴", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("보기 옵션") }, onClick = { menu = false; onViewOptions() })
                    DropdownMenuItem(text = { Text("새로고침") }, onClick = { menu = false; onRefresh() })
                }
            }
        }
    }
}

/**
 * The 보기 옵션 window in the app's card-dialog style: 보기 모드 (목록·격자·갤러리)
 * and 정렬 모드 (이름·날짜·크기·형식, with the direction under the chosen one) as
 * icon tiles, then 폴더 옵션 (폴더 먼저·숨김 파일) as checks. Every choice persists.
 */
@Composable
private fun BrowseOptionsDialog(
    scoped: Boolean,
    view: BrowseView,
    sortBy: SortBy,
    ascending: Boolean,
    foldersFirst: Boolean,
    showHidden: Boolean,
    onScope: (Boolean) -> Unit,
    onView: (BrowseView) -> Unit,
    onSort: (SortBy) -> Unit,
    onDirection: (Boolean) -> Unit,
    onFoldersFirst: (Boolean) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OloCardDialog(title = "보기 옵션", onDismiss = onDismiss) {
        OloSectionLabel("적용 범위")
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OloOptionTile("모든 폴더", !scoped, { onScope(false) }) { t ->
                Icon(Icons.Outlined.Public, null, tint = t, modifier = Modifier.size(28.dp))
            }
            OloOptionTile("이 폴더만", scoped, { onScope(true) }) { t ->
                Icon(Icons.Outlined.Folder, null, tint = t, modifier = Modifier.size(28.dp))
            }
            Box(Modifier.weight(1f)) {}
        }

        OloSectionLabel("보기 모드")
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OloOptionTile("목록", view == BrowseView.LIST, { onView(BrowseView.LIST) }) { t ->
                Icon(Icons.AutoMirrored.Filled.ViewList, null, tint = t, modifier = Modifier.size(30.dp))
            }
            OloOptionTile("격자", view == BrowseView.GRID, { onView(BrowseView.GRID) }) { t ->
                Icon(Icons.Filled.GridView, null, tint = t, modifier = Modifier.size(30.dp))
            }
            OloOptionTile("갤러리", view == BrowseView.GALLERY, { onView(BrowseView.GALLERY) }) { t ->
                Icon(Icons.Filled.PhotoLibrary, null, tint = t, modifier = Modifier.size(30.dp))
            }
            // A fourth column kept empty so three tiles sit at a natural width.
            Box(Modifier.weight(1f)) {}
        }

        OloSectionLabel("정렬 모드")
        val dirSub = if (ascending) "↑ 오름차순" else "↓ 내림차순"
        // Tapping the selected key flips the direction; tapping another switches key.
        fun pickSort(target: SortBy) { if (sortBy == target) onDirection(!ascending) else onSort(target) }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OloOptionTile("이름", sortBy == SortBy.NAME, { pickSort(SortBy.NAME) }, sub = if (sortBy == SortBy.NAME) dirSub else null) { t ->
                Icon(Icons.Filled.SortByAlpha, null, tint = t, modifier = Modifier.size(30.dp))
            }
            OloOptionTile("날짜", sortBy == SortBy.DATE, { pickSort(SortBy.DATE) }, sub = if (sortBy == SortBy.DATE) dirSub else null) { t ->
                Icon(painterResource(R.drawable.ic_sort_date), null, tint = t, modifier = Modifier.size(28.dp))
            }
            OloOptionTile("크기", sortBy == SortBy.SIZE, { pickSort(SortBy.SIZE) }, sub = if (sortBy == SortBy.SIZE) dirSub else null) { t ->
                Icon(painterResource(R.drawable.ic_sort_size), null, tint = t, modifier = Modifier.size(28.dp))
            }
            OloOptionTile("형식", sortBy == SortBy.FORMAT, { pickSort(SortBy.FORMAT) }, sub = if (sortBy == SortBy.FORMAT) dirSub else null) { t ->
                Icon(painterResource(R.drawable.ic_sort_format), null, tint = t, modifier = Modifier.size(28.dp))
            }
        }

        OloSectionLabel("폴더 옵션")
        OloCheckRow("폴더 먼저", foldersFirst, onFoldersFirst)
        OloCheckRow("숨김 파일 보기", showHidden, onShowHidden)
    }
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
) {
    val c = OloTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MediaThumbnail(kind = kind, folder = folder, name = name, folderName = folderName, sidecar = sidecar, enabled = enabled, nfoArt = nfoArt, posterName = posterName)
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
    }
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
