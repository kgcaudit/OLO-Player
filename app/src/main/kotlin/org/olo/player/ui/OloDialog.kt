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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Text
import org.olo.player.ui.theme.OloTheme

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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(c.surface)
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

/** A group heading inside a card dialog: the clay section label. */
@Composable
fun OloSectionLabel(text: String) {
    Text(
        text,
        color = OloTheme.colors.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

/**
 * One choice in a row of them: a square icon tile above a label. Selected fills
 * with clay and whitens its glyph; otherwise it is an ivory tile with an ink
 * glyph. [icon] is given the tint to draw with, so a Material vector or a pack
 * drawable both fit.
 */
@Composable
fun RowScope.OloOptionTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    sub: String? = null,
    icon: @Composable (tint: Color) -> Unit,
) {
    val c = OloTheme.colors
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(if (selected) c.accent else c.progressTrack),
            contentAlignment = Alignment.Center,
        ) {
            icon(if (selected) Color.White else c.text)
        }
        Text(
            label,
            color = if (selected) c.accent else c.text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (sub != null) Text(sub, color = c.accent, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** A labelled checkbox row for a card dialog, with an optional second line. */
@Composable
fun OloCheckRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, sub: String? = null) {
    val c = OloTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(top = 12.dp),
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
