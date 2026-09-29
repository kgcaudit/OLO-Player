package org.olo.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The coarse width bands the layout adapts to, from the current configuration
 * (so it also flips on a phone rotated to landscape, not only on tablets).
 *
 * COMPACT keeps the bottom navigation bar and single-column lists; MEDIUM and
 * EXPANDED move navigation to a side rail and let lists flow into a grid, with
 * the content held to a comfortable measure rather than stretched edge to edge.
 */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
@ReadOnlyComposable
fun widthClass(): WidthClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> WidthClass.EXPANDED
        w >= 600 -> WidthClass.MEDIUM
        else -> WidthClass.COMPACT
    }
}

@Composable
@ReadOnlyComposable
fun isWide(): Boolean = widthClass() != WidthClass.COMPACT
