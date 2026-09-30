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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    @androidx.annotation.DrawableRes rootIcon: Int = R.drawable.ic_tile_server,
) {
    val c = OloTheme.colors
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // The list/gallery choice is the person's, kept across folders and screens.
    var gallery by remember { mutableStateOf(prefs.remoteGallery()) }
    // The sort, also kept, and shared with the local browser.
    var sortBy by remember { mutableStateOf(runCatching { SortBy.valueOf(prefs.browseSortBy().uppercase()) }.getOrDefault(SortBy.NAME)) }
    var sortAsc by remember { mutableStateOf(prefs.browseSortAsc()) }
    // Whether the 보기 옵션 sheet is open.
    var showOptions by remember { mutableStateOf(false) }
    // The file a long-press opened the detail sheet on, or null when it is closed.
    var detail by remember { mutableStateOf<RemoteEntry?>(null) }
    // The current folder's own name, so a bare "E05.mkv" can borrow its series from
    // the folder ("Dark (2017)") when TMDB is queried.
    val folderName = path.trimEnd('/').substringAfterLast('/').ifBlank { rootLabel }
    val postersOn = prefs.postersEnabled()

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
        return { SidecarResolver.nfoArt(context, entries, entry.name, build) }
    }

    val visible = entries.filter { it.isDirectory || looksMedia(it.name) }
    val filtered = if (searching && query.isNotBlank()) {
        visible.filter { it.name.contains(query, ignoreCase = true) }
    } else {
        visible
    }
    val shown = BrowseSort.sort(filtered, sortBy, sortAsc)
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
            if (gallery) {
                // Folders always read as list rows (a gallery is for the films in a
                // leaf folder); the files below become a 2:3 poster grid, three wide.
                val folderRows = shown.filter { it.isDirectory }
                val fileRows = shown.filter { !it.isDirectory }
                itemsIndexed(folderRows, key = { _, e -> e.path }) { _, entry ->
                    BrowseRow(
                        kind = FileKind.FOLDER,
                        folder = true,
                        name = entry.name,
                        folderName = folderName,
                        subtitle = entrySubtitle(entry),
                        sidecar = null,
                        enabled = postersOn,
                        onClick = { onEntry(entry) },
                    )
                    CpDivider()
                }
                val lines = fileRows.chunked(3)
                items(lines.size, key = { it }) { line ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        lines[line].forEach { entry ->
                            PosterCell(
                                entry = entry,
                                folderName = folderName,
                                subtitle = entrySubtitle(entry),
                                sidecar = sidecarFor(entry),
                                enabled = postersOn,
                                onClick = { onEntry(entry) },
                                modifier = Modifier.weight(1f),
                                onLongClick = { detail = entry },
                                nfoArt = nfoArtFor(entry),
                            )
                        }
                        repeat(3 - lines[line].size) { Box(Modifier.weight(1f)) {} }
                    }
                }
            } else {
                itemsIndexed(shown, key = { _, e -> e.path }) { index, entry ->
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
                    if (index < shown.lastIndex) CpDivider()
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
        BrowseOptionsSheet(
            sortBy = sortBy,
            ascending = sortAsc,
            gallery = gallery,
            onSort = { sortBy = it; prefs.setBrowseSortBy(it.name.lowercase()) },
            onDirection = { sortAsc = it; prefs.setBrowseSortAsc(it) },
            onView = { gallery = it; prefs.setRemoteGallery(it) },
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
 * The 보기 옵션 sheet: how the list is ordered (이름·날짜·크기, 오름/내림) and its
 * layout (목록·갤러리). A bottom sheet in the OLO manner; the choices persist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseOptionsSheet(
    sortBy: SortBy,
    ascending: Boolean,
    gallery: Boolean,
    onSort: (SortBy) -> Unit,
    onDirection: (Boolean) -> Unit,
    onView: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = OloTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            OptionLabel("정렬 기준")
            OptionChips(
                options = listOf(SortBy.NAME to "이름", SortBy.DATE to "날짜", SortBy.SIZE to "크기"),
                selected = sortBy,
                onSelect = onSort,
            )
            OptionLabel("정렬 방향")
            OptionChips(
                options = listOf(true to "오름차순 ↑", false to "내림차순 ↓"),
                selected = ascending,
                onSelect = onDirection,
            )
            OptionLabel("레이아웃")
            OptionChips(
                options = listOf(false to "목록", true to "갤러리"),
                selected = gallery,
                onSelect = onView,
            )
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text,
        color = OloTheme.colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun <T> OptionChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = OloTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.accent else c.progressTrack)
                    .clickable { onSelect(value) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Color.White else c.text,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
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
        MediaThumbnail(kind = kind, folder = folder, name = name, folderName = folderName, sidecar = sidecar, enabled = enabled, nfoArt = nfoArt)
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
