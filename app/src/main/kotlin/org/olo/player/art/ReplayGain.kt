package org.olo.player.art

import kotlin.math.roundToInt

/**
 * ReplayGain 태그(REPLAYGAIN_TRACK_GAIN 등)의 값에서 dB를 뽑는다. 값은 "-6.48 dB", "+3.20 dB",
 * "7.5" 처럼 적힌다. 볼륨 정규화에 쓰는 순수 계산이라(파일·오디오 없이) 유닛 테스트로 고정한다.
 */
object ReplayGain {

    private val NUMBER = Regex("""[-+]?\d+(?:\.\d+)?""")

    /** 태그 문자열에서 첫 숫자(부호 포함)를 dB로. 없으면 null. */
    fun parseGainDb(tag: String?): Float? {
        if (tag.isNullOrBlank()) return null
        return NUMBER.find(tag)?.value?.toFloatOrNull()
    }

    /** dB → millibel(1/100 dB). LoudnessEnhancer.setTargetGain이 쓰는 단위. */
    fun toMillibel(db: Float): Int = (db * 100f).roundToInt()
}
