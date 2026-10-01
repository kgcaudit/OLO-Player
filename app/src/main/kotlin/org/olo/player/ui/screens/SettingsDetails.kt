package org.olo.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.olo.player.data.AppPreferences
import org.olo.player.data.SubtitleFont
import org.olo.player.ui.OloCardDialog
import org.olo.player.ui.OloDialogButton
import org.olo.player.ui.components.CpFieldSecret
import org.olo.player.ui.components.CpSectionLabel
import org.olo.player.ui.components.CpSettingRow
import org.olo.player.ui.theme.OloTheme

// The settings categories that carry real, persisted controls. Each reads and
// writes AppPreferences directly; the subtitle size/colour it edits are the very
// values the player already draws with, so a change here shows on the next film.

@Composable
fun PlaybackSettings(prefs: AppPreferences) {
    Column {
        SettingToggle("이어보기", "마지막 지점부터 자동 재생", prefs.resumeEnabled()) { prefs.setResumeEnabled(it) }
        SettingToggle("다음 파일 자동 재생", "폴더 내 순서대로", prefs.autoPlayNext()) { prefs.setAutoPlayNext(it) }
        SettingToggle("백그라운드 재생", "화면을 꺼도 소리 유지", prefs.backgroundPlay()) { prefs.setBackgroundPlay(it) }
        SettingStepper(
            "되감기 · 빨리감기 간격",
            initial = prefs.seekIntervalSec(),
            steps = listOf(5, 10, 15, 30, 60),
            format = { "${it}초" },
        ) { prefs.setSeekIntervalSec(it) }
        SettingSpeed("기본 재생 속도", prefs.defaultSpeed()) { prefs.setDefaultSpeed(it) }
        SettingToggle("화면 켜짐 유지", "재생 중 화면 유지", prefs.keepScreenOn()) { prefs.setKeepScreenOn(it) }
    }
}

