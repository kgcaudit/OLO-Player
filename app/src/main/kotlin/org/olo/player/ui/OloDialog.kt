package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        Column(
            Modifier
                .width(size.width)
                .heightIn(max = size.height)
                .clip(RoundedCornerShape(26.dp))
                .background(c.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Text(title, color = if (titleAccent) c.accent else c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            content()
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actions != null) actions()
                else if (dismissLabel != null) OloDialogButton(dismissLabel, onClick = onDismiss)
            }
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

/** A labelled checkbox row for a card dialog (or a settings screen), with an
 *  optional second line. */
@Composable
fun OloCheckRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, sub: String? = null, modifier: Modifier = Modifier) {
    val c = OloTheme.colors
    Row(
        modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                .background(if (checked) c.accent else Color.Transparent)
                .border(if (checked) 0.dp else 2.dp, c.outline, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(label, color = c.text, fontSize = 16.sp)
            if (sub != null) Text(sub, color = c.muted, fontSize = 12.sp)
        }
    }
}
