package org.olo.player.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.outlined.ScreenLockRotation
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.olo.player.R
import org.olo.player.data.AppPreferences
import org.olo.player.playback.PlaybackService
import org.olo.player.playback.SubtitleBundle
import org.olo.player.art.Lyrics
import org.olo.player.art.LyricsClient
import org.olo.player.art.LyricsParser
import org.olo.player.art.readRemote
import org.olo.player.subtitle.SubtitleCue
import org.olo.player.subtitle.SubtitleCues
import org.olo.player.subtitle.SubtitleSidecar
import org.olo.player.ui.components.CpSlimSlider
import org.olo.player.ui.theme.OloTheme
import org.olo.player.viewer.TextFiles

/**
 * The app's own player for a video or a sound.
 *
 * A folder of them opens as a playlist so one runs on to the next, and each
 * file remembers where it was left so it reopens there rather than at the
 * start. Video takes the whole black screen with the system bars out of the
 * way; a sound shows the same controls with nothing to look at behind them.
 *
 * A film's own subtitles, if they sit beside it as separate files, are picked
 * up and offered. The top bar keeps clear of a camera notch, a button turns
 * the screen on its side, and a finger dragged across scrubs forward or back.
 *
 * The engine is ExoPlayer, the controls are its own PlayerView -- both marked
 * unstable by the library, hence the opt-in -- so this file is thin: it wires a
 * playlist in, keeps the place, and lays a few things over the top.
 */
// media3 marks these APIs unstable through androidx's opt-in, not Kotlin's, so
// the annotation is androidx.annotation.OptIn rather than kotlin's @OptIn.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(viewer: PlayerViewModel.MediaViewer, model: PlayerViewModel) {
    val context = LocalContext.current

    // A sound gets the music player, a film gets the video player. The playlist
    // is one kind throughout (song with song, film with film), so the opened file
    // decides -- and the choice is made up here because a film hides the system
    // bars for its picture while a song leaves them, for the clock and battery.
    val isAudio = remember(viewer) {
        viewer.items.getOrNull(viewer.index)?.let { kindOf(it.name, false) == FileKind.AUDIO }
            ?: false
    }

    // Playback lives in a service so it carries on once the app is in the
    // background; this connects to it from the front, and is null until it has.
    val player = rememberMediaController(context)

    // Ask once for the notification permission the background player needs to
    // show its controls. Playback works without it, only without a notification.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Leaving on purpose -- the back arrow or the system back -- stops the sound
    // and clears the notification. Leaving the app (home, screen off) does
    // neither, so that keeps playing in the background. The place is saved
    // before the playlist is cleared, or leaving would lose it and the film
    // would reopen at the start.
    val close: () -> Unit = {
        player?.let {
            savePlaybackPosition(it, viewer.items, model)
            it.stop()
            it.clearMediaItems()
        }
        model.closeMediaViewer()
    }

    // While a film is up, the phone's bars go away so it has the whole screen;
    // leaving restores them. A song keeps the bars -- there is no picture to give
    // the screen to, and the clock is worth having while listening.
    val view = androidx.compose.ui.platform.LocalView.current
    // The browsing UI wants dark status icons on its light background (light
    // icons in the dark theme); the player is dark whatever the theme, so its
    // status/nav icons must be light or the clock and battery vanish over it.
    val browsingLightIcons = !org.olo.player.ui.theme.OloTheme.colors.isDark
    DisposableEffect(view, isAudio) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        // Light (white) system-bar icons while the dark player is up.
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        if (!isAudio) {
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Hand the icons back to the browsing default on the way out.
            controller?.isAppearanceLightStatusBars = browsingLightIcons
            controller?.isAppearanceLightNavigationBars = browsingLightIcons
        }
    }

    BackHandler(onBack = close)

    if (player == null) {
        // Connecting: a black hold with a way back, so a slow connect is never
        // a dead screen.
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                IconButton(onClick = close, modifier = Modifier.statusBarsPadding()) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White,
                    )
                }
            }
        }
        return
    }

    if (isAudio) {
        MusicPlayer(player = player, viewer = viewer, model = model, onClose = close)
    } else {
        MediaPlayer(player = player, viewer = viewer, model = model, onClose = close)
    }
}

/**
 * The music player -- the Now Playing screen for a sound.
 *
 * A film fills the black screen with a picture; a song has nothing to look at, so
 * this draws the album's own cover instead -- large and square, over a blurred
 * blow-up of the same cover -- and gives the transport a music player's shape:
 * shuffle, previous, play, next and repeat, with the folder's other songs a tap
 * away in a queue. The engine is the same service player the film uses, so the
 * song carries on in the background with its notification, and its place is kept
 * between openings the same way.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MusicPlayer(
    player: MediaController,
    viewer: PlayerViewModel.MediaViewer,
    model: PlayerViewModel,
    onClose: () -> Unit,
) {
    var index by remember { mutableIntStateOf(viewer.index) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    // While a finger is on the seek bar the ticking read-out is held back, so the
    // thumb follows the finger rather than jumping back to where the song is.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    var showQueue by remember { mutableStateOf(false) }
    // 음성도 속도를 조절할 수 있게(오디오북·강의). 플레이어는 서비스 공용이라 영상에서 바꾼
    // 속도가 묻어올 수 있어, 현재 값을 그대로 비춘다(아래에서 곡을 열 땐 1.0x로 시작한다).
    var showSpeed by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(player.playbackParameters.speed) }

    // Keep the place, mirror the player's state, and put a song the player runs on
    // from back to its start -- the same bookkeeping the film player does.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    viewer.items.getOrNull(index)?.let { model.setMediaPosition(it, 0L) }
                }
                index = player.currentMediaItemIndex
                // 다음 곡의 저장된 배속을 적용한다(파일별 기억). 없으면 음악 기본 1.0x.
                viewer.items.getOrNull(index)?.let { e ->
                    player.setPlaybackSpeed(model.savedSpeed(e).takeIf { it > 0f } ?: 1f)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    viewer.items.getOrNull(player.currentMediaItemIndex)
                        ?.let { model.setMediaPosition(it, 0L) }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffle = enabled
            }

            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }

            override fun onPlaybackParametersChanged(parameters: androidx.media3.common.PlaybackParameters) {
                playbackSpeed = parameters.speed
            }
        }
        player.addListener(listener)
        onDispose {
            savePlaybackPosition(player, viewer.items, model)
            player.removeListener(listener)
        }
    }

    // 영상과 같은 이유로, 백그라운드 전환·하드 종료에도 재생 위치가 남게 저장을 보강한다.
    PlaybackPositionKeeper(player, viewer.items, model)

    // Load the queue and start where the opened song was left. audioMediaItem does
    // no directory scan, so the items are built in hand.
    LaunchedEffect(player, viewer.items, viewer.index) {
        loadQueue(
            player,
            viewer.items,
            viewer.index,
            model,
            onSameQueue = { index = player.currentMediaItemIndex },
        ) {
            viewer.items.map { audioMediaItem(it) }
        }
    }

    // The position ticks on a half-second while a song plays, for the seek bar and
    // the elapsed read-out; held back while a finger is scrubbing.
    LaunchedEffect(player) {
        while (true) {
            if (!scrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.coerceAtLeast(0L)
            }
            kotlinx.coroutines.delay(500)
        }
    }

    val currentFile = viewer.items.getOrNull(index)
    // The song's tags and cover, read off the main thread and refreshed each time
    // the song changes. Null until read, and a song with no cover keeps it null --
    // a note glyph stands in.
    var tags by remember { mutableStateOf<MusicTags?>(null) }
    LaunchedEffect(currentFile?.prefKey) {
        tags = null
        val entry = currentFile ?: return@LaunchedEffect
        tags = withContext(Dispatchers.IO) { readMusicTags(entry) }
    }

    // The song's lyrics, found in 3 tiers (embedded tag → sidecar .lrc → online
    // LRCLIB), read off the main thread. Null when there is none, which hides the
    // lyrics affordance. 온라인 조회는 아티스트·제목이 필요해 태그가 읽힌 뒤 다시 찾는다.
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(currentFile?.prefKey, tags) {
        lyrics = null
        val entry = currentFile ?: return@LaunchedEffect
        val durSec = (durationMs / 1000L).toInt()
        lyrics = withContext(Dispatchers.IO) { resolveLyrics(entry, tags, durSec) }
    }

    val accent = Color(0xFFE8A183)
    val onDark = Color.White
    val dim = Color.White.copy(alpha = 0.6f)

    // The title (the tag's, or the filename) and the artist·album line, shown on
    // this screen and handed to the lyrics screen -- worked out once.
    // 태그가 없을 때(예: WAV) 파일명에서 아티스트·제목을 추정해, 파일명만 덜렁 뜨지 않게 한다.
    val guess = remember(currentFile?.prefKey) { currentFile?.let { org.olo.player.art.guessTrackName(it.name) } }
    val displayTitle = tags?.title?.takeIf { it.isNotBlank() }
        ?: guess?.title
        ?: currentFile?.nameWithoutExtension.orEmpty()
    val displaySubtitle = musicSubtitle(tags, guess?.artist, stringResource(R.string.music_unknown_artist))

    BackHandler(onBack = onClose)

    Surface(Modifier.fillMaxSize(), color = Color(0xFF12100E)) {
        Box(Modifier.fillMaxSize()) {
            // The blurred cover behind everything, with a dark wash over it so the
            // white text and controls read against any album. 소프트웨어 블러(약하게)만 쓴다.
            // 전엔 31+에서 하드웨어 블러 16dp를 덧댔는데, 소프트웨어 블러와 겹쳐 윤곽이 완전히
            // 사라졌다(과블러) -- 앨범 형태·색 흐름을 은은히 남기려 하드웨어 블러를 뺐다.
            tags?.background?.let { bg ->
                Image(
                    bitmap = bg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 스크림: 전면 기본 어둠 + 상·하로 더 짙은 그라데이션 비네트(상태바·하단 컨트롤 가독성,
            // 가운데 커버는 덜 눌러 또렷하게). 플랫 단색보다 깊이감이 있어 '깨진' 느낌을 없앤다.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.28f),
                        0.42f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.48f),
                    ),
                ),
            )

            // 컨트롤을 재사용 가능한 조각으로 둬, 세로(A)·펼침 2분할(B) 두 배치에서 같은 코드로
            // 그린다. 커버는 고정 비율이 아니라 '남는 공간에 맞는 최대 정사각형'으로 축소돼, 짧은
            // 폴더블 화면에서도 아래 컨트롤이 절대 잘리지 않는다(외부 음악 앱 정석: 아트 스케일,
            // 컨트롤 상시 노출). Column에 navigationBarsPadding을 줘 하단 칩이 제스처 바에 가리지
            // 않게 한다.
            // 커버를 탭하면 가사로 전환한다(가사가 있을 때). 우하단에 작은 '가사' 배지로 알린다.
            val hasLyrics = lyrics?.isEmpty == false
            val coverContent: @Composable (Modifier) -> Unit = { mod ->
                Box(
                    mod.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))
                        .then(if (hasLyrics) Modifier.clickable { showLyrics = true } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = tags?.art
                    if (art != null) {
                        Image(bitmap = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(96.dp))
                    }
                    if (hasLyrics) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(10.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color(0x99000000))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(stringResource(R.string.lyrics), style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                }
            }
            val info: @Composable () -> Unit = {
                Text(displayTitle, style = MaterialTheme.typography.headlineSmall, color = onDark, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(displaySubtitle, style = MaterialTheme.typography.bodyMedium, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            val seek: @Composable () -> Unit = {
                val shown = if (scrubbing) scrubMs else positionMs
                val range = durationMs.coerceAtLeast(1L)
                Slider(
                    value = shown.coerceIn(0L, range).toFloat(),
                    onValueChange = { value -> scrubbing = true; scrubMs = value.toLong() },
                    onValueChangeFinished = {
                        player.seekTo(scrubMs.coerceIn(0L, durationMs)); positionMs = scrubMs; scrubbing = false
                    },
                    valueRange = 0f..range.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatClock(shown), style = MaterialTheme.typography.labelMedium, color = dim)
                    Text(formatClock(durationMs), style = MaterialTheme.typography.labelMedium, color = dim)
                }
            }
            val transport: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = stringResource(R.string.music_shuffle), tint = if (shuffle) accent else dim)
                    }
                    IconButton(onClick = { player.seekToPrevious() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.music_prev), tint = onDark, modifier = Modifier.size(34.dp))
                    }
                    // 10초 뒤로/앞으로: 영상과 동일하게 Replay10/Forward10 + seekBack/Forward(설정의 탐색
                    // 간격, 기본 10초)를 쓴다 -- 오디오북·강의·긴 곡에서 요긴하고 영상과 동작이 일치한다.
                    IconButton(onClick = { player.seekBack() }) {
                        Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.video_rewind), tint = onDark, modifier = Modifier.size(34.dp))
                    }
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(accent)
                            .clickable { if (player.isPlaying) player.pause() else player.play() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.music_pause else R.string.music_play),
                            tint = Color(0xFF12100E),
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(onClick = { player.seekForward() }) {
                        Icon(Icons.Filled.Forward10, contentDescription = stringResource(R.string.video_forward), tint = onDark, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { player.seekToNext() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.music_next), tint = onDark, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = {
                        player.repeatMode = when (player.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }) {
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icon(Icons.Filled.RepeatOne, contentDescription = stringResource(R.string.music_repeat_one), tint = accent)
                            Player.REPEAT_MODE_ALL -> Icon(Icons.Filled.RepeatOn, contentDescription = stringResource(R.string.music_repeat_all), tint = accent)
                            else -> Icon(Icons.Filled.Repeat, contentDescription = stringResource(R.string.music_repeat), tint = dim)
                        }
                    }
                }
            }
            // 대기열·가사·속도. 속도는 오디오북·강의에서 요긴하고(노래는 보통 1.0x), 영상에서
            // 묻어온 속도를 여기서 바로 되돌릴 수 있어야 하므로 칩으로 노출한다.
            val secondary: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicPill(text = stringResource(R.string.music_queue), onClick = { showQueue = true }, modifier = Modifier.weight(1f))
                    if (hasLyrics) {
                        MusicPill(text = stringResource(R.string.lyrics), onClick = { showLyrics = true }, modifier = Modifier.weight(1f))
                    }
                    MusicPill(text = stringResource(R.string.audio_fx), onClick = { showEq = true }, modifier = Modifier.weight(1f))
                    MusicPill(text = speedNumber(playbackSpeed) + "x  " + stringResource(R.string.player_speed), onClick = { showSpeed = true }, modifier = Modifier.weight(1f))
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top bar: a way back, and the "now playing" label.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = onDark,
                        )
                    }
                    Text(
                        stringResource(R.string.music_now_playing),
                        style = MaterialTheme.typography.titleSmall,
                        color = dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    SleepTimerButton(player = player, tint = onDark)
                }

                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // 펼침(가로가 세로보다 넓은 폴더블·가로 화면)은 커버 왼쪽·컨트롤 오른쪽 2분할(B),
                    // 그 밖(세로)은 커버 위·컨트롤 아래 단일 열(A).
                    val wide = maxWidth > maxHeight
                    if (!wide) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                coverContent(Modifier.size(minOf(maxWidth, maxHeight) * 0.98f))
                            }
                            Spacer(Modifier.height(18.dp))
                            info()
                            Spacer(Modifier.height(16.dp))
                            seek()
                            Spacer(Modifier.height(12.dp))
                            transport()
                            Spacer(Modifier.height(16.dp))
                            secondary()
                            Spacer(Modifier.height(4.dp))
                        }
                    } else {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                coverContent(Modifier.size(minOf(maxWidth, maxHeight) * 0.94f))
                            }
                            Spacer(Modifier.width(28.dp))
                            Column(
                                Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                info()
                                Spacer(Modifier.height(18.dp))
                                seek()
                                Spacer(Modifier.height(14.dp))
                                transport()
                                Spacer(Modifier.height(18.dp))
                                secondary()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQueue) {
        MusicQueueSheet(
            items = viewer.items,
            current = index,
            onPick = { picked ->
                player.seekTo(picked, 0L)
                player.play()
                showQueue = false
            },
            onDismiss = { showQueue = false },
        )
    }

    if (showLyrics) {
        LyricsScreen(
            lyrics = lyrics ?: Lyrics(emptyList(), synced = false),
            positionMs = if (scrubbing) scrubMs else positionMs,
            title = displayTitle,
            subtitle = displaySubtitle,
            background = tags?.background,
            onSeek = { player.seekTo(it) },
            onClose = { showLyrics = false },
        )
    }

    if (showEq) {
        EqSheet(onClose = { showEq = false })
    }

    if (showSpeed) {
        MusicSpeedSheet(
            speed = playbackSpeed,
            onSpeed = {
                player.setPlaybackSpeed(it); playbackSpeed = it
                // 파일별로 배속을 기억한다 -- 같은 곡/오디오북을 다시 열면 그대로 이어진다.
                viewer.items.getOrNull(player.currentMediaItemIndex)?.let { e -> model.setMediaSpeed(e, it) }
            },
            onDismiss = { showSpeed = false },
        )
    }
}

/**
 * 음성 재생창의 재생 속도 시트: 영상 플레이어와 같은 −/값/＋ 미세조절(0.05 단위, 0.25~4x)에
 * 프리셋 칩을 더한다. 외부 음악·팟캐스트 앱 공통 패턴(1.0x 칩 → 프리셋+미세). 음악 테마에 맞춰
 * 어두운 바텀 패널로, 대기열 시트와 같은 결이다.
 */
