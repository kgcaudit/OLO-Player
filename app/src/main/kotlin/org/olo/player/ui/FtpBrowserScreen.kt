package org.olo.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.olo.player.R
import org.olo.player.data.SavedItem
import org.olo.player.ftp.FtpServer
import org.olo.player.ftp.FtpSession
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ftp.mediaUri
import org.olo.player.ftp.parentOf
import org.olo.player.ftp.prefKeyFor

/**
 * The FTP server browser: connect to a server, walk its folders, and open a
 * media file -- which builds a playlist of the folder's other media of the same
 * kind (video with video, sound with sound) and hands it to the player as
 * ftp:// sources, streamed with no local copy.
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
    // 미접속(등록·접속 중) 팝업 뒤에 흐리게 깔 위치 목록. 절전 서버가 깨는 동안에도
    // 다른 위치로 빠져나갈 수 있도록 하는 맥락 배경(없으면 테마 배경).
    connectBackdrop: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    // 한 연결(FTP 제어 채널)에 list가 동시에 날아가면 충돌·지연이 나므로, 내비게이션과
    // 포스터 판별(listFolder)의 모든 list를 세션 게이트로 직렬화한다.
    val gate = remember { kotlinx.coroutines.sync.Mutex() }
    var session by remember { mutableStateOf<FtpSession?>(null) }
    // The server this session is talking to, kept so navigation can rebuild the
    // ftp uris; seeded from a saved server on reconnect, else set on connect.
    var server by remember { mutableStateOf(preset) }
    var currentPath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // An unrecognised/changed FTPS certificate raised during connect: what to put
    // the trust dialog on, plus the target/path to retry once it is pinned.
    var pendingCert by remember { mutableStateOf<org.filezilla.ftp.net.CertificateNotTrusted?>(null) }
    var retryTarget by remember { mutableStateOf<FtpServer?>(null) }
    var retryPath by remember { mutableStateOf("/") }

    // The connection is the browser's alone; drop it when the browser leaves.
    DisposableEffect(Unit) {
        onDispose { session?.let { s -> Thread { s.disconnect() }.start() } }
    }

    // Loads a remote directory off the main thread, holding the connection open.
    // The session is stored only once a listing succeeds, so a failed connect
    // leaves the form up (session stays null) with the error shown, rather than
    // stranding the user on an empty list with no way to retype credentials.
    fun browse(target: FtpServer, path: String) {
        loading = true
        error = null
        retryTarget = target
        retryPath = path
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    gate.withLock {
                        val s = session ?: FtpSession(target)
                        s to s.list(path)
                    }
                }
            }
            result.onSuccess { (s, listed) ->
                session = s
                entries = listed.sortedWith(
                    compareBy<RemoteEntry> { e -> !e.isDirectory }
                        .thenComparator { a, b -> NaturalOrder.compare(a.name, b.name) },
                )
                currentPath = path
            }.onFailure { e ->
                if (e is org.filezilla.ftp.net.CertificateNotTrusted) pendingCert = e
                else error = e.message ?: e.toString()
            }
            loading = false
        }
    }

    // A saved server reconnects on open: skip the form and connect straight away.
    // On failure the pre-filled form stays up (session null) so it can be edited.
    LaunchedEffect(Unit) { if (autoConnect) preset?.let { browse(it, it.path.ifBlank { "/" }) } }

    BackHandler {
        val activeServer = server
        val atRoot = currentPath.trimEnd('/').isEmpty() || currentPath == "/"
        if (session != null && activeServer != null && !atRoot) browse(activeServer, parentOf(currentPath)) else onBack()
    }

    val activeServer = server
    if (session != null && activeServer != null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                RemoteBrowseList(
                    rootLabel = activeServer.name.ifBlank { activeServer.host },
                    path = currentPath,
                    entries = entries,
                    loading = loading,
                    error = error,
                    onChangeSource = onChangeSource,
                    onNavigate = { browse(activeServer, it) },
                    imageUriFor = { mediaUri(activeServer, it) },
                    listFolder = { p -> withContext(Dispatchers.IO) { gate.withLock { (session ?: FtpSession(activeServer)).list(p) } } },
                    onPlayFile = { v -> onOpen(listOf(MediaEntry(mediaUri(activeServer, v.path), v.name, prefKeyFor(activeServer, v.path))), 0) },
                    onGlobalSearch = onGlobalSearch,
                    onPlaylist = onPlaylist,
                    onSettings = onSettings,
                    rootShelf = rootShelf,
                    isFavorite = onIsFavorite?.let { f -> { v -> f(prefKeyFor(activeServer, v.path)) } },
                    onToggleFavorite = onFavorite?.let { f ->
                        { v -> f(SavedItem(key = prefKeyFor(activeServer, v.path), name = v.name, uri = mediaUri(activeServer, v.path).toString(), source = "FTP")) }
                    },
                    onEntry = { entry ->
                        if (entry.isDirectory) {
                            browse(activeServer, entry.path)
                        } else {
                            val (items, index) = playlistFrom(activeServer, entries, entry)
                            if (items.isNotEmpty()) onOpen(items, index)
                        }
                    },
                )
            }
        }
    } else {
        // 미접속: 등록 폼·접속 중을 전체화면 대신 가운데 팝업으로. 뒤의 위치 목록으로
        // 빠져나갈 수 있게 ←·X·바깥 탭은 onChangeSource(이전 메뉴)로 보낸다.
        NetConnectScaffold(
            title = stringResource(R.string.ftp_title),
            connecting = autoConnect && preset != null && error == null,
            onLeave = onChangeSource,
            backdrop = connectBackdrop,
        ) {
            ConnectForm(
                initial = preset,
                connecting = loading,
                error = error,
                onConnect = { chosen, save ->
                    server = chosen
                    if (save) onSave(chosen)
                    browse(chosen, chosen.path.ifBlank { "/" })
                },
            )
        }
    }

    pendingCert?.let { refusal ->
        val cert = refusal.certificate
        CertificateDialog(
            fingerprint = cert.fingerprint,
            subject = cert.commonName,
            issuer = cert.issuerName,
            changed = refusal.changed,
            onTrust = {
                pendingCert = null
                val pinned = (retryTarget ?: server)?.copy(pinnedCertificate = cert.fingerprint)
                if (pinned != null) {
                    server = pinned
                    onSave(pinned)
                    browse(pinned, retryPath)
                }
            },
            onCancel = {
                pendingCert = null
                error = "인증서를 신뢰하지 않아 접속을 취소했습니다."
            },
        )
    }
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

/**
 * Builds the playlist for a tapped remote file: the folder's media of the same
 * kind (video with video, sound with sound), in natural name order, as ftp
 * sources, and the index of the tapped one.
 */
private fun playlistFrom(
    server: FtpServer,
    entries: List<RemoteEntry>,
    picked: RemoteEntry,
): Pair<List<MediaEntry>, Int> {
    val wantVideo = looksVideo(picked.name)
    val items = entries
        .filter { !it.isDirectory && looksMedia(it.name) && looksVideo(it.name) == wantVideo }
        .sortedWith(compareBy(NaturalOrder) { it.name })
        .map {
            MediaEntry(
                uri = mediaUri(server, it.path),
                name = it.name,
                prefKey = prefKeyFor(server, it.path),
            )
        }
    val index = items.indexOfFirst { it.prefKey == prefKeyFor(server, picked.path) }.coerceAtLeast(0)
    return items to index
}
