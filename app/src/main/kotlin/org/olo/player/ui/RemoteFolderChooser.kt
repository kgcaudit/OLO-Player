package org.olo.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ui.theme.OloTheme

/**
 * 원격(네트워크) 폴더 선택기 -- 이동/복사 대상 폴더를 네트워크에서 훑어 고른다. 로컬 FolderChooser의
 * 네트워크 짝으로, File 대신 문자열 경로와 [listFolder](네트워크 조회)로 돈다. 폴더만 보이고,
 * 하단 '여기로 …'를 누르면 현재 보고 있는 경로로 확정한다. 상단 ＋로 그 자리에 새 폴더도 만든다.
 *
 * @param startPath  처음 보여줄 경로(보통 지금 보던 폴더).
 * @param rootPath   탐색 경계(이 위로는 못 올라간다). 보통 "/".
 * @param canConfirm 현재 경로를 대상으로 삼을 수 있는지(제자리·자기 하위 방지 등).
 * @param onMkdir    선택기 안에서 새 폴더를 만들 때(없으면 ＋ 숨김). 성공하면 목록을 새로고침한다.
 */
@Composable
fun RemoteFolderChooser(
    title: String,
    subtitle: String,
    startPath: String,
    rootPath: String,
    rootLabel: String,
    confirmLabel: String,
    listFolder: suspend (String) -> List<RemoteEntry>,
    canConfirm: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    onMkdir: (suspend (String) -> Unit)? = null,
) {
    val c = OloTheme.colors
    var dir by remember { mutableStateOf(startPath.ifBlank { rootPath }) }
    var showNewFolder by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // 하위 폴더 조회(네트워크) -- dir·reload가 바뀔 때마다 다시 읽는다. 로딩 동안엔 스피너.
    // (value는 아래에서 분명히 할당되지만 produceState lint가 못 잡는 알려진 오탐이라 좁게 끈다.)
    @Suppress("ProduceStateDoesNotAssignValue")
    val loaded by produceState<List<RemoteEntry>?>(initialValue = null, dir, reload) {
        value = null
        val listed = withContext(Dispatchers.IO) {
            runCatching { listFolder(dir).filter { it.isDirectory }.sortedBy { it.name.lowercase() } }.getOrDefault(emptyList())
        }
        value = listed
    }

    val atRoot = dir.trimEnd('/').removePrefix(rootPath.trimEnd('/')).isEmpty()
    BackHandler {
        if (!atRoot) dir = org.olo.player.net.remoteParentOf(dir) else onCancel()
    }

    Surface(Modifier.fillMaxSize(), color = c.bg) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(c.surface).padding(start = 16.dp, end = 6.dp, top = 16.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (onMkdir != null) {
                        IconButton(onClick = { showNewFolder = true }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "새 폴더", tint = c.accent)
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp)); Text(subtitle, color = c.muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                RemoteCrumb(rootPath = rootPath, rootLabel = rootLabel, dir = dir, onNavigate = { dir = it })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val subfolders = loaded
                when {
                    subfolders == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                    subfolders.isEmpty() -> Text(
                        "하위 폴더가 없습니다. 이 폴더에 두려면 아래 '$confirmLabel'을 누르세요.",
                        color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(18.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(subfolders, key = { it.path }) { f ->
                            RemoteChooserRow(name = f.name, onOpen = { dir = f.path })
                            Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 72.dp).background(c.divider))
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().background(c.surface).padding(14.dp)) {
                Text("현재 위치 · ${remoteCrumbText(rootPath, rootLabel, dir)}", color = c.muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                val allow = canConfirm(dir)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.dialog).clickable { onCancel() }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("취소", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    Box(
                        Modifier.weight(1.6f).clip(RoundedCornerShape(12.dp)).background(if (allow) c.accent else c.divider)
                            .then(if (allow) Modifier.clickable { onConfirm(dir) } else Modifier).padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(confirmLabel, color = if (allow) c.onAccent else c.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (showNewFolder && onMkdir != null) {
        NewFolderDialog(
            locationLabel = remoteCrumbText(rootPath, rootLabel, dir),
            // 원격은 이름 중복을 즉시 알 수 없어(네트워크 조회) 생성 시 서버가 실패로 알린다. 여기선 false.
            exists = { false },
            onCreate = { nm ->
                showNewFolder = false
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { onMkdir(org.olo.player.net.remoteJoin(dir, nm)) } }
                    reload++ // 성공·실패 무관하게 목록을 새로고침해 결과를 반영
                }
            },
            onCancel = { showNewFolder = false },
        )
    }
}

@Composable
private fun RemoteChooserRow(name: String, onOpen: () -> Unit) {
    val c = OloTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(c.tileFolder), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RemoteCrumb(rootPath: String, rootLabel: String, dir: String, onNavigate: (String) -> Unit) {
    val c = OloTheme.colors
    val segs = dir.removePrefix(rootPath).trim('/').split('/').filter { it.isNotEmpty() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            rootLabel, color = if (segs.isEmpty()) c.text else c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNavigate(rootPath.ifBlank { "/" }) },
        )
        var acc = rootPath.trimEnd('/')
        segs.forEachIndexed { i, seg ->
            acc = "$acc/$seg"
            val target = acc
            val last = i == segs.lastIndex
            Text("  ›  ", color = c.muted, fontSize = 13.sp)
            Text(
                seg, color = if (last) c.text else c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = if (last) Modifier else Modifier.clickable { onNavigate(target) },
            )
        }
    }
}

private fun remoteCrumbText(rootPath: String, rootLabel: String, dir: String): String {
    val rel = dir.removePrefix(rootPath).trim('/')
    return if (rel.isEmpty()) rootLabel else "$rootLabel/$rel"
}
