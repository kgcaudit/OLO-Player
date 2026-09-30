package org.olo.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The OLO design system, ported from crosspoint-reader's design handoff.
 *
 * Why a bespoke token set on top of Material3: the system is light-first and
 * carries semantic colours Material3 has no slot for -- a divider, a muted text,
 * an outline, semantic tile colours per media kind. Those live in [OloColors],
 * reached through [LocalOloColors] (and the [colors] shortcut). The handful that
 * Material3 does own -- accent, surface, text -- are mapped into its ColorScheme
 * too, so screens still written against MaterialTheme.colorScheme (the player)
 * keep working and land on the same palette.
 */
@Immutable
data class OloColors(
    val bg: Color,
    val surface: Color,
    val dialog: Color,
    val text: Color,
    val muted: Color,
    val divider: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val progressTrack: Color,
    // File-kind tile colours, ported from OLO Explorer: kinds are told apart by
    // hue (not brightness), a hue-distinct tile per kind under a two-tone white
    // glyph. See FileKind / tileColorFor.
    val tileFolder: Color,
    val tileImage: Color,
    val tileVideo: Color,
    val tileAudio: Color,
    val tileDocument: Color,
    val tileArchive: Color,
    val tileCode: Color,
    val tileApp: Color,
    val tileOther: Color,
    val isDark: Boolean,
)

private val LightOlo = OloColors(
    bg = Color(0xFFF7F4EF),
    surface = Color(0xFFFCFAF6),
    dialog = Color(0xFFEDE8DF),
    text = Color(0xFF1D1A16),
    muted = Color(0xFF574D45),
    divider = Color(0xFFD6CCC1),
    outline = Color(0xFF8B7F74),
    accent = Color(0xFFB95B3B),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFF6E0D6),
    onAccentContainer = Color(0xFF4A1E0C),
    progressTrack = Color(0xFFDCD3C6),
    tileFolder = Color(0xFFB95B3B),
    tileImage = Color(0xFF2E8B6B),
    tileVideo = Color(0xFF6A5A9E),
    tileAudio = Color(0xFFB04A6A),
    tileDocument = Color(0xFF55606B),
    tileArchive = Color(0xFF8A6A3B),
    tileCode = Color(0xFF3E7F80),
    tileApp = Color(0xFF4C7A3E),
    tileOther = Color(0xFF7A7168),
    isDark = false,
)

private val DarkOlo = OloColors(
    bg = Color(0xFF181613),
    surface = Color(0xFF1C1A16),
    dialog = Color(0xFF2B2723),
    text = Color(0xFFE8E3DA),
    muted = Color(0xFFCFC5B9),
    divider = Color(0xFF49423A),
    outline = Color(0xFF978C80),
    accent = Color(0xFFE8A183),
    onAccent = Color(0xFF12100E),
    accentContainer = Color(0xFF8A3E22),
    onAccentContainer = Color(0xFFF6E0D6),
    progressTrack = Color(0xFF39434D),
    tileFolder = Color(0xFFD1734F),
    tileImage = Color(0xFF3FA383),
    tileVideo = Color(0xFF8A7AC0),
    tileAudio = Color(0xFFC96B88),
    tileDocument = Color(0xFF6E7A86),
    tileArchive = Color(0xFFB08A54),
    tileCode = Color(0xFF55A0A1),
    tileApp = Color(0xFF69985A),
    tileOther = Color(0xFF938A80),
    isDark = true,
)

val LocalOloColors = staticCompositionLocalOf { LightOlo }

/** The OLO tokens in scope. Read as `OloTheme.colors.accent` inside a composable. */
object OloTheme {
    val colors: OloColors
        @Composable get() = LocalOloColors.current
}

// The Material3 scheme mirrors the OLO tokens so MaterialTheme.colorScheme callers
// (the player screen) land on the same palette. System font throughout -- the
// handoff asks for the platform typeface, so no fontFamily is set.
private fun oloColorScheme(c: OloColors) =
    if (c.isDark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentContainer,
            onPrimaryContainer = c.onAccentContainer,
            background = c.bg,
            onBackground = c.text,
            surface = c.surface,
            onSurface = c.text,
            surfaceVariant = c.dialog,
            onSurfaceVariant = c.muted,
            outline = c.outline,
            outlineVariant = c.divider,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentContainer,
            onPrimaryContainer = c.onAccentContainer,
            background = c.bg,
            onBackground = c.text,
            surface = c.surface,
            onSurface = c.text,
            surfaceVariant = c.dialog,
            onSurfaceVariant = c.muted,
            outline = c.outline,
            outlineVariant = c.divider,
        )
    }

// The OLO type scale mapped onto Material's slots: title 19/25 bold, body 16/24,
// label 14/20 bold, subtitle 14/20, caption 12/16. Small, calm, system-drawn.
private val OloTypography = Typography(
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun OloPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkOlo else LightOlo
    androidx.compose.runtime.CompositionLocalProvider(LocalOloColors provides colors) {
        MaterialTheme(
            colorScheme = oloColorScheme(colors),
            typography = OloTypography,
            content = content,
        )
    }
}
