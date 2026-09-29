package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 네트워크 탭: quick-open a pasted URL, reach the FTP browser, and add a server
 * through the "새 서버" protocol picker. FTP is the one live protocol; the picker
 * lists SFTP·SMB·WebDAV·NFS as 예정 so the roadmap is visible without pretending
 * they connect yet.
 */
private enum class NetNav { LANDING, PICKER, FTP, WEBDAV, SFTP, SMB, SOON }

@Composable
fun NetworkTab(model: PlayerViewModel) {
    var nav by rememberSaveable { mutableStateOf(NetNav.LANDING) }
    var soonTitle by rememberSaveable { mutableStateOf("") }
    var showUrl by rememberSaveable { mutableStateOf(false) }

    when (nav) {
        NetNav.FTP -> {
            BackHandler { nav = NetNav.LANDING }
            FtpBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = { nav = NetNav.LANDING },
            )
            return
        }
        NetNav.WEBDAV -> {
            BackHandler { nav = NetNav.LANDING }
            org.olo.player.ui.WebDavBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = { nav = NetNav.LANDING },
            )
            return
        }
        NetNav.SFTP -> {
            BackHandler { nav = NetNav.LANDING }
            org.olo.player.ui.SftpBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = { nav = NetNav.LANDING },
            )
            return
        }
        NetNav.SMB -> {
            BackHandler { nav = NetNav.LANDING }
            org.olo.player.ui.SmbBrowserScreen(
                onOpen = { items, index -> model.openEntries(items, index) },
                onBack = { nav = NetNav.LANDING },
            )
            return
        }
        NetNav.PICKER -> {
            ProtocolPicker(
                onBack = { nav = NetNav.LANDING },
                onFtp = { nav = NetNav.FTP },
                onWebDav = { nav = NetNav.WEBDAV },
                onSftp = { nav = NetNav.SFTP },
                onSmb = { nav = NetNav.SMB },
                onSoon = { soonTitle = it; nav = NetNav.SOON },
            )
            return
        }
        NetNav.SOON -> {
            ComingSoon(soonTitle, "이 프로토콜 연결은 예정되어 있습니다. 지금은 FTP를 지원합니다.", onBack = { nav = NetNav.PICKER })
            return
        }
        NetNav.LANDING -> Unit
    }

    val c = OloTheme.colors
    Column(Modifier.fillMaxSize()) {
        CpHeader("네트워크", actions = {
            CpIconButton(Icons.Outlined.Add, onClick = { nav = NetNav.PICKER }, tint = c.accent)
        })
        CpSectionLabel("빠른 열기")
        CpRow(
            title = "URL로 스트리밍",
            subtitle = "http(s):// 또는 ftp:// 붙여넣기",
            leading = { CpTile(Icons.Outlined.Link, c.accent) },
            onClick = { showUrl = true },
        )
        CpSectionLabel("저장된 서버")
        CpRow(
            title = "FTP 서버",
            subtitle = "추가 · 접속 · 원격 폴더 탐색",
            leading = { CpTile(Icons.Outlined.Dns, c.tileOther) },
            onClick = { nav = NetNav.FTP },
        )
    }

    if (showUrl) {
        OpenUrlDialog(
            onOpen = { showUrl = false; model.openNetworkUrl(it) },
            onDismiss = { showUrl = false },
        )
    }
}

@Composable
private fun ProtocolPicker(
    onBack: () -> Unit,
    onFtp: () -> Unit,
    onWebDav: () -> Unit,
    onSftp: () -> Unit,
    onSmb: () -> Unit,
    onSoon: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = OloTheme.colors
    Column(Modifier.fillMaxSize()) {
        CpHeader("새 서버", onBack = onBack)
        CpSectionLabel("지금 사용 가능")
        CpRow(
            title = "FTP",
            subtitle = "파일 전송 · REST 탐색 재생",
            leading = { CpTile(Icons.Outlined.Dns, c.accent) },
            onClick = onFtp,
            trailing = { Tag("지금", now = true) },
        )
        CpRow(
            title = "SFTP",
            subtitle = "SSH 기반 보안 전송",
            leading = { CpTile(Icons.Outlined.Dns, c.accent) },
            onClick = onSftp,
            trailing = { Tag("지금", now = true) },
        )
        CpRow(
            title = "SMB/CIFS",
            subtitle = "Windows·NAS 공유",
            leading = { CpTile(Icons.Outlined.FolderShared, c.accent) },
            onClick = onSmb,
            trailing = { Tag("지금", now = true) },
        )
        CpRow(
            title = "WebDAV",
            subtitle = "HTTP(S) 기반 원격 폴더",
            leading = { CpTile(Icons.Outlined.CloudQueue, c.accent) },
            onClick = onWebDav,
            trailing = { Tag("지금", now = true) },
        )
        CpSectionLabel("예정")
        for (p in listOf("NFS" to "유닉스 네트워크 파일시스템")) {
            CpRow(
                title = p.first,
                subtitle = p.second,
                leading = { CpTile(Icons.Outlined.Dns, c.tileOther) },
                onClick = { onSoon(p.first) },
                trailing = { Tag("예정", now = false) },
            )
        }
        CpRow(
            title = "클라우드",
            subtitle = "구글 드라이브 · OneDrive 등 · 검토 중",
            leading = { CpTile(Icons.Outlined.CloudQueue, c.tileOther) },
            onClick = { onSoon("클라우드") },
            trailing = { Tag("검토", now = false) },
        )
    }
}

@Composable
private fun Tag(text: String, now: Boolean) {
    val c = OloTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (now) c.accentContainer else c.progressTrack)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, color = if (now) c.onAccentContainer else c.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
