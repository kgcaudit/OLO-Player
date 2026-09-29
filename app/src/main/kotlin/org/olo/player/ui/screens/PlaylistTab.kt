package org.olo.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.components.CpRow
import org.olo.player.ui.components.CpTile
import org.olo.player.ui.theme.OloTheme

/**
 * 재생목록 탭: the shelves that gather sources across local and network --
 * 즐겨찾기 · 직접 URL · 최근 방문 · 최근 재생. The shelves are framed here; their
 * contents (backed by preferences) come next, so each opens an honest empty
 * detail for now.
 */
private enum class PlaylistDest(val title: String, val icon: ImageVector, val note: String) {
    FAVORITES("즐겨찾기", Icons.Outlined.StarOutline, "즐겨 찾는 서버·폴더·URL을 모읍니다."),
    URLS("직접 URL", Icons.Outlined.Link, "직접 입력한 주소를 모읍니다."),
    VISITED("최근 방문", Icons.Outlined.History, "최근 연 서버와 폴더를 기억합니다."),
    RECENT("최근 재생", Icons.Outlined.Schedule, "이어보기 지점과 함께 최근 재생을 기억합니다."),
}

@Composable
fun PlaylistTab(model: PlayerViewModel) {
    var dest by rememberSaveable { mutableStateOf<PlaylistDest?>(null) }

    dest?.let {
        ComingSoon(it.title, it.note, onBack = { dest = null })
        return
    }

    val c = OloTheme.colors
    Column(Modifier.fillMaxSize()) {
        CpHeader("재생목록")
        for (d in PlaylistDest.entries) {
            CpRow(
                title = d.title,
                subtitle = if (d == PlaylistDest.RECENT) "이어보기 지점 기억" else null,
                leading = { CpTile(d.icon, c.accent) },
                onClick = { dest = d },
            )
        }
    }
}
