package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.art.AlbumArtCandidate
import org.olo.player.ui.components.CpField
import org.olo.player.ui.theme.OloTheme

/** 앨범아트 검색/선택 결과 콜백에 쓰는 국가 스토어(iTunes) 목록. */
private val COVER_COUNTRIES = listOf("KR" to "한국", "JP" to "일본", "US" to "미국")

/**
 * 앨범아트 검색/선택 시트('음악판 TMDB'). 아티스트+앨범으로 후보 커버를 찾아 그리드로 보여 주고,
 * 국가 스토어(KR/JP/US)를 바꿔 재검색한다. 하나를 골라 [onPicked]로 원본 바이트를 넘긴다.
 *
 * 검색·다운로드는 주입받아(테스트·대조와 분리) 네트워크 구현에 묶이지 않는다.
 * @param search (아티스트, 앨범, 노래 제목, 국가) → 후보들(블로킹, IO에서 호출).
 * @param loadBytes 고른 커버의 원본 URL → 바이트(블로킹, IO).
 */
@Composable
fun CoverPickerSheet(
    initialArtist: String,
    initialAlbum: String,
    initialTitle: String,
    defaultCountry: String,
    search: suspend (artist: String, album: String, title: String, country: String) -> List<AlbumArtCandidate>,
    loadBytes: suspend (url: String) -> ByteArray?,
    // 고른 커버의 원본 바이트와 그 출처 메타(태그 보강용, 없으면 null)를 함께 넘긴다.
    onPicked: (bytes: ByteArray, meta: org.olo.player.art.SourceMeta?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var artist by remember { mutableStateOf(initialArtist) }
    var album by remember { mutableStateOf(initialAlbum) }
    var title by remember { mutableStateOf(initialTitle) }
    var country by remember { mutableStateOf(defaultCountry.uppercase().ifBlank { "KR" }) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<AlbumArtCandidate>>(emptyList()) }
    var selected by remember { mutableStateOf<AlbumArtCandidate?>(null) }
    var applying by remember { mutableStateOf(false) }

    fun runSearch() {
        loading = true
        selected = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { search(artist, album, title, country) }.getOrDefault(emptyList()) }
            results = r
            loading = false
        }
    }

    // 열자마자 씨앗(태그에서 채운 아티스트·앨범·제목)으로 한 번 자동 검색해 바로 후보를 보여 준다.
    // 사용자가 다시 '검색'을 누르지 않아도 되게 -- 대개 씨앗만으로 맞는 커버가 나온다.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (artist.isNotBlank() || album.isNotBlank() || title.isNotBlank()) runSearch()
    }

    OloCardDialog(
        title = "앨범아트 검색",
        onDismiss = onDismiss,
        titleAccent = true,
        actions = {
            OloDialogButton("취소", onClick = onDismiss, primary = false)
            OloDialogButton(if (applying) "적용 중…" else "이 커버 적용", onClick = {
                val sel = selected ?: return@OloDialogButton
                if (applying) return@OloDialogButton
                applying = true
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { runCatching { loadBytes(sel.fullUrl) }.getOrNull() }
                    applying = false
                    if (bytes != null) onPicked(bytes, sel.meta) else onDismiss() // 실패 시 그냥 닫는다(원본 불변).
                }
            })
        },
    ) {
        CoverPickerContent(
            artist = artist, onArtist = { artist = it },
            album = album, onAlbum = { album = it },
            title = title, onTitle = { title = it },
            country = country, onCountry = { country = it },
            loading = loading, results = results, selected = selected,
            onSelect = { selected = it }, onSearch = { runSearch() },
        )
    }
}

/** 시트 알맹이(검색줄·국가 토글·결과 그리드). Dialog 밖에서도 그릴 수 있게 분리(대조 렌더용). */
@Composable
internal fun CoverPickerContent(
    artist: String, onArtist: (String) -> Unit,
    album: String, onAlbum: (String) -> Unit,
    title: String, onTitle: (String) -> Unit,
    country: String, onCountry: (String) -> Unit,
    loading: Boolean,
    results: List<AlbumArtCandidate>,
    selected: AlbumArtCandidate?,
    onSelect: (AlbumArtCandidate) -> Unit,
    onSearch: () -> Unit,
) {
    val c = OloTheme.colors
    CpField(label = "아티스트", value = artist, onValueChange = onArtist)
    CpField(label = "앨범", value = album, onValueChange = onAlbum)
    // 노래 제목: EP·디지털 싱글처럼 곡마다 커버가 다른 경우 곡 단위로도 찾기 위해 검색어에 더한다.
    CpField(label = "노래 제목", value = title, onValueChange = onTitle)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("스토어", color = c.muted, fontSize = 11.sp)
        COVER_COUNTRIES.forEach { (code, label) ->
            val on = code == country
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) c.accent else Color.Transparent)
                    .border(1.dp, if (on) c.accent else c.outline, RoundedCornerShape(14.dp))
                    .clickable { onCountry(code) }.padding(horizontal = 10.dp, vertical = 5.dp),
            ) { Text("$label $code", color = if (on) Color.White else c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(14.dp)).background(c.accent).clickable(onClick = onSearch).padding(horizontal = 14.dp, vertical = 6.dp),
        ) { Text("검색", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }

    Spacer(Modifier.height(12.dp))
    when {
        loading -> Text("찾는 중…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        results.isEmpty() -> Text("아티스트·앨범·노래 제목을 넣고 검색하세요. EP·싱글은 노래 제목이 커버를 찾는 데 도움이 됩니다. 국가 스토어(KR/JP/US)로 한·일 커버도 찾습니다.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        else -> results.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cand -> Box(Modifier.weight(1f)) { CoverTile(cand, cand == selected) { onSelect(cand) } } }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun CoverTile(cand: AlbumArtCandidate, selected: Boolean, onClick: () -> Unit) {
    val c = OloTheme.colors
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(c.accent)
            .border(if (selected) 3.dp else 0.dp, c.accent, RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = cand.thumbUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Icon(Icons.Filled.Album, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(28.dp))
        Box(Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(horizontal = 5.dp, vertical = 2.dp)) {
            Text(cand.source, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Medium)
        }
        if (selected) {
            Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
