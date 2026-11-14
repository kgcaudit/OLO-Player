package org.olo.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.olo.player.ui.screens.OloHome
import org.olo.player.ui.theme.OloTheme

/**
 * The app shell when no media is open: one unified browse space (로컬 + 네트워크
 * merged into a 위치 list) with 재생목록 and 설정 reached from the top action bar --
 * no bottom navigation. The frame stays the same in every orientation; the layout
 * inside [OloHome] adapts to width (a single column on a phone, a source rail beside
 * a content pane on a tablet/unfolded screen). Opening a media item raises the
 * player over this through [model].
 */
@Composable
fun OloApp(model: PlayerViewModel) {
    val c = OloTheme.colors

    // The browsing UI follows the applied theme (not the OS, which can differ when
    // the theme is forced in 설정): a light background needs dark system icons, a
    // dark one needs light. The player flips these while it is up and hands them
    // back here on the way out.
    val view = androidx.compose.ui.platform.LocalView.current
    val darkIconsForLightBg = !c.isDark
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIconsForLightBg
            isAppearanceLightNavigationBars = darkIconsForLightBg
        }
    }

    // A bare Scaffold (no top or bottom bar) still consumes the system-bar insets
    // into its content padding, which the browse screens were written against; the
    // home and the leaf browsers sit inside that safe area.
    Scaffold(containerColor = c.bg) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            OloHome(model)
        }
    }
}