@Composable
internal fun MusicSpeedSheet(speed: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    val accent = Color(0xFFE8A183)
    val backdrop = remember { MutableInteractionSource() }
    val panel = remember { MutableInteractionSource() }
    fun step(delta: Float) = onSpeed((((speed + delta) * 20).roundToInt() / 20f).coerceIn(0.25f, 4f))
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = backdrop, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF1B1815))
                .clickable(interactionSource = panel, indication = null, onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.player_speed_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetStep("−") { step(-0.05f) }
                Text(
                    speedNumber(speed) + "x",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                SheetStep("+") { step(0.05f) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
                    val on = kotlin.math.abs(preset - speed) < 0.001f
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) accent else Color.White.copy(alpha = 0.10f))
                            .clickable { onSpeed(preset) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            speedNumber(preset),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) Color(0xFF1B1815) else Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/** A rounded, translucent pill button, for the playlist and speed under the transport. */
@Composable
private fun MusicPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * The queue: the folder's songs in order, the playing one marked, tap to jump.
 * A panel across the lower part of the screen over a dim backdrop; a tap outside
 * puts it away.
 */
@Composable
private fun MusicQueueSheet(
    items: List<MediaEntry>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = Color(0xFFE8A183)
    val backdrop = remember { MutableInteractionSource() }
    val panel = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = backdrop, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF1B1815))
                .clickable(interactionSource = panel, indication = null, onClick = {})
                .statusBarsPadding()
                .padding(vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.music_queue),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(items) { i, file ->
                    val playing = i == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(i) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (i + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (playing) accent else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            file.nameWithoutExtension,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (playing) accent else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The lyrics screen: the song's words, scrolling with it.
 *
 * The line that is due now is picked out -- larger, in the accent colour -- and
 * the list keeps it near the middle as the song runs, so the eye stays put. A
 * line tapped jumps the song to it, which turns the lyrics into a way to move
 * about the song. A song with no words shows a plain note.
 */
@Composable
internal fun LyricsScreen(
    lyrics: Lyrics,
    positionMs: Long,
    title: String,
    subtitle: String,
    background: ImageBitmap?,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val accent = Color(0xFFE8A183)
    val lines = lyrics.lines
    val listState = rememberLazyListState()
    // 동기화 가사면 지금 불릴 줄(지난 마지막 줄)을 짚고 따라 스크롤한다. 일반 가사(비동기)는
    // 짚지 않고(-1) 스크롤·탭 이동도 하지 않는다(그냥 읽는 가사).
    val current = remember(lyrics, positionMs) { LyricsParser.currentIndex(lyrics, positionMs) }
    // Keep the current line near the middle rather than at the top.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val centerPx = with(density) { (configuration.screenHeightDp.dp / 2).toPx() }.toInt()
    LaunchedEffect(current) {
        if (current >= 0) {
            runCatching { listState.animateScrollToItem(current, -centerPx + 160) }
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF12100E)) {
        Box(Modifier.fillMaxSize()) {
            background?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.66f)),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = Color.White,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.lyrics_none),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 48.dp),
                    ) {
                        itemsIndexed(lines) { i, line ->
                            val on = i == current
                            Text(
                                line.text.ifBlank { "♪" },
                                style = if (on) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.titleMedium
                                },
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) accent else Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (lyrics.synced) Modifier.clickable { onSeek(line.timeMs) } else Modifier)
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 가사를 3단으로 찾는다(먼저 찾는 것 사용): ① 파일에 심긴 가사(jaudiotagger로 USLT/LYRICS 등) →
 * ② 곡 옆 같은 이름의 .lrc(동기화) → ③ 온라인 LRCLIB(키 불필요, 아티스트·제목·앨범·길이로 조회).
 * 동기화 LRC면 시각에 맞춰 짚고, 일반 텍스트면 스크롤만. 네트워크·파일 접근은 호출부 IO에서 돈다.
 * 온라인은 아티스트·제목이 있어야 하므로 태그가 읽힌 뒤 호출된다(없으면 로컬 두 단계만).
 */
private fun resolveLyrics(entry: MediaEntry, tags: MusicTags?, durationSec: Int): Lyrics? {
    val local = entry.localFile
    // ① 내장 태그
    local?.let { readEmbeddedLyrics(it) }?.let { raw ->
        LyricsParser.parse(raw).takeIf { !it.isEmpty }?.let { return it }
    }
    // ② 사이드카 .lrc (CP949 등도 텍스트 뷰어와 같은 방식으로 디코드)
    local?.let { sidecarLrcText(it) }?.let { raw ->
        LyricsParser.parse(raw).takeIf { !it.isEmpty }?.let { return it }
    }
    // ③ 온라인 LRCLIB
    val artist = tags?.artist?.takeIf { it.isNotBlank() }
    val title = tags?.title?.takeIf { it.isNotBlank() } ?: entry.nameWithoutExtension
    if (!artist.isNullOrBlank() || title.isNotBlank()) {
        runCatching {
            LyricsClient().fetch(artist.orEmpty(), title, tags?.album.orEmpty(), durationSec)
        }.getOrNull()?.let { raw ->
            LyricsParser.parse(raw).takeIf { !it.isEmpty }?.let { return it }
        }
    }
    return null
}

/** 파일에 심긴 가사(ID3 USLT · FLAC/Vorbis LYRICS · MP4 ©lyr)를 jaudiotagger로. 없으면 null. */
private fun readEmbeddedLyrics(file: File): String? = runCatching {
    org.jaudiotagger.audio.AudioFileIO.read(file).tag
        ?.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS)
        ?.ifBlank { null }
}.getOrNull()

/** 곡 옆 같은 이름의 .lrc 원문(없으면 null). CP949 등은 텍스트 뷰어와 같은 방식으로 디코드. */
private fun sidecarLrcText(audio: File): String? {
    val dir = audio.parentFile ?: return null
    val base = audio.nameWithoutExtension
    val lrc = File(dir, "$base.lrc").takeIf { it.isFile }
        ?: dir.listFiles()?.firstOrNull { file ->
            file.isFile && file.extension.equals("lrc", ignoreCase = true) &&
                file.nameWithoutExtension.equals(base, ignoreCase = true)
        }
        ?: return null
    return runCatching { TextFiles.decode(lrc.readBytes()).text }.getOrNull()
}

/** A song's tags and cover, as the music player shows them. */
private data class MusicTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val art: ImageBitmap?,
    val background: ImageBitmap?,
)

/**
 * Reads a song's title, artist, album and embedded cover. The cover, when there
 * is one, is kept both full-size for the square and shrunk for the blurred
 * backdrop. Anything unreadable comes back null rather than throwing.
 *
 * A local song is read from its path; a web (http/https) song is read from its
 * url, which MediaMetadataRetriever can open. An ftp song, which the retriever
 * cannot reach, shows its filename and a plain note -- the stream still plays.
 */
private fun readMusicTags(entry: MediaEntry): MusicTags {
    val retriever = MediaMetadataRetriever()
    return try {
        val local = entry.localFile
        val scheme = entry.uri.scheme?.lowercase()
        when {
            local != null -> retriever.setDataSource(local.path)
            scheme == "http" || scheme == "https" ->
                retriever.setDataSource(entry.uri.toString(), HashMap<String, String>())
            else -> return MusicTags(null, null, null, null, null)
        }
        var title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        var artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        var album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        var coverBytes: ByteArray? = retriever.embeddedPicture
        // WAV는 표준 태그 컨테이너가 없어 리트리버가 비워 온다. 로컬 WAV에 한해 RIFF 안의
        // INFO/ID3 청크를 직접 읽어 제목·아티스트·앨범·앨범아트를 보완한다(있을 때만).
        if (local != null && local.extension.equals("wav", ignoreCase = true) &&
            title.isNullOrBlank() && artist.isNullOrBlank() && album.isNullOrBlank() && coverBytes == null
        ) {
            org.olo.player.art.readWavTags(local)?.let { w ->
                title = w.title
                artist = w.artist
                album = w.album
                coverBytes = w.picture
            }
        }
        val cover = coverBytes?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }
        MusicTags(
            title = title,
            artist = artist,
            album = album,
            art = cover?.asImageBitmap(),
            background = cover?.let { blurredCover(it) }?.asImageBitmap(),
        )
    } catch (_: Exception) {
        MusicTags(null, null, null, null, null)
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * A soft, dark backdrop from a cover. 종전엔 40px로 줄여 확대만 해 격자가 깨져 보였다 -- 이제
 * [org.olo.player.art.backdropFromCover]로 적당한 해상도(≈260px)에 '진짜 블러'(박스 3패스)를
 * 약하게 적용해, 매끈하되 앨범 윤곽은 은은히 남긴다(하드웨어 블러는 과블러라 뺐다).
 */
private fun blurredCover(cover: Bitmap): Bitmap? = org.olo.player.art.backdropFromCover(cover)

/** The line under a song's title: artist, and album when the file names one.
 *  [guessedArtist]는 태그가 없을 때 파일명에서 추정한 아티스트(없으면 null) -- 태그 > 추정 >
 *  "알 수 없음" 순으로 쓴다. */
private fun musicSubtitle(tags: MusicTags?, guessedArtist: String?, unknownArtist: String): String {
    val artist = tags?.artist?.takeIf { it.isNotBlank() }
        ?: guessedArtist?.takeIf { it.isNotBlank() }
        ?: unknownArtist
    val album = tags?.album?.takeIf { it.isNotBlank() }
    return if (album != null) "$artist · $album" else artist
}

/**
 * 지금 화면에 보이는 방향을 그대로 재현하는 구체 방향 상수. 가로/세로는 Configuration이 확실히
 * 알려주고(자연방향 가정 없음 → 폴더블/태블릿도 안전), rotation으로 정/역만 가린다. 이 값을
 * 저장해 두면 복귀·재생성 뒤에도 같은 방향으로 다시 잠글 수 있다.
 */
private fun currentLockOrientation(activity: android.app.Activity): Int {
    val landscape = activity.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        activity.display?.rotation ?: android.view.Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.rotation
    }
    return lockOrientationFor(landscape, rotation)
}

/**
 * (가로/세로, rotation) → 구체 방향 상수. rotation 0·90 쪽을 '정', 180·270 쪽을 '역'으로 봐
 * 자연방향이 세로든 가로든 일관되게 지금 방향을 재현한다. 순수 함수라 단위 테스트로 고정한다.
 */
internal fun lockOrientationFor(landscape: Boolean, rotation: Int): Int {
    val normal = rotation == android.view.Surface.ROTATION_0 || rotation == android.view.Surface.ROTATION_90
    return if (landscape) {
        if (normal) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    } else {
        if (normal) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    }
}

/** A playable for a song: a plain media item, no subtitle sidecars to look for. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun audioMediaItem(entry: MediaEntry): MediaItem = buildMediaItem(entry.uri, emptyList(), entry.prefKey, title = entry.nameWithoutExtension)

/**
 * Sets the player's queue to [items] and starts at [index] where that file was
 * last left -- unless the service is already on this very queue (as after a
 * rotation), when resetting it would jerk playback back to the start and
 * [onSameQueue] runs instead. The items are built by [build], which the caller
 * supplies so a song builds them in hand while a film builds them off the main
 * thread (its subtitle scan reads the directory).
 *
 * A controller strips a MediaItem's localConfiguration crossing to the service,
 * so the uri that survives is the one in the request metadata; the guard compares
 * against that, which is what lets it recognise the same queue rather than
 * restarting playback on every rotation.
 */
private suspend fun loadQueue(
    player: MediaController,
    items: List<MediaEntry>,
    index: Int,
    model: PlayerViewModel,
    onSameQueue: () -> Unit,
    build: suspend () -> List<MediaItem>,
) {
    val wantUris = items.map { it.uri }
    val haveUris = (0 until player.mediaItemCount).map {
        player.getMediaItemAt(it).requestMetadata.mediaUri
    }
    if (haveUris != wantUris) {
        val startEntry = items.getOrNull(index) ?: return
        // 설정 › 재생: start at the saved point only when 이어보기 is on, and open at
        // the default speed the settings tree carries.
        val start = if (model.resumeEnabled()) model.mediaPosition(startEntry) else 0L
        player.setMediaItems(build(), index, start)
        player.prepare()
        // 음성은 그 파일에 저장된 배속으로, 없으면 1.0x로 연다 -- 노래는 보통 등속이고, 영상에서
        // 올려둔 기본 속도가 플레이어(서비스 공용)에 남아 음악에 묻어오던 혼란을 끊는다. 오디오북·
        // 강의는 속도 칩으로 올린 값이 파일별로 저장돼 다음에 그대로 이어진다.
        player.setPlaybackSpeed(model.savedSpeed(startEntry).takeIf { it > 0f } ?: 1f)
        player.playWhenReady = true
    } else {
        onSameQueue()
    }
}

