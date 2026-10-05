package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import org.olo.player.data.PlaylistStore
import org.olo.player.data.PosterOverride
import org.olo.player.data.SavedItem
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.browseColumns
import org.olo.player.ui.looksVideo
import org.olo.player.ui.rememberRemoteArt
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
    val empty: String,
) {
    FAVORITES("즐겨찾기", Icons.Outlined.StarOutline, PlaylistStore.Shelf.FAVORITES, "별표한 항목이 여기에 모입니다."),
    // 최근 방문은 서버와 직접 URL을 함께 담는다(직접 URL 셸프를 따로 두지 않는다).
    VISITED("최근 방문", Icons.Outlined.History, PlaylistStore.Shelf.SERVERS, "최근 연 서버·주소가 여기에 모입니다."),
    RECENT("최근 재생", Icons.Outlined.Schedule, PlaylistStore.Shelf.RECENTS, "최근 재생한 항목이 여기에 모입니다."),
}

// 접속 가능한 프로토콜(경로 없는 호스트 항목이면 재생이 아니라 '접속'으로 간다).
private val CONNECTABLE_SCHEMES = setOf("ftp", "sftp", "smb", "webdav")

/** 보관함 항목이 '파일'이 아니라 접속할 '서버 호스트'인지 -- 접속 가능한 스킴이고 경로(파일)가
 *  없는 주소면 서버로 본다(예: "ftp://host"). 이런 항목 탭은 플레이어가 아니라 접속으로 가야 한다. */
private fun isServerHost(item: SavedItem): Boolean {
    val uri = android.net.Uri.parse(item.uri)
    val noPath = uri.path.isNullOrBlank() || uri.path == "/"
    return uri.scheme?.lowercase() in CONNECTABLE_SCHEMES && noPath
}

/** 항목 탭 공통 처리: 서버 호스트면 접속(onConnectServer), 그 외(파일·직접 URL)는 재생. */
private fun openItem(model: PlayerViewModel, item: SavedItem, onConnectServer: (SavedItem) -> Unit) {
    if (isServerHost(item)) onConnectServer(item) else model.openSaved(item)
}

/** 미디어 URI의 상위 폴더 이름(예: ".../Movies/영화.mkv" → "Movies"). 포스터 질의 때 파일명이
 *  얇으면 폴더명으로 제목을 보강하도록 -- 브라우즈 격자의 folderName과 같은 역할. 없으면 null. */
private fun parentFolderName(uri: String): String? {
    val path = android.net.Uri.parse(uri).path?.trimEnd('/') ?: return null
    val parent = path.substringBeforeLast('/', "").substringAfterLast('/')
    return parent.ifBlank { null }
}

@Composable
fun PlaylistTab(
    model: PlayerViewModel,
    onBack: () -> Unit = {},
    // 서버 호스트 항목 탭 -> 접속·탐색 플로우로(플레이어 아님). OloHome이 저장된 자격증명을
    // 호스트로 찾아 접속한다. 비우면 종전처럼 재생으로만 동작한다.
    onConnectServer: (SavedItem) -> Unit = {},
) {
    var shelf by rememberSaveable { mutableStateOf<PlaylistShelf?>(null) }

    shelf?.let {
        ShelfDetail(model, it, onBack = { shelf = null }, onConnectServer = onConnectServer)
        return
    }

    BackHandler(onBack = onBack)
    // 한 화면에 세 묶음을 바로 펼친다: 셸프 목록만 보여 두 번 눌러야 내용이 나오던 것을, 각
    // 섹션 헤더(개수 + '전체 >') 아래 가로 레일(즐겨찾기·최근 재생)과 컴팩트 행(최근 방문)으로
    // 내용을 즉시 보여 1탭 재생되게 한다. 백킹 리스트는 진입 시 한 번 읽고, 레일의 ⋮에서
    // 제거·즐겨찾기 토글이 즉시 반영된다. '전체 >'는 기존 상세(격자/목록)를 더보기로 연다.
    val favBacking = remember { mutableStateListOf<SavedItem>().apply { addAll(read(model, PlaylistShelf.FAVORITES)) } }
    val recentBacking = remember { mutableStateListOf<SavedItem>().apply { addAll(read(model, PlaylistShelf.RECENT)) } }
    val visitedBacking = remember { mutableStateListOf<SavedItem>().apply { addAll(read(model, PlaylistShelf.VISITED)) } }
    val backingOf: (PlaylistShelf) -> SnapshotStateList<SavedItem> = {
        when (it) {
            PlaylistShelf.FAVORITES -> favBacking
            PlaylistShelf.RECENT -> recentBacking
            PlaylistShelf.VISITED -> visitedBacking
        }
    }
    Column(Modifier.fillMaxSize()) {
        CpHeader("보관함", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            for (s in PlaylistShelf.entries) {
                LandingSection(model, s, backingOf(s), onSeeAll = { shelf = s }, onConnectServer = onConnectServer)
            }
        }
    }
}

