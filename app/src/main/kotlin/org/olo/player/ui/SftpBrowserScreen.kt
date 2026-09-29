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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.R
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ftp.parentOf
import org.olo.player.net.SftpServer
import org.olo.player.net.SftpSession
import org.olo.player.net.sftpMediaUri
import org.olo.player.net.sftpPrefKey

/**
 * The SFTP browser: connect over SSH, walk folders, and open a media file --
 * a same-kind playlist streamed as sftp:// sources with no local copy. Mirrors
 * the FTP/WebDAV browsers and shares [RemoteEntry].
 */
@Composable
fun SftpBrowserScreen(
    onOpen: (items: List<MediaEntry>, index: Int) -> Unit,
    onBack: () -> Unit,
    preset: SftpServer? = null,
    autoConnect: Boolean = false,
    onSave: (SftpServer) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(preset) }
    var session by remember { mutableStateOf<SftpSession?>(null) }
    var currentPath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { session?.let { s -> Thread { s.disconnect() }.start() } }
    }
    BackHandler(onBack = onBack)

    fun browse(target: SftpServer, path: String) {
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val s = session ?: SftpSession(target)
                    s to s.list(path)
                }
            }
            result.onSuccess { (s, listed) ->
                session = s
                entries = listed.sortedWith(
                    compareBy<RemoteEntry> { e -> !e.isDirectory }
                        .thenComparator { a, b -> NaturalOrder.compare(a.name, b.name) },
                )
                currentPath = path
            }.onFailure { error = it.message ?: it.toString() }
            loading = false
        }
    }

    LaunchedEffect(Unit) { if (autoConnect) preset?.let { browse(it, it.path.ifBlank { "/" }) } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.primary)
                }
                Text("SFTP", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }

            val active = server
            when {
                session != null && active != null -> RemoteBrowseList(
                    path = currentPath,
                    entries = entries,
                    loading = loading,
                    error = error,
                    atRoot = currentPath.trimEnd('/').isEmpty(),
                    onUp = { browse(active, parentOf(currentPath)) },
                    onEntry = { entry ->
                        if (entry.isDirectory) browse(active, entry.path)
                        else {
                            val (items, index) = sftpPlaylist(active, entries, entry)
                            if (items.isNotEmpty()) onOpen(items, index)
                        }
                    },
                )
                autoConnect && preset != null && error == null -> NetConnecting()
                else -> SftpForm(
                    initial = preset,
                    connecting = loading,
                    error = error,
                    onConnect = { chosen, save -> server = chosen; if (save) onSave(chosen); browse(chosen, chosen.path.ifBlank { "/" }) },
                )
            }
        }
    }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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

private fun sftpPlaylist(
    server: SftpServer,
    entries: List<RemoteEntry>,
    picked: RemoteEntry,
): Pair<List<MediaEntry>, Int> {
    val wantVideo = looksVideo(picked.name)
    val items = entries
        .filter { !it.isDirectory && looksMedia(it.name) && looksVideo(it.name) == wantVideo }
        .sortedWith(compareBy(NaturalOrder) { it.name })
        .map { MediaEntry(uri = sftpMediaUri(server, it.path), name = it.name, prefKey = sftpPrefKey(server, it.path)) }
    val index = items.indexOfFirst { it.prefKey == sftpPrefKey(server, picked.path) }.coerceAtLeast(0)
    return items to index
}