/**
 * Loads a film playlist in two phases so the tapped film starts at once: the opened
 * film is built and played first, then its siblings are built off-thread and spliced
 * around it. Building a film's item scans the folder for sidecar subtitles (and
 * rewrites SAMI), so building the whole folder up front delayed the first frame on a
 * folder of many films; here only the tapped one gates playback. A queue that already
 * matches (a rotation or a return from the background) just resyncs the index.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun loadVideoQueueProgressive(
    player: MediaController,
    items: List<MediaEntry>,
    index: Int,
    model: PlayerViewModel,
    cacheDir: File,
    context: android.content.Context,
    onSameQueue: () -> Unit,
) {
    val wantUris = items.map { it.uri }
    val haveUris = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).requestMetadata.mediaUri }
    if (haveUris == wantUris) {
        onSameQueue()
        return
    }
    val startEntry = items.getOrNull(index) ?: return
    val start = if (model.resumeEnabled()) model.mediaPosition(startEntry) else 0L

    // Phase 1: the tapped film alone, playing immediately.
    val current = withContext(Dispatchers.IO) { mediaItemFor(startEntry, cacheDir, context) }
    player.setMediaItems(listOf(current), 0, start)
    player.prepare()
    // 그 파일에 저장된 배속이 있으면 그걸로, 없으면 설정의 기본 속도로 연다(파일별 기억).
    player.setPlaybackSpeed(model.savedSpeed(startEntry).takeIf { it > 0f } ?: model.defaultSpeed())
    player.playWhenReady = true

    // Only one film to play -- nothing to splice.
    if (items.size <= 1) return

    // Phase 2: the rest, built off-thread, then spliced before/after the current one
    // so previous/next and autoplay see the whole folder. Guard against a newer open
    // having replaced the single item while we were building.
    val before = withContext(Dispatchers.IO) { items.take(index).map { mediaItemFor(it, cacheDir, context) } }
    val after = withContext(Dispatchers.IO) { items.drop(index + 1).map { mediaItemFor(it, cacheDir, context) } }
    val stillCurrent = player.mediaItemCount == 1 &&
        player.getMediaItemAt(0).requestMetadata.mediaUri == startEntry.uri
    if (!stillCurrent) return
    if (before.isNotEmpty()) player.addMediaItems(0, before)
    if (after.isNotEmpty()) player.addMediaItems(after)
    // before를 앞에 끼우면 재생 중 항목의 인덱스가 밀리지만, 재생 중 MediaItem 자체는 그대로라
    // media3가 onMediaItemTransition을 쏘지 않는다 → UI의 index가 0에 멈춰 제목·태그·자막선택이
    // 0번 항목으로 잘못 잡힌다(비선두 영화를 열 때). splice 뒤 실제 인덱스로 맞춰 준다.
    if (before.isNotEmpty()) onSameQueue()
}

/**
 * The sleep-timer button, for both players.
 *
 * The timer itself lives in the playback service, so it stops the sound even with
 * the app in the background and the screen off. This is only its face: a moon that
 * lights up while a timer is set, a sheet to choose the minutes, and a countdown
 * kept in step with the service -- asked once on opening, then run down here.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SleepTimerButton(player: MediaController, tint: Color) {
    // When the pause is due, on the same elapsed-time clock the service uses, and
    // the seconds left counted down from it. Held across a rotation so the moon
    // stays lit and the count does not restart.
    var dueElapsed by rememberSaveable { mutableLongStateOf(0L) }
    var remainingMs by remember { mutableLongStateOf(0L) }
    var showPicker by remember { mutableStateOf(false) }

    // On opening, ask the service how long is left, so a timer set on one screen
    // shows on the next and survives coming back from the background.
    LaunchedEffect(player) {
        val remaining = querySleepRemaining(player)
        dueElapsed = if (remaining > 0L) SystemClock.elapsedRealtime() + remaining else 0L
    }
    // Count down while a timer is set; clear it when it runs out.
    LaunchedEffect(dueElapsed) {
        if (dueElapsed <= 0L) {
            remainingMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            val left = (dueElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingMs = left
            if (left <= 0L) {
                dueElapsed = 0L
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }
    val active = dueElapsed > 0L

    IconButton(onClick = { showPicker = true }) {
        Icon(
            Icons.Filled.Bedtime,
            contentDescription = stringResource(R.string.sleep_timer),
            tint = if (active) Color(0xFFE8A183) else tint,
        )
    }

    if (showPicker) {
        SleepTimerSheet(
            remainingMs = if (active) remainingMs else 0L,
            onPick = { minutes ->
                sendSleep(player, minutes)
                dueElapsed = if (minutes > 0) {
                    SystemClock.elapsedRealtime() + minutes * 60_000L
                } else {
                    0L
                }
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

// The choices the sleep timer offers, in minutes; zero turns it off.
private val SLEEP_TIMER_OPTIONS = listOf(0, 15, 30, 45, 60)

/**
 * The sleep-timer choices, in a small dark panel in the middle of the screen: how
 * much is left if a timer is running, then off and the minute options. A tap
 * outside puts it away.
 */
