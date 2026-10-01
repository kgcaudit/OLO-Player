package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.data.SavedItem
import org.olo.player.data.SavedServer
import org.olo.player.data.SavedServerStore
import org.olo.player.ui.LocalMedia
import org.olo.player.ui.OpenUrlDialog
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.theme.OloColors
import org.olo.player.ui.theme.OloTheme

/**
 * The app shell (no media open): one browse space that merges 로컬 and 네트워크 into
 * a single 위치 list, with 재생목록 and 설정 reached from the top action bar rather
 * than a bottom tab. Opening a location hands off to the shared browsers; opening a
 * media item raises the player over this through [PlayerViewModel].
 *
 * Responsive to the two Fold specs: a single scrolling column on the cover screen,
 * a source rail beside a content pane on the main screen.
 */
private enum class HomeNav { HOME, STORAGE, FTP, SFTP, SMB, WEBDAV, PICKER, PLAYLIST, SETTINGS, SEARCH }

private fun navFor(protocol: String) = when (protocol) {
    SavedServer.PROTO_SFTP -> HomeNav.SFTP
    SavedServer.PROTO_SMB -> HomeNav.SMB
    SavedServer.PROTO_WEBDAV -> HomeNav.WEBDAV
    else -> HomeNav.FTP
}

@Composable
fun OloHome(model: PlayerViewModel) {
    val context = LocalContext.current
    val store = remember { SavedServerStore(context) }
    var servers by remember { mutableStateOf(store.list()) }
    val onSaved: () -> Unit = { servers = store.list() }

    // A back stack of destinations, not a single state, so 뒤로가기 returns to the
    // previous screen (검색·소스 선택 등) rather than collapsing straight to 홈.
    val navStack = rememberSaveable(
        saver = listSaver(
            save = { it.map(HomeNav::name) },
            restore = { it.map(HomeNav::valueOf).toMutableStateList() },
        ),
    ) { mutableStateListOf(HomeNav.HOME) }
    val nav = navStack.last()
    var showUrl by rememberSaveable { mutableStateOf(false) }
    // The saved server a browser opens with, and whether to connect at once.
    var preset by remember { mutableStateOf<SavedServer?>(null) }
    var presetAuto by remember { mutableStateOf(false) }

    // Push a destination / pop one; at 홈 (stack of one) the system handles back.
    val go = { dest: HomeNav -> navStack.add(dest); Unit }
    val back: () -> Unit = { if (navStack.size > 1) navStack.removeAt(navStack.lastIndex) }

    when (nav) {
        HomeNav.STORAGE -> {
            BackHandler(onBack = back)
            LocalMedia(onOpenMedia = { model.openLocalMedia(it) }, onBack = back)
            return
        }
        HomeNav.FTP -> {
            BackHandler(onBack = back)
            org.olo.player.ui.FtpBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = back,
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_FTP }?.toFtp(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
            )
            return
        }
        HomeNav.SFTP -> {
            BackHandler(onBack = back)
            org.olo.player.ui.SftpBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = back,
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_SFTP }?.toSftp(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
            )
            return
        }
        HomeNav.SMB -> {
            BackHandler(onBack = back)
            org.olo.player.ui.SmbBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = back,
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_SMB }?.toSmb(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
            )
            return
        }
        HomeNav.WEBDAV -> {
            BackHandler(onBack = back)
            org.olo.player.ui.WebDavBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = back,
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_WEBDAV }?.toWebDav(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
            )
            return
        }
        HomeNav.PICKER -> {
            ProtocolPicker(onBack = back, onProtocol = { p -> preset = null; presetAuto = false; go(navFor(p)) })
            return
        }
        HomeNav.PLAYLIST -> {
            PlaylistTab(model, onBack = back)
            return
        }
        HomeNav.SETTINGS -> {
            SettingsTab(model, onBack = back)
            return
        }
        HomeNav.SEARCH -> {
            SearchScreen(
                servers = servers,
                saved = (model.favorites() + model.recents() + model.urls() + model.servers()).distinctBy { it.key },
                onBack = back,
                onOpenSaved = { model.openSaved(it) },
                onServer = { s -> preset = s; presetAuto = true; go(navFor(s.protocol)) },
            )
            return
        }
        HomeNav.HOME -> Unit
    }

    HomeContent(
        servers = servers,
        favorites = model.favorites(),
        recents = model.recents(),
        onSearch = { go(HomeNav.SEARCH) },
        onPlaylist = { go(HomeNav.PLAYLIST) },
        onSettings = { go(HomeNav.SETTINGS) },
        onStorage = { go(HomeNav.STORAGE) },
        onUrl = { showUrl = true },
        onServer = { s -> preset = s; presetAuto = true; go(navFor(s.protocol)) },
        onEditServer = { s -> preset = s; presetAuto = false; go(navFor(s.protocol)) },
        onDeleteServer = { s -> store.remove(s.id); servers = store.list() },
        onAddServer = { preset = null; presetAuto = false; go(HomeNav.PICKER) },
        onOpenSaved = { model.openSaved(it) },
    )

    if (showUrl) {
        OpenUrlDialog(
            onOpen = { showUrl = false; model.openNetworkUrl(it) },
            onDismiss = { showUrl = false },
        )
    }
}

