package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.BuildConfig
import org.olo.player.data.AppPreferences
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.components.CpRow
import org.olo.player.ui.components.CpSettingRow
import org.olo.player.ui.components.CpTile
import org.olo.player.ui.theme.OloTheme

/**
 * 설정 탭: the settings tree, reorganised to a standard, coherent taxonomy of eight
 * groups (after the redesign research), each an icon-pack tile with a one-line
 * summary, reachable through a search box. The player reads the same store, so a
 * default set here is what a fresh file opens with.
 */
private enum class SettingCategory(val title: String, val summary: String, val icon: ImageVector) {
    INTERFACE("인터페이스", "테마 · 언어 · 목록/그리드 · 시작 화면", Icons.Outlined.Palette),
    PLAYBACK("재생", "이어보기 · 배속 · 백그라운드 · PiP", Icons.Outlined.PlayCircle),
    VIDEO("비디오", "디코더 · 화면비 · HDR", Icons.Outlined.Movie),
    AUDIO("오디오", "출력 · 선호 언어 · 증폭 · 지연", Icons.Outlined.VolumeUp),
    SUBTITLE("자막", "표시 · 크기 · 색 · 인코딩 · 지연", Icons.Outlined.Subtitles),
    GESTURE("제스처 · 조작", "밝기 · 볼륨 · 탐색 · 잠금", Icons.Outlined.TouchApp),
    NETWORK("네트워크 · 스트리밍", "버퍼 · 캐시", Icons.Outlined.Cloud),
    ADVANCED("고급 · 정보", "기록 · 저장소 · 버전", Icons.Outlined.Info),
}

@Composable
fun SettingsTab(model: PlayerViewModel, onBack: () -> Unit = {}) {
    var dest by rememberSaveable { mutableStateOf<SettingCategory?>(null) }
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    dest?.let { cat ->
        BackHandler { dest = null }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CpHeader(cat.title, onBack = { dest = null })
            when (cat) {
                // 인터페이스 gathers the old 일반 + 목록 into one coherent group.
                SettingCategory.INTERFACE -> { GeneralSettings(prefs, model); ListSettings(prefs) }
                SettingCategory.PLAYBACK -> PlaybackSettings(prefs)
                SettingCategory.VIDEO -> VideoSettings(prefs)
                SettingCategory.AUDIO -> AudioSettings(prefs)
                SettingCategory.SUBTITLE -> SubtitleSettings(prefs)
                SettingCategory.GESTURE -> GestureSettings(prefs)
                SettingCategory.NETWORK -> NetworkSettings(prefs)
                SettingCategory.ADVANCED -> AdvancedSettings()
            }
        }
        return
    }

    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    val cats = SettingCategory.entries.filter {
        query.isBlank() || it.title.contains(query, true) || it.summary.contains(query, true)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CpHeader("설정", onBack = onBack)
        SearchBox(query = query, onQuery = { query = it })
        cats.forEach { cat ->
            CpRow(
                title = cat.title,
                subtitle = cat.summary,
                leading = { CpTile(cat.icon, OloTheme.colors.accent) },
                onClick = { dest = cat },
            )
        }
    }
}

/** The settings search box: filters the group list by title or summary. */
@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val c = OloTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(c.progressTrack)
            .heightIn(min = 46.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("설정 검색", color = c.muted, fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 고급 · 정보: storage/version and the honest placeholders for what is still to land. */
@Composable
private fun AdvancedSettings() {
    val c = OloTheme.colors
    Column {
        CpSettingRow(label = "버전", value = "OLO Player ${BuildConfig.VERSION_NAME}", trailing = null)
        CpSettingRow(label = "오픈소스 라이선스", value = "사용된 라이브러리", onClick = {})
        Text(
            "기록·저장소·진단 옵션은 다음 단계에서 추가됩니다.",
            color = c.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
