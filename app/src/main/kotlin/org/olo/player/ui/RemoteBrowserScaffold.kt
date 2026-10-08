package org.olo.player.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.olo.player.data.SavedItem
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ftp.parentOf

/**
 * 네 네트워크 브라우저(FTP·SFTP·SMB·WebDAV)의 뼈대. 화면들이 ~90% 똑같았다: 접속 상태 기계
 * (세션·현재경로·목록·로딩·오류), 폴더 나열(browse, 세션 게이트로 직렬화), 뒤로가기(한 폴더 위/
 * 나가기), 자동접속, 목록 UI 연결, 사이드카 포함 재생 큐 생성. 그 공통부를 여기 한 번만 둔다.
 *
 * 프로토콜마다 다른 것만 매개변수로 받는다: 서버 타입 [S]·세션 타입 [T], 세션 생성/나열/해제,
 * 재생 URI·이어보기 key 생성기, 라벨·출처표기·제목, 접속 폼([connectForm]), 그리고 신뢰 문제
 * (FTP·WebDAV 인증서 / SFTP 호스트키 / SMB 없음)를 [isTrustChallenge]로 가려 [trustDialog]로
 * 처리한다. 이렇게 하면 내비게이션·게이트·목록 연결 같은 버그 표면이 한곳에 모여 유지보수와
 * 일괄 수정이 쉬워진다.
 */
