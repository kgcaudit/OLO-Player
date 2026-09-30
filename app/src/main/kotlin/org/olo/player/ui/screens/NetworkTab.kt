package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.R
import org.olo.player.data.SavedServer
import org.olo.player.data.SavedServerStore
import org.olo.player.ui.FtpBrowserScreen
import org.olo.player.ui.OpenUrlDialog
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.components.CpIconButton
import org.olo.player.ui.components.CpRow
import org.olo.player.ui.components.CpSectionLabel
import org.olo.player.ui.components.CpTile
import org.olo.player.ui.theme.OloTheme

/**
 * 네트워크 탭: quick-open a pasted URL, keep a list of saved servers to reconnect
 * with one tap, and add a server through the "새 서버" protocol picker. Four
 * protocols connect for real (FTP·SFTP·SMB·WebDAV); the picker still lists what
 * is planned so the roadmap stays visible.
 *
 * A saved server drives the browser two ways: tapping it reconnects straight
 * away (autoConnect), while its ⋮ · 편집 opens the same form pre-filled so host
 * or 비밀번호 can be changed before connecting again.
 */
private enum class NetNav { LANDING, PICKER, FTP, WEBDAV, SFTP, SMB }

private fun navFor(protocol: String) = when (protocol) {
    SavedServer.PROTO_SFTP -> NetNav.SFTP
    SavedServer.PROTO_SMB -> NetNav.SMB
    SavedServer.PROTO_WEBDAV -> NetNav.WEBDAV
    else -> NetNav.FTP
}

@Composable
fun NetworkTab(model: PlayerViewModel) {
    val context = LocalContext.current
    val store = remember { SavedServerStore(context) }
    var servers by remember { mutableStateOf(store.list()) }

    var nav by rememberSaveable { mutableStateOf(NetNav.LANDING) }
    var showUrl by rememberSaveable { mutableStateOf(false) }
    // The saved server a browser opens with, and whether to connect at once
    // (reconnect) or wait on the pre-filled form (편집/새 서버).
    var preset by remember { mutableStateOf<SavedServer?>(null) }
    var presetAuto by remember { mutableStateOf(false) }

    // On a successful connect the form re-saves; refresh the landing list so a
    // new or edited server shows the moment we return.
    val onSaved: () -> Unit = { servers = store.list() }

    when (nav) {
        NetNav.FTP -> {
            BackHandler { nav = NetNav.LANDING }
            FtpBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = { nav = NetNav.LANDING },
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_FTP }?.toFtp(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
            )
            return
        }
        NetNav.WEBDAV -> {
            BackHandler { nav = NetNav.LANDING }
            org.olo.player.ui.WebDavBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = { nav = NetNav.LANDING },
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_WEBDAV }?.toWebDav(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
            )
            return
        }
        NetNav.SFTP -> {
            BackHandler { nav = NetNav.LANDING }
            org.olo.player.ui.SftpBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = { nav = NetNav.LANDING },
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_SFTP }?.toSftp(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
            )
            return
        }
        NetNav.SMB -> {
            BackHandler { nav = NetNav.LANDING }
            org.olo.player.ui.SmbBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = { nav = NetNav.LANDING },
                preset = preset?.takeIf { it.protocol == SavedServer.PROTO_SMB }?.toSmb(),
                autoConnect = presetAuto,
                onSave = { store.save(SavedServer.of(it)); onSaved() },
            )
            return
        }
        NetNav.PICKER -> {
            ProtocolPicker(
                onBack = { nav = NetNav.LANDING },
                onProtocol = { p -> preset = null; presetAuto = false; nav = navFor(p) },
            )
            return
        }
        NetNav.LANDING -> Unit
    }

    val c = OloTheme.colors
    Column(Modifier.fillMaxSize()) {
        CpHeader("네트워크", actions = {
            CpIconButton(Icons.Outlined.Add, onClick = { preset = null; presetAuto = false; nav = NetNav.PICKER }, tint = c.accent)
        })
        CpSectionLabel("빠른 열기")
        CpRow(
            title = "URL로 스트리밍",
            subtitle = "http(s):// 또는 ftp:// 붙여넣기",
            leading = { CpTile(Icons.Outlined.Link, c.accent) },
            onClick = { showUrl = true },
        )
        CpSectionLabel("저장된 서버")
        if (servers.isEmpty()) {
            Text(
                stringResource(R.string.net_saved_empty),
                color = c.muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        } else {
            for (s in servers) {
                SavedServerRow(
                    server = s,
                    onOpen = { preset = s; presetAuto = true; nav = navFor(s.protocol) },
                    onEdit = { preset = s; presetAuto = false; nav = navFor(s.protocol) },
                    onDelete = { store.remove(s.id); servers = store.list() },
                )
            }
        }
    }

    if (showUrl) {
        OpenUrlDialog(
            onOpen = { showUrl = false; model.openNetworkUrl(it) },
            onDismiss = { showUrl = false },
        )
    }
}

@Composable
private fun SavedServerRow(
    server: SavedServer,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = OloTheme.colors
    val (icon, tint) = when (server.protocol) {
        SavedServer.PROTO_SMB -> Icons.Outlined.FolderShared to c.tileOther
        SavedServer.PROTO_WEBDAV -> Icons.Outlined.CloudQueue to c.tileOther
        else -> Icons.Outlined.Dns to c.tileOther
    }
    CpRow(
        title = server.label,
        subtitle = serverSubtitle(server),
        leading = { CpTile(icon, tint) },
        onClick = onOpen,
        trailing = { RowMenu(onEdit = onEdit, onDelete = onDelete) },
    )
}

/** A one-line summary of where a saved server points: 프로토콜 · 계정@호스트[:포트]. */
private fun serverSubtitle(s: SavedServer): String {
    val proto = s.protocol.uppercase()
    val account = s.user.ifBlank { "anonymous" }
    val hostPort = if (s.protocol == SavedServer.PROTO_SMB && s.share.isNotBlank()) {
        "${s.host}/${s.share}"
    } else {
        s.host
    }
    return "$proto · $account@$hostPort"
}

@Composable
private fun RowMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        CpIconButton(Icons.Outlined.MoreVert, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.net_edit)) }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.net_delete)) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
internal fun ProtocolPicker(
    onBack: () -> Unit,
    onProtocol: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = OloTheme.colors
    // Only the protocols that actually connect. Everything here is available, so
    // no "지금 사용 가능" label and no "지금" tag -- with nothing planned beside
    // them, that contrast has no second side left to mean anything.
    Column(Modifier.fillMaxSize()) {
        CpHeader("새 서버", onBack = onBack)
        CpRow(
            title = "FTP",
            subtitle = "파일 전송 · REST 탐색 재생",
            leading = { CpTile(Icons.Outlined.Dns, c.accent) },
            onClick = { onProtocol(SavedServer.PROTO_FTP) },
        )
        CpRow(
            title = "SFTP",
            subtitle = "SSH 기반 보안 전송",
            leading = { CpTile(Icons.Outlined.Dns, c.accent) },
            onClick = { onProtocol(SavedServer.PROTO_SFTP) },
        )
        CpRow(
            title = "SMB/CIFS",
            subtitle = "Windows·NAS 공유",
            leading = { CpTile(Icons.Outlined.FolderShared, c.accent) },
            onClick = { onProtocol(SavedServer.PROTO_SMB) },
        )
        CpRow(
            title = "WebDAV",
            subtitle = "HTTP(S) 기반 원격 폴더",
            leading = { CpTile(Icons.Outlined.CloudQueue, c.accent) },
            onClick = { onProtocol(SavedServer.PROTO_WEBDAV) },
        )
    }
}
