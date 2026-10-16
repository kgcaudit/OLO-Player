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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    val subfolders = remember(dir) {
        dir.listFiles()?.asList().orEmpty()
            .filter { it.isDirectory && it.canRead() }
            .sortedBy { it.name.lowercase() }
    }
    val atRoot = dir.path == root.path

    // 뒤로가기는 상위 폴더로, 루트에선 취소.
    BackHandler {
        val parent = dir.parentFile
        if (!atRoot && parent != null && parent.path.length >= root.path.length) dir = parent else onCancel()
    }

    Surface(Modifier.fillMaxSize(), color = c.bg) {
        Column(Modifier.fillMaxSize()) {
            // 헤더 + 브레드크럼
            Column(Modifier.fillMaxWidth().background(c.surface).padding(16.dp)) {
                Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
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
}

@Composable
private fun ChooserRow(folder: File, onOpen: () -> Unit) {
    val c = OloTheme.colors
    val subCount = remember(folder) {
        folder.listFiles()?.count { it.isDirectory } ?: 0
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
