package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import org.olo.player.ui.theme.OloTheme

/**
 * 모든 다이얼로그 공통 속성: Compose Dialog의 플랫폼 기본 폭(좁은 세로형)을 꺼, 가로(누운
 * 폰)에서도 다이얼로그가 넓어질 수 있게 한다. 이게 없으면 가로에서도 좁은 폭에 갇혀 내용이
 * 세로로 길어지고 짧은 높이에서 바닥이 잘린다.
 */
val OloDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

/**
 * 반응형 다이얼로그 최대 크기. 가로(누운 폰)는 넓고 낮게(2열 등 여유), 세로는 적당한 폭.
 * 폭은 definite로 줘 다이얼로그가 가운데로 모이고, 높이는 화면의 90~92%로 묶어 넘치면
 * 안에서 스크롤한다. [wide]=내용 많은 다이얼로그(재생 설정·상세 등)는 더 넓게, 단순 카드는
 * 과하게 늘어나지 않게 좁게 둔다.
 */
@Composable
fun rememberDialogMaxSize(wide: Boolean = true): DpSize {
    val cfg = LocalConfiguration.current
    val w = cfg.screenWidthDp.dp
    val h = cfg.screenHeightDp.dp
    val landscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    return if (landscape) {
        DpSize((w * 0.92f).coerceAtMost(if (wide) 720.dp else 520.dp), h * 0.92f)
    } else {
        DpSize((w * 0.94f).coerceAtMost(if (wide) 460.dp else 420.dp), h * 0.90f)
    }
}

/**
 * The app's dialog shell (after OLO Explorer's 보기 옵션): a centred card, not a
 * full-width sheet -- a title at the top, the body in [content], and a 닫기 at the
 * foot. The one window shape every choice-and-toggle dialog in the app takes, so
 * they read as one family.
 */
@Composable
fun OloCardDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String? = "닫기",
    titleAccent: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OloTheme.colors
    val size = rememberDialogMaxSize(wide = false)
    Dialog(onDismissRequest = onDismiss, properties = OloDialogProperties) {
        // 공통 조밀 스펙(통일): 제목 18sp · 패딩 18dp · 발 12dp. 짧은 작업 시트부터 긴 설정까지
        // 모든 카드 다이얼로그가 같은 몸을 써, 내용 대비 과하게 크지 않게 한다.
        Column(
            Modifier
                .width(size.width)
                .heightIn(max = size.height)
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(title, color = if (titleAccent) c.accent else c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            content()
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actions != null) actions()
                else if (dismissLabel != null) OloDialogButton(dismissLabel, onClick = onDismiss)
            }
        }
    }
}

/**
 * 공용 작업 행(아이콘 배지 + 제목 + 짧은 설명). 파일/폴더 작업·앨범아트/가사 시트가 같은 몸을
 * 쓴다(종전 OpRow·ActionRow 중복 통합). 위험 작업은 붉은색([danger]), 비활성이면 흐리게.
 */
@Composable
fun OloActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = OloTheme.colors
    val tone = if (danger) androidx.compose.ui.graphics.Color(0xFFB23B3B) else c.accent
    val a = if (enabled) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tone.copy(alpha = 0.14f * a)),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(icon, contentDescription = null, tint = tone.copy(alpha = a), modifier = Modifier.size(16.dp)) }
        androidx.compose.foundation.layout.Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = (if (danger) tone else c.text).copy(alpha = a), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = c.muted.copy(alpha = a), fontSize = 11.sp)
        }
    }
}

/** A text button for a card dialog's foot: clay when it is the affirmative one,
 *  muted when it steps back. */
@Composable
fun OloDialogButton(label: String, onClick: () -> Unit, primary: Boolean = true) {
    val c = OloTheme.colors
    Text(
        label,
        color = if (primary) c.accent else c.muted,
        fontSize = 16.sp,
        fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