@Composable
fun VideoSettings(prefs: AppPreferences) {
    val c = OloTheme.colors
    var decoder by remember { mutableStateOf(prefs.decoder()) }
    Column {
        // 디코더만 비디오 소관. 제스처(배속·더블탭)는 '제스처 · 조작' 그룹에서 다룬다
        // -- 같은 설정을 두 곳에서 편집하면 어느 쪽이 참인지 흐려지므로 한 곳으로 모은다.
        SettingChoice(
            label = "디코더",
            sub = "하드웨어 우선 · 실패 시 소프트웨어",
            options = listOf("auto" to "자동", "hw" to "H/W", "sw" to "S/W"),
            selected = decoder,
        ) { decoder = it; prefs.setDecoder(it) }
        Text(
            "화면비 · HDR 옵션은 다음 단계에서 추가됩니다.",
            color = c.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun SubtitleSettings(prefs: AppPreferences) {
    val context = LocalContext.current
    val c = OloTheme.colors
    var pos by remember { mutableStateOf(prefs.subtitlePosition()) }
    var scale by remember { mutableFloatStateOf(prefs.subtitleScale()) }
    var color by remember { mutableIntStateOf(prefs.subtitleColor()) }
    // The chosen font: its display name (for the row) and its loaded Typeface (for
    // the preview and the player). Picking a TTF/OTF copies it into app storage.
    var fontName by remember { mutableStateOf(prefs.subtitleFontName()) }
    var fontFace by remember { mutableStateOf(SubtitleFont.typeface(context)) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val installed = SubtitleFont.install(context, uri)
            if (installed != null) {
                prefs.setSubtitleFontName(installed)
                fontName = installed
                fontFace = SubtitleFont.typeface(context)
            }
        }
    }
    Column {
        SettingToggle("자막 보기", null, prefs.subtitleEnabled()) { prefs.setSubtitleEnabled(it) }
        SettingSlider(
            label = "크기",
            value = (scale - AppPreferences.MIN_SUBTITLE_SCALE) /
                (AppPreferences.MAX_SUBTITLE_SCALE - AppPreferences.MIN_SUBTITLE_SCALE),
        ) { frac ->
            val s = AppPreferences.MIN_SUBTITLE_SCALE +
                frac * (AppPreferences.MAX_SUBTITLE_SCALE - AppPreferences.MIN_SUBTITLE_SCALE)
            scale = s
            prefs.setSubtitleStyle(s, color)
        }
        val scaleFrac = (scale - AppPreferences.MIN_SUBTITLE_SCALE) /
            (AppPreferences.MAX_SUBTITLE_SCALE - AppPreferences.MIN_SUBTITLE_SCALE)
        SubtitlePreview(scaleFrac = scaleFrac, color = color, outline = prefs.subtitleOutline(), typeface = fontFace)
        SettingSwatches(
            label = "색",
            colors = SUBTITLE_COLORS,
            selected = color,
        ) { color = it; prefs.setSubtitleStyle(scale, it) }
        SettingToggle("외곽선", null, prefs.subtitleOutline()) { prefs.setSubtitleOutline(it) }
        SettingChoice(
            label = "위치",
            sub = null,
            options = listOf("top" to "위", "bottom" to "아래"),
            selected = pos,
        ) { pos = it; prefs.setSubtitlePosition(it) }
        // 글꼴: pick a TTF/OTF, or fall back to the default. "*/*" is allowed because
        // many providers tag font files as application/octet-stream, not font/*.
        CpSettingRow(
            label = "글꼴",
            value = fontName ?: "기본 글꼴",
            onClick = {
                fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*"))
            },
        )
        if (fontName != null) {
            CpSettingRow(
                label = "글꼴 초기화",
                value = "기본값 사용",
                onClick = { SubtitleFont.clear(context); prefs.setSubtitleFontName(null); fontName = null; fontFace = null },
            )
        }
        Text(
            "TTF·OTF 글꼴 파일을 선택하면 자막에 적용됩니다.",
            color = c.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

/**
 * Live subtitle preview over a warm cinematic still (not a flat black box, so it
 * stays on-concept): the caption is drawn in the selected colour with an outline
 * halo for legibility, at a size that tracks the 크기 slider -- exactly how the
 * player will render it over video.
 */
@Composable
private fun SubtitlePreview(scaleFrac: Float, color: Int, outline: Boolean, typeface: android.graphics.Typeface? = null) {
    val sizeSp = (14f + scaleFrac.coerceIn(0f, 1f) * 16f).sp
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).aspectRatio(16f / 7f)
            .background(Brush.linearGradient(listOf(Color(0xFF4B3F52), Color(0xFF6E5A4B), Color(0xFF332C27)))),
    ) {
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.55f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)))),
        )
        Box(
            Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 7.dp, vertical = 3.dp),
        ) { Text("미리보기", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp) }
        Text(
            "가나다 AaBb 미리보기",
            color = Color(color),
            fontSize = sizeSp,
            fontWeight = FontWeight.Bold,
            fontFamily = typeface?.let { FontFamily(it) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, start = 8.dp, end = 8.dp),
            style = if (outline) TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.9f), offset = Offset(0f, 0f), blurRadius = 6f)) else TextStyle(),
        )
    }
}

@Composable
fun GeneralSettings(prefs: AppPreferences, model: org.olo.player.ui.PlayerViewModel) {
    Column {
        // Theme: follow the system, or force light/dark. Goes through the view
        // model's observable state so the whole app re-themes at once.
        SettingChoice(
            label = "테마",
            sub = "시스템 설정을 따르거나 밝게·어둡게 고정",
            options = listOf("system" to "시스템", "light" to "라이트", "dark" to "다크"),
            selected = model.themeMode,
        ) { model.chooseTheme(it) }
        // 화면 켜짐 유지·다음 파일 자동 재생은 '재생' 그룹 소관이라 그쪽에만 둔다
        // -- 같은 설정을 두 그룹에서 편집하면 어느 값이 참인지 흐려진다.
    }
}