@Composable
private fun SleepTimerSheet(
    remainingMs: Long,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // The player is always dark, so its dialogs force the dark theme even when the
    // app runs light -- an ivory card over a dark film would jar.
    org.olo.player.ui.theme.OloPlayerTheme(darkTheme = true) {
        val c = OloTheme.colors
        OloCardDialog(title = stringResource(R.string.sleep_timer), onDismiss = onDismiss) {
            if (remainingMs > 0L) {
                Text(
                    stringResource(R.string.sleep_timer_left, formatClock(remainingMs)),
                    color = c.accent,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            }
            for (minutes in SLEEP_TIMER_OPTIONS) {
                val label = if (minutes == 0) {
                    stringResource(R.string.sleep_timer_off)
                } else {
                    stringResource(R.string.sleep_timer_minutes, minutes)
                }
                Text(
                    label,
                    color = c.text,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(minutes) }
                        .padding(horizontal = 6.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Sets or cancels the service's sleep timer; a non-positive minutes cancels it. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun sendSleep(player: MediaController, minutes: Int) {
    val action = if (minutes > 0) PlaybackService.CMD_SLEEP_SET else PlaybackService.CMD_SLEEP_CANCEL
    val args = Bundle().apply {
        if (minutes > 0) putInt(PlaybackService.EXTRA_SLEEP_MINUTES, minutes)
    }
    player.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
}

/** Asks the service how many milliseconds are left on the sleep timer, or zero. */
@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun querySleepRemaining(player: MediaController): Long {
    val future =
        player.sendCustomCommand(SessionCommand(PlaybackService.CMD_SLEEP_QUERY, Bundle.EMPTY), Bundle.EMPTY)
    val result = withContext(Dispatchers.IO) { runCatching { future.get() }.getOrNull() }
    return result?.extras?.getLong(PlaybackService.EXTRA_SLEEP_REMAINING, 0L) ?: 0L
}

/**
 * Connects to the playback service and hands back its controller, or null while
 * the connection is still being made. Letting go of the controller on the way
 * out releases the front end without stopping the service's player, so the
 * sound carries on in the background.
 */
@Composable
private fun rememberMediaController(context: Context): MediaController? {
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { runCatching { future.get() }.getOrNull()?.let { controller = it } },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

/**
 * The player's face: the picture and controls, the top bar, the seek gesture
 * and the subtitle settings, over a service controller that outlives it.
 */
// The seek gesture is a touch listener on the player view rather than a Compose
// overlay: an overlay on top of the view swallows the taps the player's own
// controls need, so the menu stopped coming up. The listener never consumes a
// tap -- it watches for a sideways drag and leaves everything else to the view.
@SuppressLint("ClickableViewAccessibility")
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MediaPlayer(
    player: MediaController,
    viewer: PlayerViewModel.MediaViewer,
    model: PlayerViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    // Which file is showing, and a counter bumped when the tracks change so the
    // subtitle sheet's list rebuilds.
    var index by remember { mutableIntStateOf(viewer.index) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    // The player is the service's, so a speed set on one screen shows on the
    // next; kept in step through the listener below.
    var playbackSpeed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    // The controls are the app's own -- drawn in Compose over the picture, never
    // inside the player view -- so they are laid out and take touches at full
    // screen size whatever the zoom does to the picture. These drive them.
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    // A-B repeat: two marks the film loops between (nPlayer-style segment repeat).
    // -1 means unset; when both are set, playback that reaches B jumps back to A.
    var abA by remember { mutableLongStateOf(-1L) }
    var abB by remember { mutableLongStateOf(-1L) }
    // Touch lock: hides the controls and stands the gestures down, so a pocket or
    // a lean on the screen cannot seek or pause. A tap shows the unlock button.
    var locked by rememberSaveable { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }

    // Keep the place. A file the player moves on from, or plays to the end, is
    // put back to the start; one left partway keeps its position, unless it is
    // within a second of the end, which reads as finished. The player belongs
    // to the service and is only let go of here, not released, so the sound can
    // carry on in the background.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Only a file the player ran on to the next from is put back to
                // the start; a playlist being set or cleared (opening, or leaving
                // and clearing) must not zero the place we just saved.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    viewer.items.getOrNull(index)?.let { model.setMediaPosition(it, 0L) }
                }
                index = player.currentMediaItemIndex
                // 다음 영상의 저장된 배속을 적용한다(파일별 기억). 없으면 설정의 기본 속도.
                viewer.items.getOrNull(index)?.let { e ->
                    player.setPlaybackSpeed(model.savedSpeed(e).takeIf { it > 0f } ?: model.defaultSpeed())
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    viewer.items.getOrNull(player.currentMediaItemIndex)
                        ?.let { model.setMediaPosition(it, 0L) }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackParametersChanged(
                parameters: androidx.media3.common.PlaybackParameters,
            ) {
                playbackSpeed = parameters.speed
            }
        }
        player.addListener(listener)
        onDispose {
            savePlaybackPosition(player, viewer.items, model)
            player.removeListener(listener)
        }
    }

    // 백그라운드 전환(전화·홈·앱 전환·화면 끔)과 하드 종료에도 재생 위치가 남게, 생명주기
    // ON_STOP·주기 체크포인트로 저장을 보강한다(onDispose 하나에만 기대지 않는다).
    PlaybackPositionKeeper(player, viewer.items, model)

    // The play position ticks on a half-second for the seek bar and the elapsed
    // read-out; held back while a finger is scrubbing so the thumb follows it.
    // The same tick drives A-B repeat: past B, jump back to A.
    LaunchedEffect(player) {
        while (true) {
            val pos = player.currentPosition.coerceAtLeast(0L)
            if (!scrubbing) {
                positionMs = pos
                durationMs = player.duration.coerceAtLeast(0L)
            }
            if (abA >= 0 && abB > abA && pos >= abB) {
                player.seekTo(abA)
            }
            kotlinx.coroutines.delay(500)
        }
    }

    // Load the playlist and start where the opened film was left. Building a film's
    // item scans the folder for sidecar subtitles and rewrites any SAMI, so doing it
    // for every sibling before playing made a folder of films slow to start. Instead
    // the tapped film is built and played first, then the siblings fill in behind it.
    LaunchedEffect(player, viewer.items, viewer.index) {
        loadVideoQueueProgressive(
            player = player,
            items = viewer.items,
            index = viewer.index,
            model = model,
            cacheDir = context.cacheDir,
            context = context,
            onSameQueue = { index = player.currentMediaItemIndex },
        )
    }

    // The screen's own turning: on, it follows the sensor and turns with the
    // phone; off, it freezes the orientation currently on screen.
    //
    // 잠금은 구체 방향(가로/세로)을 강제하지 않고 SCREEN_ORIENTATION_LOCKED로 "지금 보이는
    // 방향"을 그대로 얼린다. 예전엔 현재 rotation을 가로/세로로 환산해 고정했는데, 그 환산이
    // ROTATION_0=세로로 단정한다 -- 폴더블·태블릿은 자연방향이 가로라 가로로 보는 중에도
    // ROTATION_0이라 세로로 오판하고, 잠금 순간 강제 세로 → 시스템 레터박스로 화면이 쪼그라드는
    // 심각한 버그가 났다. LOCKED는 자연방향과 무관하게 현재를 고정하므로 그 오판이 없다.
    val activity = context as? android.app.Activity
    // 잠금 방향을 '지금 보이는 방향'의 구체 상수(가로/세로/역가로/역세로)로 저장한다.
    // 예전엔 SCREEN_ORIENTATION_LOCKED를 썼는데, 이는 '적용되는 그 순간'의 방향을 얼린다.
    // 홈에 갔다 돌아와 액티비티가 다시 세워지면 그 순간의 물리 방향으로 다시 잠겨, 눕히기 전
    // 방향으로 되돌아갔다(사용자 제보). 구체 상수를 rememberSaveable로 들고 다시 걸면 복귀·
    // 재생성과 무관하게 잠근 방향이 유지된다. 상수는 Configuration.orientation(가로/세로는 확실)로
    // 정하고 rotation으로 정/역만 가린다 -- ROTATION_0=세로로 단정하던 옛 오판(폴더블/태블릿)을 피한다.
    var autoRotate by rememberSaveable { mutableStateOf(true) }
    var lockedOrientation by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    fun applyOrientation() {
        activity?.requestedOrientation = if (autoRotate) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            lockedOrientation.takeIf { it != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                ?: ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }
    LaunchedEffect(autoRotate, lockedOrientation) { applyOrientation() }
    // 복귀 시 다시 건다: 백그라운드 동안 시스템이 방향 요청을 초기화했거나 액티비티가 다시
    // 세워져도, 저장해 둔 잠금 방향을 재적용해 화면 상태가 그대로 유지되게 한다.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, autoRotate, lockedOrientation) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) applyOrientation()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // Hand the screen's brightness back to the system on the way out;
            // the left-edge drag may have pinned it.
            activity?.window?.let { window ->
                window.attributes = window.attributes.also {
                    it.screenBrightness =
                        android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // While something is playing the screen is held awake, so a film is not
    // dimmed or slept through for want of a touch. The flag is cleared the
    // moment playback pauses, and on the way out.
    val keepScreenOnPref = remember { org.olo.player.data.AppPreferences(context).keepScreenOn() }
    DisposableEffect(player, activity, keepScreenOnPref) {
        val window = activity?.window
        val keepAwake = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        fun sync() {
            if (keepScreenOnPref && player.isPlaying) window?.addFlags(keepAwake) else window?.clearFlags(keepAwake)
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = sync()
        }
        player.addListener(listener)
        sync()
        onDispose {
            player.removeListener(listener)
            window?.clearFlags(keepAwake)
        }
    }

    // The left of the picture is a brightness dial, the right a volume dial:
    // slide up or down on either. Brightness is the window's own, handed back to
    // the system on the way out; volume is the media stream's. Each shows a
    // read-out while the finger is down. Brightness opens at full and holds where
    // it is set; volume follows the stream.
    val audio = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    // Held across a rotation or a trip to the background -- which is what used to
    // drop the brightness back to the system's level. Brightness begins at full.
    var brightness by rememberSaveable { mutableFloatStateOf(1f) }
    var volume by rememberSaveable { mutableFloatStateOf(-1f) }
    var brightnessHud by remember { mutableFloatStateOf(-1f) }
    var volumeHud by remember { mutableFloatStateOf(-1f) }
    // Applied from here, not only on a drag, so the window takes the remembered
    // brightness on opening -- full, to start -- and takes it again after a
    // rotation or a return from the background, which reset the window otherwise.
    LaunchedEffect(activity, brightness) {
        activity?.window?.let { window ->
            window.attributes = window.attributes.also { it.screenBrightness = brightness }
        }
    }
    val onBrightnessDelta: (Float) -> Unit = { fraction ->
        val next = (brightness + fraction).coerceIn(0.01f, 1f)
        brightness = next
        brightnessHud = next
        volumeHud = -1f
    }
    val onVolumeDelta: (Float) -> Unit = { fraction ->
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val start = if (volume in 0f..1f) {
            volume
        } else {
            audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max
        }
        val next = (start + fraction).coerceIn(0f, 1f)
        volume = next
        audio.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            (next * max).roundToInt(),
            0,
        )
        volumeHud = next
        brightnessHud = -1f
    }
    val onGestureEnd: () -> Unit = {
        brightnessHud = -1f
        volumeHud = -1f
    }

    // The top bar sits below a camera notch, not under it. The status bar's
    // inset drops to zero once the bar is hidden, so the largest seen is
    // latched and the cutout is taken into account directly.
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val cutoutTop = WindowInsets.displayCutout.getTop(density)
    // Latched with a plain remember, not saved across configuration change: the
    // largest inset seen this orientation is held (the status bar's drops to zero
    // once the bar hides), but a rotation starts the latch over, so a portrait
    // notch does not leave an over-tall bar in landscape.
    var reservedTopPx by remember { mutableIntStateOf(0) }
    val reservedTop = maxOf(reservedTopPx, statusTop, cutoutTop)
    LaunchedEffect(reservedTop) { reservedTopPx = reservedTop }
    val reservedTopDp = with(density) { reservedTop.toDp() }

    // A finger dragged across the picture scrubs: the distance maps to time, a
    // full width being two minutes, and a read-out of where the release would
    // land shows while the drag is in hand. The drag is watched on the player
    // view itself (see the class comment) rather than an overlay, so it is fed
    // as a preview here and committed on release.
    var seekTarget by remember { mutableLongStateOf(-1L) }
    val onSeekPreview: (Long) -> Unit = { seekTarget = it }
    val onSeekCommit: () -> Unit = {
        val target = seekTarget
        if (target >= 0) player.seekTo(target)
        seekTarget = -1L
    }

    // The chrome -- the top bar, the transport and the seek bar -- is the app's
    // own, drawn in Compose over the picture. It starts hidden so a film plays
    // under a clear screen and comes up on a tap. A counter, bumped on every
    // touch of it, restarts the hide timer so it does not vanish mid-use.
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsTick by remember { mutableIntStateOf(0) }
    // In picture-in-picture the system draws the window's own controls, so the
    // app's chrome and gestures stand down; the small window shows only the picture.
    val inPip = model.inPip
    LaunchedEffect(inPip) { if (inPip) controlsVisible = false }
    val showControls: () -> Unit = {
        controlsVisible = true
        controlsTick++
    }
    // While a film plays, the chrome hides itself a few seconds after the last
    // touch; paused, it stays, so the buttons are there to be read.
    LaunchedEffect(controlsVisible, isPlaying, controlsTick) {
        if (controlsVisible && isPlaying) {
            kotlinx.coroutines.delay(3500)
            controlsVisible = false
        }
    }
    // While locked, a tap flashes the unlock button; it fades on its own so the
    // film is not left with a button over it.
    LaunchedEffect(lockHint) {
        if (lockHint) {
            kotlinx.coroutines.delay(2000)
            lockHint = false
        }
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var showSubtitleSheet by remember { mutableStateOf(false) }

    // Subtitle look, kept for the whole app. Applied to the player's subtitle
    // view whenever it or the settings change, and written back so the next
    // video opens the same way.
    var subScale by rememberSaveable { mutableStateOf(model.subtitleScale()) }
    var subColor by rememberSaveable { mutableStateOf(model.subtitleColor()) }
    // 설정 › 자막: outline on/off and top/bottom anchor, read once for this film.
    val appPrefs = remember { org.olo.player.data.AppPreferences(context) }
    val subOutline = remember { appPrefs.subtitleOutline() }
    val subPosTop = remember { appPrefs.subtitlePosition() == "top" }
    // 자막 행간(앱 오버레이 경로)·디코딩 문자셋·내장 스타일 적용 여부: 설정 › 자막에서
    // 정하고 재생 시작 때 읽는다. 행간은 재생 설정에서도 바로 조절하므로 가변 상태로 둔다.
    var subLineSpacing by rememberSaveable { mutableStateOf(appPrefs.subtitleLineSpacing()) }
    val subEncoding = remember { appPrefs.subtitleEncoding() }
    val subEmbedded = remember { appPrefs.subtitleEmbeddedStyles() }
    // 굵게: 설정 › 자막의 기본값으로 시작하되, 재생 설정 다이얼로그에서 바로 끄고 켤 수 있게
    // 가변 상태로 둔다(바꾸면 subFontStyled·오버레이가 즉시 다시 그려져 영상 위에 반영).
    var subBold by rememberSaveable { mutableStateOf(appPrefs.subtitleBold()) }
    // The chosen subtitle font (TTF/OTF), or null for the player's default. '굵게'면
    // 선택 글꼴(없으면 시스템 기본)에서 볼드 변형을 만들어 media3 SubtitleView에 넘긴다.
    val subFont = remember { org.olo.player.data.SubtitleFont.typeface(context) }
    val subFontStyled = remember(subFont, subBold) {
        if (subBold) android.graphics.Typeface.create(subFont ?: android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        else subFont
    }
    LaunchedEffect(playerViewRef, subScale, subColor, subOutline, subPosTop, subFontStyled, subEmbedded) {
        val subtitleView = playerViewRef?.subtitleView ?: return@LaunchedEffect
        // '원문'이면 자막 파일의 색/스타일을 그대로 쓴다(색 고정 해제). 글자 크기만은 항상
        // 사용자의 '크기'가 이기도록 임베디드 폰트 크기는 끈다. 원문일 때 아래 전경색(흰색)은
        // 색 지정이 없는 큐에만 적용되는 폴백이다.
        val original = subColor == AppPreferences.SUBTITLE_COLOR_ORIGINAL
        // '자막 내장 스타일 적용'(SSA/ASS·내장 자막의 색·굵기·위치)을 독립 토글로 분리.
        // '원문' 색을 고른 경우는 파일 색을 쓰겠다는 뜻이므로 내장 스타일도 함께 켠다.
        subtitleView.setApplyEmbeddedStyles(subEmbedded || original)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setFractionalTextSize(subScale)
        // 자막 세로 위치 기준(BBC/Netflix·SMPTE 타이틀세이프): 가로 영상은 로워서드,
        // 바닥에서 10~15% 여백. media3 기본 8%는 바닥에 붙어 보여 하단은 10%로 올린다.
        // 위=위쪽은 위에서 ~10%(84% 패딩)로 둬 상단 타이틀세이프를 맞춘다. 지연 오버레이도
        // 같은 10%를 써 모든 자막 경로의 위치를 일치시킨다.
        subtitleView.setBottomPaddingFraction(if (subPosTop) 0.84f else 0.10f)
        subtitleView.setStyle(
            CaptionStyleCompat(
                if (original) android.graphics.Color.WHITE else subColor,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                if (subOutline) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.BLACK,
                subFontStyled,
            ),
        )
    }
    LaunchedEffect(subScale, subColor) { model.setSubtitleStyle(subScale, subColor) }
    val gestureSpeedPref = remember { appPrefs.gestureSpeed() }
    val doubleTapSeekPref = remember { appPrefs.doubleTapSeek() }

    // The subtitle tracks the player has, named for the picker: which number,
    // whether it comes from a file beside the film or from inside it, its
    // format and its language. Rebuilt whenever the tracks change.
    val undLabel = stringResource(R.string.subtitle_language_unknown)
    val textTracks = remember(tracksVersion, player, undLabel) {
        mapTracksOfType(player, C.TRACK_TYPE_TEXT) { group, i, number ->
            val format = group.getTrackFormat(i)
            val external = isExternalSubtitle(format)
            val language = format.language?.takeIf { it.isNotBlank() }
            TextTrack(
                group = group,
                trackIndex = i,
                number = number,
                external = external,
                format = subtitleFormat(external, format),
                language = trackLanguageName(language) ?: language ?: undLabel,
                kind = if (external) null else subtitleKind(format),
                token = subtitleToken(external, format, number),
                selected = group.isTrackSelected(i),
            )
        }
    }
    val subtitleOn = textTracks.any { it.selected }

    // Choosing a subtitle, and remembering the choice against this file so it
    // comes back the same next time. Turning the toggle on picks the first
    // track; off disables text.
    val currentFile = viewer.items.getOrNull(index)
    val onSelectTrack: (TextTrack) -> Unit = { track ->
        applyTextTrack(player, track)
        currentFile?.let { model.setSubtitleChoice(it, track.token) }
    }
    val onSubtitleToggle: (Boolean) -> Unit = { on ->
        if (on) {
            textTracks.firstOrNull()?.let { onSelectTrack(it) }
        } else {
            disableTextTracks(player)
            currentFile?.let { model.setSubtitleChoice(it, SUBTITLE_OFF_TOKEN) }
        }
    }

    // Subtitle delay -- external subtitles only. media3 cannot shift subtitle
    // timing, so when a nudge is set the app reads the selected external subtitle's
    // cues, turns the player's own text off (no double), and draws them itself
    // offset. In sync (0), the player renders it exactly as before.
    val selectedExternal = textTracks.firstOrNull { it.selected && it.external }
    val subCuesUri = remember(selectedExternal?.token, tracksVersion) {
        selectedExternal?.let { externalSubtitleUri(player, it) }
    }
    // value는 아래에서 분명히 할당되지만, produceState의 lint 검사가 이 대입을
    // 잡지 못하는 알려진 오탐이라 이 규칙만 좁게 끈다.
    @Suppress("ProduceStateDoesNotAssignValue")
    val delayCues by produceState<List<SubtitleCue>?>(null, subCuesUri, subEncoding) {
        value = subCuesUri?.let { uri -> withContext(Dispatchers.IO) { readSubtitleCues(context, uri, subEncoding) } }
    }
    var subDelayMs by remember(currentFile?.prefKey) {
        mutableLongStateOf(currentFile?.let { model.subtitleDelay(it) } ?: 0L)
    }
    // 외부 자막(파싱 가능한 일반 텍스트)은 항상 앱이 직접 그린다 -- media3 SubtitleView엔
    // 줄 간격 API가 없어, 줄 간격·디코딩 문자셋을 적용하려면 앱 오버레이가 유일한 경로다.
    // 지연(subDelayMs)은 그 위에 얹는 시간 오프셋일 뿐(0이면 제자리). ASS 등 파싱 불가
    // 외부 자막은 delayCues가 null이라 종전처럼 media3가 렌더한다.
    val subOverlayActive = delayCues != null
    // Hand rendering to the app whenever it draws the external subtitle; give it
    // back to the player when it does not (unparseable format, or none selected).
    LaunchedEffect(subOverlayActive, selectedExternal?.token) {
        if (subOverlayActive) disableTextTracks(player)
        else selectedExternal?.let { applyTextTrack(player, it) }
    }
    LaunchedEffect(subDelayMs, currentFile?.prefKey) {
        currentFile?.let { model.setSubtitleDelay(it, subDelayMs) }
    }
    val onSubtitleDelay: (Long) -> Unit = { subDelayMs = it.coerceIn(-60_000L, 60_000L) }
    // The control shows only when a nudge can actually apply: an external subtitle
    // whose format the app can parse (SRT/VTT, or a SAMI already converted to VTT).
    val showSubtitleDelay = selectedExternal != null && delayCues != null

    // 내장(또는 지연 없는) 텍스트 자막도 행간·굵게가 먹도록 앱이 직접 그린다 -- media3
    // SubtitleView엔 줄 간격 API가 없어, 플레이어가 내는 현재 큐(onCues)를 받아 오버레이로
    // 그리고 SubtitleView는 가린다. 비트맵 자막(PGS/VOBSUB)은 그릴 수 없어 그대로 둔다.
    var liveCues by remember(player) { mutableStateOf<List<Cue>>(emptyList()) }
    // 영상 실제 표시 크기를 알아야 자막을 '검은 여백'이 아니라 '영상 아래 가장자리'에 맞춰
    // 그릴 수 있다(media3 SubtitleView가 그러듯). videoSize와 Box 크기로 레터박스를 계산한다.
    var videoSize by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) { liveCues = cueGroup.cues }
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) { videoSize = size }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    val liveHasBitmap = liveCues.any { it.bitmap != null }
    val liveTextCues = if (liveHasBitmap) emptyList() else liveCues.filter { it.text != null }
    // 외부 지연 오버레이(subOverlayActive)가 켜졌을 땐 그쪽이 그린다. 그 외(내장 텍스트 등)만
    // 여기서 그린다. 선택 자막이 꺼져 있으면 당연히 안 그린다.
    val liveOverlayActive = subtitleOn && !subOverlayActive && liveTextCues.isNotEmpty()
    // 텍스트 자막을 우리가 그리는 동안 media3 SubtitleView를 투명하게 숨겨 이중 표시를 막는다.
    // 비트맵 자막(PGS 등)이면 우리가 못 그리므로 다시 보이게 한다. 큐 유무가 아니라 '비트맵
    // 여부'로 토글해 자막 줄이 바뀔 때마다 깜빡이지 않게 한다.
    val hideNativeSubtitles = subtitleOn && !subOverlayActive && !liveHasBitmap
    LaunchedEffect(hideNativeSubtitles, playerViewRef) {
        playerViewRef?.subtitleView?.alpha = if (hideNativeSubtitles) 0f else 1f
    }
    // 자막을 영상 '아래 가장자리'에 맞추기 위한 Box 크기(아래 onSizeChanged로 채워짐).
    var playerBoxPx by remember { mutableStateOf(IntSize.Zero) }

    // The audio tracks the film carries, for choosing between them when it has
    // more than one. Rebuilt with the tracks, the way the subtitles are.
    val audioTracks = remember(tracksVersion, player, undLabel) {
        mapTracksOfType(player, C.TRACK_TYPE_AUDIO) { group, i, number ->
            val format = group.getTrackFormat(i)
            val language = format.language?.takeIf { it.isNotBlank() }
            AudioTrack(
                group = group,
                trackIndex = i,
                number = number,
                language = trackLanguageName(language) ?: language ?: undLabel,
                detail = audioDetail(format),
                selected = group.isTrackSelected(i),
            )
        }
    }
    val onSelectAudio: (AudioTrack) -> Unit = { track -> applyAudioTrack(player, track) }
    val onSpeed: (Float) -> Unit = { speed ->
        player.setPlaybackSpeed(speed)
        playbackSpeed = speed
        // 파일별로 배속을 기억한다 -- 같은 영상을 다시 열면 그 배속으로 시작한다.
        viewer.items.getOrNull(player.currentMediaItemIndex)?.let { e -> model.setMediaSpeed(e, speed) }
    }

    // On opening a file, put back the subtitle it was last watched with -- once,
    // as soon as the tracks are known. With no saved choice, the text selection
    // is cleared to its default instead, so a previous film's "off" does not
    // carry over and keep this one's subtitle from showing (the player, and its
    // selection, are the service's and outlive one film).
    var subtitleAppliedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentFile, tracksVersion) {
        val entry = currentFile ?: return@LaunchedEffect
        if (subtitleAppliedFor == entry.prefKey) return@LaunchedEffect
        if (textTracks.isEmpty() && player.playbackState != Player.STATE_READY) return@LaunchedEffect
        when (val token = model.subtitleChoice(entry)) {
            null -> {
                // No saved choice: follow the 설정 › 자막 "자막 보기" default -- on
                // shows a track, off starts the film without text.
                val defaultOn = appPrefs.subtitleEnabled()
                // 외부 자막이 붙어 있으면 그걸 우선 선택한다 -- 영상 내 자막에 원하는 언어가
                // 없어 사용자가 일부러 옆에 붙인 것이므로 내장보다 앞세운다. 여럿이면 선호
                // 언어와 맞는 외부, 없으면 첫 외부. 외부가 없을 때만 내장 자동 선택(선호
                // 언어는 trackSelectionParameters가 처리)으로 넘어간다.
                val externalPick = if (defaultOn) {
                    val prefName = trackLanguageName(appPrefs.preferredSubtitleLang())
                    val external = textTracks.filter { it.external }
                    external.firstOrNull { prefName != null && it.language == prefName }
                        ?: external.firstOrNull()
                } else {
                    null
                }
                if (externalPick != null) {
                    applyTextTrack(player, externalPick)
                } else {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !defaultOn)
                        .build()
                }
            }
            SUBTITLE_OFF_TOKEN -> disableTextTracks(player)
            else -> textTracks.firstOrNull { it.token == token }?.let { applyTextTrack(player, it) }
        }
        subtitleAppliedFor = entry.prefKey
    }

    // The picture's fit -- letterboxed, cropped to fill, or stretched -- cycled
    // by the aspect button.
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    LaunchedEffect(playerViewRef, resizeMode) { playerViewRef?.resizeMode = resizeMode }

    // Two fingers pinched apart or together zoom the picture in or out, held
    // between a fraction of its size and four times it. Zooming out below its own
    // size shrinks the picture to the middle with black around it -- which is how
    // a film that a camera notch cuts into is pulled clear of the notch.
    var videoScale by rememberSaveable { mutableFloatStateOf(1f) }
    val onScaleDelta: (Float) -> Unit = { factor ->
        videoScale = (videoScale * factor).coerceIn(MIN_VIDEO_SCALE, MAX_VIDEO_SCALE)
    }
    // The zoom scales the whole player view (the graphicsLayer below), which is
    // the only box that fills the screen -- the content frame sizes itself to the
    // film's letterbox, so scaling that would confine the zoom to no effect. The
    // player view now carries no controls of its own (they are the app's, in
    // Compose, over the top and never scaled), so scaling it moves only the
    // picture and there is nothing to carry off.
    // Whether the picture has been zoomed off its own size, so the "back to 1x"
    // chip is offered -- pinching to exactly 1x by hand is not something to ask of
    // anyone.
    val zoomed = kotlin.math.abs(videoScale - 1f) > 0.01f

    // 영상 실제 표시 높이(dp)와, 자막을 '영상 아래 가장자리'에 맞추기 위한 여백(dp)을 함께
    // 구한다. 글자 크기는 화면이 아니라 '영상 표시 높이'에 비례시켜야 가로/세로에서 크기가
    // 들쭉날쭉하지 않는다(media3 SubtitleView의 fractionalTextSize와 같은 기준). videoSize·
    // Box 크기·줌으로 FIT 레터박스를 계산하며, 영상 크기를 아직 모르면 화면 기준으로 폴백.
    val subDensity = LocalDensity.current
    val fallbackHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val subVideoHeightDp: Float
    val subEdgeMargin: androidx.compose.ui.unit.Dp
    run {
        val boxH = playerBoxPx.height.toFloat()
        val boxW = playerBoxPx.width.toFloat()
        val vAspect = if (videoSize.height > 0) {
            videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        } else {
            0f
        }
        if (boxH <= 0f || boxW <= 0f) {
            subVideoHeightDp = fallbackHeightDp
            subEdgeMargin = (fallbackHeightDp * 0.10f).dp
        } else {
            val fitH = if (vAspect > 0f) (boxW / vAspect).coerceAtMost(boxH) else boxH
            val displayH = fitH * videoScale
            val belowVideo = ((boxH - displayH) / 2f).coerceAtLeast(0f)
            // 영상 하단 가장자리에서 10% 안쪽(로워서드·타이틀세이프) -- media3 SubtitleView의
            // 하단 패딩 10%와 같은 자리. 레터박스 여백(belowVideo)을 더해 '영상 아래'에 붙인다.
            val marginPx = (belowVideo + displayH * 0.10f).coerceIn(boxH * 0.03f, boxH * 0.45f)
            subVideoHeightDp = with(subDensity) { displayH.toDp().value }
            subEdgeMargin = with(subDensity) { marginPx.toDp() }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize().onSizeChanged { playerBoxPx = it }) {
            AndroidView(
                factory = { ctx ->
                    // Inflated (not new PlayerView(ctx)) so it uses a TextureView,
                    // which the pinch-zoom can scale; a SurfaceView cannot.
                    val playerView = android.view.LayoutInflater.from(ctx)
                        .inflate(R.layout.media_player_view, null) as PlayerView
                    playerView.player = player
                    // The view draws the picture and the subtitles, nothing else:
                    // its own controls are turned off and the app draws its own in
                    // Compose over the top, so the zoom (which scales this view)
                    // never touches them and they never fall out of reach.
                    playerView.useController = false
                    playerView.setBackgroundColor(android.graphics.Color.BLACK)
                    playerViewRef = playerView
                    playerView
                },
                // Let go of the player when the view goes, so a released view is
                // not left referenced and still fed frames. The controller outlives
                // this (it is the service's) and is released separately.
                onRelease = { it.player = null },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = videoScale
                        scaleY = videoScale
                    },
            )
            // The app-drawn subtitle, shown only while a delay nudge is on -- the
            // player's own text is off then, so this stands in for it, offset in
            // time. Non-interactive, so it never takes a gesture.
            if (subOverlayActive) {
                DelayedSubtitleOverlay(
                    player = player,
                    cues = delayCues.orEmpty(),
                    delayMs = subDelayMs,
                    scale = subScale,
                    color = subColor,
                    outline = subOutline,
                    top = subPosTop,
                    lineSpacing = subLineSpacing,
                    typeface = subFontStyled,
                    bold = subBold,
                    edgeMargin = subEdgeMargin,
                    videoHeightDp = subVideoHeightDp,
                )
            }
            // 내장/지연 없는 텍스트 자막: 플레이어의 현재 큐를 앱이 그려 행간·굵게를 적용한다.
            if (liveOverlayActive) {
                LiveSubtitleOverlay(
                    cues = liveTextCues,
                    scale = subScale,
                    color = subColor,
                    outline = subOutline,
                    top = subPosTop,
                    lineSpacing = subLineSpacing,
                    typeface = subFontStyled,
                    bold = subBold,
                    applyEmbedded = subEmbedded || subColor == AppPreferences.SUBTITLE_COLOR_ORIGINAL,
                    edgeMargin = subEdgeMargin,
                    videoHeightDp = subVideoHeightDp,
                )
            }
            // The gesture layer: a full-screen sheet over the picture that reads
            // every touch, so shrinking the picture never shrinks where a gesture
            // lands. It stands down while the controls are up, letting the built-in
            // seek bar and buttons take touches instead. See VideoGestures for the
            // arbitration -- one finger dials or scrubs, two fingers zoom, and the
            // two never leak into each other. Stood down entirely while locked.
            if (!controlsVisible && !locked && !inPip) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .videoGestures(
                            player = player,
                            onShowControls = showControls,
                            onBrightnessDelta = onBrightnessDelta,
                            onVolumeDelta = onVolumeDelta,
                            onSeekPreview = onSeekPreview,
                            onSeekCommit = onSeekCommit,
                            onScaleDelta = onScaleDelta,
                            onGestureEnd = onGestureEnd,
                            doubleTapSeek = doubleTapSeekPref,
                            gestureSpeed = gestureSpeedPref,
                        ),
                )
            }
            // The chrome, the app's own: a scrim that dims the picture and takes a
            // tap to put the chrome away, the top bar, the centre transport and the
            // seek bar. Drawn in Compose over the picture, so the zoom never moves
            // it and its buttons are always where they are drawn.
            if (controlsVisible && !locked && !inPip) {
                // Every touch of the chrome restarts its hide timer.
                val onTouchChrome: () -> Unit = { controlsTick++ }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { controlsVisible = false })
                        }
                        .background(Color.Black.copy(alpha = 0.28f)),
                )
                // Top bar: back, filename, sleep timer, aspect, rotate.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(top = reservedTopDp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = Color.White,
                        )
                    }
                    Text(
                        viewer.items.getOrNull(index)?.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    )
                    SleepTimerButton(player = player, tint = Color.White)
                    // A-B repeat: first tap marks A, second marks B (and the loop
                    // begins), third clears it. The label says which comes next.
                    val abActive = abA >= 0 && abB > abA
                    IconButton(onClick = {
                        onTouchChrome()
                        val at = player.currentPosition.coerceAtLeast(0L)
                        when {
                            abA < 0 -> abA = at
                            abB <= abA && at > abA -> abB = at
                            else -> { abA = -1L; abB = -1L }
                        }
                    }) {
                        Text(
                            when {
                                abActive -> "A-B"
                                abA >= 0 -> "B"
                                else -> "A"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (abActive || abA >= 0) Color(0xFFE8A183) else Color.White,
                        )
                    }
                    IconButton(onClick = {
                        onTouchChrome()
                        locked = true
                        controlsVisible = false
                    }) {
                        Icon(
                            Icons.Outlined.LockOpen,
                            contentDescription = stringResource(R.string.action_lock),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = {
                            onTouchChrome()
                            // Changing the fit also puts a pinch zoom back to 1x,
                            // so the two ways of sizing the picture do not stack up
                            // into a state that is hard to read or undo.
                            videoScale = 1f
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT ->
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM ->
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.AspectRatio,
                            contentDescription = stringResource(R.string.action_aspect),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = {
                        onTouchChrome()
                        if (autoRotate) {
                            // 잠그기: 지금 보이는 방향을 구체 상수로 고정 → 복귀 후에도 유지.
                            lockedOrientation = activity?.let { currentLockOrientation(it) }
                                ?: ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            autoRotate = false
                        } else {
                            autoRotate = true
                            lockedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }) {
                        if (autoRotate) {
                            Icon(
                                Icons.Outlined.ScreenRotation,
                                contentDescription = stringResource(R.string.action_rotate),
                                tint = Color.White,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.ScreenLockRotation,
                                contentDescription = stringResource(R.string.action_rotate_lock),
                                tint = Color.White,
                            )
                        }
                    }
                }
                // Centre transport: ten seconds back, play/pause, ten seconds on.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            onTouchChrome()
                            player.seekBack()
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.Replay10,
                            contentDescription = stringResource(R.string.video_rewind),
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                onTouchChrome()
                                if (player.isPlaying) player.pause() else player.play()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPlaying) {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.music_pause),
                                tint = Color.Black,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.music_play),
                                tint = Color.Black,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onTouchChrome()
                            player.seekForward()
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.Forward10,
                            contentDescription = stringResource(R.string.video_forward),
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                // Bottom bar: elapsed, the seek bar, total, and the settings gear.
                val shownPos = if (scrubbing) scrubMs else positionMs
                val seekRange = durationMs.coerceAtLeast(1L)
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        // 바닥에서 띄운다: 홈으로 가려 화면 맨 아래를 쓸어올릴 때 스크러버를
                        // 실수로 건드리지 않도록 제스처/내비 인셋 + 여백만큼 올린다.
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatClock(shownPos),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Slider(
                        value = shownPos.coerceIn(0L, seekRange).toFloat(),
                        onValueChange = { value ->
                            scrubbing = true
                            scrubMs = value.toLong()
                            controlsTick++
                        },
                        onValueChangeFinished = {
                            player.seekTo(scrubMs.coerceIn(0L, durationMs))
                            positionMs = scrubMs
                            scrubbing = false
                            controlsTick++
                        },
                        valueRange = 0f..seekRange.toFloat(),
                        // 얇은 진행바: 기본 Material 트랙(굵음)+큰 썸 대신 3dp 트랙 + 12dp 썸으로
                        // 날렵하게. 활성 비율은 현재 위치로 직접 그려 썸과 맞춘다.
                        thumb = {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
                        },
                        track = {
                            val frac = (shownPos.coerceIn(0L, seekRange).toFloat() / seekRange.toFloat())
                                .coerceIn(0f, 1f)
                            Box(
                                Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.3f)),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    )
                    Text(
                        formatClock(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    IconButton(onClick = {
                        onTouchChrome()
                        showSubtitleSheet = true
                    }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            tint = Color.White,
                        )
                    }
                }
            }
            // Locked: a bare layer that swallows every touch (no seek, no pause);
            // a tap flashes an unlock button in the corner, tapping it unlocks.
            if (locked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { lockHint = true })
                        },
                )
                if (lockHint) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable {
                                locked = false
                                lockHint = false
                            }
                            .padding(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.action_unlock),
                            tint = Color.White,
                        )
                    }
                }
            }
            // Where the scrub would land, shown only while a drag is in hand.
            if (seekTarget >= 0) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = formatClock(seekTarget) + " / " + formatClock(player.duration.coerceAtLeast(0L)),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            // The brightness and volume read-outs: a bar up the side the dial is
            // on -- brightness on the left, volume on the right -- shown only
            // while that dial is in hand.
            if (brightnessHud >= 0) {
                EdgeLevelBar(
                    level = brightnessHud,
                    icon = Icons.Filled.LightMode,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            if (volumeHud >= 0) {
                EdgeLevelBar(
                    level = volumeHud,
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            // A "back to 1x" chip: one tap from a shrunk or blown-up picture back
            // to normal, without hunting for exactly 1x by hand. It rides with the
            // controls -- shown only while they are, so a film watched shrunk (to
            // clear a notch) is not saddled with a chip the whole way through -- and
            // sits below the top bar's row so it never lands on the filename.
            if (zoomed && controlsVisible) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = reservedTopDp + 64.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { videoScale = 1f }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.video_zoom_reset),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }

    if (showSubtitleSheet) {
        PlayerSettingsSheet(
            subtitleOn = subtitleOn,
            tracks = textTracks,
            onToggle = onSubtitleToggle,
            onSelectTrack = onSelectTrack,
            audioTracks = audioTracks,
            onSelectAudio = onSelectAudio,
            speed = playbackSpeed,
            onSpeed = onSpeed,
            scale = subScale,
            color = subColor,
            onScale = { subScale = it },
            onColor = { subColor = it },
            lineSpacing = subLineSpacing,
            onLineSpacing = { subLineSpacing = it; appPrefs.setSubtitleLineSpacing(it) },
            bold = subBold,
            onBold = { subBold = it; appPrefs.setSubtitleBold(it) },
            subtitleDelayMs = subDelayMs,
            onSubtitleDelay = onSubtitleDelay,
            showSubtitleDelay = showSubtitleDelay,
            onDismiss = { showSubtitleSheet = false },
        )
    }
}

/**
 * The player's settings, in a small translucent panel in the middle of the
 * picture: subtitles, the audio track, playback speed, and the subtitle look.
 *
 * A panel about half the width, not a sheet across the bottom, so the film stays
 * visible around it rather than being covered; a tap outside puts it away.
 * Subtitles have a switch and, under it, the tracks -- each named by its number,
 * by whether it sits beside the film or inside it, and by its format and
 * language, the shown one marked. Audio lists the film's sound tracks, but only
 * for a film that carries more than one -- the double-audio case. Speed runs from
 * half to double. Size and colour hold for every film. The lists keep to media3's
 * own template: a heading, then radio rows.
 */
@Composable
private fun PlayerSettingsSheet(
    subtitleOn: Boolean,
    tracks: List<TextTrack>,
    onToggle: (Boolean) -> Unit,
    onSelectTrack: (TextTrack) -> Unit,
    audioTracks: List<AudioTrack>,
    onSelectAudio: (AudioTrack) -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    scale: Float,
    color: Int,
    onScale: (Float) -> Unit,
    onColor: (Int) -> Unit,
    lineSpacing: Float,
    onLineSpacing: (Float) -> Unit,
    bold: Boolean,
    onBold: (Boolean) -> Unit,
    subtitleDelayMs: Long,
    onSubtitleDelay: (Long) -> Unit,
    showSubtitleDelay: Boolean,
    onDismiss: () -> Unit,
) {
    // 버튼(⚙) 탭으로 여는 창이라 끌어올리는 바텀시트가 아니라 중앙 다이얼로그로 둔다(손잡이
    // 없음, 바깥 탭으로 닫음). 반응형 크기: 가로(누운 폰)는 넓고 낮게(재생|자막 2열이 여유
    // 있게), 세로는 적당한 폭. 내용이 길면(트랙 많음) 높이 안에서만 스크롤한다.
    val size = rememberDialogMaxSize(wide = true)
    Dialog(onDismissRequest = onDismiss, properties = OloDialogProperties) {
        Column(
            Modifier
                .width(size.width)
                .heightIn(max = size.height)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                "재생 설정",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            // 섹션을 두 묶음으로 나눈다: 펼침(가로)은 재생|자막 2열로 높이를 줄이고, 커버
            // (세로)는 한 열로 쌓는다. 버튼 탭으로 여는 다이얼로그라 손잡이는 없다.
            val landscape = LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE

            // 재생 묶음: 대분류(재생) + 소분류(2글자) 속도·(복수일 때)음성. 반복은 요청대로 제거.
            val playbackGroup: @Composable ColumnScope.() -> Unit = {
                SettingsMajor("재생")
                MinorRow("속도") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SheetStep("−") { onSpeed((((speed - 0.05f) * 20).roundToInt() / 20f).coerceIn(0.25f, 4f)) }
                        Text(
                            speedNumber(speed) + "x",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        SheetStep("+") { onSpeed((((speed + 0.05f) * 20).roundToInt() / 20f).coerceIn(0.25f, 4f)) }
                    }
                }
                // 음성: 복수 오디오 트랙일 때만 노출(단일이면 숨겨 공간 절약).
                if (audioTracks.size > 1) {
                    MinorRow("음성") {
                        Column {
                            audioTracks.forEach { track ->
                                TrackRow(
                                    selected = track.selected,
                                    onClick = { onSelectAudio(track) },
                                    title = stringResource(R.string.audio_track_label, track.number),
                                    detail = "${track.detail} · ${track.language}",
                                )
                            }
                        }
                    }
                }
            }

            // 자막 묶음: 대분류(스위치) + 소분류(2글자) 크기·행간·색상·(해당 시)지연·언어(트랙).
            val subtitleGroup: @Composable ColumnScope.() -> Unit = {
                SettingsMajorSwitch("자막", subtitleOn, onToggle)
                MinorRow("크기") {
                    // 공간 효율화: 두꺼운 Material 슬라이더 대신 슬림 슬라이더(값은 0~1 분수로 환산).
                    val sMin = AppPreferences.MIN_SUBTITLE_SCALE
                    val sMax = AppPreferences.MAX_SUBTITLE_SCALE
                    CpSlimSlider(
                        value = (scale - sMin) / (sMax - sMin),
                        onValueChange = { onScale(sMin + it * (sMax - sMin)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 굵게: 크기 바로 아래(글자 모양 묶음). 재생 중에도 즉시 반영되게 다이얼로그에서
                // 끄고 켠다. 왼쪽 '가나다'는 켜짐이면 굵게 그려져 효과를 바로 보여준다.
                MinorRow("굵게") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "가나다",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = bold, onCheckedChange = onBold)
                    }
                }
                // 행간: 일반 텍스트 자막(앱 오버레이)의 줄 간격 배수. 현재 값을 %로 표시.
                MinorRow("행간") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val lsMin = AppPreferences.MIN_SUBTITLE_LINESPACING
                        val lsMax = AppPreferences.MAX_SUBTITLE_LINESPACING
                        CpSlimSlider(
                            value = (lineSpacing - lsMin) / (lsMax - lsMin),
                            onValueChange = { onLineSpacing(lsMin + it * (lsMax - lsMin)) },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(lineSpacing * 100).roundToInt()}%",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp),
                        )
                    }
                }
                MinorRow("색상") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (swatch in SUBTITLE_COLORS) {
                            val chosen = swatch == color
                            val ringColor = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            if (swatch == AppPreferences.SUBTITLE_COLOR_ORIGINAL) {
                                // '원문': 단색이 아니라 여러 색을 담은 스와치로 '색 고정 아님'을 표시.
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .border(width = if (chosen) 3.dp else 1.dp, color = ringColor, shape = CircleShape)
                                        .padding(3.dp)
                                        .background(Brush.sweepGradient(ORIGINAL_SWATCH), CircleShape)
                                        .clickable { onColor(swatch) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("원", color = Color(0xFF222222), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                            } else {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .border(width = if (chosen) 3.dp else 1.dp, color = ringColor, shape = CircleShape)
                                        .padding(3.dp)
                                        .background(Color(swatch), CircleShape)
                                        .clickable { onColor(swatch) },
                                )
                            }
                        }
                    }
                }
                // 지연: 동기 안 맞는 외부 자막 ±0.1s 미세 조정(해당 자막일 때만).
                if (showSubtitleDelay) {
                    MinorRow("지연") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "늦으면 +, 빠르면 −",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SheetStep("−") { onSubtitleDelay(subtitleDelayMs - 100) }
                                Text(
                                    delayLabel(subtitleDelayMs),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(58.dp),
                                )
                                SheetStep("+") { onSubtitleDelay(subtitleDelayMs + 100) }
                                if (subtitleDelayMs != 0L) {
                                    Text(
                                        "↺",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 18.sp,
                                        modifier = Modifier.clickable { onSubtitleDelay(0) }.padding(start = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                val trackRows: @Composable () -> Unit = {
                    tracks.forEach { track ->
                        val source = stringResource(
                            if (track.external) R.string.subtitle_external else R.string.subtitle_internal,
                        )
                        TrackRow(
                            selected = track.selected,
                            onClick = { onSelectTrack(track) },
                            title = stringResource(R.string.subtitle_track_label, source, track.number),
                            // 식별이 먼저: 언어 · (SDH/강제) · 형식. 같은 언어라도 SDH 여부로 갈린다.
                            detail = listOfNotNull(track.language, track.kind, track.format).joinToString(" · "),
                        )
                    }
                }
                // 언어: 자막 트랙 선택(언어·형식으로 식별). 많으면 고정 프레임 안에서 스크롤.
                MinorRow("언어") {
                    if (tracks.size > SUBTITLE_TRACK_FRAME_THRESHOLD) {
                        val trackScroll = rememberScrollState()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(208.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(trackScroll)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) { trackRows() }
                            if (trackScroll.value > 0) {
                                Box(
                                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(20.dp)
                                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, Color.Transparent))),
                                )
                            }
                            if (trackScroll.value < trackScroll.maxValue) {
                                Box(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))),
                                )
                            }
                        }
                    } else {
                        Column { trackRows() }
                    }
                }
            }

            if (landscape) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(Modifier.weight(1f)) { playbackGroup() }
                    Column(Modifier.weight(1f)) { subtitleGroup() }
                }
            } else {
                playbackGroup()
                Spacer(Modifier.height(14.dp))
                subtitleGroup()
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OloDialogButton("닫기", onClick = onDismiss)
            }
        }
    }
}

