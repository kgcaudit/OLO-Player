package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.olo.player.data.AppPreferences
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.components.CpDivider
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.components.CpSettingRow

/**
 * 설정 탭: the settings tree -- eight categories of app-wide defaults. The player
 * reads the same store, so a default set here is what a fresh file opens with
 * (the subtitle size/colour edited under 자막 is exactly what the player draws).
 * Categories with live controls open a real detail; the rest, whose consumers
 * are still to land, open an honest placeholder.
 */
private enum class SettingCategory(val title: String, val summary: String, val note: String) {
    GENERAL("일반", "재생 화면 · 메뉴 · 잠금", ""),
    LIST("목록", "보기 · 정렬 · 포스터", "목록 보기 방식과 정렬 기본값."),
    PLAYBACK("재생", "이어보기 · 되감기 · 배속", ""),
    VIDEO("비디오", "화면비 · 디코더(H/W·S/W)", ""),
    AUDIO("오디오", "증폭 · 지연 · 선호 언어", "음량 증폭·오디오 지연·선호 언어."),
    SUBTITLE("자막", "지연 · 인코딩 · 스타일", ""),
    GESTURE("제스처", "밝기·볼륨·탐색 지정", ""),
    NETWORK("네트워크", "버퍼 · 캐시", "버퍼 크기·캐시 정책·연결 시간초과."),
}

@Composable
fun SettingsTab(model: PlayerViewModel, onBack: () -> Unit = {}) {
    var dest by rememberSaveable { mutableStateOf<SettingCategory?>(null) }
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    dest?.let { cat ->
        BackHandler { dest = null }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CpHeader(cat.title, onBack = { dest = null })
            when (cat) {
                SettingCategory.PLAYBACK -> PlaybackSettings(prefs)
                SettingCategory.VIDEO -> VideoSettings(prefs)
                SettingCategory.SUBTITLE -> SubtitleSettings(prefs)
                SettingCategory.GENERAL -> GeneralSettings(prefs, model)
                SettingCategory.GESTURE -> GestureSettings(prefs)
                SettingCategory.LIST -> ListSettings(prefs)
                SettingCategory.AUDIO -> AudioSettings(prefs)
                SettingCategory.NETWORK -> NetworkSettings(prefs)
            }
        }
        return
    }

    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CpHeader("설정", onBack = onBack)
        val cats = SettingCategory.entries
        cats.forEachIndexed { i, cat ->
            CpSettingRow(label = cat.title, value = cat.summary, onClick = { dest = cat })
            if (i < cats.lastIndex) CpDivider()
        }
    }
}
