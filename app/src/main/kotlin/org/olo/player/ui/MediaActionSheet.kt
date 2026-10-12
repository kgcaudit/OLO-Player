package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
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
 * 앨범아트/가사 하위 작업 시트. 편집기엔 '커버 ›' '가사 ›' 버튼만 두고, 세부 작업(찾기·기기에서·
 * 삭제)은 이 시트에 모은다 -- 편집 공간을 심플·체계적으로 유지하기 위함(사용자 요청).
 *
 * 저장·권한·네트워크는 호스트가 콜백으로 처리한다. 이 파일은 표현·분기만 담당한다.
 */

/** 시트 안의 작업 한 줄: 아이콘 배지 + 제목 + 설명. 삭제는 위험색([danger]). 비활성이면 흐리게. */
@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    desc: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = OloTheme.colors
    val tone = if (danger) Color(0xFFB23B3B) else c.accent
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.14f * alpha)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tone.copy(alpha = alpha), modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = (if (danger) tone else c.text).copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = c.muted.copy(alpha = alpha), fontSize = 11.sp)
        }
    }
}

/**
 * 앨범아트 작업 시트: 온라인에서 찾기 · 기기에서 선택 · 삭제. [hasArt]가 없으면 삭제는 흐리게.
 */
@Composable
fun AlbumArtActionSheet(
    hasArt: Boolean,
    onFind: () -> Unit,
    onPickLocal: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    OloCardDialog(title = "앨범아트", onDismiss = onDismiss, dismissLabel = "닫기") {
        AlbumArtActionBody(hasArt, onFind, onPickLocal, onRemove)
    }
}

/** 앨범아트 시트 알맹이(대조 렌더가 다이얼로그 밖에서도 같은 몸을 쓰도록 분리). */
@Composable
internal fun AlbumArtActionBody(hasArt: Boolean, onFind: () -> Unit, onPickLocal: () -> Unit, onRemove: () -> Unit) {
    ActionRow(Icons.Filled.Search, "온라인에서 찾기", "iTunes · MusicBrainz 커버 검색", onClick = onFind)
    ActionRow(Icons.Filled.PhotoLibrary, "기기에서 선택", "사진을 정사각으로 잘라 지정", onClick = onPickLocal)
    ActionRow(Icons.Filled.Delete, "앨범아트 삭제", "파일에 심긴 커버 제거", danger = true, enabled = hasArt, onClick = onRemove)
}

/**
 * 가사 작업 시트: 가사 찾기 · 기기에서(.lrc 불러오기) · 삭제. [hasLyrics]가 없으면 삭제는 흐리게.
 */
@Composable
fun LyricsActionSheet(
    hasLyrics: Boolean,
    onFind: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    OloCardDialog(title = "가사", onDismiss = onDismiss, dismissLabel = "닫기") {
        LyricsActionBody(hasLyrics, onFind, onImport, onRemove)
    }
}

/** 가사 시트 알맹이(대조 렌더용 분리). */
@Composable
internal fun LyricsActionBody(hasLyrics: Boolean, onFind: () -> Unit, onImport: () -> Unit, onRemove: () -> Unit) {
    ActionRow(Icons.Filled.Search, "가사 찾기", "LRCLIB 검색 · 동기/일반 저장", onClick = onFind)
    ActionRow(Icons.Filled.Description, "기기에서 (.lrc)", "저장된 가사 파일 불러오기", onClick = onImport)
    ActionRow(Icons.Filled.Delete, "저장된 가사 삭제", ".lrc · 앱 보관 제거", danger = true, enabled = hasLyrics, onClick = onRemove)
}
