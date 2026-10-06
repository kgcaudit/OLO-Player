package org.olo.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
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
import org.olo.player.ui.components.CpSlimSlider
import org.olo.player.ui.components.CpToggle
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
    var subLang by remember { mutableStateOf(prefs.preferredSubtitleLang()) }
    var subEncoding by remember { mutableStateOf(prefs.subtitleEncoding()) }
    var bold by remember { mutableStateOf(prefs.subtitleBold()) }
    val lsMin = AppPreferences.MIN_SUBTITLE_LINESPACING
    val lsMax = AppPreferences.MAX_SUBTITLE_LINESPACING

    // 공간 효율화 + 반응형: 설정 › 자막은 항목이 많아 커버(세로)에서 스크롤이 길었다. 보기(표시·
    // 크기·행간·색)와 꾸밈(외곽선·굵게·위치·글꼴·내장·인코딩) 두 묶음으로 나눠, 펼침(가로)에선
    // 2열로 배치해 가로 폭을 쓰고 세로를 절반으로 줄인다. 커버에선 한 열로 쌓는다.
    val display: @Composable ColumnScope.() -> Unit = {
        SettingToggle("자막 보기", null, prefs.subtitleEnabled()) { prefs.setSubtitleEnabled(it) }
        // 내장 자막이 여러 개면 이 언어 트랙을 우선 선택(파일별 저장 선택이 있으면 그게 우선).
        SettingChoice(
            label = "선호 언어",
            sub = "자막이 여러 개일 때 우선",
            options = listOf("" to "자동", "ko" to "한국어", "en" to "영어", "ja" to "일본어"),
            selected = subLang,
        ) { subLang = it; prefs.setPreferredSubtitleLang(it) }
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
        SubtitlePreview(scaleFrac = scaleFrac, color = color, outline = prefs.subtitleOutline(), typeface = fontFace, bold = bold)
        // 행간: media3 SubtitleView엔 줄 간격 API가 없어 앱이 직접 그리는 텍스트 자막에만 적용.
        SettingSlider(
            label = "행간",
            value = (prefs.subtitleLineSpacing() - lsMin) / (lsMax - lsMin),
            valueLabel = { frac -> "${(((lsMin + frac * (lsMax - lsMin)) * 100) + 0.5f).toInt()}%" },
        ) { frac -> prefs.setSubtitleLineSpacing(lsMin + frac * (lsMax - lsMin)) }
        SettingHelp("텍스트 자막(SRT·VTT·SMI)만 · 비트맵 자막 제외")
        SettingSwatches(
            label = "색",
            colors = SUBTITLE_COLORS,
            selected = color,
        ) { color = it; prefs.setSubtitleStyle(scale, it) }
        if (color == AppPreferences.SUBTITLE_COLOR_ORIGINAL) {
            SettingHelp("'원문'은 자막 파일 색을 그대로 사용(고정 안 함).")
        }
    }
    val decor: @Composable ColumnScope.() -> Unit = {
        SettingToggle("외곽선", null, prefs.subtitleOutline()) { prefs.setSubtitleOutline(it) }
        // 굵게: 얇은 사용자 글꼴도 강제 볼드로 그려 영상 위 가독성을 높인다. 미리보기도 반영.
        SettingToggle("굵게", "얇은 글꼴도 강하게 — 가독성↑", bold) { bold = it; prefs.setSubtitleBold(it) }
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
            value = fontName ?: "기본 글꼴 · TTF/OTF 선택",
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
        // SSA/ASS·내장 자막의 색·굵기·위치를 그대로 쓸지. '원문' 색과 별개의 독립 토글.
        SettingToggle(
            "내장 스타일 적용",
            "SSA/ASS 색·굵기·위치 그대로",
            prefs.subtitleEmbeddedStyles(),
        ) { prefs.setSubtitleEmbeddedStyles(it) }
        // 자막 인코딩: 레거시 SRT/SMI가 □□□로 깨질 때 수동 지정. 기본 '자동'은 BOM→UTF-8→MS949.
        SettingChoice(
            label = "인코딩",
            sub = "깨진 자막(□□□) 복구",
            options = listOf(
                "" to "자동",
                "utf-8" to "UTF-8",
                "euc-kr" to "EUC-KR",
                "shift-jis" to "일본어",
                "gb18030" to "중국어",
            ),
            selected = subEncoding,
        ) { subEncoding = it; prefs.setSubtitleEncoding(it) }
        // 엔진 한계를 정직하게 고지(과대광고 금지): libass 도입 시 지원 예정이라 로드맵으로 둔다.
        SettingHelp("내장 폰트·고급 ASS 효과(가라오케·블러 등)는 media3 미지원 — libass 도입 시 예정.")
    }

    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { display() }
            Column(Modifier.weight(1f)) { decor() }
        }
    } else {
        Column { display(); decor() }
    }
}

/** 설정 화면의 옅은 도움말 한 줄(간결하게). 길면 두 줄까지. */
@Composable
private fun SettingHelp(text: String) {
    Text(
        text,
        color = OloTheme.colors.muted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
    )
}

/**
 * Live subtitle preview over a warm cinematic still (not a flat black box, so it
 * stays on-concept): the caption is drawn in the selected colour with an outline
 * halo for legibility, at a size that tracks the 크기 slider -- exactly how the
 * player will render it over video.
 */