/** 보관함 한 화면의 섹션 하나: 헤더(아이콘·제목·개수·'전체 >') + 내용(포스터 가로 레일 /
 *  최근 방문은 컴팩트 행). 비었으면 안내 한 줄. 내용 관리(삭제·즐겨찾기)는 레일에서 바로 되고,
 *  전체는 '전체 >'로 기존 상세 화면에서 본다. */
@Composable
private fun LandingSection(
    model: PlayerViewModel,
    shelf: PlaylistShelf,
    shelfItems: SnapshotStateList<SavedItem>,
    onSeeAll: () -> Unit,
    onConnectServer: (SavedItem) -> Unit,
) {
    val c = OloTheme.colors
    // 해석된 포스터를 이 섹션이 사는 동안 유지한다(스크롤로 레일 항목이 폐기됐다 다시 와도
    // 포스터가 바로 보이게 -- 브라우즈의 remoteArtCache와 같은 역할).
    val artCache = remember { mutableStateMapOf<String, Any?>() }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(shelf.icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(shelf.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text("${shelfItems.size}", color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (shelfItems.isNotEmpty()) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("전체", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.ChevronRight, null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (shelfItems.isEmpty()) {
        Text(shelf.empty, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    } else if (shelf == PlaylistShelf.VISITED) {
        // 서버·URL은 포스터가 없어 컴팩트 행으로(앞쪽 4개). 전체는 '전체 >'.
        shelfItems.take(4).forEach { item -> SavedRow(model, shelf, item, shelfItems, onConnectServer) }
    } else {
        // 즐겨찾기·최근 재생: 2:3 포스터 가로 레일(앞쪽 12개).
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shelfItems.take(12), key = { it.key + it.savedAt }) { item ->
                PosterCard(model, shelf, item, shelfItems, Modifier.width(104.dp), onConnectServer, artCache)
            }
        }
    }
}

@Composable
private fun ShelfDetail(model: PlayerViewModel, shelf: PlaylistShelf, onBack: () -> Unit, onConnectServer: (SavedItem) -> Unit) {
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
            PosterGrid(model, shelf, items, onConnectServer)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.key + it.savedAt }) { item ->
                    SavedRow(model, shelf, item, items, onConnectServer)
                }
            }
        }
    }
}

/** 즐겨찾기·최근 재생 격자: SavedItem들을 2:3 포스터 카드로. 열 수는 브라우즈 격자와 같은 규칙. */
@Composable
private fun PosterGrid(model: PlayerViewModel, shelf: PlaylistShelf, items: SnapshotStateList<SavedItem>, onConnectServer: (SavedItem) -> Unit) {
    val artCache = remember { mutableStateMapOf<String, Any?>() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = browseColumns(maxWidth.value, gallery = false)
        val lines = items.chunked(cols)
        LazyColumn(Modifier.fillMaxSize()) {
            items(lines.size, key = { "grid$it" }) { line ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    lines[line].forEach { item -> PosterCard(model, shelf, item, items, Modifier.weight(1f), onConnectServer, artCache) }
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
    onConnectServer: (SavedItem) -> Unit,
    artCache: SnapshotStateMap<String, Any?>,
) {
    val c = OloTheme.colors
    val context = LocalContext.current
    val isVideo = remember(item.key) { looksVideo(item.name) }
    // 브라우즈 격자(PosterCell)와 동일한 포스터 해석 파이프라인으로 통일한다 -- 종전엔 최근
    // 재생만 '파일명 1개·폴더명 없음'으로 질의가 약해, 브라우즈에선 뜨는 포스터가 여기선 빈
    // 타일이 됐다. 사용자가 고른 포스터(override, 같은 미디어 URI 키라 두 화면이 공유) >
    // TMDB(파일명 + 상위 폴더명 보강). override가 있으면 TMDB는 건드리지 않는다.
    val override = remember(item.key) { PosterOverride.get(context, item.uri) }
    val folderName = remember(item.key) { parentFolderName(item.uri) }
    val remote = rememberRemoteArt(
        queries = listOf(item.name),
        folderName = folderName,
        attempt = isVideo && override == null,
        nfoArt = null,
        cache = artCache,
        cacheKey = item.key,
    )
    val art = if (isVideo) override ?: remote else null
    var menu by remember { mutableStateOf(false) }
    val fav = remember(item.key) { mutableStateOf(model.isFavorite(item.key)) }
    val resume = if (shelf == PlaylistShelf.RECENT) model.savedPosition(item.key) else 0L
    val sub = buildString {
        append(item.source)
        if (resume > 0) append(" · 이어보기 ${formatClock(resume)}")
    }
    Column(modifier.clickable { openItem(model, item, onConnectServer) }) {
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
    onConnectServer: (SavedItem) -> Unit,
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
        onClick = { openItem(model, item, onConnectServer) },
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
