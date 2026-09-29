package org.olo.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.olo.player.ui.FilePickerScreen
import org.olo.player.ui.MediaViewerScreen
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.theme.OloPlayerTheme

/**
 * The one screen the app has: a file picker until a media file is opened, then
 * the player over it. Playback itself lives in [org.olo.player.playback.PlaybackService],
 * so a film or a song carries on when this activity is put in the background.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OloPlayerTheme {
                val model: PlayerViewModel = viewModel()
                val viewer = model.mediaViewer
                if (viewer != null) {
                    MediaViewerScreen(viewer = viewer, model = model)
                } else {
                    FilePickerScreen(onOpenMedia = { model.openMedia(it) })
                }
            }
        }
    }
}