/** A small square −/+ step button for the settings sheet. */
@Composable
private fun SheetStep(glyph: String, onStep: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onStep),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A subtitle delay as a signed label in seconds: "0초", "+0.3초", "−0.5초". */
private fun delayLabel(ms: Long): String =
    if (ms == 0L) "0초" else "%+.1f초".format(ms / 1000.0).replace('-', '−')

// 대분류 머리말의 높이: 스위치가 들어가는 '자막'과 글자만 있는 '재생'이 같은 높이를 갖도록
// 고정해, 2열(펼침)에서 좌우 구분선이 같은 선에 오게 한다(스위치가 행을 키워 어긋나던 문제).
private val SettingsMajorHeight = 34.dp

/** 재생 설정의 대분류 머리말(재생/자막): 악센트 색의 굵은 라벨 아래 옅은 구분선을 둬
 *  소분류와 위계를 가른다. */
@Composable
private fun SettingsMajor(text: String) {
    Row(Modifier.fillMaxWidth().height(SettingsMajorHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            fontSize = 15.sp,
            letterSpacing = 0.5.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    SettingsMajorDivider()
}

/** 대분류 '자막'처럼 우측에 켜기/끄기 스위치를 함께 다는 머리말. */
@Composable
private fun SettingsMajorSwitch(text: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(SettingsMajorHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            fontSize = 15.sp,
            letterSpacing = 0.5.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = on,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
    SettingsMajorDivider()
}

@Composable
private fun SettingsMajorDivider() {
    Box(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp).height(1.5.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    )
}

/** 소분류 한 줄: 2글자 라벨을 왼쪽 고정폭에, 컨트롤을 오른쪽에 둬 폼처럼 각을 맞춘다. */
@Composable
private fun MinorRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** One track in a list: a radio, a title, and a quieter detail line under it. */
@Composable
private fun TrackRow(selected: Boolean, onClick: () -> Unit, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(22.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                // Set explicitly: the panel is a Box, not a Material Surface, so
                // an unset text colour falls back to black and vanishes on the
                // dark panel -- which is why the track name could not be read.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A slim bar up one edge of the picture showing a level from empty to full,
 * with its dial's icon above it -- the brightness or volume read-out while a
 * finger is on it, in the manner of the phone's own volume slider.
 */
@Composable
private fun EdgeLevelBar(level: Float, icon: ImageVector, modifier: Modifier) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(6.dp)
                .height(150.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(Color.White),
            )
        }
    }
}

/**
 * Every supported track of [type] in the player's current selection, numbered
 * from one and turned into a row by [row]. Both the subtitle and the audio
 * pickers are built from this, so the walk over the track groups -- skipping the
 * unsupported ones and counting the rest -- is written once, not twice.
 */
private inline fun <T> mapTracksOfType(
    player: Player,
    type: Int,
    row: (group: Tracks.Group, trackIndex: Int, number: Int) -> T,
): List<T> = buildList {
    var number = 0
    for (group in player.currentTracks.groups.filter { it.type == type }) {
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            number++
            add(row(group, i, number))
        }
    }
}

/**
 * A subtitle track as the picker shows it: its place in the list, whether it
 * came from a file or from inside the film, its format and language, whether it
 * is the one showing, and a token that names it in the saved-choice memory.
 */
private data class TextTrack(
    val group: Tracks.Group,
    val trackIndex: Int,
    val number: Int,
    val external: Boolean,
    val format: String,
    val language: String,
    // 같은 언어 자막 구분용 꼬리표: "SDH"(청각장애인용)·"강제", 일반이면 null.
    val kind: String?,
    val token: String,
    val selected: Boolean,
)

/** The value saved against a file whose subtitles the reader turned off. */
private const val SUBTITLE_OFF_TOKEN = "off"

/** Selects [track]'s text, turning subtitles on if they were off. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun applyTextTrack(player: Player, track: TextTrack) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .build()
}

/** Turns subtitles off. */
private fun disableTextTracks(player: Player) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

/** The content uri of an external subtitle track: matched from the media item's
 *  subtitle configurations by the id the sidecar builder stamped on it. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun externalSubtitleUri(player: Player, track: TextTrack): android.net.Uri? {
    val id = track.group.getTrackFormat(track.trackIndex).id ?: return null
    return player.currentMediaItem?.localConfiguration?.subtitleConfigurations
        ?.firstOrNull { it.id == id }?.uri
}

/**
 * Builds an annotated caption from text that may carry basic style tags
 * (<i>/<b>/<u>, already lower-cased by the parser), turning them into italic,
 * bold and underline spans so the app-drawn subtitle keeps the emphasis an
 * external SRT asks for. Tags may nest; a stray close tag is ignored. With no
 * tags this is just the plain text.
 */
private fun buildSubtitleAnnotated(text: String): AnnotatedString {
    if (!text.contains('<')) return AnnotatedString(text)
    return buildAnnotatedString {
        var italic = 0
        var bold = 0
        var underline = 0
        var idx = 0
        val tag = Regex("""</?([ibu])>""")
        fun emit(s: String) {
            if (s.isEmpty()) return
            if (italic == 0 && bold == 0 && underline == 0) {
                append(s)
            } else {
                withStyle(
                    SpanStyle(
                        fontStyle = if (italic > 0) FontStyle.Italic else null,
                        fontWeight = if (bold > 0) FontWeight.Bold else null,
                        textDecoration = if (underline > 0) TextDecoration.Underline else null,
                    ),
                ) { append(s) }
            }
        }
        for (m in tag.findAll(text)) {
            emit(text.substring(idx, m.range.first))
            val closing = m.value.startsWith("</")
            val delta = if (closing) -1 else 1
            when (m.groupValues[1]) {
                "i" -> italic = (italic + delta).coerceAtLeast(0)
                "b" -> bold = (bold + delta).coerceAtLeast(0)
                "u" -> underline = (underline + delta).coerceAtLeast(0)
            }
            idx = m.range.last + 1
        }
        emit(text.substring(idx))
    }
}

/** Reads a subtitle file's text and parses its cues, or null when it cannot be
 *  read or is a format the app does not parse (only SRT/VTT, incl. converted
 *  SAMI). [encoding] forces a charset for the bytes ("" = auto-detect). Runs off
 *  the main thread. */
private fun readSubtitleCues(context: Context, uri: android.net.Uri, encoding: String = ""): List<SubtitleCue>? = runCatching {
    val bytes = when (uri.scheme) {
        "file", null -> uri.path?.let { java.io.File(it).readBytes() }
        else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } ?: return null
    SubtitleCues.parse(decodeSubtitleBytes(bytes, encoding)).ifEmpty { null }
}.getOrNull()

/**
 * Turns subtitle bytes into text. With an explicit [encoding] the chosen charset
 * is used leniently (so a wrong byte shows a replacement, not an exception); with
 * "" the BOM decides, else UTF-8 is tried strictly and MS949 (the common Korean
 * code page) is the fallback -- the same choice [SamiSubtitles] makes, so legacy
 * SRT/SMI that came in a Korean/Japanese code page no longer shows as □□□.
 */
private fun decodeSubtitleBytes(bytes: ByteArray, encoding: String): String {
    // UI 토큰 → 실제 Charset 이름. EUC-KR은 상위호환인 MS949(CP949)로 디코딩해 더 넓게 복구.
    val charsetName = when (encoding.lowercase()) {
        "", "auto" -> null
        "utf-8", "utf8" -> "UTF-8"
        "euc-kr", "ms949", "cp949" -> "MS949"
        "shift-jis", "shift_jis", "sjis" -> "Shift_JIS"
        "gb18030", "gbk" -> "GB18030"
        else -> encoding
    }
    if (charsetName != null) {
        runCatching { return String(bytes, charset(charsetName)) }
    }
    // BOM이 있으면 그 인코딩을 신뢰한다(가장 확실한 신호).
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }
    runCatching {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }
    return runCatching { String(bytes, charset("MS949")) }.getOrElse { String(bytes, Charsets.UTF_8) }
}

