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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
) {
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<FtpSession?>(null) }
    // The server this session is talking to, kept so navigation can rebuild the
    // ftp uris; set once on a successful connect.
    var server by remember { mutableStateOf<FtpServer?>(null) }
    var currentPath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // The connection is the browser's alone; drop it when the browser leaves.
    DisposableEffect(Unit) {
        onDispose { session?.let { s -> Thread { s.disconnect() }.start() } }
    }

    BackHandler(onBack = onBack)

    // Loads a remote directory off the main thread, holding the connection open.
    // The session is stored only once a listing succeeds, so a failed connect
    // leaves the form up (session stays null) with the error shown, rather than
    // stranding the user on an empty list with no way to retype credentials.
    fun browse(target: FtpServer, path: String) {
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val s = session ?: FtpSession(target)
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
            // Top bar with a way back to the local picker.
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
                    stringResource(R.string.ftp_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val activeServer = server
            if (session == null || activeServer == null) {
                ConnectForm(
                    connecting = loading,
                    error = error,
                    onConnect = { chosen ->
                        server = chosen
                        browse(chosen, chosen.path.ifBlank { "/" })
                    },
                )
            } else {
                RemoteList(
                    path = currentPath,
                    entries = entries,
                    loading = loading,
                    error = error,
                    atRoot = currentPath == "/",
                    onUp = { browse(activeServer, parentOf(currentPath)) },
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
    }
}

@Composable
private fun ConnectForm(
    connecting: Boolean,
    error: String?,
    onConnect: (FtpServer) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/") }
    var encoding by remember { mutableStateOf("") }
    var passive by remember { mutableStateOf(true) }
    var ftps by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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

@Composable
private fun RemoteList(
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
            Text(
                path,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.width(18.dp).height(18.dp),
                )
            }
        }
        if (error != null) {
            Text(
                stringResource(R.string.ftp_error, error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (!atRoot) {
                item {
                    EntryRow(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        label = stringResource(R.string.pick_up),
                        onClick = onUp,
                    )
                }
            }
            val shown = entries.filter { it.isDirectory || looksMedia(it.name) }
            if (shown.isEmpty() && !loading) {
                item {
                    Text(
                        stringResource(R.string.ftp_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
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
