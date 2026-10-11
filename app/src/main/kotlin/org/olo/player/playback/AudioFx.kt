package org.olo.player.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 로컬 파일의 ReplayGain(REPLAYGAIN_TRACK_GAIN) 태그를 dB로 읽는다. VorbisComment(FLAC·OGG)는
 * 키로 바로 읽히고, 없으면 null(정규화 대상 아님). 블로킹이라 백그라운드에서 호출한다.
 */
internal fun readTrackGainDb(file: File): Float? = runCatching {
    val tag = org.jaudiotagger.audio.AudioFileIO.read(file).tag ?: return null
    val raw = sequenceOf("REPLAYGAIN_TRACK_GAIN", "replaygain_track_gain")
        .mapNotNull { key -> runCatching { tag.getFirst(key) }.getOrNull()?.ifBlank { null } }
        .firstOrNull()
    org.olo.player.art.ReplayGain.parseGainDb(raw)
}.getOrNull()

/**
 * 오디오 효과(이퀄라이저·베이스·서라운드·볼륨 정규화)를 플레이어의 오디오 세션에 붙여 관리하는
 * 단일 지점. 서비스(ExoPlayer)와 UI(효과 시트)가 같은 프로세스라 object로 공유한다.
 *
 * - 효과 자체는 Android AudioEffect라 '실제 오디오 세션'이 있어야 만들어진다(기기 전용). 그래서
 *   밴드 수·주파수·레벨 범위·프리셋은 세션이 있으면 기기 값을, 없으면 기본값(5밴드)을 돌려줘
 *   UI가 어디서나 그려진다.
 * - 설정(켬·프리셋·밴드 레벨·베이스·서라운드·정규화)은 자체 SharedPreferences에 저장해 재생 간 유지.
 * - 볼륨 정규화는 곡의 ReplayGain(dB)을 LoudnessEnhancer 목표 게인으로 적용한다(설정 '증폭'과 합산).
 *
 * 모든 효과 조작은 기기·드라이버 사정으로 실패할 수 있어 runCatching으로 감싼다(실패=무효과).
 */
object AudioFx {

    // 기기 EQ가 없을 때 UI가 쓸 기본 밴드(중심 주파수 Hz)와 레벨 범위.
    private val DEFAULT_FREQS = intArrayOf(60, 230, 910, 3600, 14000)
    private const val DEFAULT_MIN_MB = -1500
    private const val DEFAULT_MAX_MB = 1500
    const val BASS_MAX = 1000
    const val VIRT_MAX = 1000

    private var appCtx: Context? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var sessionId = 0
    private var attached = false

    // 설정(저장됨).
    private var cfgEnabled = false
    private var cfgBands = IntArray(DEFAULT_FREQS.size) // millibel
    private var cfgBass = 0
    private var cfgVirt = 0
    private var cfgNormalize = false
    // 현재 곡의 ReplayGain(dB, 없으면 null)과 설정 '증폭'(millibel).
    private var trackGainDb: Float? = null
    private var boostMb = 0

    fun init(context: Context) {
        if (appCtx != null) return
        appCtx = context.applicationContext
        load()
    }

    /** 세션이 바뀌면(서비스가 호출) 효과를 다시 만들어 설정을 적용한다. */
    fun attach(context: Context, audioSessionId: Int) {
        init(context)
        releaseEffects()
        sessionId = audioSessionId
        attached = audioSessionId != 0
        if (!attached) return
        runCatching { eq = Equalizer(0, audioSessionId) }
        runCatching { bass = BassBoost(0, audioSessionId) }
        runCatching { virt = Virtualizer(0, audioSessionId) }
        runCatching { loud = LoudnessEnhancer(audioSessionId) }
        // 저장된 밴드 수가 기기와 다르면 맞춘다.
        val n = bandCount()
        if (cfgBands.size != n) cfgBands = IntArray(n) { cfgBands.getOrElse(it) { 0 } }
        applyAll()
    }

    fun release() {
        releaseEffects()
        sessionId = 0
        attached = false
    }

    private fun releaseEffects() {
        runCatching { eq?.release() }; eq = null
        runCatching { bass?.release() }; bass = null
        runCatching { virt?.release() }; virt = null
        runCatching { loud?.release() }; loud = null
    }

    // ── UI가 읽는 정보(기기 값 우선, 없으면 기본) ──
    fun enabled(): Boolean = cfgEnabled
    fun normalize(): Boolean = cfgNormalize
    fun bassStrength(): Int = cfgBass
    fun virtStrength(): Int = cfgVirt
    fun bandCount(): Int = runCatching { eq?.numberOfBands?.toInt() }.getOrNull() ?: DEFAULT_FREQS.size
    fun centerFreqHz(band: Int): Int =
        runCatching { eq?.getCenterFreq(band.toShort())?.let { it / 1000 } }.getOrNull()
            ?: DEFAULT_FREQS.getOrElse(band) { 1000 }
    fun levelRangeMb(): IntRange =
        runCatching { eq?.bandLevelRange?.let { it[0].toInt()..it[1].toInt() } }.getOrNull()
            ?: (DEFAULT_MIN_MB..DEFAULT_MAX_MB)
    fun bandLevelMb(band: Int): Int = cfgBands.getOrElse(band) { 0 }
    fun presetNames(): List<String> =
        runCatching {
            eq?.let { e -> (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) } }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: listOf("표준", "팝", "록", "재즈", "댄스", "보컬", "베이스")

