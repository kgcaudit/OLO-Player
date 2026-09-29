package org.olo.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.olo.player.ui.components.CpDivider
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.components.CpSettingRow

/**
 * 설정 탭: the settings tree -- eight categories, each a sub-screen of app-wide
 * defaults. The player's live sheet shares these values, so a default set here
 * is what a fresh file opens with. The category screens are framed; their
 * controls land next, so each opens an honest placeholder for now.
 */
private enum class SettingCategory(val title: String, val summary: String, val note: String) {
    GENERAL("일반", "재생 화면 · 메뉴 · 잠금", "화면 유지·메뉴 배치·잠금 기본값."),
    LIST("목록", "보기 · 정렬 · 썸네일", "목록 보기 방식과 정렬 기본값."),
    PLAYBACK("재생", "이어보기 · 되감기 · 배속", "이어보기·자동재생·되감기 간격·기본 배속."),
    VIDEO("비디오", "화면비 · 디코더(H/W·S/W)", "화면 비율·디코더·회전·제스처 배속."),
    AUDIO("오디오", "증폭 · 지연 · 선호 언어", "음량 증폭·오디오 지연·선호 언어."),
    SUBTITLE("자막", "지연 · 인코딩 · 스타일", "지연·인코딩·크기·색·외곽선·위치."),
    GESTURE("제스처", "밝기·볼륨·탐색 지정", "좌우/상하 제스처 동작 지정."),
    NETWORK("네트워크", "버퍼 · 캐시", "버퍼 크기·캐시 정책·연결 시간초과."),
}

@Composable
fun SettingsTab() {
    var dest by rememberSaveable { mutableStateOf<SettingCategory?>(null) }

    dest?.let {
        ComingSoon(it.title, it.note, onBack = { dest = null })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CpHeader("설정")
        val cats = SettingCategory.entries
        cats.forEachIndexed { i, cat ->
            CpSettingRow(
                label = cat.title,
                value = cat.summary,
                onClick = { dest = cat },
            )
            if (i < cats.lastIndex) CpDivider()
        }
    }
}
