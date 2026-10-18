package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.ui.theme.OloTheme

/**
 * 다중 선택 하단 고정 바: 이동 · 복사 · 삭제. 선택이 0개면 흐리게 비활성.
 * (로컬 탐색기에서만, 선택 모드일 때 목록 위에 겹쳐 띄운다.)
 */
@Composable
fun BatchActionBar(enabled: Boolean, onMove: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val c = OloTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BatchBtn(Icons.AutoMirrored.Filled.DriveFileMove, "이동", enabled = enabled, onClick = onMove)
        BatchBtn(Icons.Filled.ContentCopy, "복사", enabled = enabled, onClick = onCopy)
        BatchBtn(Icons.Filled.Delete, "삭제", enabled = enabled, danger = true, onClick = onDelete)
    }
}

@Composable
private fun BatchBtn(icon: ImageVector, label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val c = OloTheme.colors
    val tint = when {
        !enabled -> c.muted
        danger -> Color(0xFFB23B3B)
        else -> c.accent
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 14.dp)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 6.dp),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (enabled && danger) Color(0xFFB23B3B) else if (enabled) c.text else c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** 일괄 삭제 확인 다이얼로그. */
@Composable
fun BatchDeleteDialog(count: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = OloTheme.colors
    OloCardDialog(
        title = "삭제",
        onDismiss = onCancel,
        actions = {
            OloDialogButton("취소", onClick = onCancel, primary = false)
            OloDialogButton("삭제", onClick = onConfirm)
        },
    ) {
        Text("선택한 ${count}개 항목을 삭제할까요?", color = c.text, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("폴더는 안의 내용까지 모두 삭제됩니다. 되돌릴 수 없습니다.", color = Color(0xFFB23B3B), fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
    }
}

/** 일괄 작업 권한 안내 다이얼로그('모든 파일 접근'). */
@Composable
fun BatchPermissionDialog(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
    val c = OloTheme.colors
    OloCardDialog(
        title = "권한 필요",
        onDismiss = onCancel,
        actions = {
            OloDialogButton("취소", onClick = onCancel, primary = false)
            OloDialogButton("설정 열기", onClick = onOpenSettings)
        },
    ) {
        Text("파일·폴더를 관리하려면 '모든 파일 접근' 권한이 필요합니다.", color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(6.dp))
        Text("설정에서 허용한 뒤 다시 시도해 주세요.", color = c.muted, fontSize = 11.sp)
    }
}
