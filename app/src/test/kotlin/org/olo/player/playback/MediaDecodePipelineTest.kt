package org.olo.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 재생 파이프라인 대조: 실제 ExoPlayer를 Robolectric에서 구동해 "실제 미디어 파일을 추출(demux)→
 * 디코드→끝까지 재생"이 되는지 기기 없이 고정한다.
 *
 * 코덱(MediaCodec)이 필요한 H.264/AAC 같은 포맷의 실제 프레임 디코딩은 기기 실리콘이 있어야 해
 * 여기선 증명할 수 없다. 대신 **코덱이 필요 없는 PCM(WAV)** 을 쓴다: media3의 WAV 추출기가 컨테이너를
 * 읽어 오디오 트랙을 뽑고, 원시 PCM을 디코드해 오디오 싱크로 흘려 STATE_ENDED에 이르는 전 과정을
 * 실제 플레이어로 밟는다. 즉 추출·트랙선택·디코드 파이프라인·재생 클럭·완료까지가 실데이터로 검증된다.
 */
@RunWith(RobolectricTestRunner::class)
class MediaDecodePipelineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `extracts, decodes and plays a real PCM WAV to the end`() {
        val sampleRate = 8000
        val seconds = 1
        val wav = File(createTempDirectory("olo-media").toFile(), "tone.wav")
        wav.writeBytes(makePcm16MonoWav(sampleRate, seconds))

        val player = TestExoPlayerBuilder(context).build()
        try {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
            player.prepare()
            // prepare가 끝나면 실제로 트랙을 뽑았는지 확인(추출 성공) -- 오디오 트랙 1개.
            TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
            assertEquals("WAV에서 길이를 읽어야 한다(약 1초)", 1000L, player.duration)
            val tracks = player.currentTracks.groups
            assertTrue("오디오 트랙이 하나 있어야 한다", tracks.isNotEmpty())
            val audioFormat = tracks.first().getTrackFormat(0)
            assertEquals("raw PCM 오디오로 추출돼야 한다", MimeTypes.AUDIO_RAW, audioFormat.sampleMimeType)
            assertEquals(1, audioFormat.channelCount)
            assertEquals(sampleRate, audioFormat.sampleRate)

            // 끝까지 디코드·재생해 완료 상태에 도달하는지(파이프라인 end-to-end).
            player.play()
            TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED)
            assertEquals(Player.STATE_ENDED, player.playbackState)
        } finally {
            player.release()
            wav.delete()
        }
    }

    /** 16-bit PCM 모노 WAV 바이트를 손으로 만든다(코덱 불필요 -- 추출·디코드 경로를 실데이터로 밟기 위함). */
    private fun makePcm16MonoWav(sampleRate: Int, seconds: Int): ByteArray {
        val numSamples = sampleRate * seconds
        val data = ByteArrayOutputStream()
        for (i in 0 until numSamples) {
            val v = (sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 12000).toInt()
            data.write(v and 0xFF)
            data.write((v shr 8) and 0xFF)
        }
        val pcm = data.toByteArray()
        val out = ByteArrayOutputStream()
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(n: Int) { out.write(n and 0xFF); out.write((n shr 8) and 0xFF); out.write((n shr 16) and 0xFF); out.write((n shr 24) and 0xFF) }
        fun le16(n: Int) { out.write(n and 0xFF); out.write((n shr 8) and 0xFF) }
        val byteRate = sampleRate * 2
        str("RIFF"); le32(36 + pcm.size); str("WAVE")
        str("fmt "); le32(16); le16(1); le16(1); le32(sampleRate); le32(byteRate); le16(2); le16(16)
        str("data"); le32(pcm.size); out.write(pcm)
        return out.toByteArray()
    }
}