@Composable
fun <S : Any, T : Any> RemoteBrowserScaffold(
    onOpen: (items: List<MediaEntry>, index: Int) -> Unit,
    onBack: () -> Unit,
    preset: S?,
    autoConnect: Boolean,
    onSave: (S) -> Unit,
    onChangeSource: () -> Unit,
    onGlobalSearch: (() -> Unit)?,
    onPlaylist: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    rootShelf: (@Composable () -> Unit)?,
    onIsFavorite: ((String) -> Boolean)?,
    onFavorite: ((SavedItem) -> Unit)?,
    connectBackdrop: (@Composable () -> Unit)?,
    title: String,
    sourceTag: String,
    rootLabelOf: (S) -> String,
    rootPathOf: (S) -> String,
    uriFor: (S, String) -> Uri,
    keyFor: (S, String) -> String,
    newSession: (S) -> T,
    listWith: (T, String) -> List<RemoteEntry>,
    disconnect: (T) -> Unit,
    isTrustChallenge: (Throwable) -> Boolean = { false },
    // 신뢰 대화상자(인증서/호스트키). pending=걸린 예외, server=시도 중이던 서버. retryWith(핀된
    // 서버)로 신뢰·저장·재시도, dismiss(오류문구)로 취소. SMB는 null.
    trustDialog: (@Composable (pending: Throwable, server: S?, retryWith: (S) -> Unit, dismiss: (String?) -> Unit) -> Unit)? = null,
    connectForm: @Composable (preset: S?, connecting: Boolean, error: String?, onConnect: (S, Boolean) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 한 연결에 list가 동시에 날아가면 충돌·지연이 나므로, 내비게이션과 포스터 판별의 모든
    // list를 세션 게이트로 직렬화한다.
    val gate = remember { Mutex() }
    var session by remember { mutableStateOf<T?>(null) }
    var server by remember { mutableStateOf(preset) }
    var currentPath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // 저장 서버 자동 접속이 실패했을 때, 기본은 실패 카드를 보이고 "설정 편집"을 누르면
    // true가 되어 등록 폼으로 전환한다(실패 카드 ↔ 폼 전환 플래그).
    var editing by remember { mutableStateOf(false) }
    var pendingTrust by remember { mutableStateOf<Throwable?>(null) }
    var retryTarget by remember { mutableStateOf<S?>(null) }
    var retryPath by remember { mutableStateOf("/") }

    // The connection is the browser's alone; drop it when the browser leaves.
    DisposableEffect(Unit) {
        onDispose { session?.let { s -> Thread { disconnect(s) }.start() } }
    }

    // Loads a remote directory off the main thread, holding the connection open.
    // The session is stored only once a listing succeeds, so a failed connect
    // leaves the form up (session stays null) with the error shown.
    fun browse(target: S, path: String) {
        loading = true
        error = null
        retryTarget = target
        retryPath = path
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    gate.withLock {
                        val s = session ?: newSession(target)
                        s to listWith(s, path)
                    }
                }
            }
            result.onSuccess { (s, listed) ->
                session = s
                entries = listed.sortedWith(RemoteEntryOrder)
                currentPath = path
            }.onFailure { e ->
                if (isTrustChallenge(e)) pendingTrust = e else error = e.message ?: e.toString()
            }
            loading = false
        }
    }

    // A saved server reconnects on open: skip the form and connect straight away.
    LaunchedEffect(Unit) { if (autoConnect) preset?.let { browse(it, rootPathOf(it)) } }

    // System back goes up one folder while browsing, and leaves at the root.
    BackHandler {
        val active = server
        val atRoot = currentPath.trimEnd('/').isEmpty() || currentPath == "/"
        if (session != null && active != null && !atRoot) browse(active, parentOf(currentPath)) else onBack()
    }

    val active = server
    if (session != null && active != null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                RemoteBrowseList(
                    rootLabel = rootLabelOf(active),
                    path = currentPath,
                    entries = entries,
                    loading = loading,
                    error = error,
                    onChangeSource = onChangeSource,
                    onNavigate = { browse(active, it) },
                    // 플레이어 저장 키(네트워크=keyFor)와 같게 -> 상세의 이어보기·자막·배속이 맞는다.
                    prefKeyFor = { v -> keyFor(active, v.path) },
                    imageUriFor = { uriFor(active, it) },
                    listFolder = { p -> withContext(Dispatchers.IO) { gate.withLock { listWith(session ?: newSession(active), p) } } },
                    onPlayFile = { v, subs ->
                        onOpen(
                            listOf(
                                MediaEntry(
                                    uriFor(active, v.path), v.name, keyFor(active, v.path),
                                    externalSubs = subs.map { MediaEntry.ExternalSub(uriFor(active, it.path), it.name) },
                                ),
                            ),
                            0,
                        )
                    },
                    onGlobalSearch = onGlobalSearch,
                    onPlaylist = onPlaylist,
                    onSettings = onSettings,
                    rootShelf = rootShelf,
                    isFavorite = onIsFavorite?.let { f -> { v -> f(keyFor(active, v.path)) } },
                    onToggleFavorite = onFavorite?.let { f ->
                        { v -> f(SavedItem(key = keyFor(active, v.path), name = v.name, uri = uriFor(active, v.path).toString(), source = sourceTag)) }
                    },
                    onEntry = { entry ->
                        if (entry.isDirectory) {
                            browse(active, entry.path)
                        } else {
                            val (items, index) = remotePlaylist(entries, entry, { p -> uriFor(active, p) }, { p -> keyFor(active, p) })
                            if (items.isNotEmpty()) onOpen(items, index)
                        }
                    },
                )
            }
        }
    } else {
        // 미접속: 등록 폼·접속 중·접속 실패를 전체화면 대신 가운데 팝업으로. ←·X·바깥 탭은 이전 메뉴로.
        // 저장 서버 자동 접속의 결과만 접속 중/실패 카드로 구분한다(편집 중이면 폼 유지).
        val autoConnecting = autoConnect && preset != null && error == null && !editing
        val autoFailedPreset = preset?.takeIf { autoConnect && error != null && !editing }
        NetConnectScaffold(
            title = title,
            connecting = autoConnecting,
            onLeave = onChangeSource,
            backdrop = connectBackdrop,
            failure = autoFailedPreset?.let { p ->
                {
                    // 저장 서버 실패: 등록 폼 대신 다시 시도/설정 편집/닫기. 설정을 고쳐야 할 때만
                    // 폼으로(editing=true). 다시 시도는 같은 서버로 재접속.
                    NetConnectFailedBody(
                        serverName = rootLabelOf(p),
                        reason = error,
                        onRetry = { browse(p, rootPathOf(p)) },
                        onEdit = { editing = true },
                        onLeave = onChangeSource,
                    )
                }
            },
        ) {
            connectForm(preset, loading, error) { chosen, save ->
                server = chosen
                if (save) onSave(chosen)
                browse(chosen, rootPathOf(chosen))
            }
        }
    }

    pendingTrust?.let { pending ->
        trustDialog?.invoke(
            pending,
            retryTarget ?: server,
            { pinned ->
                pendingTrust = null
                server = pinned
                onSave(pinned)
                browse(pinned, retryPath)
            },
            { msg ->
                pendingTrust = null
                if (msg != null) error = msg
            },
        )
    }
}