@Composable
fun ListSettings(prefs: AppPreferences) {
    var view by remember { mutableStateOf(prefs.listView()) }
    var sort by remember { mutableStateOf(prefs.listSort()) }
    Column {
        SettingChoice(
            label = "보기",
            sub = "목록 또는 그리드",
            options = listOf("list" to "리스트", "grid" to "그리드"),
            selected = view,
        ) { view = it; prefs.setListView(it) }
        SettingChoice(
            label = "정렬",
            sub = null,
            options = listOf("date" to "최근", "name" to "이름", "size" to "크기"),
            selected = sort,
        ) { sort = it; prefs.setListSort(it) }
        SettingToggle("썸네일 표시", "목록에서 미리보기 타일 표시", prefs.listThumbnails()) { prefs.setListThumbnails(it) }
        PosterSettings(prefs)
    }
}

// The opt-in poster section: off until the person turns it on and consents once,
// because turning it on sends file names to TMDB. A personal API key overrides the
// build's default; the attribution below is shown wherever TMDB data appears.
@Composable
private fun PosterSettings(prefs: AppPreferences) {
    val c = OloTheme.colors
    var enabled by remember { mutableStateOf(prefs.postersEnabled()) }
    var key by remember { mutableStateOf(prefs.tmdbApiKey()) }
    var askConsent by remember { mutableStateOf(false) }

    fun toggle() {
        val want = !enabled
        // The first time it is turned on, ask before anything is sent; the notice is
        // shown once, then a later toggle is immediate.
        if (want && !prefs.posterNoticeSeen()) {
            askConsent = true
        } else {
            enabled = want
            prefs.setPostersEnabled(want)
        }
    }

    CpSectionLabel("포스터·썸네일 (TMDB)")
    org.olo.player.ui.OloCheckRow(
        label = "영화·드라마 포스터",
        checked = enabled,
        onToggle = { toggle() },
        sub = "파일명으로 포스터를 받아 표시",
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    if (enabled) {
        CpFieldSecret(
            label = "TMDB API 키",
            value = key,
            onValueChange = { key = it; prefs.setTmdbApiKey(it) },
            placeholder = "기본 키 사용",
        )
        Text(
            TMDB_ATTRIBUTION,
            color = c.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }

    if (askConsent) {
        OloCardDialog(
            title = "포스터 기능을 켤까요?",
            onDismiss = { askConsent = false },
            actions = {
                OloDialogButton("취소", onClick = { askConsent = false }, primary = false)
                OloDialogButton("켜기", onClick = {
                    askConsent = false
                    prefs.setPosterNoticeSeen(true)
                    enabled = true
                    prefs.setPostersEnabled(true)
                })
            },
        ) {
            Text(POSTER_CONSENT, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private const val POSTER_CONSENT =
    "포스터를 받으려면 파일·폴더 이름이 TMDB(themoviedb.org)로 전송됩니다. " +
        "이름만 보내며, 다른 정보는 전송하지 않습니다.\n\n" +
        "이 제품은 TMDB API를 사용하지만 TMDB가 보증하거나 인증하지 않았습니다."

private const val TMDB_ATTRIBUTION =
    "이 제품은 TMDB API를 사용하지만 TMDB가 보증하거나 인증하지 않았습니다."

@Composable
fun AudioSettings(prefs: AppPreferences) {
    var lang by remember { mutableStateOf(prefs.preferredAudioLang()) }
    Column {
        SettingChoice(
            label = "선호 언어",
            sub = "트랙이 여러 개일 때 우선 선택",
            options = listOf("" to "자동", "ko" to "한국어", "en" to "영어", "ja" to "일본어"),
            selected = lang,
        ) { lang = it; prefs.setPreferredAudioLang(it) }
        SettingSlider(
            label = "음량 증폭",
            value = prefs.audioBoostMb() / 2000f,
        ) { frac -> prefs.setAudioBoostMb((frac * 2000).roundToInt()) }
    }
}

@Composable
fun NetworkSettings(prefs: AppPreferences) {
    Column {
        SettingToggle("큰 버퍼", "불안정한 연결에서 더 많이 미리 받기", prefs.netBufferLarge()) { prefs.setNetBufferLarge(it) }
        // 절전 NAS가 깨는 데 걸리는 시간을 감안해 연결 제한시간을 조절(기본 30초). 바꾸면
        // 다음 접속부터, 재시작 뒤에도 유지되도록 NetConfig에도 즉시 반영한다.
        SettingStepper(
            label = "연결 제한시간",
            initial = prefs.connectTimeoutSec(),
            steps = listOf(15, 30, 45, 60, 90, 120),
            format = { "${it}초" },
        ) { prefs.setConnectTimeoutSec(it); org.olo.player.data.NetConfig.connectTimeoutMs = it * 1000 }
    }
}

@Composable
fun GestureSettings(prefs: AppPreferences) {
    Column {
        SettingToggle("제스처로 배속", "길게 눌러 2배속", prefs.gestureSpeed()) { prefs.setGestureSpeed(it) }
        SettingToggle("더블탭 탐색", "좌/우로 되감기·빨리감기", prefs.doubleTapSeek()) { prefs.setDoubleTapSeek(it) }
    }
}

// ---- Reusable setting controls -------------------------------------------------

@Composable
private fun SettingToggle(label: String, sub: String?, initial: Boolean, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(initial) }
    // The card family's check row, so every toggle across the settings tree reads
    // the same as a dialog's.
    org.olo.player.ui.OloCheckRow(
        label = label,
        checked = on,
        onToggle = { on = it; onChange(it) },
        sub = sub,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun SettingStepper(
    label: String,
    initial: Int,
    steps: List<Int>,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    var value by remember { mutableIntStateOf(initial) }
    val c = OloTheme.colors
    CpSettingRow(label = label, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton("−") {
                val i = steps.indexOf(value).coerceAtLeast(0)
                if (i > 0) { value = steps[i - 1]; onChange(value) }
            }
            Text(format(value), color = c.text, fontSize = 15.sp, modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepButton("+") {
                val i = steps.indexOf(value)
                if (i in 0 until steps.lastIndex) { value = steps[i + 1]; onChange(value) }
            }
        }
    })
}

@Composable
private fun SettingSpeed(label: String, initial: Float, onChange: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(initial) }
    val c = OloTheme.colors
    CpSettingRow(label = label, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton("−") { value = ((value - 0.05f).coerceAtLeast(0.25f) * 20).roundToInt() / 20f; onChange(value) }
            Text("%.2fx".format(value), color = c.text, fontSize = 15.sp, modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepButton("+") { value = ((value + 0.05f).coerceAtMost(4f) * 20).roundToInt() / 20f; onChange(value) }
        }
    })
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) {
    val c = OloTheme.colors
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.surface)
            .border(1.dp, c.divider, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = c.text, fontSize = 20.sp) }
}

@Composable
private fun SettingChoice(
    label: String,
    sub: String?,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = OloTheme.colors
    Column {
        CpSettingRow(label = label, value = sub, trailing = null)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((key, text) in options) {
                val on = key == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (on) c.accent else Color.Transparent)
                        .border(1.dp, if (on) c.accent else c.outline, RoundedCornerShape(9.dp))
                        .clickable { onSelect(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, color = if (on) c.onAccent else c.text, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    val c = OloTheme.colors
    var v by remember { mutableFloatStateOf(value) }
    // A row like the other settings: label on the left, the slider filling the
    // rest, so 자막 크기 reads on the same rhythm as the toggles and steppers.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = c.text, fontSize = 16.sp)
        Slider(
            value = v,
            onValueChange = { v = it; onChange(it) },
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.progressTrack,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingSwatches(label: String, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val c = OloTheme.colors
    CpSettingRow(label = label, trailing = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (argb in colors) {
                val on = argb == selected
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.divider, CircleShape)
                        .clickable { onSelect(argb) },
                )
            }
        }
    })
}

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt(), 0xFF76FF03.toInt(),
)
