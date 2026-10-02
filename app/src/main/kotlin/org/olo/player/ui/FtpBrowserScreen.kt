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
import org.filezilla.ftp.net.CertificateNotTrusted
import org.olo.player.R
import org.olo.player.data.SavedItem
import org.olo.player.ftp.FtpServer
import org.olo.player.ftp.FtpSession
import org.olo.player.ftp.mediaUri
import org.olo.player.ftp.prefKeyFor

/**
 * The FTP server browser: connect to a server, walk its folders, and open a
 * media file -- which builds a playlist of the folder's other media of the same
 * kind and hands it to the player as ftp:// sources, streamed with no local copy.
 *
 * 접속 상태 기계·내비게이션·목록 연결은 [RemoteBrowserScaffold]가 공통으로 맡고, 여기선 FTP
 * 고유한 것만 준다: 접속 폼, uri/key 생성, 세션 생성, 그리고 FTPS 인증서 신뢰 대화상자.
 */
@Composable
fun FtpBrowserScreen(
    onOpen: (items: List<MediaEntry>, index: Int) -> Unit,
    onBack: () -> Unit,
    preset: FtpServer? = null,
    autoConnect: Boolean = false,
    onSave: (FtpServer) -> Unit = {},
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
        title = stringResource(R.string.ftp_title),
        sourceTag = "FTP",
        rootLabelOf = { it.name.ifBlank { it.host } },
        rootPathOf = { it.path.ifBlank { "/" } },
        uriFor = { s, p -> mediaUri(s, p) },
        keyFor = { s, p -> prefKeyFor(s, p) },
        newSession = { FtpSession(it) },
        listWith = { s, p -> s.list(p) },
        disconnect = { it.disconnect() },
        isTrustChallenge = { it is CertificateNotTrusted },
        trustDialog = { pending, srv, retryWith, dismiss ->
            val refusal = pending as CertificateNotTrusted
            val cert = refusal.certificate
            CertificateDialog(
                fingerprint = cert.fingerprint,
                subject = cert.commonName,
                issuer = cert.issuerName,
                changed = refusal.changed,
                onTrust = { srv?.copy(pinnedCertificate = cert.fingerprint)?.let(retryWith) },
                onCancel = { dismiss("인증서를 신뢰하지 않아 접속을 취소했습니다.") },
            )
        },
        connectForm = { p, connecting, err, onConnect -> ConnectForm(p, connecting, err, onConnect) },
    )
}

@Composable
private fun ConnectForm(
    initial: FtpServer?,
    connecting: Boolean,
    error: String?,
    onConnect: (FtpServer, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "21") }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pass by remember { mutableStateOf(initial?.pass ?: "") }
    var path by remember { mutableStateOf(initial?.path ?: "/") }
    var encoding by remember { mutableStateOf(initial?.encoding ?: "") }
    var passive by remember { mutableStateOf(initial?.passive ?: true) }
    var ftps by remember { mutableStateOf(initial?.ftps ?: false) }
    var save by remember { mutableStateOf(true) }

    // 스크롤은 팝업 카드(NetConnectScaffold)가 맡으므로 여기선 내용만 쌓는다.
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_name), name, { name = it }, placeholder = "선택")
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_host), host, { host = it }, required = true)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_user), user, { user = it }, placeholder = "anonymous")
        org.olo.player.ui.components.CpFieldSecret(stringResource(R.string.ftp_password), pass, { pass = it })
        org.olo.player.ui.components.CpSectionLabel(stringResource(R.string.ftp_advanced))
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_port), port, { port = it.filter(Char::isDigit).take(5) }, keyboardType = KeyboardType.Number)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_path), path, { path = it }, placeholder = "/")
        org.olo.player.ui.components.CpSelectRow(stringResource(R.string.ftp_encoding_label), org.olo.player.ui.components.ENCODING_OPTIONS, encoding) { encoding = it }
        org.olo.player.ui.components.CpToggleRow(stringResource(R.string.ftp_passive), passive) { passive = it }
        org.olo.player.ui.components.CpToggleRow(stringResource(R.string.ftp_ftps), ftps) { ftps = it }
        org.olo.player.ui.components.CpToggleRow(stringResource(R.string.net_save_server), save) { save = it }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onConnect(
                    FtpServer(
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 21,
                        user = user.trim(),
                        pass = pass,
                        name = name.trim(),
                        path = path.trim().ifBlank { "/" },
                        encoding = encoding.trim(),
                        passive = passive,
                        ftps = ftps,
                    ),
                    save,
                )
            },
            enabled = host.isNotBlank() && !connecting,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Text(
                if (connecting) stringResource(R.string.ftp_connecting)
                else stringResource(R.string.ftp_connect),
            )
        }
        if (error != null) {
            Text(
                stringResource(R.string.ftp_error, error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}
