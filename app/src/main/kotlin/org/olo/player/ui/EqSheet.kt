package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.olo.player.playback.AudioFx
import org.olo.player.ui.theme.OloTheme

/**
 * 오디오 효과 시트(공간 최적화): 한 다이얼로그에 전체 사용·프리셋·이퀄라이저·베이스/서라운드·
 * 볼륨 정규화를 스크롤 없이 담는다. 값은 [AudioFx]를 직접 읽고 바꿔 재생 중 바로 적용된다.
 */
@Composable
fun EqSheet(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember { AudioFx.init(ctx); true }
    OloCardDialog(
        title = "오디오 효과",
        onDismiss = onClose,
        titleAccent = true,
        actions = { OloDialogButton("닫기", onClick = onClose) },
    ) {
        EqContent()
    }
}

/** 시트 알맹이(전체사용·프리셋·EQ·베이스/서라운드·정규화·초기화). Dialog 밖에서도 그릴 수 있게
 *  분리해 대조 렌더가 같은 몸을 쓴다. 상태는 [AudioFx]를 직접 읽고 바꿔 재생 중 바로 적용된다. */
@Composable
internal fun EqContent() {
    val c = OloTheme.colors
    var enabled by remember { mutableStateOf(AudioFx.enabled()) }
    var normalize by remember { mutableStateOf(AudioFx.normalize()) }
    var bass by remember { mutableStateOf(AudioFx.bassStrength()) }
    var virt by remember { mutableStateOf(AudioFx.virtStrength()) }
    val range = remember { AudioFx.levelRangeMb() }
    val freqs = remember { (0 until AudioFx.bandCount()).map { AudioFx.centerFreqHz(it) } }
    var bands by remember { mutableStateOf((0 until AudioFx.bandCount()).map { AudioFx.bandLevelMb(it) }) }
    val presets = remember { AudioFx.presetNames() }
    var preset by remember { mutableStateOf(-1) }

    fun refreshBands() { bands = (0 until AudioFx.bandCount()).map { AudioFx.bandLevelMb(it) } }

    Column(Modifier.fillMaxWidth()) {
        // 전체 사용.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("전체 사용", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; AudioFx.setEnabled(it) }, colors = switchColors(c))
        }
        Spacer(Modifier.height(8.dp))

        // 프리셋(가로 스크롤).
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEachIndexed { i, name ->
                Pill(name, on = preset == i) { preset = i; AudioFx.usePreset(i); refreshBands() }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 이퀄라이저: 좌측 dB 축 + 짧은 수직 슬라이더(기기 제공 밴드).
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg).padding(12.dp)) {
            val maxDb = (range.last / 100)
            Column(Modifier.height(96.dp).padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text("+$maxDb", color = c.muted, fontSize = 8.sp)
                Text("0", color = c.muted, fontSize = 8.sp)
                Text("−$maxDb", color = c.muted, fontSize = 8.sp)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                bands.forEachIndexed { i, mb ->
                    BandSlider(
                        level = mb, min = range.first, max = range.last,
                        label = freqLabel(freqs.getOrElse(i) { 1000 }),
                        onLevel = { v ->
                            bands = bands.toMutableList().also { it[i] = v }
                            preset = -1
                            AudioFx.setBand(i, v)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 베이스 + 서라운드(2열).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LabeledSlider("베이스", bass, AudioFx.BASS_MAX, Modifier.weight(1f)) { bass = it; AudioFx.setBass(it) }
            LabeledSlider("서라운드", virt, AudioFx.VIRT_MAX, Modifier.weight(1f)) { virt = it; AudioFx.setVirt(it) }
        }
        Spacer(Modifier.height(10.dp))

        // 볼륨 정규화.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("볼륨 정규화", color = c.text, fontSize = 13.sp)
                Text("ReplayGain 태그로 곡마다 음량 평준화", color = c.muted, fontSize = 9.sp)
            }
            Switch(checked = normalize, onCheckedChange = { normalize = it; AudioFx.setNormalize(it) }, colors = switchColors(c))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("효과는 재생 중 바로 적용 · 밴드는 기기 제공", color = c.muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, c.outline, RoundedCornerShape(12.dp))
                    .clickable { AudioFx.reset(); refreshBands(); bass = 0; virt = 0; preset = -1 }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) { Text("초기화", color = c.muted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun switchColors(c: org.olo.player.ui.theme.OloColors) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = c.accent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = c.outline,
    uncheckedBorderColor = c.outline,
)

/** 한 밴드의 수직 슬라이더(위로 드래그=증가). 가운데가 0dB. */
@Composable
private fun BandSlider(level: Int, min: Int, max: Int, label: String, onLevel: (Int) -> Unit) {
    val c = OloTheme.colors
    val cur by rememberUpdatedState(level)
    val frac = ((level - min).toFloat() / (max - min)).coerceIn(0f, 1f) // 0=바닥, 1=꼭대기
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
        Box(
            Modifier.width(18.dp).height(96.dp).pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val span = (max - min).toFloat()
                    val d = -dragAmount / size.height * span // 위로 끌면 +
                    onLevel((cur + d).roundToInt().coerceIn(min, max))
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(5.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.outline)) {
                // 0dB 위로/아래로 채운다(가운데 기준).
                Box(Modifier.fillMaxWidth().fillMaxHeight(frac).align(Alignment.BottomCenter).clip(RoundedCornerShape(3.dp)).background(c.accent))
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = (96 * frac).dp - 6.dp).size(14.dp).clip(CircleShape).background(c.accent))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = c.muted, fontSize = 8.sp)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, max: Int, mod: Modifier, onValue: (Int) -> Unit) {
    val c = OloTheme.colors
    Column(mod) {
        Text(label, color = c.text, fontSize = 11.sp)
        Slider(
            value = value.toFloat(), onValueChange = { onValue(it.roundToInt()) },
            valueRange = 0f..max.toFloat(),
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.outline),
        )
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = OloTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) c.accent else Color.Transparent)
            .border(1.dp, if (on) c.accent else c.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 5.dp),
    ) { Text(text, color = if (on) Color.White else c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
}

// 중심 주파수(Hz)를 짧은 라벨로: 1000 미만은 "N", 이상은 "Nk"(소수 한 자리까지).
private fun freqLabel(hz: Int): String =
    if (hz < 1000) "$hz" else {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}k" else "${(Math.round(k * 10) / 10f)}k"
    }
