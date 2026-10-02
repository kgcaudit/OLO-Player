package org.olo.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.olo.player.R
import org.olo.player.data.SavedItem
import org.olo.player.net.HostKeyUnverified
import org.olo.player.net.SftpServer
import org.olo.player.net.SftpSession
import org.olo.player.net.sftpMediaUri
import org.olo.player.net.sftpPrefKey

/**
 * The SFTP (SSH) browser. 공통 뼈대는 [RemoteBrowserScaffold]가 맡고, 여기선 SFTP 고유한 것만
 * 준다: 접속 폼·uri/key·세션 생성과, 처음 보는/바뀐 호스트 키를 사용자가 핀하도록 하는 대화상자.
 */
@Composable
fun SftpBrowserScreen(
    onOpen: (items: List<MediaEntry>, index: Int) -> Unit,
    onBack: () -> Unit,
    preset: SftpServer? = null,
    autoConnect: Boolean = false,
    onSave: (SftpServer) -> Unit = {},
    onChangeSource: () -> Unit = {},
    onGlobalSearch: (() -> Unit)? = null,
    onPlaylist: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    rootShelf: (@Composable () -> Unit)? = null,
    onIsFavorite: ((String) -> Boolean)? = null,
    onFavorite: ((SavedItem) -> Unit)? = null,
    connectBackdrop: (@Composable () -> Unit)? = null,
) {
    RemoteBrowserScaffold(
        onOpen = onOpen,
        onBack = onBack,
        preset = preset,
        autoConnect = autoConnect,
        onSave = onSave,
        onChangeSource = onChangeSource,
        onGlobalSearch = onGlobalSearch,
        onPlaylist = onPlaylist,
        onSettings = onSettings,
        rootShelf = rootShelf,
        onIsFavorite = onIsFavorite,
        onFavorite = onFavorite,
        connectBackdrop = connectBackdrop,
        title = "SFTP",
        sourceTag = "SFTP",
        rootLabelOf = { it.name.ifBlank { it.host } },
        rootPathOf = { it.path.ifBlank { "/" } },
        uriFor = { s, p -> sftpMediaUri(s, p) },
        keyFor = { s, p -> sftpPrefKey(s, p) },
        newSession = { SftpSession(it) },
        listWith = { s, p -> s.list(p) },
        disconnect = { it.disconnect() },
        isTrustChallenge = { it is HostKeyUnverified },
        trustDialog = { pending, srv, retryWith, dismiss ->
            val unverified = pending as HostKeyUnverified
            HostKeyDialog(
                fingerprint = unverified.fingerprint,
                algorithm = unverified.algorithm,
                changed = unverified.changed,
                onTrust = { srv?.copy(knownHostKey = unverified.fingerprint)?.let(retryWith) },
                onCancel = { dismiss("호스트 키를 신뢰하지 않아 접속을 취소했습니다.") },
            )
        },
        connectForm = { p, connecting, err, onConnect -> SftpForm(p, connecting, err, onConnect) },
    )
}

@Composable
private fun SftpForm(initial: SftpServer?, connecting: Boolean, error: String?, onConnect: (SftpServer, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "22") }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pass by remember { mutableStateOf(initial?.pass ?: "") }
    var path by remember { mutableStateOf(initial?.path ?: "/") }
    var save by remember { mutableStateOf(true) }

    // 스크롤은 팝업 카드(NetConnectScaffold)가 맡는다.
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_name), name, { name = it }, placeholder = "선택")
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_host), host, { host = it }, required = true)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_user), user, { user = it }, placeholder = "anonymous")
        org.olo.player.ui.components.CpFieldSecret(stringResource(R.string.ftp_password), pass, { pass = it })
        org.olo.player.ui.components.CpSectionLabel(stringResource(R.string.ftp_advanced))
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_port), port, { port = it.filter(Char::isDigit).take(5) }, keyboardType = KeyboardType.Number)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_path), path, { path = it }, placeholder = "/")
        org.olo.player.ui.components.CpToggleRow(stringResource(R.string.net_save_server), save) { save = it }
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            onClick = {
                onConnect(
                    SftpServer(
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 22,
                        user = user.trim(),
                        pass = pass,
                        name = name.trim(),
                        path = path.trim().ifBlank { "/" },
                    ),
                    save,
                )
            },
            enabled = host.isNotBlank() && !connecting,
        ) {
            Text(if (connecting) stringResource(R.string.ftp_connecting) else stringResource(R.string.ftp_connect))
        }
        if (error != null) {
            Text(stringResource(R.string.ftp_error, error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
        }
    }
}
