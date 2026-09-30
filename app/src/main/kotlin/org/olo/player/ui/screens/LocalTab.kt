package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.olo.player.ui.LocalMedia
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.components.CpRow
import org.olo.player.ui.components.CpTile
import org.olo.player.ui.theme.OloTheme

/**
 * 로컬 탭: a landing that splits the phone's media into 비디오 · 오디오 · 내부 저장소,
 * and under each the folder browser filtered to that kind (내부 저장소 shows both).
 * The browser is the ported [LocalMedia] -- permission prompt and all -- so the
 * proven local-playback path is unchanged; only the entry framing is new.
 */
private enum class LocalDest(val title: String) {
    VIDEO("비디오"),
    AUDIO("오디오"),
    STORAGE("내부 저장소"),
}

@Composable
fun LocalTab(model: PlayerViewModel) {
    var dest by rememberSaveable { mutableStateOf<LocalDest?>(null) }

    if (dest == null) {
        val c = OloTheme.colors
        Column(Modifier.fillMaxSize()) {
            CpHeader("로컬")
            CpRow(
                title = LocalDest.VIDEO.title,
                subtitle = "기기의 모든 영상",
                leading = { CpTile(Icons.Outlined.Movie, c.tileVideo) },
                onClick = { dest = LocalDest.VIDEO },
            )
            CpRow(
                title = LocalDest.AUDIO.title,
                subtitle = "기기의 모든 음악",
                leading = { CpTile(Icons.Outlined.MusicNote, c.tileAudio) },
                onClick = { dest = LocalDest.AUDIO },
            )
            CpRow(
                title = LocalDest.STORAGE.title,
                subtitle = "폴더로 탐색",
                leading = { CpTile(Icons.Outlined.Smartphone, c.tileFolder) },
                onClick = { dest = LocalDest.STORAGE },
            )
        }
    } else {
        val current = dest!!
        BackHandler { dest = null }
        when (current) {
            // 비디오/오디오 are the whole phone aggregated under a plain header.
            LocalDest.VIDEO, LocalDest.AUDIO -> Column(Modifier.fillMaxSize()) {
                CpHeader(current.title, onBack = { dest = null })
                LocalLibrary(model, video = current == LocalDest.VIDEO)
            }
            // 내부 저장소 walks folders through the shared browse list, which brings
            // its own header (source · breadcrumb · 검색 · ⋮), so no CpHeader here.
            LocalDest.STORAGE -> LocalMedia(
                onOpenMedia = { model.openLocalMedia(it) },
                onBack = { dest = null },
            )
        }
    }
}
