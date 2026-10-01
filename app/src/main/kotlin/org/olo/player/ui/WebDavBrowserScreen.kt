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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ftp.parentOf
import org.olo.player.net.WebDavServer
import org.olo.player.net.WebDavSession
import org.olo.player.net.webDavMediaUri
import org.olo.player.net.webDavPrefKey

/**
 * The WebDAV browser: connect to a server, walk its collections, and open a
 * media file -- which builds a same-kind playlist and hands it to the player as
 * webdav:// sources, streamed as ranged HTTP with no local copy. Mirrors the FTP
 * browser; the two share [RemoteEntry] and the row.
 */
@Composable
fun WebDavBrowserScreen(
    onOpen: (items: List<MediaEntry>, index: Int) -> Unit,
    onBack: () -> Unit,
    preset: WebDavServer? = null,
    autoConnect: Boolean = false,
    onSave: (WebDavServer) -> Unit = {},
    onChangeSource: () -> Unit = {},
    onGlobalSearch: (() -> Unit)? = null,
    onPlaylist: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    rootShelf: (@Composable () -> Unit)? = null,
    onIsFavorite: ((String) -> Boolean)? = null,
    onFavorite: ((SavedItem) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    // 포스터 판별 list가 내비게이션과 겹쳐 서버를 몰아치지 않도록 list를 직렬화한다.
    val gate = remember { kotlinx.coroutines.sync.Mutex() }
    var server by remember { mutableStateOf(preset) }
    var session by remember { mutableStateOf<WebDavSession?>(null) }
    var currentPath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCert by remember { mutableStateOf<org.filezilla.ftp.net.CertificateNotTrusted?>(null) }
    var retryTarget by remember { mutableStateOf<WebDavServer?>(null) }
    var retryPath by remember { mutableStateOf("/") }

    fun browse(target: WebDavServer, path: String) {
        loading = true
        error = null
        retryTarget = target
        retryPath = path
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    gate.withLock {
                        val s = session ?: WebDavSession(target)
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

    LaunchedEffect(Unit) { if (autoConnect) preset?.let { browse(it, it.path.ifBlank { "/" }) } }

    BackHandler {
        val active = server
        val atRoot = currentPath.trimEnd('/').isEmpty() || currentPath == "/"
        if (session != null && active != null && !atRoot) browse(active, parentOf(currentPath)) else onBack()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            val active = server
            when {
                session != null && active != null -> RemoteBrowseList(
                    rootLabel = active.name.ifBlank { active.host },
                    path = currentPath,
                    entries = entries,
                    loading = loading,
                    error = error,
                    onChangeSource = onChangeSource,
                    onNavigate = { browse(active, it) },
                    imageUriFor = { webDavMediaUri(active, it) },
                    listFolder = { p -> withContext(Dispatchers.IO) { gate.withLock { (session ?: WebDavSession(active)).list(p) } } },
                    onPlayFile = { v -> onOpen(listOf(MediaEntry(webDavMediaUri(active, v.path), v.name, webDavPrefKey(active, v.path))), 0) },
                    onGlobalSearch = onGlobalSearch,
                    onPlaylist = onPlaylist,
                    onSettings = onSettings,
                    rootShelf = rootShelf,
                    isFavorite = onIsFavorite?.let { f -> { v -> f(webDavPrefKey(active, v.path)) } },
                    onToggleFavorite = onFavorite?.let { f ->
                        { v -> f(SavedItem(key = webDavPrefKey(active, v.path), name = v.name, uri = webDavMediaUri(active, v.path).toString(), source = "WebDAV")) }
                    },
                    onEntry = { entry ->
                        if (entry.isDirectory) {
                            browse(active, entry.path)
                        } else {
                            val (items, index) = webDavPlaylist(active, entries, entry)
                            if (items.isNotEmpty()) onOpen(items, index)
                        }
                    },
                )
                autoConnect && preset != null && error == null -> {
                    NetTopBar("WebDAV", onBack)
                    NetConnecting()
                }
                else -> {
                    NetTopBar("WebDAV", onBack)
                    WebDavForm(
                        initial = preset,
                        connecting = loading,
                        error = error,
                        onConnect = { chosen, save -> server = chosen; if (save) onSave(chosen); browse(chosen, chosen.path.ifBlank { "/" }) },
                    )
                }
            }
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
private fun WebDavForm(initial: WebDavServer?, connecting: Boolean, error: String?, onConnect: (WebDavServer, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "80") }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pass by remember { mutableStateOf(initial?.pass ?: "") }
    var path by remember { mutableStateOf(initial?.path ?: "/") }
    var tls by remember { mutableStateOf(initial?.tls ?: false) }
    var save by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_name), name, { name = it }, placeholder = "선택")
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_host), host, { host = it }, required = true)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_user), user, { user = it })
        org.olo.player.ui.components.CpFieldSecret(stringResource(R.string.ftp_password), pass, { pass = it })
        org.olo.player.ui.components.CpSectionLabel(stringResource(R.string.ftp_advanced))
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_port), port, { port = it.filter(Char::isDigit).take(5) }, keyboardType = KeyboardType.Number)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_path), path, { path = it }, placeholder = "/")
        org.olo.player.ui.components.CpToggleRow("HTTPS", tls) { tls = it; if (it && port == "80") port = "443" else if (!it && port == "443") port = "80" }
        org.olo.player.ui.components.CpToggleRow(stringResource(R.string.net_save_server), save) { save = it }
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            onClick = {
                onConnect(
                    WebDavServer(
                        host = host.trim(),
                        port = port.toIntOrNull() ?: if (tls) 443 else 80,
                        user = user.trim(),
                        pass = pass,
                        tls = tls,
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

private fun webDavPlaylist(
    server: WebDavServer,
    entries: List<RemoteEntry>,
    picked: RemoteEntry,
): Pair<List<MediaEntry>, Int> {
    val wantVideo = looksVideo(picked.name)
    val items = entries
        .filter { !it.isDirectory && looksMedia(it.name) && looksVideo(it.name) == wantVideo }
        .sortedWith(compareBy(NaturalOrder) { it.name })
        .map { MediaEntry(uri = webDavMediaUri(server, it.path), name = it.name, prefKey = webDavPrefKey(server, it.path)) }
    val index = items.indexOfFirst { it.prefKey == webDavPrefKey(server, picked.path) }.coerceAtLeast(0)
    return items to index
}