@Composable
internal fun HomeContent(
    servers: List<SavedServer>,
    favorites: List<SavedItem>,
    recents: List<SavedItem>,
    onSearch: () -> Unit,
    onPlaylist: () -> Unit,
    onSettings: () -> Unit,
    onStorage: () -> Unit,
    onUrl: () -> Unit,
    onServer: (SavedServer) -> Unit,
    onEditServer: (SavedServer) -> Unit,
    onDeleteServer: (SavedServer) -> Unit,
    onAddServer: () -> Unit,
    onOpenSaved: (SavedItem) -> Unit,
) {
    val c = OloTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val wide = maxWidth >= 700.dp
        if (wide) {
            Column(Modifier.fillMaxSize()) {
                ActionBar(c, onSearch, onPlaylist, onSettings)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.width(322.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        SectionLabel("위치", c)
                        Locations(servers, onStorage, onUrl, onServer, onEditServer, onDeleteServer, onAddServer, c)
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.divider))
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Shelves(favorites, recents, onOpenSaved, c)
                        if (favorites.isEmpty() && recents.isEmpty()) EmptyHint(c)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ActionBar(c, onSearch, onPlaylist, onSettings)
                Shelves(favorites, recents, onOpenSaved, c)
                SectionLabel("위치", c)
                Locations(servers, onStorage, onUrl, onServer, onEditServer, onDeleteServer, onAddServer, c)
                Spacer(Modifier.heightIn(min = 16.dp))
            }
        }
    }
}

@Composable
private fun ActionBar(c: OloColors, onSearch: () -> Unit, onPlaylist: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        ActionIcon(Icons.Outlined.Search, "검색", onSearch, c)
        ActionIcon(Icons.AutoMirrored.Outlined.PlaylistPlay, "재생목록", onPlaylist, c)
        ActionIcon(Icons.Outlined.Settings, "설정", onSettings, c)
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, cd: String, onClick: () -> Unit, c: OloColors) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = cd, tint = c.text, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun Shelves(favorites: List<SavedItem>, recents: List<SavedItem>, onOpen: (SavedItem) -> Unit, c: OloColors) {
    if (favorites.isNotEmpty()) {
        SectionLabel("즐겨찾기", c)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            favorites.take(12).forEach { PosterCard(it, onOpen, c) }
        }
    }
    if (recents.isNotEmpty()) {
        SectionLabel("최근 재생", c)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            recents.take(12).forEach { WideCard(it, onOpen, c) }
        }
    }
}

