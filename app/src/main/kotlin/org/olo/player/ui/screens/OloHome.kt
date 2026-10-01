package org.olo.player.ui.screens

import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import org.olo.player.data.AppPreferences
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

// The source the app opens to, resolved from the remembered last choice: a saved
// server when it still exists, else 이 기기.
private data class SourceInit(val nav: HomeNav, val server: SavedServer?, val auto: Boolean)

private fun resolveLastSource(last: String?, servers: List<SavedServer>): SourceInit {
    if (last != null && last.startsWith("server:")) {
        val id = last.removePrefix("server:")
        servers.firstOrNull { it.id == id }?.let { return SourceInit(navFor(it.protocol), it, true) }
    }
    return SourceInit(HomeNav.STORAGE, null, false)
}

@Composable
fun OloHome(model: PlayerViewModel) {
    val context = LocalContext.current
    val store = remember { SavedServerStore(context) }
    val prefs = remember { AppPreferences(context) }
    var servers by remember { mutableStateOf(store.list()) }
    val onSaved: () -> Unit = { servers = store.list() }

    // There is no separate 홈: the app opens straight into a source browse (the
    // last-used one), and the old 홈 (최근 재생 + 위치) returns on demand as the source
    // switcher sheet. The base source stays composed under every overlay, so coming
    // back from a film, 설정 or the switcher lands on the same folder and connection.
    val initial = remember { resolveLastSource(prefs.lastSource(), store.list()) }
    var baseNav by rememberSaveable { mutableStateOf(initial.nav) }
    var preset by remember { mutableStateOf(initial.server) }
    var presetAuto by remember { mutableStateOf(initial.auto) }
    // One overlay at a time over the base: 재생목록·설정·검색·소스 선택(PICKER).
    var overlay by rememberSaveable { mutableStateOf<HomeNav?>(null) }
    var switcherOpen by remember { mutableStateOf(false) }
    var showUrl by rememberSaveable { mutableStateOf(false) }

    fun selectLocal() {
        baseNav = HomeNav.STORAGE; preset = null; presetAuto = false
        prefs.setLastSource("local"); switcherOpen = false; overlay = null
    }
    fun selectServer(s: SavedServer, auto: Boolean) {
        baseNav = navFor(s.protocol); preset = s; presetAuto = auto
        prefs.setLastSource("server:${s.id}"); switcherOpen = false; overlay = null
    }

    // System back at a source root leaves the app (there is no 홈 to fall back to);
    // a browser deeper in folders handles its own up-navigation first. 실수로 한 번에
    // 나가지 않도록 2단계로: 첫 뒤로가기는 토스트만, 2초 안에 다시 누르면 종료한다.
    val activity = context as? android.app.Activity
    var lastBackAt by remember { mutableStateOf(0L) }
    val onBaseBack: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000L) {
            activity?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(context, "한 번 더 누르면 종료됩니다", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val openSwitcher: () -> Unit = { switcherOpen = true }
    val toSearch: () -> Unit = { overlay = HomeNav.SEARCH }
    val toPlaylist: () -> Unit = { overlay = HomeNav.PLAYLIST }
    val toSettings: () -> Unit = { overlay = HomeNav.SETTINGS }
    // 즐겨찾기: 모델이 저장소를 쥐고 있으므로 여부·토글을 여기서 각 브라우저에 넘긴다.
    // (브라우저는 소스의 URI·키를 아므로 SavedItem을 만들어 되돌려준다.)
    val onIsFavorite: (String) -> Boolean = { model.isFavorite(it) }
    val onFavorite: (SavedItem) -> Unit = { model.toggleFavorite(it) }
    // 서버 미접속(등록·접속 중) 팝업 뒤에 흐리게 깔리는 위치 목록 -- 소스 전환기와 같은
    // 내용이라, 절전 서버가 깨는 동안에도 어디로 갈 수 있는지 한눈에 보이고 ←·바깥 탭으로
    // 전환기(이전 메뉴)를 열어 내부 저장소 등으로 바로 빠져나갈 수 있다.
    val connectBackdrop: @Composable () -> Unit = {
        SourceSwitcherContent(
            servers = servers, favorites = model.favorites(),
            onStorage = { selectLocal() },
            onUrl = { switcherOpen = false; showUrl = true },
            onServer = { selectServer(it, true) },
            onEditServer = { selectServer(it, false) },
            onDeleteServer = { s -> store.remove(s.id); servers = store.list() },
            onAddServer = { overlay = HomeNav.PICKER },
            onOpenSaved = { model.openSaved(it) },
        )
    }
    // 최근 재생은 본문 상단 셸프를 쓰지 않고 ⋮ → 재생목록에서만 본다(사용자 선택). 그래서
    // 브라우저에 rootShelf를 넘기지 않는다(루트에도 셸프가 뜨지 않음).

    Box(Modifier.fillMaxSize()) {
        // The base: the current source's browser, always composed.
        when (baseNav) {
            HomeNav.FTP -> org.olo.player.ui.FtpBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = onBaseBack,
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_FTP }?.toFtp(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
                onChangeSource = openSwitcher,
                onGlobalSearch = toSearch, onPlaylist = toPlaylist, onSettings = toSettings,
                onIsFavorite = onIsFavorite, onFavorite = onFavorite,
                connectBackdrop = connectBackdrop,
            )
            HomeNav.SFTP -> org.olo.player.ui.SftpBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = onBaseBack,
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_SFTP }?.toSftp(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
                onChangeSource = openSwitcher,
                onGlobalSearch = toSearch, onPlaylist = toPlaylist, onSettings = toSettings,
                onIsFavorite = onIsFavorite, onFavorite = onFavorite,
                connectBackdrop = connectBackdrop,
            )
            HomeNav.SMB -> org.olo.player.ui.SmbBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = onBaseBack,
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_SMB }?.toSmb(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
                onChangeSource = openSwitcher,
                onGlobalSearch = toSearch, onPlaylist = toPlaylist, onSettings = toSettings,
                onIsFavorite = onIsFavorite, onFavorite = onFavorite,
                connectBackdrop = connectBackdrop,
            )
            HomeNav.WEBDAV -> org.olo.player.ui.WebDavBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = onBaseBack,
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_WEBDAV }?.toWebDav(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
                onChangeSource = openSwitcher,
                onGlobalSearch = toSearch, onPlaylist = toPlaylist, onSettings = toSettings,
                onIsFavorite = onIsFavorite, onFavorite = onFavorite,
                connectBackdrop = connectBackdrop,
            )
            else -> LocalMedia(
                onOpenMedia = { model.openLocalMedia(it) },
                onBack = onBaseBack,
                onChangeSource = openSwitcher,
                onGlobalSearch = toSearch, onPlaylist = toPlaylist, onSettings = toSettings,
                onIsFavorite = onIsFavorite, onFavorite = onFavorite,
            )
        }

        // Overlays over the base, each opaque and full-screen.
        when (overlay) {
            HomeNav.PLAYLIST -> Surface(Modifier.fillMaxSize(), color = OloTheme.colors.bg) {
                BackHandler { overlay = null }
                PlaylistTab(model, onBack = { overlay = null })
            }
            HomeNav.SETTINGS -> Surface(Modifier.fillMaxSize(), color = OloTheme.colors.bg) {
                BackHandler { overlay = null }
                SettingsTab(model, onBack = { overlay = null })
            }
            HomeNav.SEARCH -> Surface(Modifier.fillMaxSize(), color = OloTheme.colors.bg) {
                BackHandler { overlay = null }
                SearchScreen(
                    servers = servers,
                    saved = (model.favorites() + model.recents() + model.urls() + model.servers()).distinctBy { it.key },
                    onBack = { overlay = null },
                    onOpenSaved = { model.openSaved(it) },
                    onServer = { s -> selectServer(s, true) },
                )
            }
            HomeNav.PICKER -> Surface(Modifier.fillMaxSize(), color = OloTheme.colors.bg) {
                BackHandler { overlay = null }
                ProtocolPicker(onBack = { overlay = null }, onProtocol = { p -> preset = null; presetAuto = false; baseNav = navFor(p); overlay = null })
            }
            else -> Unit
        }

        // The source switcher: an OLO-Explorer-style bottom sheet over the live
        // browse (저장소·서버·즐겨찾기), not a full-screen 홈. 최근 재생은 루트 셸프에만
        // 두고 시트에서는 뺀다 -- 전환기는 "어디로 갈지"를 고르는 곳이지 재생 이력이 아니다.
        if (switcherOpen) {
            SourceSwitcherSheet(
                servers = servers,
                favorites = model.favorites(),
                onDismiss = { switcherOpen = false },
                onStorage = { selectLocal() },
                onUrl = { switcherOpen = false; showUrl = true },
                onServer = { selectServer(it, true) },
                onEditServer = { selectServer(it, false) },
                onDeleteServer = { s -> store.remove(s.id); servers = store.list() },
                onAddServer = { switcherOpen = false; overlay = HomeNav.PICKER },
                onOpenSaved = { switcherOpen = false; model.openSaved(it) },
            )
        }

        if (showUrl) {
            OpenUrlDialog(
                onOpen = { showUrl = false; model.openNetworkUrl(it) },
                onDismiss = { showUrl = false },
            )
        }
    }
}

