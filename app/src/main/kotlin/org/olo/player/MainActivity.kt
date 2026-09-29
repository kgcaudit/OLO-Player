package org.olo.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.olo.player.ui.MediaViewerScreen
import org.olo.player.ui.OloApp
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.theme.OloPlayerTheme

/**
 * The app's two faces: the four-tab shell ([OloApp]) for finding media, and the
 * player ([MediaViewerScreen]) raised over it once something is opened. Playback
 * itself lives in [org.olo.player.playback.PlaybackService], so a film or a song
 * carries on when this activity is put in the background.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The view model is created above the theme so the chosen theme mode
            // (설정 › 일반) can drive OloPlayerTheme: "system" follows the OS, else
            // it is forced light or dark, and a change recomposes the whole app.
            val model: PlayerViewModel = viewModel()
            val dark = when (model.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            OloPlayerTheme(darkTheme = dark) {
                val viewer = model.mediaViewer
                if (viewer != null) {
                    MediaViewerScreen(viewer = viewer, model = model)
                } else {
                    OloApp(model = model)
                }
            }
        }
    }
}
