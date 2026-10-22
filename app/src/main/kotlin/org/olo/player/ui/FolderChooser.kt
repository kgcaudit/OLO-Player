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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.olo.player.art.LocalFileOps
import org.olo.player.ui.components.CpField
import org.olo.player.ui.theme.OloTheme

/**
 * 앱 자체 폴더 선택기(이동/다른 폴더로 복사 대상 고르기). 안드로이드 시스템 SAF 선택기 대신
 * 기존 탐색기와 같은 OLO 룩으로 폴더만 훑어 하단 '여기로 이동'을 누른다. '모든 파일 접근'
 * 권한이 있을 때만 쓰며(호출부가 보장), File 경로를 직접 돌려줘 이동은 rename으로 즉시 끝난다.
 *
 * @param startDir  처음 보여줄 폴더(보통 지금 보던 폴더).
 * @param root      탐색 경계(이 위로는 못 올라간다)와 브레드크럼 맨 앞 라벨의 기준.
 * @param canConfirm 현재 폴더를 대상으로 삼을 수 있는지(예: 원본이 있는 폴더·자기 자신으론 못 옮김).
 * @param onConfirm 고른 대상 폴더(현재 보고 있는 폴더)로 확정.
 */
@Composable
fun FolderChooser(
    title: String,
    subtitle: String,
    startDir: File,
    root: File,
    rootLabel: String,
    confirmLabel: String,
    canConfirm: (File) -> Boolean,
    onConfirm: (File) -> Unit,
    onCancel: () -> Unit,
) {
    val c = OloTheme.colors
    var dir by remember { mutableStateOf(startDir) }
    var showNewFolder by remember { mutableStateOf(false) }
    // 하위 폴더 조회는 디스크 I/O -- 메인 스레드에서 돌리면 폴더가 크거나 네트워크 볼륨일 때 멎는다.
    // produceState로 IO에 넘기고, dir이 바뀔 때마다 다시 읽는다. (아래 value 대입을 lint가 못 잡는
    // 알려진 오탐이라 이 규칙만 좁게 끈다.)
    @Suppress("ProduceStateDoesNotAssignValue")
    val subfolders by produceState(initialValue = emptyList<File>(), dir) {
        value = withContext(Dispatchers.IO) {
            dir.listFiles()?.asList().orEmpty()
                .filter { it.isDirectory && it.canRead() }
                .sortedBy { it.name.lowercase() }
        }
    }
    val atRoot = dir.path == root.path

    // 뒤로가기는 상위 폴더로, 루트에선 취소.
    BackHandler {
        val parent = dir.parentFile
        if (!atRoot && parent != null && parent.path.length >= root.path.length) dir = parent else onCancel()
    }

    Surface(Modifier.fillMaxSize(), color = c.bg) {
        Column(Modifier.fillMaxSize()) {
            // 헤더 + 브레드크럼 (+ 새 폴더)
            Column(Modifier.fillMaxWidth().background(c.surface).padding(start = 16.dp, end = 6.dp, top = 16.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "새 폴더", tint = c.accent)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = c.muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                ChooserCrumb(root = root, rootLabel = rootLabel, dir = dir, onNavigate = { dir = it })
            }

            // 폴더만 표시(파일 숨김). 탭하면 안으로 진입.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (subfolders.isEmpty()) {
                    Text(
                        "하위 폴더가 없습니다. 이 폴더로 옮기려면 아래 '$confirmLabel'을 누르세요.",
                        color = c.muted, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(subfolders, key = { it.path }) { f ->
                            ChooserRow(folder = f, onOpen = { dir = f })
                            Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 72.dp).background(c.divider))
                        }
                    }
                }
            }

            // 하단 고정 바: 현재 위치 + 취소/확정.
            Column(Modifier.fillMaxWidth().background(c.surface).padding(14.dp)) {
                Text("현재 위치 · ${crumbText(root, rootLabel, dir)}", color = c.muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                val allow = canConfirm(dir)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.dialog)
                            .clickable { onCancel() }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("취소", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    Box(
                        Modifier.weight(1.6f).clip(RoundedCornerShape(12.dp))
                            .background(if (allow) c.accent else c.divider)
                            .then(if (allow) Modifier.clickable { onConfirm(dir) } else Modifier)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            confirmLabel,
                            color = if (allow) c.onAccent else c.muted,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        NewFolderDialog(
            locationLabel = crumbText(root, rootLabel, dir),
            exists = { File(dir, it).exists() },
            onCreate = { nm ->
                showNewFolder = false
                val target = File(dir, nm)
                if (target.mkdirs()) dir = target // 만든 폴더로 바로 진입
            },
            onCancel = { showNewFolder = false },
        )
    }
}

/**
 * 새 폴더 이름 입력 다이얼로그. 이름 검증(빈 이름·금지문자·중복)은 여기서 하고, 실제 생성(mkdir)은
 * [onCreate]를 받은 호출부가 한다 -- 폴더 선택기(현재 위치)와 탐색기(현재 폴더) 둘 다 재사용한다.
 */
@Composable
fun NewFolderDialog(
    locationLabel: String,
    exists: (String) -> Boolean,
    onCreate: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val c = OloTheme.colors
    var name by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    OloCardDialog(
        title = "새 폴더",
        onDismiss = onCancel,
        actions = {
            OloDialogButton("취소", onClick = onCancel, primary = false)
            OloDialogButton("만들기", onClick = {
                val nm = name.trim()
                when {
                    !LocalFileOps.isValidName(nm) -> err = "이름이 올바르지 않습니다."
                    exists(nm) -> err = "같은 이름이 이미 있습니다."
                    else -> onCreate(nm)
                }
            })
        },
    ) {
        CpField(label = "새 폴더 이름", value = name, onValueChange = { name = it; err = null })
        err?.let { Text(it, color = Color(0xFFB23B3B), fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp)) }
        Text("현재 위치에 만듭니다 · $locationLabel", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ChooserRow(folder: File, onOpen: () -> Unit) {
    val c = OloTheme.colors
    // 하위 폴더 수도 디스크 I/O -- 각 행이 메인 스레드에서 세면 목록 스크롤이 끊긴다. IO로 넘긴다.
    // (value 대입을 lint가 못 잡는 알려진 오탐이라 이 규칙만 좁게 끈다.)
    @Suppress("ProduceStateDoesNotAssignValue")
    val subCount by produceState(initialValue = 0, folder) {
        value = withContext(Dispatchers.IO) { folder.listFiles()?.count { it.isDirectory } ?: 0 }
    }
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(c.tileFolder), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(if (subCount > 0) "하위 폴더 ${subCount}개" else "폴더", color = c.muted, fontSize = 11.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
    }
}

// 루트 라벨 + 현재 경로의 폴더들을 칩으로. 각 칩을 누르면 그 깊이로 되돌아간다.
@Composable
private fun ChooserCrumb(root: File, rootLabel: String, dir: File, onNavigate: (File) -> Unit) {
    val c = OloTheme.colors
    val segs = dir.path.removePrefix(root.path).trim('/').split('/').filter { it.isNotEmpty() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            rootLabel,
            color = if (segs.isEmpty()) c.text else c.accent,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNavigate(root) },
        )
        var acc = root
        segs.forEachIndexed { i, seg ->
            acc = File(acc, seg)
            val target = acc
            val last = i == segs.lastIndex
            Text("  ›  ", color = c.muted, fontSize = 13.sp)
            Text(
                seg,
                color = if (last) c.text else c.accent,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = if (last) Modifier else Modifier.clickable { onNavigate(target) },
            )
        }
    }
}

private fun crumbText(root: File, rootLabel: String, dir: File): String {
    val rel = dir.path.removePrefix(root.path).trim('/')
    return if (rel.isEmpty()) rootLabel else "$rootLabel/$rel"
}