/**
 * 소스 전환기: 브라우즈 위로 올라오는 바텀시트. 상단 핸들·딤 스크림·부분 높이는
 * [ModalBottomSheet]가 맡고, 안에 저장소·서버·즐겨찾기 세 묶음만 담는다. 전체화면
 * 홈을 열지 않으므로 "어거지로 창을 하나 더 쌓는" 느낌 없이 현재 폴더 위에서 바로 고른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSwitcherSheet(
    servers: List<SavedServer>,
    favorites: List<SavedItem>,
    onDismiss: () -> Unit,
    onStorage: () -> Unit,
    onUrl: () -> Unit,
    onServer: (SavedServer) -> Unit,
    onEditServer: (SavedServer) -> Unit,
    onDeleteServer: (SavedServer) -> Unit,
    onAddServer: () -> Unit,
    onOpenSaved: (SavedItem) -> Unit,
) {
    val c = OloTheme.colors
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        SourceSwitcherContent(
            servers = servers, favorites = favorites,
            onStorage = onStorage, onUrl = onUrl, onServer = onServer,
            onEditServer = onEditServer, onDeleteServer = onDeleteServer,
            onAddServer = onAddServer, onOpenSaved = onOpenSaved,
        )
    }
}

/**
 * 전환기 시트의 본문(핸들·스크림을 뺀 순수 내용). ModalBottomSheet 프레임과 분리해,
 * 대조 스크린샷이 틀 없이 이 내용만 실제로 렌더할 수 있게 한다(상세 시트 패턴과 동일).
 */
