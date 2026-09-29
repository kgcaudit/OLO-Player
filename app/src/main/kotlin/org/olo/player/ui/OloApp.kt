package org.olo.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.ui.screens.LocalTab
import org.olo.player.ui.screens.NetworkTab
import org.olo.player.ui.screens.PlaylistTab
import org.olo.player.ui.screens.SettingsTab
import org.olo.player.ui.theme.OloTheme

/**
 * The app shell when no media is open: the four-tab frame the whole GUI hangs
 * from -- 로컬 · 네트워크 · 재생목록 · 설정 -- with a bottom navigation bar. Each
 * tab keeps its own inner state (a landing and, under it, a browser or detail),
 * so switching tabs and coming back lands where it left. Opening a media item
 * from any tab hands off through [model], which raises the player over this.
 */
enum class OloTab(val label: String, val icon: ImageVector) {
    LOCAL("로컬", Icons.Outlined.FolderOpen),
    NETWORK("네트워크", Icons.Outlined.Dns),
    PLAYLIST("재생목록", Icons.AutoMirrored.Outlined.PlaylistPlay),
    SETTINGS("설정", Icons.Outlined.Settings),
}

@Composable
fun OloApp(model: PlayerViewModel) {
    val c = OloTheme.colors
    var tab by rememberSaveable { mutableStateOf(OloTab.LOCAL) }
    Scaffold(
        containerColor = c.bg,
        bottomBar = {
            NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
                for (t in OloTab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label, modifier = Modifier.size(24.dp)) },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accent,
                            selectedTextColor = c.accent,
                            unselectedIconColor = c.muted,
                            unselectedTextColor = c.muted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
        ) {
            when (tab) {
                OloTab.LOCAL -> LocalTab(model)
                OloTab.NETWORK -> NetworkTab(model)
                OloTab.PLAYLIST -> PlaylistTab(model)
                OloTab.SETTINGS -> SettingsTab()
            }
        }
    }
}
