package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.olo.player.art.Posters
import org.olo.player.data.PlaylistStore
import org.olo.player.data.PosterOverride
import org.olo.player.data.SavedItem
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.browseColumns
import org.olo.player.ui.looksVideo
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.components.CpIconButton
import org.olo.player.ui.components.CpRow
import org.olo.player.ui.components.CpTile
import org.olo.player.ui.theme.OloTheme

/**
 * 재생목록 탭: the four shelves that gather sources across local and network --
 * 즐겨찾기 · 직접 URL · 최근 방문 · 최근 재생. Recents, URLs and visited servers fill
 * themselves as media and servers are opened; favourites are toggled from any
 * row's overflow menu. Each shelf opens a list; a tap reopens the source.
 */
private enum class PlaylistShelf(
    val title: String,
    val icon: ImageVector,
    val shelf: PlaylistStore.Shelf,
    val subtitle: String?,
    val empty: String,
) {
    FAVORITES("즐겨찾기", Icons.Outlined.StarOutline, PlaylistStore.Shelf.FAVORITES, null, "별표한 항목이 여기에 모입니다."),
    // 최근 방문은 서버와 직접 URL을 함께 담는다(직접 URL 셸프를 따로 두지 않는다).
    VISITED("최근 방문", Icons.Outlined.History, PlaylistStore.Shelf.SERVERS, "서버 · 직접 URL", "최근 연 서버·주소가 여기에 모입니다."),
    RECENT("최근 재생", Icons.Outlined.Schedule, PlaylistStore.Shelf.RECENTS, "이어보기 지점 기억", "최근 재생한 항목이 여기에 모입니다."),
}

@Composable
fun PlaylistTab(model: PlayerViewModel, onBack: () -> Unit = {}) {
    var shelf by rememberSaveable { mutableStateOf<PlaylistShelf?>(null) }

    shelf?.let {
        ShelfDetail(model, it, onBack = { shelf = null })
        return
    }

    BackHandler(onBack = onBack)
    val c = OloTheme.colors
    Column(Modifier.fillMaxSize()) {
        CpHeader("재생목록", onBack = onBack)
        for (s in PlaylistShelf.entries) {
            CpRow(
                title = s.title,
                subtitle = s.subtitle,
                leading = { CpTile(s.icon, c.accent) },
                onClick = { shelf = s },
            )
        }
    }
}

@Composable
private fun ShelfDetail(model: PlayerViewModel, shelf: PlaylistShelf, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val c = OloTheme.colors
    // Read the shelf once on entry into an observable list; edits below mutate it
    // in step with the store, so the list reflows without a full re-query.
    val items = remember(shelf) { mutableStateListOf<SavedItem>().apply { addAll(read(model, shelf)) } }

    Column(Modifier.fillMaxSize()) {
        CpHeader(shelf.title, onBack = onBack)
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(shelf.empty, color = c.muted, fontSize = 14.sp)
            }
        } else if (shelf == PlaylistShelf.FAVORITES || shelf == PlaylistShelf.RECENT) {
            // 즐겨찾기·최근 재생은 격자(포스터) 보기 -- 브라우즈 격자와 같은 결로, 영상은 포스터로 한눈에.
            PosterGrid(model, shelf, items)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.key + it.savedAt }) { item ->
                    SavedRow(model, shelf, item, items)
                }
            }
        }
    }
}

/** 즐겨찾기·최근 재생 격자: SavedItem들을 2:3 포스터 카드로. 열 수는 브라우즈 격자와 같은 규칙. */
@Composable
private fun PosterGrid(model: PlayerViewModel, shelf: PlaylistShelf, items: SnapshotStateList<SavedItem>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = browseColumns(maxWidth.value, gallery = false)
        val lines = items.chunked(cols)
        LazyColumn(Modifier.fillMaxSize()) {
            items(lines.size, key = { "grid$it" }) { line ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    lines[line].forEach { item -> PosterCard(model, shelf, item, items, Modifier.weight(1f)) }
                    repeat(cols - lines[line].size) { Box(Modifier.weight(1f)) {} }
                }
            }
        }
    }
}