@Composable
internal fun SourceSwitcherContent(
    servers: List<SavedServer>,
    favorites: List<SavedItem>,
    onStorage: () -> Unit,
    onUrl: () -> Unit,
    onServer: (SavedServer) -> Unit,
    onEditServer: (SavedServer) -> Unit,
    onDeleteServer: (SavedServer) -> Unit,
    onAddServer: () -> Unit,
    onOpenSaved: (SavedItem) -> Unit,
) {
    val c = OloTheme.colors
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
        SheetSectionLabel("저장소", null, c)
        StorageRow(onStorage, c)
        SheetSectionLabel("서버", onAddServer, c)
        for (s in servers) {
            val icon = when (s.protocol) {
                SavedServer.PROTO_SMB -> Icons.Outlined.FolderShared
                SavedServer.PROTO_WEBDAV -> Icons.Outlined.CloudQueue
                else -> Icons.Outlined.Dns
            }
            SwitcherRow(
                icon, c.tileOther, s.label, serverSubtitle(s), onClick = { onServer(s) }, c = c,
                trailing = { ServerMenu(onEdit = { onEditServer(s) }, onDelete = { onDeleteServer(s) }, c = c) },
            )
        }
        SwitcherRow(Icons.Outlined.Link, c.accent, "URL 열기", "http(s):// 또는 ftp:// 스트리밍", onClick = onUrl, c = c)
        SheetSectionLabel("즐겨찾기", null, c)
        if (favorites.isEmpty()) {
            // 즐겨찾기 입구는 브라우즈 길게누름 상세 시트의 ⭐뿐이므로, 비어 있을 때
            // 어디서 추가하는지 한 줄로 안내한다(여기선 추가 버튼을 두지 않는다).
            Text(
                "브라우즈에서 파일을 길게 눌러 ⭐로 추가합니다.",
                color = c.muted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        } else {
            for (fav in favorites.take(24)) {
                SwitcherRow(Icons.Filled.Star, c.tileVideo, fav.name, fav.source, onClick = { onOpenSaved(fav) }, c = c)
            }
        }
    }
}

/** 시트 안의 섹션 제목. [onAdd]가 있으면 우측에 + (서버 추가). */
@Composable
private fun SheetSectionLabel(text: String, onAdd: (() -> Unit)?, c: OloColors) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = c.accent, fontSize = 13.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onAdd != null) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, "서버 추가", tint = c.accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** 시트 안의 한 줄: 타일 아이콘 + 제목/부제(+선택적 사용량 막대) + 선택적 트레일링. */
@Composable
private fun SwitcherRow(
    icon: ImageVector,
    tile: Color,
    title: String,
    sub: String,
    onClick: () -> Unit,
    c: OloColors,
    usage: Float? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFFF4F1EC), modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (usage != null) {
                Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.progressTrack)) {
                    Box(Modifier.fillMaxWidth(usage).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
                }
            }
        }
        if (trailing != null) trailing()
    }
}

/** 내부 저장소 줄: 사용 가능 용량과 사용량 막대를 함께 보여준다. */
@Composable
private fun StorageRow(onClick: () -> Unit, c: OloColors) {
    val (total, avail) = remember { storageBytes() }
    val usage = if (total > 0) ((total - avail).toFloat() / total).coerceIn(0f, 1f) else 0f
    val sub = if (total > 0) "${gbOf(total)} 중 ${gbOf(avail)} 사용 가능" else "내부 저장소 · SD 카드"
    SwitcherRow(Icons.Outlined.Smartphone, c.tileFolder, "내부 저장소", sub, onClick = onClick, c = c, usage = usage.takeIf { total > 0 })
}

/** 기기 내부 저장소의 (전체, 사용 가능) 바이트. 읽기 실패 시 (0,0)이라 막대를 숨긴다. */
private fun storageBytes(): Pair<Long, Long> = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    stat.totalBytes to stat.availableBytes
}.getOrDefault(0L to 0L)

private fun gbOf(bytes: Long): String = "%.1f GB".format(bytes / 1_000_000_000.0)


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