/**
 * Draws the delayed subtitle over the picture, styled like the player's own
 * (fraction-of-height size, chosen colour, an outline as a shadow, top or bottom).
 * Ticks its own position at 100ms so a caption lands on time, without disturbing
 * the 500ms UI tick.
 */
@Composable
private fun BoxScope.DelayedSubtitleOverlay(
    player: Player,
    cues: List<SubtitleCue>,
    delayMs: Long,
    scale: Float,
    color: Int,
    outline: Boolean,
    top: Boolean,
    lineSpacing: Float,
    typeface: android.graphics.Typeface?,
    bold: Boolean,
    edgeMargin: androidx.compose.ui.unit.Dp,
    videoHeightDp: Float,
) {
    var pos by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition.coerceAtLeast(0L)
            kotlinx.coroutines.delay(100)
        }
    }
    val text = SubtitleCues.activeText(cues, pos - delayMs) ?: return
    // <i>/<b>/<u> 기본 서식을 기울임·굵게·밑줄로 살려 그린다(나머지 태그는 파싱에서 제거됨).
    val annotated = remember(text) { buildSubtitleAnnotated(text) }
    // 글자 크기는 '영상 표시 높이'에 비례(화면이 아니라) -- 가로/세로에서 크기가 일관된다.
    val size = (videoHeightDp * scale).sp
    // 영상 아래 가장자리에 맞춘 여백(호출부에서 레터박스·줌을 반영해 계산).
    val subMargin = edgeMargin
    // '원문' 색은 파일 색을 쓴다는 센티넬(투명)이라 그대로 칠하면 보이지 않는다 -- 일반
    // 텍스트 자막엔 색 정보가 없으므로 흰색으로 대표해 그린다(SubtitleView 폴백과 동일).
    val drawColor = if (color == AppPreferences.SUBTITLE_COLOR_ORIGINAL) Color.White else Color(color)
    // 선택한 글꼴(TTF/OTF)을 오버레이에도 적용 -- 외부 자막이 이제 항상 이 경로로 그려지므로
    // media3 경로와 글꼴이 어긋나지 않게 한다. '굵게'면 호출부에서 볼드 변형 Typeface를 넘겨
    // 받으므로(기본 글꼴도 볼드 변형), 구체 Typeface를 FontFamily로 싸도 합성 없이 확실히
    // 굵게 그려진다. 기본 글꼴·굵게 아님이면 null이라 시스템 기본을 쓴다.
    val fontFamily = typeface?.let { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(it)) }
    Text(
        annotated,
        color = drawColor,
        fontSize = size,
        lineHeight = size * lineSpacing,
        textAlign = TextAlign.Center,
        fontFamily = fontFamily,
        // '굵게'면 전체를 볼드로. <b> 스팬은 그대로 유지되고, 평문도 함께 굵어진다. 굵게가
        // 아니면 보통(Normal) -- 체크 전후 대비가 또렷하도록 중간굵기를 쓰지 않는다.
        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
        style = if (outline) {
            TextStyle(shadow = Shadow(Color.Black, androidx.compose.ui.geometry.Offset.Zero, blurRadius = 8f))
        } else {
            TextStyle()
        },
        modifier = Modifier
            .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (top) subMargin else 0.dp, bottom = if (top) 0.dp else subMargin),
    )
}