@Composable
private fun SubtitlePreview(scaleFrac: Float, color: Int, outline: Boolean, typeface: android.graphics.Typeface? = null, bold: Boolean = false) {
    val sizeSp = (14f + scaleFrac.coerceIn(0f, 1f) * 16f).sp
    // 자막 한 줄을 확인할 만큼의 고정 높이 -- 폭 전체 16:7은 세로가 과하게 커, 필요한 만큼만.
    // '원문'은 파일 색을 쓰는 뜻이라 여기선 미리볼 색이 없어 흰색으로 대표해 보여준다.
    val previewColor = if (color == AppPreferences.SUBTITLE_COLOR_ORIGINAL) Color.White else Color(color)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).height(72.dp)
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
            color = previewColor,
            fontSize = sizeSp,
            // '굵게' 토글을 그대로 반영: 켜면 볼드, 끄면 보통(실제 자막 두께와 일치).
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = typeface?.let { FontFamily(it) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp, start = 8.dp, end = 8.dp),
            style = if (outline) TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.9f), offset = Offset(0f, 0f), blurRadius = 6f)) else TextStyle(),
        )
    }
}

@Composable
fun GeneralSettings(model: org.olo.player.ui.PlayerViewModel) {
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
    // 다른 토글과 같은 '라벨 + 오른쪽 스위치' 한 줄로 통일(이전 왼쪽 체크박스 → 스위치).
    Row(
        Modifier.fillMaxWidth().clickable { toggle() }.padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("영화·드라마 포스터", color = c.text, fontSize = 16.sp, lineHeight = 20.sp)
            Text("파일명으로 포스터를 받아 표시", color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        CpToggle(checked = enabled) { toggle() }
    }
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
    val c = OloTheme.colors
    // 공간 효율화: 왼쪽 체크박스(OloCheckRow) 대신 라벨 왼쪽 + 오른쪽 스위치 한 줄 -- 재생
    // 설정 다이얼로그의 스위치와 통일되고, 행 높이가 줄며 정렬이 깔끔해진다.
    Row(
        Modifier.fillMaxWidth().clickable { on = !on; onChange(on) }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 16.sp, lineHeight = 20.sp)
            if (sub != null) Text(sub, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        CpToggle(checked = on) { on = it; onChange(it) }
    }
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
    // 공간 효율화: 라벨+버튼을 2행으로 쌓던 것을 줄인다. 선택지가 적거나(≤3) 가로(펼침)면
    // 라벨 옆에 슬림칩을 한 줄로 두고, 선택지가 많고 세로면 라벨(설명) 아래에 슬림칩을 한 줄로
    // 둔다. 칩은 어느 쪽이든 얇게(세로 패딩 5dp) 그려 높이를 줄인다.
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val inline = options.size <= 3 || landscape
    if (inline) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChoiceLabel(label, sub, Modifier.weight(1.1f))
            Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((key, text) in options) {
                    ChoiceChip(text, key == selected, Modifier.weight(1f)) { onSelect(key) }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
            ChoiceLabel(label, sub, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((key, text) in options) {
                    ChoiceChip(text, key == selected, Modifier.weight(1f)) { onSelect(key) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceLabel(label: String, sub: String?, modifier: Modifier) {
    val c = OloTheme.colors
    Column(modifier) {
        Text(label, color = c.text, fontSize = 16.sp, lineHeight = 20.sp)
        if (sub != null) Text(sub, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = OloTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.accent else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) c.onAccent else c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, valueLabel: ((Float) -> String)? = null, onChange: (Float) -> Unit) {
    val c = OloTheme.colors
    var v by remember { mutableFloatStateOf(value) }
    // A row like the other settings: label on the left, the slider filling the
    // rest, so 자막 크기 reads on the same rhythm as the toggles and steppers.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = c.text, fontSize = 16.sp, modifier = Modifier.width(40.dp))
        // 공간 효율화: 두꺼운 Material 슬라이더 대신 슬림 슬라이더(트랙 4dp·썸 16dp)로 행 높이↓.
        CpSlimSlider(value = v, onValueChange = { v = it; onChange(it) }, modifier = Modifier.weight(1f))
        // 줄 간격처럼 수치가 의미 있는 슬라이더만 현재 값을 숫자로 보여준다(크기는 미리보기로 확인).
        if (valueLabel != null) {
            Text(valueLabel(v), color = c.muted, fontSize = 13.sp, modifier = Modifier.width(44.dp))
        }
    }
}

@Composable
private fun SettingSwatches(label: String, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val c = OloTheme.colors
    CpSettingRow(label = label, trailing = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (argb in colors) {
                val on = argb == selected
                if (argb == AppPreferences.SUBTITLE_COLOR_ORIGINAL) {
                    // '원문': 단색이 아니라 여러 색을 담은 스와치로 '색 고정 아님'을 나타낸다.
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Brush.sweepGradient(ORIGINAL_SWATCH))
                            .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.divider, CircleShape)
                            .clickable { onSelect(argb) },
                        contentAlignment = Alignment.Center,
                    ) { Text("원", color = Color(0xFF222222), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                } else {
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
        }
    })
}

// 맨 앞 '원문'(자막 파일 색 유지) 다음에 단색들. 흰색이 기본.
private val SUBTITLE_COLORS = listOf(
    AppPreferences.SUBTITLE_COLOR_ORIGINAL,
    0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt(), 0xFF76FF03.toInt(),
)

// '원문' 스와치의 무지개 채움 -- 여러 색을 한 조각에 담아 '색을 고정하지 않음'을 표시.
private val ORIGINAL_SWATCH = listOf(
    Color.White, Color(0xFFFFEB3B), Color(0xFF00E5FF), Color(0xFF76FF03), Color.White,
)
