package org.olo.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** 크로스페이드(볼륨 램프)의 위치→볼륨 계산을 고정한다. cf=4초, 곡 60초 기준. */
class CrossfadeVolumeTest {

    private val cf = 4_000L
    private val dur = 60_000L

    @Test fun `off returns full volume`() {
        assertEquals(1f, crossfadeVolume(30_000, dur, 0L, true), 1e-4f)
    }

    @Test fun `head fades in from zero`() {
        assertEquals(0f, crossfadeVolume(0, dur, cf, true), 1e-4f)
        assertEquals(0.5f, crossfadeVolume(2_000, dur, cf, true), 1e-4f)
        assertEquals(1f, crossfadeVolume(4_000, dur, cf, true), 1e-4f)
    }

    @Test fun `tail fades out when there is a next track`() {
        assertEquals(1f, crossfadeVolume(56_000, dur, cf, true), 1e-4f)
        assertEquals(0.5f, crossfadeVolume(58_000, dur, cf, true), 1e-4f)
        assertEquals(0f, crossfadeVolume(60_000, dur, cf, true), 1e-4f)
    }

    @Test fun `last track does not fade out`() {
        assertEquals(1f, crossfadeVolume(58_000, dur, cf, hasMore = false), 1e-4f)
    }

    @Test fun `mid track is full volume`() {
        assertEquals(1f, crossfadeVolume(30_000, dur, cf, true), 1e-4f)
    }

    @Test fun `very short track is not faded`() {
        // 2cf 미만(여기선 7초 < 8초)이면 원음 -- 음량이 계속 얕아지는 걸 막는다.
        assertEquals(1f, crossfadeVolume(0, 7_000, cf, true), 1e-4f)
        assertEquals(1f, crossfadeVolume(6_500, 7_000, cf, true), 1e-4f)
    }

    @Test fun `unknown duration is full volume`() {
        assertEquals(1f, crossfadeVolume(1_000, -1, cf, true), 1e-4f)
    }
}