/**
 * Draws the player's current text cues (internal subtitles, or any text subtitle
 * media3 is rendering) itself, so 행간·굵게·크기·색 apply -- media3's SubtitleView
 * has no line-spacing API. With [applyEmbedded] the cue's own styling (color, bold,
 * italic, underline the parser set) is kept; otherwise the user's colour wins.
 * Bitmap cues (PGS/VOBSUB) never reach here -- those stay on the SubtitleView.
 */
@Composable
private fun BoxScope.LiveSubtitleOverlay(
    cues: List<Cue>,
    scale: Float,
    color: Int,
    outline: Boolean,
    top: Boolean,
    lineSpacing: Float,
    typeface: android.graphics.Typeface?,
    bold: Boolean,
    applyEmbedded: Boolean,
    edgeMargin: androidx.compose.ui.unit.Dp,
    videoHeightDp: Float,
) {
    val annotated = remember(cues, applyEmbedded) {
        val parts = cues.mapNotNull { it.text }
        if (parts.isEmpty()) return@remember null
        buildAnnotatedString {
            parts.forEachIndexed { i, cs ->
                if (i > 0) append("\n")
                appendCueText(cs, applyEmbedded)
            }
        }
    } ?: return
    // 글자 크기는 '영상 표시 높이'에 비례(화면이 아니라) -- 가로/세로에서 크기가 일관된다.
    val size = (videoHeightDp * scale).sp
    val subMargin = edgeMargin
    val drawColor = if (color == AppPreferences.SUBTITLE_COLOR_ORIGINAL) Color.White else Color(color)
    val fontFamily = typeface?.let { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(it)) }
    Text(
        annotated,
        color = drawColor,
        fontSize = size,
        lineHeight = size * lineSpacing,
        textAlign = TextAlign.Center,
        fontFamily = fontFamily,
        // 굵게 체크 전후가 또렷이 구분되도록 보통(Normal)↔볼드. 호출부가 볼드 변형 Typeface를
        // 함께 넘겨주므로 구체 글꼴을 써도 합성 실패 없이 굵어진다.
        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
        style = if (outline) {
            TextStyle(shadow = Shadow(Color.Black, androidx.compose.ui.geometry.Offset.Zero, blurRadius = 8f))
        } else {
            TextStyle()
        },
        modifier = Modifier
            .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (top) subMargin else 0.dp, bottom = if (top) 0.dp else subMargin),
    )
}

/** Appends one cue's text, keeping its parser-set styling (color/bold/italic/underline)
 *  when [applyEmbedded]; otherwise plain so the user's colour/size wins. */
private fun AnnotatedString.Builder.appendCueText(cs: CharSequence, applyEmbedded: Boolean) {
    val start = length
    append(cs.toString())
    if (!applyEmbedded || cs !is android.text.Spanned) return
    for (span in cs.getSpans(0, cs.length, Any::class.java)) {
        val s = start + cs.getSpanStart(span)
        val e = start + cs.getSpanEnd(span)
        if (e <= s) continue
        when (span) {
            is android.text.style.ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), s, e)
            is android.text.style.UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
            is android.text.style.StyleSpan -> when (span.style) {
                android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
                android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
                android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), s, e)
            }
        }
    }
}

/**
 * Whether a track is a subtitle the app attached from a file rather than one
 * carried inside the film. The mark it was given (an id) is the sure sign; a
 * label that is a subtitle filename is the fallback, for a media3 that dropped
 * the id in passing.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun isExternalSubtitle(format: androidx.media3.common.Format): Boolean {
    if (format.id?.startsWith(EXTERNAL_SUB_ID_PREFIX) == true) return true
    val labelExt = format.label?.substringAfterLast('.', "")?.lowercase()
    return labelExt != null && labelExt in SubtitleSidecar.EXTENSIONS
}

/**
 * The short format name for a track: for an external one, the file's own
 * extension (SRT, SMI, ASS); for one inside the film, its codec (SUBRIP, VTT).
 * When a subtitle has been transcoded to media3's cues, its own format is kept
 * in the codecs field, which is read here so the cue name never shows.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun subtitleFormat(external: Boolean, format: androidx.media3.common.Format): String {
    if (external) {
        val ext = format.label?.substringAfterLast('.', "")?.uppercase().orEmpty()
        if (ext.isNotEmpty()) return ext
    }
    val mime = if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) {
        format.codecs ?: format.sampleMimeType
    } else {
        format.sampleMimeType
    }
    return when (mime) {
        MimeTypes.APPLICATION_SUBRIP -> "SUBRIP"
        MimeTypes.TEXT_VTT -> "VTT"
        MimeTypes.TEXT_SSA -> "SSA"
        MimeTypes.APPLICATION_TTML -> "TTML"
        MimeTypes.APPLICATION_PGS -> "PGS"
        MimeTypes.APPLICATION_DVBSUBS -> "DVB"
        // The internal cues name is never shown: fall back to the label's
        // extension, then to a plain "SUB", when the real format is not known.
        MimeTypes.APPLICATION_MEDIA3_CUES, null ->
            format.label?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.isNotEmpty() }
                ?: "SUB"
        else -> mime.substringAfterLast('/').uppercase()
    }
}

/**
 * An audio track as the picker shows it: its place in the list, its language and
 * a short codec-and-channels detail, and whether it is the one playing. There to
 * choose between the audio tracks of a film that carries more than one.
 */
private data class AudioTrack(
    val group: Tracks.Group,
    val trackIndex: Int,
    val number: Int,
    val language: String,
    val detail: String,
    val selected: Boolean,
)

/** Selects [track]'s audio. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun applyAudioTrack(player: Player, track: AudioTrack) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
        .build()
}

/**
 * A short "codec · channels" line for an audio track -- AAC · STEREO, AC3 · 5.1
 * -- in the manner of media3's own track names, for telling two tracks apart.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun audioDetail(format: androidx.media3.common.Format): String {
    val codec = when (val mime = format.sampleMimeType) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "AC3"
        MimeTypes.AUDIO_E_AC3 -> "EAC3"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_OPUS -> "OPUS"
        MimeTypes.AUDIO_VORBIS -> "VORBIS"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        null -> null
        else -> mime.substringAfterLast('/').uppercase()
    }
    val channels = when (format.channelCount) {
        1 -> "MONO"
        2 -> "STEREO"
        6 -> "5.1"
        8 -> "7.1"
        androidx.media3.common.Format.NO_VALUE, 0 -> null
        else -> "${format.channelCount}ch"
    }
    return listOfNotNull(codec, channels).joinToString(" · ").ifEmpty { "AUDIO" }
}

// How far a pinch can size the picture: down to a fraction of its own size, so a
// film a camera notch cuts into can be shrunk clear of the notch, and up to four
// times it. One (its own size) sits between the two.
private const val MIN_VIDEO_SCALE = 0.4f
private const val MAX_VIDEO_SCALE = 4f

/** A speed as a label, dropping the ".0" on a whole one: "1", "1.5". */
private fun speedNumber(speed: Float): String =
    if (speed == speed.toLong().toFloat()) speed.toLong().toString() else speed.toString()

/**
 * A stable key for a track, for remembering which one a file was watched with.
 * The track's number is folded in so two internal tracks of the same language --
 * two English, say -- do not share a key and re-select each other's first match.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun subtitleToken(external: Boolean, format: androidx.media3.common.Format, number: Int): String =
    if (external) {
        format.id ?: "ext$number"
    } else {
        val name = format.language?.takeIf { it.isNotBlank() } ?: format.label ?: format.id ?: "int"
        "$name#$number"
    }

// The colours the subtitle can be. '원문'(색 고정 해제, 자막 파일 색 유지) first, then
// white and the caption colours people reach for on a dark film.
private val SUBTITLE_COLORS = listOf(
    org.olo.player.data.AppPreferences.SUBTITLE_COLOR_ORIGINAL,
    0xFFFFFFFF.toInt(),
    0xFFFFEB3B.toInt(),
    0xFF00E5FF.toInt(),
    0xFF76FF03.toInt(),
    0xFFFF5252.toInt(),
)

// '원문' 스와치의 무지개 채움 -- 여러 색을 담아 '색을 고정하지 않음'을 나타낸다.
private val ORIGINAL_SWATCH = listOf(
    Color.White, Color(0xFFFFEB3B), Color(0xFF00E5FF), Color(0xFF76FF03), Color.White,
)

// 자막 트랙이 이 수를 넘으면 고정 높이 프레임 안에서 스크롤한다(시트가 길어지지 않게).
// 이하이면 프레임 없이 그대로 펼친다(빈 프레임이 생기지 않게).
private const val SUBTITLE_TRACK_FRAME_THRESHOLD = 5

// A one-finger drag is one of these for its whole length, fixed the moment it
// begins by the way it leans. Deciding once and holding it is what keeps a dial
// from turning into a scrub -- or the reverse -- when the finger wanders, and it
// is why a drag and the menu tap can never both fire.
private const val DRAG_NONE = 0
private const val DRAG_BRIGHTNESS = 1
private const val DRAG_VOLUME = 2
private const val DRAG_SEEK = 3

// How fast the brightness and volume dials move: a full sweep of either takes
// about a third of the height, rather than the whole of it, which felt sluggish.
private const val DIAL_SENSITIVITY = 3f

// A full sideways sweep scrubs two minutes.
private const val SEEK_SPAN_MS = 120_000f

/**
 * The video player's touch language, on one arbitrated pipeline over a full-screen
 * layer -- so shrinking the picture never shrinks where a gesture lands.
 *
 * The number of fingers decides the family and nothing crosses over. Two fingers
 * zoom; and once a second finger has touched down, the one-finger dials are held
 * off until every finger lifts, so releasing a pinch never lurches the brightness
 * or the scrub -- the interference that pinch-to-shrink brought. One finger, past a
 * small dead-zone, is a dial or a scrub, fixed the moment it crosses the threshold
 * and held to the end: a vertical drag is brightness on the left half and volume on
 * the right, a sideways drag scrubs. A touch that never crosses the threshold is
 * left unconsumed for [onShowControls]/play-pause -- the tap detector below.
 */