@Composable
private fun PosterCard(item: SavedItem, onOpen: (SavedItem) -> Unit, c: OloColors) {
    Column(Modifier.width(120.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(item) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(c.tileVideo, c.tileVideo.copy(alpha = 0.72f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Movie, null, tint = Color.White.copy(alpha = 0.28f), modifier = Modifier.size(38.dp)) }
        Text(item.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun WideCard(item: SavedItem, onOpen: (SavedItem) -> Unit, c: OloColors) {
    Column(Modifier.width(224.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(item) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(c.tileVideo, c.tileVideo.copy(alpha = 0.72f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Movie, null, tint = Color.White.copy(alpha = 0.28f), modifier = Modifier.size(34.dp)) }
        Text(item.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(item.source, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Locations(
    servers: List<SavedServer>,
    onStorage: () -> Unit,
    onUrl: () -> Unit,
    onServer: (SavedServer) -> Unit,
    onEditServer: (SavedServer) -> Unit,
    onDeleteServer: (SavedServer) -> Unit,
    onAddServer: () -> Unit,
    c: OloColors,
) {
    LocationRow("이 기기", "내부 저장소 · SD 카드", Icons.Outlined.Smartphone, c.tileFolder, onStorage, c)
    LocationRow("URL 열기", "http(s):// 또는 ftp:// 스트리밍", Icons.Outlined.Link, c.accent, onUrl, c)
    for (s in servers) {
        val icon = when (s.protocol) {
            SavedServer.PROTO_SMB -> Icons.Outlined.FolderShared
            SavedServer.PROTO_WEBDAV -> Icons.Outlined.CloudQueue
            else -> Icons.Outlined.Dns
        }
        LocationRow(
            title = s.label,
            subtitle = serverSubtitle(s),
            icon = icon,
            tile = c.tileOther,
            onClick = { onServer(s) },
            c = c,
            trailing = { ServerMenu(onEdit = { onEditServer(s) }, onDelete = { onDeleteServer(s) }, c = c) },
        )
    }
    AddServerRow(onAddServer, c)
}

@Composable
private fun LocationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tile: Color,
    onClick: () -> Unit,
    c: OloColors,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 66.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFFF4F1EC), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.outline, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ServerMenu(onEdit: () -> Unit, onDelete: () -> Unit, c: OloColors) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.MoreVert, null, tint = c.text, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("편집") }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text("삭제") }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
private fun AddServerRow(onClick: () -> Unit, c: OloColors) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 58.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.progressTrack), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, null, tint = c.accent, modifier = Modifier.size(24.dp))
        }
        Text("서버 추가", color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Global search across everything the person has already touched -- saved servers,
 * favourites, recent plays, pasted URLs and visited servers -- matched by name or
 * host. It searches remembered items, not a full device/network crawl, so it is
 * instant and offline; opening a hit reconnects a server or reopens the media.
 */
@Composable
private fun SearchScreen(
    servers: List<SavedServer>,
    saved: List<SavedItem>,
    onBack: () -> Unit,
    onOpenSaved: (SavedItem) -> Unit,
    onServer: (SavedServer) -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = OloTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val srv = if (q.isBlank()) emptyList() else servers.filter { it.label.contains(q, true) || it.host.contains(q, true) }
    val items = if (q.isBlank()) emptyList() else saved.filter { it.name.contains(q, true) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", tint = c.text, modifier = Modifier.size(24.dp))
            }
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.progressTrack).heightIn(min = 44.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("제목 · 서버 이름 검색", color = c.muted, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (q.isBlank()) {
                item { Hint("즐겨찾기 · 최근 · 서버를 이름으로 찾습니다.", c) }
            } else if (srv.isEmpty() && items.isEmpty()) {
                item { Hint("검색 결과가 없습니다.", c) }
            } else {
                if (srv.isNotEmpty()) {
                    item { SectionLabel("서버", c) }
                    items(srv, key = { "s${it.id}" }) { s ->
                        val icon = when (s.protocol) {
                            SavedServer.PROTO_SMB -> Icons.Outlined.FolderShared
                            SavedServer.PROTO_WEBDAV -> Icons.Outlined.CloudQueue
                            else -> Icons.Outlined.Dns
                        }
                        LocationRow(s.label, serverSubtitle(s), icon, c.tileOther, { onServer(s) }, c)
                    }
                }
                if (items.isNotEmpty()) {
                    item { SectionLabel("재생목록", c) }
                    items(items, key = { "i${it.key}" }) { item ->
                        LocationRow(item.name, item.source, Icons.Outlined.Movie, c.tileVideo, { onOpenSaved(item) }, c)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, c: OloColors) {
    Text(text, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
}

/** A one-line summary of where a saved server points: 프로토콜 · 계정@호스트[/공유]. */
private fun serverSubtitle(s: SavedServer): String {
    val proto = s.protocol.uppercase()
    val account = s.user.ifBlank { "anonymous" }
    val hostPort = if (s.protocol == SavedServer.PROTO_SMB && s.share.isNotBlank()) "${s.host}/${s.share}" else s.host
    return "$proto · $account@$hostPort"
}

@Composable
private fun SectionLabel(text: String, c: OloColors) {
    Text(
        text,
        color = c.accent,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun EmptyHint(c: OloColors) {
    Text(
        "즐겨찾기·최근 재생이 여기에 모입니다.",
        color = c.muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
    )
}