    // ── 변경(설정 저장 + 즉시 적용) ──
    fun setEnabled(on: Boolean) { cfgEnabled = on; applyAll(); save() }

    fun setBand(band: Int, mb: Int) {
        if (band !in cfgBands.indices) return
        cfgBands[band] = mb.coerceIn(levelRangeMb().first, levelRangeMb().last)
        if (cfgEnabled) runCatching { eq?.setBandLevel(band.toShort(), cfgBands[band].toShort()) }
        save()
    }

    fun usePreset(index: Int) {
        runCatching {
            eq?.let { e ->
                e.enabled = cfgEnabled
                e.usePreset(index.toShort())
                cfgBands = IntArray(e.numberOfBands.toInt()) { e.getBandLevel(it.toShort()).toInt() }
            }
        }
        save()
    }

    fun setBass(strength: Int) {
        cfgBass = strength.coerceIn(0, BASS_MAX)
        runCatching { bass?.let { it.setStrength(cfgBass.toShort()); it.enabled = cfgEnabled && cfgBass > 0 } }
        save()
    }

    fun setVirt(strength: Int) {
        cfgVirt = strength.coerceIn(0, VIRT_MAX)
        runCatching { virt?.let { it.setStrength(cfgVirt.toShort()); it.enabled = cfgEnabled && cfgVirt > 0 } }
        save()
    }

    fun setNormalize(on: Boolean) { cfgNormalize = on; applyLoudness(); save() }

    /** 설정 '증폭'(millibel). 서비스가 세션 붙일 때 전달해 정규화와 합산한다. */
    fun setBoostMb(mb: Int) { boostMb = mb; applyLoudness() }

    /** 현재 곡의 ReplayGain(dB). 서비스가 트랙이 바뀔 때 읽어 넘긴다(없으면 null). */
    fun setTrackGainDb(db: Float?) { trackGainDb = db; applyLoudness() }

    /** 밴드·베이스·서라운드를 0으로, 프리셋 '표준'에 가깝게 초기화. */
    fun reset() {
        cfgBands = IntArray(bandCount()) { 0 }
        cfgBass = 0; cfgVirt = 0
        applyAll(); save()
    }

    private fun applyAll() {
        runCatching {
            eq?.let { e ->
                e.enabled = cfgEnabled
                for (i in 0 until e.numberOfBands.toInt()) {
                    e.setBandLevel(i.toShort(), cfgBands.getOrElse(i) { 0 }.toShort())
                }
            }
        }
        runCatching { bass?.let { it.setStrength(cfgBass.toShort()); it.enabled = cfgEnabled && cfgBass > 0 } }
        runCatching { virt?.let { it.setStrength(cfgVirt.toShort()); it.enabled = cfgEnabled && cfgVirt > 0 } }
        applyLoudness()
    }

    // 정규화(ReplayGain dB → millibel)와 설정 '증폭'을 합쳐 LoudnessEnhancer에 건다. 둘 다 0이면 끔.
    private fun applyLoudness() {
        val gainMb = (if (cfgEnabled && cfgNormalize) trackGainDb?.let { org.olo.player.art.ReplayGain.toMillibel(it) } ?: 0 else 0) + boostMb
        runCatching {
            loud?.let {
                it.setTargetGain(gainMb)
                it.enabled = gainMb != 0
            }
        }
    }

    // ── 저장/복원(자체 SharedPreferences) ──
    private fun prefs() = appCtx?.getSharedPreferences("audiofx", Context.MODE_PRIVATE)

    private fun save() {
        val o = JSONObject()
        o.put("enabled", cfgEnabled)
        o.put("bass", cfgBass)
        o.put("virt", cfgVirt)
        o.put("normalize", cfgNormalize)
        o.put("bands", JSONArray().apply { cfgBands.forEach { put(it) } })
        prefs()?.edit()?.putString("config", o.toString())?.apply()
    }

    private fun load() {
        val s = prefs()?.getString("config", null) ?: return
        runCatching {
            val o = JSONObject(s)
            cfgEnabled = o.optBoolean("enabled", false)
            cfgBass = o.optInt("bass", 0)
            cfgVirt = o.optInt("virt", 0)
            cfgNormalize = o.optBoolean("normalize", false)
            val arr = o.optJSONArray("bands")
            if (arr != null) cfgBands = IntArray(arr.length()) { arr.optInt(it, 0) }
        }
    }
}