private fun Modifier.videoGestures(
    player: MediaController,
    onShowControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: () -> Unit,
    onScaleDelta: (Float) -> Unit,
    onGestureEnd: () -> Unit,
    // 설정 › 제스처: double-tap the left/right third to jump by the seek step
    // (centre toggles play), and hold anywhere for 2x while pressed.
    doubleTapSeek: Boolean,
    gestureSpeed: Boolean,
): Modifier = this
    .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var mode = DRAG_NONE
            var multiTouch = false
            val start = down.position
            var last = down.position
            var seekBase = 0L
            val slop = viewConfiguration.touchSlop
            val width = size.width.toFloat().coerceAtLeast(1f)
            val height = size.height.toFloat().coerceAtLeast(1f)
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    multiTouch = true
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) onScaleDelta(zoom)
                    event.changes.forEach { it.consume() }
                } else if (multiTouch) {
                    // One finger left after a pinch: swallow it so it starts no dial.
                    event.changes.forEach { it.consume() }
                } else {
                    val change = event.changes.firstOrNull { it.pressed }
                    if (change != null) {
                        val pos = change.position
                        if (mode == DRAG_NONE) {
                            val movedX = kotlin.math.abs(pos.x - start.x)
                            val movedY = kotlin.math.abs(pos.y - start.y)
                            if (movedX > slop || movedY > slop) {
                                mode = if (movedY >= movedX) {
                                    if (start.x < width / 2f) DRAG_BRIGHTNESS else DRAG_VOLUME
                                } else {
                                    DRAG_SEEK
                                }
                                if (mode == DRAG_SEEK) seekBase = player.currentPosition
                            }
                        }
                        when (mode) {
                            DRAG_BRIGHTNESS -> {
                                onBrightnessDelta((last.y - pos.y) / height * DIAL_SENSITIVITY)
                                change.consume()
                            }
                            DRAG_VOLUME -> {
                                onVolumeDelta((last.y - pos.y) / height * DIAL_SENSITIVITY)
                                change.consume()
                            }
                            DRAG_SEEK -> {
                                val duration = player.duration
                                if (duration > 0) {
                                    val delta = ((pos.x - start.x) / width * SEEK_SPAN_MS).toLong()
                                    onSeekPreview((seekBase + delta).coerceIn(0L, duration))
                                }
                                change.consume()
                            }
                            else -> Unit
                        }
                        last = pos
                    }
                }
                if (event.changes.all { !it.pressed }) break
            }
            if (mode == DRAG_SEEK) onSeekCommit()
            onGestureEnd()
        }
    }
    .pointerInput(doubleTapSeek, gestureSpeed) {
        fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }
        // Speed held only while a long-press is down; the prior speed is captured
        // at the press and restored on release.
        var boostedFrom: Float? = null
        detectTapGestures(
            onTap = { onShowControls() },
            onDoubleTap = { offset ->
                if (doubleTapSeek) {
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    when {
                        offset.x < w * 0.35f -> player.seekBack()
                        offset.x > w * 0.65f -> player.seekForward()
                        else -> togglePlay()
                    }
                } else {
                    togglePlay()
                }
            },
            onLongPress = if (!gestureSpeed) null else { _ ->
                boostedFrom = player.playbackParameters.speed
                player.setPlaybackSpeed(2f)
            },
            onPress = {
                tryAwaitRelease()
                boostedFrom?.let { player.setPlaybackSpeed(it); boostedFrom = null }
            },
        )
    }

/**
 * Saves where the playing file is now, so it reopens there. A file within a
 * second of its end is saved at the start, since that reads as finished. Does
 * nothing once the playlist is empty -- there is nothing to place.
 */
private fun savePlaybackPosition(player: Player, items: List<MediaEntry>, model: PlayerViewModel) {
    if (player.mediaItemCount == 0) return
    val at = player.currentMediaItemIndex
    val save = org.olo.player.data.resumePositionToSave(player.currentPosition, player.duration)
    items.getOrNull(at)?.let { model.setMediaPosition(it, save) }
}

/**
 * 재생 위치가 "정상적으로 나갈 때"만 저장되던 구멍을 메운다. 종전엔 뒤로가기(close)와 뷰어
 * 컴포저블 onDispose에만 저장이 걸려 있어, 전화 수신·홈·앱 전환·화면 끔으로 앱이 백그라운드로
 * 간 뒤 OS가 프로세스를 회수하거나(저메모리) 크래시·강제종료되면 onDispose가 실행되지 않아
 * 마지막 지점이 유실됐다. 두 방어선을 둔다:
 *
 *  - 생명주기 ON_STOP: 앱이 백그라운드로 가는 그 순간 1회 저장 -- 백그라운드 kill 직전 보장되는
 *    마지막 콜백이라, 전화·홈·앱 전환·화면 끔을 모두 덮는다.
 *  - 주기 체크포인트: 재생 중 [checkpointMs]마다 저장 -- ON_STOP조차 못 받는 하드 크래시·강제
 *    kill에 대비한 안전망(손실 상한 = 체크포인트 간격). 일시정지 중엔 값이 안 변하므로 건너뛴다.
 *
 * (onDispose·close의 저장은 그대로 둔다 -- 곱게 나갈 때의 즉시 저장.)
 */
@Composable
private fun PlaybackPositionKeeper(
    player: Player,
    items: List<MediaEntry>,
    model: PlayerViewModel,
    checkpointMs: Long = 5_000L,
) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player, items) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                savePlaybackPosition(player, items, model)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player, items) {
        while (true) {
            kotlinx.coroutines.delay(checkpointMs)
            if (player.isPlaying) savePlaybackPosition(player, items, model)
        }
    }
}


/**
 * A playable, with any subtitle files found beside it attached.
 *
 * A film often ships its subtitles as a separate file in the same folder,
 * named for the film with a language tag on the end -- "movie.mp4" beside
 * "movie.ko.srt" and "movie.en.srt". Those are gathered and offered as
 * selectable tracks, the first (or a Korean one) shown by default. A SAMI
 * (.smi) among them is turned into WebVTT in [cacheDir] first, since the player
 * has no SAMI reader of its own.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun mediaItemFor(entry: MediaEntry, cacheDir: File, context: android.content.Context): MediaItem {
    // 사이드카 자막은 영상 옆 파일이다. 로컬은 재생 시점에 폴더를 직접 스캔하고, 네트워크는
    // 디스크가 없어 브라우저가 폴더를 나열할 때 찾아 둔 externalSubs를 받아, 작은 자막 파일을
    // 캐시로 내려받아(= 로컬과 같은 file:// 경로로) 붙인다. 이러면 로컬/네트워크가 한 경로로
    // SAMI 변환·기본선택까지 똑같이 처리되고, 재생 중 자막을 원격 스트리밍하지 않아 견고하다.
    val local = entry.localFile
    val found = if (local != null) localSidecars(local, cacheDir) else remoteSidecars(entry, cacheDir, context)
    return buildMediaItem(entry.uri, subtitleConfigurations(found), entry.prefKey, title = entry.nameWithoutExtension)
}

/**
 * A MediaItem for [uri] with [subtitles], built to survive the trip to the
 * playback service. A controller keeps only a MediaItem's id and metadata
 * across that boundary, so the uri is put in the request metadata and the
 * subtitles in the metadata extras, and the service restores both (see
 * SubtitleBundle). Without this the service's player would get a film with no
 * sound file and no external subtitles.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun buildMediaItem(
    uri: Uri,
    subtitles: List<MediaItem.SubtitleConfiguration>,
    // 위치·자막 저장 키(prefKey). mediaId로 실어, 서비스(onTaskRemoved 등)가 UI 없이도
    // 현재 아이템을 같은 키로 저장할 수 있게 한다. 기본은 uri -- 키를 주지 않는 경로 대비.
    mediaId: String = uri.toString(),
    // 알림·잠금화면 미디어 컨트롤에 띄울 제목. 비어 있으면 시스템이 앱 이름("OLO Player 실행
    // 중")만 보여줘 곡 정보가 없어 보였다. 파일명을 기본 제목으로 깔아 둔다 -- 태그(ID3 등)가
    // 있는 파일은 ExoPlayer가 추출한 태그 제목/아티스트/앨범아트가 이 위로 덮어써 더 풍부해지고,
    // 태그가 없는 파일·영상은 최소한 파일명이 뜬다.
    title: String? = null,
): MediaItem = MediaItem.Builder()
    .setUri(uri)
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setExtras(SubtitleBundle.encode(subtitles))
            .build(),
    )
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setSubtitleConfigurations(subtitles)
    .build()

// 자막 포맷·확장자·이름매칭·언어는 로컬/네트워크 공용 규칙(SubtitleSidecar)에서 가져온다.

@androidx.annotation.OptIn(UnstableApi::class)
private fun localSidecars(video: File, cacheDir: File): List<SidecarSub> {
    val dir = video.parentFile ?: return emptyList()
    val base = video.nameWithoutExtension.lowercase()
    val candidates = dir.listFiles()?.filter { it.isFile } ?: return emptyList()

    return candidates.mapNotNull { file ->
        val ext = file.extension.lowercase()
        if (ext !in SubtitleSidecar.EXTENSIONS) return@mapNotNull null
        val stem = file.nameWithoutExtension.lowercase()
        // The subtitle belongs to this film if its name is near enough the
        // film's -- the film's, the film's with a language tag, or a close
        // release name -- so a subtitle whose name is not word-for-word the
        // film's still attaches.
        if (!SubtitleSidecar.nameMatches(base, stem)) return@mapNotNull null
        // SAMI is rewritten to a .vtt the player can read; the rest are used as
        // they are. A .smi that will not convert is dropped rather than shown
        // blank.
        val (uri, mime) = if (ext in SubtitleSidecar.SAMI) {
            val vtt = SamiSubtitles.toVttFile(cacheDir, file) ?: return@mapNotNull null
            Uri.fromFile(vtt) to MimeTypes.TEXT_VTT
        } else {
            Uri.fromFile(file) to (SubtitleSidecar.MIME[ext] ?: return@mapNotNull null)
        }
        SidecarSub(uri, mime, sidecarLanguage(base, stem), file.name)
    }
}

/**
 * 네트워크 소스의 사이드카 자막. 브라우저가 폴더를 나열할 때 이름으로 찾아 둔 원격 자막
 * 파일들([MediaEntry.externalSubs])을 작은 파일이니 캐시로 통째로 내려받아, 그 뒤로는 로컬과
 * 똑같이 다룬다(SAMI는 VTT로 변환, 나머지는 그대로 file://). 재생 중 자막을 원격 스트리밍하지
 * 않으므로 견고하고, 깨진/못 받은 자막은 그 한 개만 조용히 건너뛴다.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun remoteSidecars(entry: MediaEntry, cacheDir: File, context: android.content.Context): List<SidecarSub> {
    if (entry.externalSubs.isEmpty()) return emptyList()
    val base = entry.name.substringBeforeLast('.', entry.name).lowercase()
    val dir = File(cacheDir, "remote-subs").apply { mkdirs() }
    return entry.externalSubs.mapNotNull { sub ->
        val ext = sub.fileName.substringAfterLast('.', "").lowercase()
        if (ext !in SubtitleSidecar.EXTENSIONS) return@mapNotNull null
        // 원격 자막을 통째로 받아 캐시에 쓴다(상한 2MB). 실패하면 이 자막만 건너뛴다.
        val bytes = runCatching { readRemote(context, sub.uri, MAX_SUBTITLE_BYTES) }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val cached = File(dir, "${sub.uri.toString().hashCode()}.$ext")
        runCatching { cached.writeBytes(bytes) }.getOrNull() ?: return@mapNotNull null
        val (uri, mime) = if (ext in SubtitleSidecar.SAMI) {
            val vtt = SamiSubtitles.toVttFile(cacheDir, cached) ?: return@mapNotNull null
            Uri.fromFile(vtt) to MimeTypes.TEXT_VTT
        } else {
            Uri.fromFile(cached) to (SubtitleSidecar.MIME[ext] ?: return@mapNotNull null)
        }
        val stem = sub.fileName.substringBeforeLast('.', sub.fileName).lowercase()
        SidecarSub(uri, mime, sidecarLanguage(base, stem), sub.fileName)
    }
}

/**
 * 고른 사이드카들을 media3 자막 구성으로. 한국어가 있으면 그것을, 없으면 첫 번째를 기본으로
 * 켠다. 파일에서 온 자막이라는 표식(EXTERNAL_SUB_ID_PREFIX)을 id에 달아 선택창이 내장과
 * 구분해 파일 확장자를 포맷으로 보여줄 수 있게 한다.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun subtitleConfigurations(found: List<SidecarSub>): List<MediaItem.SubtitleConfiguration> {
    val defaultIdx = found.indexOfFirst { it.language == "ko" }.let {
        if (it >= 0) it else if (found.isNotEmpty()) 0 else -1
    }
    return found.mapIndexed { i, sub ->
        MediaItem.SubtitleConfiguration.Builder(sub.uri)
            .setMimeType(sub.mime)
            .setLanguage(sub.language)
            .setLabel(sub.label)
            .setId(EXTERNAL_SUB_ID_PREFIX + sub.label)
            .setSelectionFlags(if (i == defaultIdx) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }
}

/** 자막 파일명이 영상명으로 시작할 때 그 뒤 꼬리에서 언어를 읽는다(아니면 알 수 없음). */
private fun sidecarLanguage(videoBase: String, subtitleStem: String): String? {
    val tag = if (subtitleStem.startsWith(videoBase)) {
        subtitleStem.removePrefix(videoBase).trimStart('.', '_', '-', ' ')
    } else {
        ""
    }
    return SubtitleSidecar.languageOf(tag)
}

// A subtitle track the app added from a file, rather than one carried inside
// the film, is marked by an id starting with this, so the picker can say which
// is which and show the file's own extension as the format.
private const val EXTERNAL_SUB_ID_PREFIX = "olo-ext:"

// 원격 자막 파일 다운로드 상한. 자막은 보통 수십 KB라 넉넉히 2MB면 충분하다.
private const val MAX_SUBTITLE_BYTES = 2 * 1024 * 1024

private data class SidecarSub(
    val uri: Uri,
    val mime: String,
    val language: String?,
    val label: String,
)