@Composable
private fun PosterCard(
    model: PlayerViewModel,
    shelf: PlaylistShelf,
    item: SavedItem,
    backing: SnapshotStateList<SavedItem>,
    modifier: Modifier,
) {
    val c = OloTheme.colors
    val context = LocalContext.current
    val isVideo = remember(item.key) { looksVideo(item.name) }
    var art by remember(item.key) { mutableStateOf<Any?>(null) }
    LaunchedEffect(item.key) {
        val override = PosterOverride.get(context, item.uri)
        art = override ?: if (isVideo) runCatching { Posters.get(context).posterUrl(item.name, null) }.getOrNull() else null
    }
    var menu by remember { mutableStateOf(false) }
    val fav = remember(item.key) { mutableStateOf(model.isFavorite(item.key)) }
    val resume = if (shelf == PlaylistShelf.RECENT) model.savedPosition(item.key) else 0L
    val sub = buildString {
        append(item.source)
        if (resume > 0) append(" · 이어보기 ${formatClock(resume)}")
    }
    Column(modifier.clickable { model.openSaved(item) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(if (isVideo) c.tileVideo else c.tileOther, (if (isVideo) c.tileVideo else c.tileOther).copy(alpha = 0.72f))))) {
            if (art != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(art).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
                    Icon(if (isVideo) Icons.Outlined.Movie else Icons.Outlined.MusicNote, null, tint = Color.White.copy(alpha = 0.30f), modifier = Modifier.size(44.dp))
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x80000000)).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.MoreVert, "더보기", tint = Color.White, modifier = Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (shelf == PlaylistShelf.FAVORITES) {
                        // 즐겨찾기에선 "즐겨찾기 제거"="삭제"로 같은 동작이라 "삭제" 하나만 둔다.
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            leadingIcon = { Icon(Icons.Outlined.Star, null) },
                            onClick = { menu = false; model.toggleFavorite(item); backing.remove(item) },
                        )
                    } else {
                        // 최근 재생: 즐겨찾기 토글(행은 남김)과, 최근에서 지우기(삭제)는 서로 다르다.
                        DropdownMenuItem(
                            text = { Text(if (fav.value) "즐겨찾기 제거" else "즐겨찾기 추가") },
                            leadingIcon = { Icon(if (fav.value) Icons.Outlined.Star else Icons.Outlined.StarOutline, null) },
                            onClick = { menu = false; fav.value = model.toggleFavorite(item) },
                        )
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            onClick = { menu = false; model.removeSaved(shelf.shelf, item.key); backing.remove(item) },
                        )
                    }
                }
            }
        }
        Text(item.name, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, start = 2.dp))
        Text(sub, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun SavedRow(
    model: PlayerViewModel,
    shelf: PlaylistShelf,
    item: SavedItem,
    backing: SnapshotStateList<SavedItem>,
) {
    val c = OloTheme.colors
    var menu by remember { mutableStateOf(false) }
    val fav = remember(item.key) { mutableStateOf(model.isFavorite(item.key)) }
    val resume = if (shelf == PlaylistShelf.RECENT) model.savedPosition(item.key) else 0L
    val sub = buildString {
        append(item.source)
        if (resume > 0) append(" · 이어보기 ${formatClock(resume)}")
    }
    CpRow(
        title = item.name,
        subtitle = sub,
        leading = { CpTile(sourceIcon(item.source), sourceColor(item.source, c)) },
        onClick = { model.openSaved(item) },
        trailing = {
            Box {
                CpIconButton(Icons.Outlined.MoreVert, onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (fav.value) "즐겨찾기 제거" else "즐겨찾기 추가") },
                        leadingIcon = { Icon(if (fav.value) Icons.Outlined.Star else Icons.Outlined.StarOutline, null) },
                        onClick = {
                            menu = false
                            val nowFav = model.toggleFavorite(item)
                            fav.value = nowFav
                            // Removing a favourite from within the favourites shelf drops the row.
                            if (!nowFav && shelf == PlaylistShelf.FAVORITES) backing.remove(item)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("삭제") },
                        onClick = {
                            menu = false
                            // 최근 방문은 서버·URL이 섞여 있어, 둘 다에서 지운다(없는 쪽은 무시).
                            if (shelf == PlaylistShelf.VISITED) {
                                model.removeSaved(PlaylistStore.Shelf.SERVERS, item.key)
                                model.removeSaved(PlaylistStore.Shelf.URLS, item.key)
                            } else {
                                model.removeSaved(shelf.shelf, item.key)
                            }
                            backing.remove(item)
                        },
                    )
                }
            }
        },
    )
}

private fun read(model: PlayerViewModel, shelf: PlaylistShelf): List<SavedItem> = when (shelf) {
    PlaylistShelf.FAVORITES -> model.favorites()
    // 최근 방문 = 서버 + 직접 URL을 합쳐, 최근(savedAt) 순으로. 키 중복은 한 번만.
    PlaylistShelf.VISITED -> (model.servers() + model.urls())
        .distinctBy { it.key }
        .sortedByDescending { it.savedAt }
    PlaylistShelf.RECENT -> model.recents()
}

private fun sourceIcon(source: String): ImageVector = when (source) {
    "기기" -> Icons.Outlined.Smartphone
    "FTP" -> Icons.Outlined.Dns
    else -> Icons.Outlined.Link
}

private fun sourceColor(source: String, c: org.olo.player.ui.theme.OloColors) = when (source) {
    "기기" -> c.tileFolder
    "FTP" -> c.tileOther
    else -> c.tileVideo
}

private fun formatClock(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
