package org.olo.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.ui.theme.OloTheme

// OLO's shared UI parts (the "Cp" family from the design handoff), each drawing
// itself from the OLO tokens so every screen reads as one system: a header with
// a small title, list rows with a semantic tile, a settings row, a toggle, a
// section label, a hairline divider. Dimensions come straight from the handoff
// (gutter 16, row 64, tile 40/corner 12, touch 48).

private val Gutter = 16.dp

/** Top bar: an optional back arrow, a small title, and trailing actions. */
@Composable
fun CpHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = OloTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CpIconButton(Icons.AutoMirrored.Outlined.ArrowBack, onClick = onBack)
        } else {
            Spacer(Modifier.width(Gutter - 4.dp))
        }
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** A 48dp touch target holding a 24dp line icon; the header/action button. */
@Composable
fun CpIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val c = OloTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: c.text, modifier = Modifier.size(24.dp))
    }
}

/** Accent section label above a group of rows: small, bold, letter-spaced. */
@Composable
fun CpSectionLabel(text: String) {
    Text(
        text,
        color = OloTheme.colors.accent,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A hairline divider inset to the text column. */
@Composable
fun CpDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(OloTheme.colors.divider),
    )
}

/** A 40dp rounded tile carrying a media-kind icon, its colour semantic. */
@Composable
fun CpTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFF4F1EC), modifier = Modifier.size(22.dp))
    }
}

/**
 * The staple list row: a leading slot (usually a [CpTile]), a title with an
 * optional subtitle, and a trailing slot (a chevron by default). 64dp tall.
 */
@Composable
fun CpRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { CpChevron() },
) {
    val c = OloTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

/** The right-pointing chevron that ends a navigating row. */
@Composable
fun CpChevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = OloTheme.colors.outline,
        modifier = Modifier.size(22.dp),
    )
}

/**
 * A settings row: a label, an optional value line under it, and a trailing slot
 * (a chevron for a sub-screen, a [CpToggle] for a switch, text for a value).
 */
@Composable
fun CpSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { CpChevron() },
) {
    val c = OloTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 16.sp, lineHeight = 22.sp)
            if (value != null) {
                Text(value, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        if (trailing != null) trailing()
    }
}

/** A switch tinted with the accent, over the OLO track colours. */
@Composable
fun CpToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = OloTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = c.outline,
            uncheckedBorderColor = c.outline,
            checkedBorderColor = c.accent,
        ),
    )
}
