package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.data.PlaylistStore
import org.olo.player.data.SavedItem
import org.olo.player.ui.PlayerViewModel
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
    URLS("직접 URL", Icons.Outlined.Link, PlaylistStore.Shelf.URLS, null, "입력한 주소가 여기에 모입니다."),
    VISITED("최근 방문", Icons.Outlined.History, PlaylistStore.Shelf.SERVERS, null, "최근 연 서버가 여기에 모입니다."),
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
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.key + it.savedAt }) { item ->
                    SavedRow(model, shelf, item, items)
                }
            }
        }
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
                            model.removeSaved(shelf.shelf, item.key)
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
    PlaylistShelf.URLS -> model.urls()
    PlaylistShelf.VISITED -> model.servers()
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
