package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.olo.player.ui.FtpBrowserScreen
import org.olo.player.ui.OpenUrlDialog
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.components.CpRow
import org.olo.player.ui.components.CpSectionLabel
import org.olo.player.ui.components.CpTile
import org.olo.player.ui.theme.OloTheme

/**
 * 네트워크 탭: quick-open a pasted URL (http(s)/ftp), and reach saved servers.
 * The saved-server path opens the ported FTP browser as-is; the full "새 서버"
 * protocol picker (SFTP/SMB/WebDAV/NFS) is a later step, so for now FTP is the
 * one live protocol here.
 */
@Composable
fun NetworkTab(model: PlayerViewModel) {
    var showFtp by rememberSaveable { mutableStateOf(false) }
    var showUrl by rememberSaveable { mutableStateOf(false) }

    if (showFtp) {
        BackHandler { showFtp = false }
        FtpBrowserScreen(
            onOpen = { items, index -> model.openEntries(items, index) },
            onBack = { showFtp = false },
        )
        return
    }

    val c = OloTheme.colors
    Column(Modifier.fillMaxSize()) {
        CpHeader("네트워크")
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
            onClick = { showFtp = true },
        )
    }

    if (showUrl) {
        OpenUrlDialog(
            onOpen = {
                showUrl = false
                model.openNetworkUrl(it)
            },
            onDismiss = { showUrl = false },
        )
    }
}
