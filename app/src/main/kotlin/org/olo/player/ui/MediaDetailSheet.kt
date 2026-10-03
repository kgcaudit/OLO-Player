package org.olo.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.olo.player.art.CastMember
import org.olo.player.art.MediaDetails
import org.olo.player.art.MediaTech
import org.olo.player.art.MediaTitle
import org.olo.player.art.Posters
import org.olo.player.art.TitleParser
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ui.theme.OloColors
import org.olo.player.ui.theme.OloTheme

/**
 * 버튼/롱프레스로 여는 "상세정보" 다이얼로그. 예전엔 탐색기에서도 보는 파일 사실(이름·용량·
 * 수정·경로)만 보여 줘 "무엇을 보는가"는 알 수 없었다. 이제 작품을 설명한다:
 * 아이덴티티(배경+포스터+제목/원제·평점·연도·러닝타임·등급) → 장르 → 줄거리 → 출연·제작 →
 * 기술 정보(이 파일) → 파일 정보 → 액션. (확정 구상안.)
 *
 * 포스터는 그리드 카드와 똑같은 입력·캐시로 풀어 섬네일과 항상 일치하고, 작품 메타는 TMDB
 * 상세에서 받는다. TMDB가 없거나(키 미설정·미매칭) 아직 로딩 중이면 작품 섹션을 접고 파일
 * 정보만 보여 준다 -- 그래도 기술 정보는 파일명에서 읽어 채운다.
 */
@Composable
fun MediaDetailSheet(
    entry: RemoteEntry,
    folderName: String?,
    sidecar: Any?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    posterName: String? = null,
    posterNameAlt: String? = null,
    overrideUrl: String? = null,
    nfoArt: (suspend () -> Any?)? = null,
    artCache: SnapshotStateMap<String, Any?>? = null,
    resumeMs: Long = 0L,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onChangePoster: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val c = OloTheme.colors

    // 포스터 해석을 그리드와 통일: override → 사이드카 → TMDB(폴더 제목 우선, 파일명 보조).
    val queries = buildList { add(posterName ?: entry.name); posterNameAlt?.let { if (it != posterName) add(it) } }
    val remote = rememberRemoteArt(queries, folderName, overrideUrl == null && sidecar == null, nfoArt, artCache)
    val poster = overrideUrl ?: sidecar ?: remote

    // 작품 메타(TMDB)와 회차 스틸은 상세를 열 때만 받는다. 포스터와 같은 제목 기준으로 질의해
    // 그리드가 가리키는 작품과 같은 상세를 가져온다. 실패/로딩 중이면 null → 작품 섹션 접힘.
    val lookupName = posterName ?: entry.name
    var details by remember(lookupName, folderName) { mutableStateOf<MediaDetails?>(null) }
    var still by remember(entry.name) { mutableStateOf<String?>(null) }
    LaunchedEffect(lookupName, folderName) {
        details = runCatching { Posters.get(context).details(lookupName, folderName) }.getOrNull()
        still = runCatching { Posters.get(context).stillUrl(entry.name, folderName) }.getOrNull()
    }
    val tech = remember(entry.name) { MediaTech.parse(entry.name) }

    // 긴 상세(출연·줄거리까지)가 작은 화면(커버)에서 넘칠 수 있어 카드 높이를 화면의 90%로
    // 묶고 내부를 세로 스크롤한다. 짧으면 스크롤이 생기지 않아 티가 나지 않는다.
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = screenH * 0.9f)
                .clip(RoundedCornerShape(26.dp)).background(c.dialog)
                .verticalScroll(rememberScrollState()),
        ) {
            MediaDetailContent(
                entry = entry,
                folderName = folderName,
                details = details,
                tech = tech,
                still = still,
                poster = poster,
                resumeMs = resumeMs,
                favorite = favorite,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onChangePoster = onChangePoster,
            )
        }
    }
}

