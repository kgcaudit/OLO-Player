package org.olo.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The media player's engine, living in a service so it outlasts the screen.
 *
 * The player used to be built inside the viewer and released when the viewer
 * closed, which meant it stopped the moment the app went to the background and
 * never had a notification. Held here instead, the sound carries on when the
 * phone is put down and media3 posts the playback notification with its
 * controls for free. The viewer connects to this from the front with a
 * MediaController and hands it a playlist; nothing else talks to it.
 *
 * The player also takes and holds audio focus, and pauses when the headphones
 * are pulled out -- the two things a listener expects of anything that plays a
 * sound and neither of which it did before.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    // Whether the file now playing is a sound rather than a film. It decides
    // which controls the session offers: a film moves ten seconds at a time and
    // withholds the between-file skip (see onConnect), a song is a music player
    // and keeps skip-to-previous and skip-to-next so the notification and the
    // lock screen carry them. Starts on the film's side -- the stricter one --
    // until a loaded item says otherwise.
    private var currentIsAudio = false

    companion object {
        // The sound extensions, mirroring the file list's own (FileKind): what
        // opens as a song rather than a film, and so gets the music controls.
        private val AUDIO_EXTENSIONS =
            setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus")

        // The sleep timer, driven from the player screen: set it going for a
        // number of minutes, cancel it, or ask how long is left. The work is done
        // here, in the service, so the timer stops the sound even with the app in
        // the background and the screen off -- which is the whole point of it.
        const val CMD_SLEEP_SET = "org.olo.player.SLEEP_SET"
        const val CMD_SLEEP_CANCEL = "org.olo.player.SLEEP_CANCEL"
        const val CMD_SLEEP_QUERY = "org.olo.player.SLEEP_QUERY"
        const val EXTRA_SLEEP_MINUTES = "minutes"
        const val EXTRA_SLEEP_REMAINING = "remaining"
    }

    // The pending sleep-timer pause, and when it is due, on the elapsed-time
    // clock so a change of wall clock cannot move it. Zero means none set.
    private val sleepHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null
    private var sleepDueElapsed = 0L
    // 증폭 효과(LoudnessEnhancer)를 서비스 수명에 묶어 둬, 서비스 종료 시 확실히 해제한다
    // (오디오 세션 id가 UNSET으로 떨어지지 않고 끝나도 AudioEffect가 새지 않도록).
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    private fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        val delay = minutes * 60_000L
        sleepDueElapsed = SystemClock.elapsedRealtime() + delay
        val runnable = Runnable {
            session?.player?.let { if (it.isPlaying) it.pause() }
            sleepDueElapsed = 0L
            sleepRunnable = null
        }
        sleepRunnable = runnable
        sleepHandler.postDelayed(runnable, delay)
    }

    private fun cancelSleepTimer() {
        sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
        sleepRunnable = null
        sleepDueElapsed = 0L
    }

    /** Milliseconds left on the sleep timer, or zero when none is set. */
    private fun sleepRemainingMs(): Long =
        if (sleepDueElapsed > 0L) {
            (sleepDueElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            0L
        }

    /**
     * A pending intent that opens the app when the notification is tapped -- the
     * launcher intent, which brings the running task to the front rather than
     * starting a second copy. Null only if the package somehow has no launcher.
     */
    private fun sessionActivityIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Whether [item] is a sound, read from its file extension. */
    private fun isAudioItem(item: MediaItem?): Boolean {
        val uri = item?.localConfiguration?.uri ?: item?.requestMetadata?.mediaUri ?: return false
        val ext = (uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    // The full set, and the film's set with the between-file skips taken out.
    @UnstableApi
    private fun fullPlayerCommands(): Player.Commands =
        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS

    @UnstableApi
    private fun videoPlayerCommands(): Player.Commands =
        fullPlayerCommands().buildUpon()
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

    // The default session commands plus the app's own sleep-timer ones, so a
    // controller may set, cancel and query the timer. Used both when a controller
    // connects and whenever the player commands are refreshed, so the custom ones
    // are never dropped in the refresh.
    @UnstableApi
    private fun sessionCommands(): SessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(SessionCommand(CMD_SLEEP_SET, Bundle.EMPTY))
            .add(SessionCommand(CMD_SLEEP_CANCEL, Bundle.EMPTY))
            .add(SessionCommand(CMD_SLEEP_QUERY, Bundle.EMPTY))
            .build()

    /**
     * Grants or withholds the between-file skip to every connected controller --
     * the app's own, and the internal one the notification draws from -- to match
     * the kind of the item now playing. Called whenever the playing item changes,
     * so opening a song turns the skip on and opening a film turns it off.
     */
    @UnstableApi
    private fun applyCommandsFor(item: MediaItem?) {
        currentIsAudio = isAudioItem(item)
        val session = session ?: return
        val commands = if (currentIsAudio) fullPlayerCommands() else videoPlayerCommands()
        for (controller in session.connectedControllers) {
            session.setAvailableCommands(controller, sessionCommands(), commands)
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        val prefs = org.olo.player.data.AppPreferences(this)
        // The rewind/fast-forward step follows the 설정 › 재생 default, so the side
        // buttons jump by whatever the person chose (10s unless changed).
        val seekStepMs = prefs.seekIntervalSec() * 1000L
        // 설정 › 비디오 · 디코더: "auto" leaves media3's own order (hardware first),
        // "sw"/"hw" reorder the candidates toward software- or hardware-only. Decoder
        // fallback stays on, so an unusable first pick still lands on a working one.
        val decoderPref = prefs.decoder()
        val codecSelector = if (decoderPref == "auto") {
            MediaCodecSelector.DEFAULT
        } else {
            MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                if (decoderPref == "sw") infos.sortedByDescending { it.softwareOnly }
                else infos.sortedByDescending { it.hardwareAccelerated }
            }
        }
        // 자막을 "추출 시 전부"가 아니라 "렌더 시 고른 트랙만" 파싱하는 1.3 이전 경로로
        // 되돌리려면 TextRenderer의 레거시 디코딩을 켜야 한다. media3 1.5.1에는
        // DefaultRenderersFactory에 그 플래그가 없어(상위 버전에 추가됨), 만들어지는
        // TextRenderer에 직접 experimentalSetLegacyDecodingEnabled(true)를 건다. 이것은
        // MediaSourceFactory의 parseSubtitlesDuringExtraction(false)와 반드시 짝이다 --
        // 이것 없이 끄면 "Legacy decoding is disabled"로 고른 자막이 재생을 내린다(지난
        // 되돌림의 원인이 바로 이 짝 누락).
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildTextRenderers(
                context: android.content.Context,
                output: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: ArrayList<androidx.media3.exoplayer.Renderer>,
            ) {
                super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
                out.forEach { r ->
                    if (r is androidx.media3.exoplayer.text.TextRenderer) {
                        r.experimentalSetLegacyDecodingEnabled(true)
                    }
                }
            }
        }
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector)
        // 설정 › 네트워크 · 버퍼: a larger streaming buffer for shaky connections,
        // else media3's default. Applies to every source; harmless for local.
        val loadControl = if (prefs.netBufferLarge()) {
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(60_000, 120_000, 2_500, 5_000)
                .build()
        } else {
            androidx.media3.exoplayer.DefaultLoadControl.Builder().build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            // Sources are read through the app's own factory, so an ftp:// file
            // streams straight off the server (see OloDataSourceFactory) rather
            // than only file and http being playable.
            //
            // 내장 자막이 많을 때의 시작 지연 근본 해결: media3 1.4가 "자막을 추출 시 전부
            // 파싱"으로 기본값을 바꾸면서(선택 안 한 트랙까지, 이미지 자막은 Bitmap까지)
            // 수십 개 자막 MKV의 시작이 느려졌다(상류 미해결 이슈 androidx/media#2667).
            // parseSubtitlesDuringExtraction(false) + 렌더러의 legacyDecodingEnabled(true)를
            // "함께" 켜 1.3 이전처럼 "고른 트랙만 렌더 시 파싱"으로 되돌린다 -- 자막 개수와
            // 무관하게 즉시 시작한다. (지난 되돌림은 이 짝 플래그를 빠뜨려 깨졌던 것이다.)
            //
            // 한계: 이 두 experimental 플래그는 상류가 "향후 제거 예정"이라 명시했다. 제거되면
            // 대안은 (a) #2667의 정식 수정 채택, 또는 (b) libavformat 기반 엔진(libVLC/mpv)로
            // 교체(demux-on-demand라 영구 면역·#3250류 디먹서 결함도 해소, 단 APK 수십 MB↑·
            // LGPL 전용 빌드·UI 재구성 비용). 커스텀 FTP/SFTP/SMB/WebDAV DataSource는 추출기
            // 상위라 이 문제와 무관하며 어느 쪽이든 재사용 가능.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(OloDataSourceFactory(this))
                    .experimentalParseSubtitlesDuringExtraction(false),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // The side buttons jump ten seconds back and on, rather than to the
            // previous or next file: this is what makes the controls show a
            // rewind and a fast-forward.
            .setSeekBackIncrementMs(seekStepMs)
            .setSeekForwardIncrementMs(seekStepMs)
            .build()
        // 설정 › 선호 언어: 트랙이 여러 개일 때 오디오·자막에서 이 언어를 우선 선택한다
        // ("" = media3 자동). 자막은 파일별로 저장된 선택(subtitleChoice)이 있으면 그게
        // 우선하고, 없을 때만 이 선호 언어로 자동 선택된다(MediaViewerScreen 기본 선택 참고).
        val prefAudio = prefs.preferredAudioLang().takeIf { it.isNotEmpty() }
        val prefText = prefs.preferredSubtitleLang().takeIf { it.isNotEmpty() }
        if (prefAudio != null || prefText != null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .apply {
                    prefAudio?.let { setPreferredAudioLanguage(it) }
                    prefText?.let { setPreferredTextLanguage(it) }
                }
                .build()
        }
        // 설정 › 오디오 · 증폭: extra loudness in millibels through a LoudnessEnhancer
        // bound to the player's audio session. Rebuilt whenever the session changes;
        // wrapped in try/catch since some devices refuse the effect.
        val boostMb = prefs.audioBoostMb()
        if (boostMb > 0) {
            player.addListener(object : Player.Listener {
                @UnstableApi
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    loudnessEnhancer?.release()
                    loudnessEnhancer = null
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        runCatching {
                            loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                                setTargetGain(boostMb)
                                enabled = true
                            }
                        }
                    }
                }
            })
        }
        // 설정 › 재생 · 다음 파일 자동 재생: off pauses at each item's end instead of
        // rolling into the next file.
        player.pauseAtEndOfMediaItems = !prefs.autoPlayNext()
        // 설정 › 재생 · 백그라운드 재생: off pauses a video when the app leaves the
        // foreground; a song keeps playing (that is the point of a music service),
        // so only video is paused. Read live so toggling it needs no restart.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!prefs.backgroundPlay() && !currentIsAudio && player.isPlaying) player.pause()
            }
        })
        session = MediaSession.Builder(this, player)
            .setCallback(RestoringCallback())
            // Tapping the notification, or the lock-screen player, opens the app --
            // bringing the running task back to the front rather than starting it
            // over. Without this the notification's body did nothing when tapped.
            .apply { sessionActivityIntent()?.let { setSessionActivity(it) } }
            .build()
        // The controls a song and a film offer differ, so the session watches
        // which is playing and moves the between-file skip on and off to suit.
        player.addListener(object : Player.Listener {
            @UnstableApi
            override fun onEvents(target: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                    )
                ) {
                    applyCommandsFor(target.currentMediaItem)
                }
            }
        })
        // The playback notification carries play/pause alone. media3's default
        // draws a skip-to-previous and a skip-to-next around it, but the side
        // buttons here move within one film, not between films, so between-file
        // skips have no place on the notification -- only the previous button was
        // showing anyway, and it did nothing a listener would expect.
        setMediaNotificationProvider(PlayPauseOnlyNotificationProvider(this))
    }

    /**
     * media3's notification, kept to the buttons that fit what is playing.
     *
     * A film carries play/pause alone: its side buttons move ten seconds, not
     * between files, so a skip has no place on the notification. A song is a
     * music player and keeps its skip-to-previous and skip-to-next around the
     * play button. The two are told apart by the commands the session grants
     * (see applyCommandsFor) -- a film has no skip command, so the skip buttons
     * are never generated for it and the filter has nothing to drop; a song has
     * them, and they are kept.
     */
    @UnstableApi
    private class PlayPauseOnlyNotificationProvider(context: android.content.Context) :
        DefaultMediaNotificationProvider(context) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean,
        ): ImmutableList<CommandButton> =
            ImmutableList.copyOf(
                super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
                    .filter {
                        it.playerCommand == Player.COMMAND_PLAY_PAUSE ||
                            it.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
                            it.playerCommand == Player.COMMAND_SEEK_TO_NEXT
                    },
            )
    }

    /**
     * Puts back what the controller drops in transit -- the uri, from the
     * request metadata, and the subtitle files, from the metadata extras -- so
     * the service's player receives a whole MediaItem rather than a hollow one.
     *
     * An item that still arrives whole (its uri and subtitles intact) is left
     * exactly as it is. Only when the subtitles went missing are they put back
     * from the extras, and only when the uri went missing is it put back from
     * the request metadata -- so a rebuild never wipes subtitles that were
     * already there, which is how they came to vanish before.
     */
    @UnstableApi
    private inner class RestoringCallback : MediaSession.Callback {
        // The system's own media controls -- the lock screen and the notification
        // panel's player on Samsung and Android 13+ -- draw their buttons from the
        // commands the session advertises, not from the notification layout. So
        // the skip-to-previous and skip-to-next commands are withheld here: the
        // side controls move ten seconds within one film, not between files, and a
        // between-file skip has no place there. Rewind and fast-forward are their
        // own commands (seek back and forward) and are untouched, and a film still
        // runs on to the next on its own -- that is the player's doing, not a
        // command a controller sends.
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // The song grants the skip, the film withholds it; a controller that
            // connects before anything is loaded takes the film's stricter set,
            // and applyCommandsFor moves it the moment an item begins.
            val playerCommands = if (currentIsAudio) fullPlayerCommands() else videoPlayerCommands()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(sessionCommands())
                .build()
        }

        // The sleep-timer commands: set it for a number of minutes, cancel it, or
        // ask how long is left. Each replies with the milliseconds remaining, so
        // the screen that asked can show and count down the same figure the
        // service is keeping.
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SLEEP_SET -> setSleepTimer(args.getInt(EXTRA_SLEEP_MINUTES, 0))
                CMD_SLEEP_CANCEL -> cancelSleepTimer()
                CMD_SLEEP_QUERY -> Unit
                else -> return Futures.immediateFuture(
                    // media3가 SessionResult.RESULT_ERROR_* 상수를 SessionError.ERROR_*로
                    // 옮겼다. 알 수 없는 커스텀 명령이라 "지원하지 않음"으로 되돌린다.
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED),
                )
            }
            val extras = Bundle().apply { putLong(EXTRA_SLEEP_REMAINING, sleepRemainingMs()) }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val restored = mediaItems.map { item ->
                val local = item.localConfiguration
                if (local != null && local.subtitleConfigurations.isNotEmpty()) {
                    // Whole already -- uri and subtitles both present.
                    item
                } else {
                    val uri = local?.uri ?: item.requestMetadata.mediaUri
                    if (uri == null) {
                        item
                    } else {
                        item.buildUpon()
                            .setUri(uri)
                            .setSubtitleConfigurations(
                                SubtitleBundle.decode(item.mediaMetadata.extras),
                            )
                            .build()
                    }
                }
            }.toMutableList()
            return Futures.immediateFuture(restored)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Swiping the app away with nothing playing (or paused) should not leave a
    // silent service and its notification behind; a running one is left alone
    // so the sound survives the swipe.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        // 최근앱에서 앱을 밀어 닫아 UI가 사라져도 마지막 재생 지점이 남게, 여기서 한 번 더
        // 저장한다. 현재 아이템의 mediaId가 저장 키(prefKey)라 UI 없이도 같은 키로 쓴다(뷰어의
        // savePlaybackPosition과 같은 규칙: 끝 1초 이내면 다 본 것으로 보고 처음으로 되돌린다).
        if (player != null && player.mediaItemCount > 0) {
            val key = player.currentMediaItem?.mediaId
            if (!key.isNullOrEmpty()) {
                val save = org.olo.player.data.resumePositionToSave(player.currentPosition, player.duration)
                org.olo.player.data.AppPreferences(this).setMediaPosition(key, save)
            }
        }
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        cancelSleepTimer()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
