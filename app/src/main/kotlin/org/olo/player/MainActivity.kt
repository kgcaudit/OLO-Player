package org.olo.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.olo.player.playback.PlaybackService
import org.olo.player.ui.MediaViewerScreen
import org.olo.player.ui.OloApp
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.theme.OloPlayerTheme

/**
 * The app's two faces: the four-tab shell ([OloApp]) for finding media, and the
 * player ([MediaViewerScreen]) raised over it once something is opened. Playback
 * itself lives in [org.olo.player.playback.PlaybackService], so a film or a song
 * carries on when this activity is put in the background.
 *
 * The activity also drives picture-in-picture: when the person leaves while a
 * video plays, it shrinks into a floating window that keeps playing, with a single
 * play/pause action. It talks to the same session the player screen does, through
 * its own lightweight controller, so PiP needs nothing threaded down from Compose.
 */
class MainActivity : ComponentActivity() {

    private val model: PlayerViewModel by viewModels()
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    // The PiP window's play/pause action fires this back to us; we toggle the same
    // controller the window is showing.
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        // Keep the action's icon and the window's shape current as play state and
        // the video's size change.
        override fun onIsPlayingChanged(isPlaying: Boolean) = updatePipParams()
        override fun onVideoSizeChanged(videoSize: VideoSize) = updatePipParams()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this, pipReceiver, IntentFilter(ACTION_PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            // The view model is created above the theme so the chosen theme mode
            // (설정 › 일반) can drive OloPlayerTheme: "system" follows the OS, else
            // it is forced light or dark, and a change recomposes the whole app.
            val dark = when (model.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            OloPlayerTheme(darkTheme = dark) {
                // The player is overlaid ON TOP of the browse shell, not swapped in for
                // it, so OloApp stays composed while a film plays. Closing the player
                // then returns to the exact folder (and connection) the person was in,
                // rather than rebuilding the shell from its 홈 start.
                val viewer = model.mediaViewer
                Box(Modifier.fillMaxSize()) {
                    OloApp(model = model)
                    if (viewer != null) {
                        MediaViewerScreen(viewer = viewer, model = model)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()
                controller?.addListener(playerListener)
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    override fun onStop() {
        super.onStop()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(pipReceiver) }
    }

    // Leaving (Home / recents) while a video plays shrinks it into PiP instead of
    // just backgrounding, so the picture stays on screen.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldEnterPip()) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        model.onPipModeChanged(isInPictureInPictureMode)
    }

    private fun shouldEnterPip(): Boolean {
        val c = controller ?: return false
        // Only a playing video: a song has nothing to show, and a paused film would
        // freeze in the window.
        return model.mediaViewer != null && c.videoSize.height > 0 && c.isPlaying
    }

    private fun updatePipParams() {
        if (isInPictureInPictureMode) runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun pipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        controller?.videoSize?.takeIf { it.width > 0 && it.height > 0 }?.let {
            builder.setAspectRatio(clampAspect(it.width, it.height))
        }
        builder.setActions(listOf(toggleAction(controller?.isPlaying == true)))
        return builder.build()
    }

    private fun toggleAction(isPlaying: Boolean): RemoteAction {
        val icon = Icon.createWithResource(
            this,
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        val label = if (isPlaying) "일시정지" else "재생"
        val intent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_PIP_TOGGLE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(icon, label, label, intent)
    }

    // Android accepts a PiP aspect only within roughly [0.418, 2.39]; clamp so an
    // unusually wide or tall video is still let into the window.
    private fun clampAspect(width: Int, height: Int): Rational {
        val ratio = width.toFloat() / height.toFloat()
        return when {
            ratio < MIN_ASPECT -> Rational(418, 1000)
            ratio > MAX_ASPECT -> Rational(239, 100)
            else -> Rational(width, height)
        }
    }

    companion object {
        const val ACTION_PIP_TOGGLE = "org.olo.player.PIP_TOGGLE"
        private const val MIN_ASPECT = 0.418f
        private const val MAX_ASPECT = 2.39f
    }
}