/**
 * 상세 본문(아트·메타 모두 해석된 상태). 스크린샷 대조를 위해 프레임과 분리해, 가짜
 * 데이터로도 단독 렌더된다. [details]가 null이면 작품 섹션을 접고 파일 정보 중심으로 그린다.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MediaDetailContent(
    entry: RemoteEntry,
    folderName: String?,
    details: MediaDetails?,
    tech: MediaTech,
    still: Any?,
    poster: Any?,
    resumeMs: Long,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onChangePoster: (() -> Unit)?,
) {
    val context = LocalContext.current
    val c = OloTheme.colors
    val kind = kindOf(entry.name, entry.isDirectory)
    val parsed = remember(entry.name, folderName) { TitleParser.parse(entry.name, folderName) }
    val named = remember(parsed, entry.name) { nameFor(parsed, entry.name) }

    val title = details?.title ?: named.title
    // 부제: 원제가 있으면 원제. 없을 때 파싱한 부제를 쓰되, 연도만인 경우는 아래 메타 줄에서
    // 이미 보여 주므로 중복 표기하지 않는다(에피소드의 "시즌 1 · 2화" 같은 건 그대로 둔다).
    val original = details?.originalTitle ?: named.sub?.takeUnless { it.toIntOrNull() != null }
    val backdrop = details?.backdropUrl ?: still

    Column(Modifier.fillMaxWidth()) {
        // 1) 아이덴티티: 배경(백드롭/스틸)이 있으면 헤더로 깔고 카드 바탕으로 페이드시킨다.
        if (backdrop != null) {
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(backdrop).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.dialog))))
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroPoster(poster, kind, context)
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(title, color = c.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (original != null) {
                    Text(original, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                IdentityMeta(c, details, named)
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            // 2) 장르
            if (details != null && details.genres.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    details.genres.forEach { Chip(c, it) }
                }
            }
            // 3) 줄거리
            if (details != null && details.overview.isNotBlank()) {
                Section(c, "줄거리")
                Text(details.overview, color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
            }
            // 4) 출연·제작
            if (details != null && (details.director != null || details.cast.isNotEmpty())) {
                Section(c, "출연 · 제작")
                if (details.director != null) {
                    Text("감독  ${details.director}", color = c.text, fontSize = 13.sp)
                    if (details.cast.isNotEmpty()) Spacer(Modifier.height(10.dp))
                }
                if (details.cast.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        details.cast.forEach { CastChip(c, it, context) }
                    }
                }
            }
            // 5) 기술 정보(이 파일) -- 파일명에서 읽어, TMDB가 없어도 채운다.
            val techChips = tech.chips()
            if (techChips.isNotEmpty()) {
                Section(c, "기술 정보")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    techChips.forEach { Chip(c, it) }
                }
            }
            // 6) 파일 정보
            Section(c, "파일 정보")
            MetaLine("파일", entry.name)
            val size = entry.size?.takeIf { it >= 0 && !entry.isDirectory }?.let { humanSize(it) }
            val date = entry.modified?.takeIf { it > 0 }?.let { formatDate(it) }
            val sizeDate = listOfNotNull(size, date).joinToString(" · ")
            if (sizeDate.isNotBlank()) MetaLine("용량", sizeDate)
            MetaLine("경로", entry.path.substringBeforeLast('/').ifBlank { "/" })

            // 7) 액션
            Spacer(Modifier.height(18.dp))
            PlayButton(c, resumeMs, onPlay)
            if (onChangePoster != null || onToggleFavorite != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onChangePoster != null) GhostButton(c, "포스터 변경", onChangePoster)
                    if (onToggleFavorite != null) GhostButton(c, if (favorite) "즐겨찾기 해제" else "즐겨찾기", onToggleFavorite)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// 아이덴티티 메타 한 줄: ★평점 · 연도 · 러닝타임 · 등급 배지. 있는 것만, 가운뎃점으로 잇는다.
// details가 없으면 파싱한 연도만이라도 보여 준다.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityMeta(c: OloColors, details: MediaDetails?, named: Named) {
    val texts = buildList {
        details?.rating?.let { add("★ ${"%.1f".format(it)}") }
        (details?.year ?: named.sub?.toIntOrNull())?.let { add(it.toString()) }
        details?.runtimeMinutes?.let { add(runtimeText(it)) }
    }
    val cert = details?.certification
    if (texts.isEmpty() && cert == null) return
    // 좁은 다이얼로그(커버)에서 한 줄에 다 못 들어가면 줄을 넘겨 접는다 -- Row로 두면 긴 토큰
    // ("1시간 48분")이 글자 단위로 깨지거나 등급 배지가 잘려 나간다(대조에서 확인).
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        texts.forEachIndexed { i, t ->
            if (i > 0) Dot(c)
            val isRating = i == 0 && details?.rating != null
            Text(t, color = if (isRating) c.accent else c.text, fontSize = 13.sp, fontWeight = if (isRating) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        }
        if (cert != null) {
            if (texts.isNotEmpty()) Dot(c)
            Badge(c, cert)
        }
    }
}

@Composable
private fun HeroPoster(poster: Any?, kind: FileKind, context: android.content.Context) {
    val mod = Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
    if (poster != null) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(poster).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = mod,
        )
    } else {
        Box(mod.background(tileColorFor(kind)), contentAlignment = Alignment.Center) {
            Icon(painterResource(kind.glyph), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun PlayButton(c: OloColors, resumeMs: Long, onPlay: () -> Unit) {
    // 저장된 지점이 있으면 "이어보기 MM:SS"로, 없으면 "재생"으로. (처음부터 재생 분기는 재생
    // 엔진의 위치 초기화가 필요해 이후 단계 + 실기기 확인 몫.)
    val label = if (resumeMs > 0) "이어보기  ${clock(resumeMs)}" else "재생"
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(c.accent).clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = c.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GhostButton(c: OloColors, label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(c.accentContainer)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = c.onAccentContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Section(c: OloColors, title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, color = c.accent, fontSize = 12.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Chip(c: OloColors, text: String) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.accentContainer).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = c.onAccentContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CastChip(c: OloColors, member: CastMember, context: android.content.Context) {
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val mod = Modifier.size(48.dp).clip(CircleShape)
        if (member.profileUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(member.profileUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = mod,
            )
        } else {
            Box(mod.background(c.tileOther))
        }
        Text(member.name, color = c.text, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Badge(c: OloColors, text: String) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(c.outline).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = c.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Dot(c: OloColors) {
    Text("·", color = c.muted, fontSize = 13.sp)
}

@Composable
private fun MetaLine(k: String, v: String) {
    val c = OloTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(k, color = c.muted, fontSize = 13.sp, modifier = Modifier.width(52.dp))
        Text(v, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class Named(val title: String, val sub: String?)

private fun nameFor(parsed: MediaTitle, fallback: String): Named = when (parsed) {
    is MediaTitle.Movie -> Named(parsed.title, parsed.year?.toString())
    is MediaTitle.Episode -> Named(parsed.series, "시즌 ${parsed.season} · ${parsed.episode}화")
    MediaTitle.Unknown -> Named(fallback, null)
}

// "1시간 48분" / "48분".
private fun runtimeText(min: Int): String {
    val h = min / 60
    val m = min % 60
    return if (h > 0) "${h}시간 ${m}분" else "${m}분"
}

// 재생 위치 밀리초를 "H:MM:SS"/"M:SS"로.
private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
