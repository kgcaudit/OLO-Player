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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf<WebDavServer?>(null) }
    var session by remember { mutableStateOf<WebDavSession?>(null) }
    var currentPath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    fun browse(target: WebDavServer, path: String) {
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val s = session ?: WebDavSession(target)
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "WebDAV",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val active = server
            if (session == null || active == null) {
                WebDavForm(
                    connecting = loading,
                    error = error,
                    onConnect = { chosen -> server = chosen; browse(chosen, chosen.path.ifBlank { "/" }) },
                )
            } else {
                WebDavList(
                    path = currentPath,
                    entries = entries,
                    loading = loading,
                    error = error,
                    atRoot = currentPath.trimEnd('/').isEmpty(),
                    onUp = { browse(active, parentOf(currentPath)) },
                    onEntry = { entry ->
                        if (entry.isDirectory) {
                            browse(active, entry.path)
                        } else {
                            val (items, index) = webDavPlaylist(active, entries, entry)
                            if (items.isNotEmpty()) onOpen(items, index)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WebDavForm(connecting: Boolean, error: String?, onConnect: (WebDavServer) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/") }
    var tls by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_name), name, { name = it }, placeholder = "선택")
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_host), host, { host = it }, required = true)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_user), user, { user = it })
        org.olo.player.ui.components.CpFieldSecret(stringResource(R.string.ftp_password), pass, { pass = it })
        org.olo.player.ui.components.CpSectionLabel(stringResource(R.string.ftp_advanced))
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_port), port, { port = it.filter(Char::isDigit).take(5) }, keyboardType = KeyboardType.Number)
        org.olo.player.ui.components.CpField(stringResource(R.string.ftp_path), path, { path = it }, placeholder = "/")
        org.olo.player.ui.components.CpToggleRow("HTTPS", tls) { tls = it; if (it && port == "80") port = "443" else if (!it && port == "443") port = "80" }
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

@Composable
private fun WebDavList(
    path: String,
    entries: List<RemoteEntry>,
    loading: Boolean,
    error: String?,
    atRoot: Boolean,
    onUp: () -> Unit,
    onEntry: (RemoteEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(path, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp).height(18.dp))
        }
        if (error != null) {
            Text(stringResource(R.string.ftp_error, error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (!atRoot) {
                item {
                    EntryRow(
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = stringResource(R.string.pick_up),
                        onClick = onUp,
                    )
                }
            }
            val shown = entries.filter { it.isDirectory || looksMedia(it.name) }
            if (shown.isEmpty() && !loading) {
                item {
                    Text(stringResource(R.string.ftp_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                }
            }
            items(shown, key = { it.path }) { entry ->
                EntryRow(
                    icon = {
                        Icon(
                            when {
                                entry.isDirectory -> Icons.Filled.Folder
                                kindOf(entry.name, false) == FileKind.AUDIO -> Icons.Filled.MusicNote
                                else -> Icons.Filled.Movie
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    label = entry.name,
                    onClick = { onEntry(entry) },
                )
            }
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
