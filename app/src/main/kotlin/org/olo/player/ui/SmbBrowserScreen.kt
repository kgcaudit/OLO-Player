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
import org.olo.player.net.SmbServer
import org.olo.player.net.SmbSession
import org.olo.player.net.smbMediaUri
import org.olo.player.net.smbPrefKey

/**
 * The SMB/CIFS browser. 공통 뼈대는 [RemoteBrowserScaffold]가 맡고, 여기선 SMB 고유한 것만
 * 준다: 접속 폼과 uri/key·세션 생성. SMB는 서버 인증서/호스트키 핀이 없어(메시지 서명으로
 * 무결성을 보장) 신뢰 대화상자가 없다.
 */
@Composable
fun SmbBrowserScreen(
    onOpen: (items: List<MediaEntry>, index: Int) -> Unit,
    onBack: () -> Unit,
    preset: SmbServer? = null,
    autoConnect: Boolean = false,
    onSave: (SmbServer) -> Unit = {},
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
        title = "SMB/CIFS",
        sourceTag = "SMB",
        rootLabelOf = { it.name.ifBlank { it.host } },
        rootPathOf = { it.path.ifBlank { "/" } },
        uriFor = { s, p -> smbMediaUri(s, p) },
        keyFor = { s, p -> smbPrefKey(s, p) },
        newSession = { SmbSession(it) },
        listWith = { s, p -> s.list(p) },
        disconnect = { it.disconnect() },
        connectForm = { p, connecting, err, onConnect -> SmbForm(p, connecting, err, onConnect) },
    )
}

@Composable
private fun SmbForm(initial: SmbServer?, connecting: Boolean, error: String?, onConnect: (SmbServer, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var share by remember { mutableStateOf(initial?.share ?: "") }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pass by remember { mutableStateOf(initial?.pass ?: "") }
    var domain by remember { mutableStateOf(initial?.domain ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "445") }
    var path by remember { mutableStateOf(initial?.path ?: "/") }
    var encrypt by remember { mutableStateOf(initial?.encrypt ?: false) }
    var save by remember { mutableStateOf(true) }

    // 스크롤은 팝업 카드(NetConnectScaffold)가 맡는다.
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_name), name, { name = it }, placeholder = "선택")
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_host), host, { host = it }, required = true)
        org.olo.player.ui.components.CpField(stringResource(R.string.smb_share), share, { share = it }, required = true)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_user), user, { user = it })
        org.olo.player.ui.components.CpFieldSecret(stringResource(R.string.ftp_password), pass, { pass = it })
        org.olo.player.ui.components.CpSectionLabel(stringResource(R.string.ftp_advanced))
        org.olo.player.ui.components.CpField(stringResource(R.string.smb_domain), domain, { domain = it }, placeholder = "선택")
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_port), port, { port = it.filter(Char::isDigit).take(5) }, keyboardType = KeyboardType.Number)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_path), path, { path = it }, placeholder = "/")
        org.olo.player.ui.components.CpToggleRow(stringResource(R.string.smb_encrypt), encrypt) { encrypt = it }
        org.olo.player.ui.components.CpToggleRow(stringResource(R.string.net_save_server), save) { save = it }
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            onClick = {
                onConnect(
                    SmbServer(
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 445,
                        user = user.trim(),
                        pass = pass,
                        domain = domain.trim(),
                        share = share.trim(),
                        name = name.trim(),
                        path = path.trim().ifBlank { "/" },
                        encrypt = encrypt,
                    ),
                    save,
                )
            },
            enabled = host.isNotBlank() && share.isNotBlank() && !connecting,
        ) {
            Text(if (connecting) stringResource(R.string.ftp_connecting) else stringResource(R.string.ftp_connect))
        }
        if (error != null) {
            Text(stringResource(R.string.ftp_error, error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
        }
    }
}
