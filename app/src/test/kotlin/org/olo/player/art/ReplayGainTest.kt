package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ReplayGain 태그 → dB → millibel 변환을 고정한다(볼륨 정규화 입력). */
class ReplayGainTest {

    @Test
    fun `parses signed decibel with and without unit`() {
        assertEquals(-6.48f, ReplayGain.parseGainDb("-6.48 dB")!!, 1e-4f)
        assertEquals(3.2f, ReplayGain.parseGainDb("+3.20 dB")!!, 1e-4f)
        assertEquals(7.5f, ReplayGain.parseGainDb("7.5")!!, 1e-4f)
        assertEquals(0f, ReplayGain.parseGainDb("0.00 dB")!!, 1e-4f)
    }

    @Test
    fun `blank or non-numeric yields null`() {
        assertNull(ReplayGain.parseGainDb(null))
        assertNull(ReplayGain.parseGainDb(""))
        assertNull(ReplayGain.parseGainDb("dB"))
    }

    @Test
    fun `db to millibel rounds`() {
        assertEquals(-648, ReplayGain.toMillibel(-6.48f))
        assertEquals(320, ReplayGain.toMillibel(3.2f))
    }
}
