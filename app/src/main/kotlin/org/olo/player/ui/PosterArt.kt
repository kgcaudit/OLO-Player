package org.olo.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import org.olo.player.art.PosterLookup
import org.olo.player.art.Posters
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ui.theme.OloTheme

// The poster aspect ratio everywhere it is shown: the standard 2:3 sheet.
private const val POSTER_RATIO = 2f / 3f

// 캐시(메모리·디스크)에서 온 이미지는 페이드 없이 즉시, 진짜 네트워크 로드만 부드럽게
// 크로스페이드한다. 콜드 런치 때 이미 받아둔 포스터가 "로딩하듯 깜박이며" 뜨던 것을
// 없앤다(Coil은 메모리 적중만 페이드를 건너뛰고 디스크 적중은 페이드하므로 직접 가린다).
private val CacheAwareCrossfade = Transition.Factory { target, result ->
    if (result is SuccessResult && result.dataSource != DataSource.NETWORK) {
        Transition.Factory.NONE.create(target, result)
    } else {
        CrossfadeTransition.Factory().create(target, result)
    }
}

/**
 * 폴더 카드가 어떤 성격인지 한눈에: [FILM]은 탭하면 바로 재생되는 단일영화(▶), [SERIES]는
 * 탭하면 들어가는 여러 영상 묶음(겹장), [FOLDER]는 일반 폴더(폴더 글리프). 포스터 위에서도
 * 보이게 좌하단에 작은 배지로 얹는다. (Infuse식 ▶ · Jellyfin/NextPlayer식 묶음 표식 참고.)
 */
enum class FolderBadgeKind { FILM, SERIES, FOLDER }

@Composable
internal fun FolderBadge(kind: FolderBadgeKind, sizeDp: Int) {
    val bg = if (kind == FolderBadgeKind.FILM) OloTheme.colors.accent else Color(0xCC000000)
    val glyph = when (kind) {
        FolderBadgeKind.FILM -> Icons.Filled.PlayArrow
        FolderBadgeKind.SERIES -> Icons.Filled.Layers
        FolderBadgeKind.FOLDER -> Icons.Filled.Folder
    }
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 3).dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, contentDescription = null, tint = Color.White, modifier = Modifier.size((sizeDp * 0.6f).dp))
    }
}

/**
 * Resolves the poster URL for one entry, once, off the main thread. Returns null
 * while it is still loading, when posters are off, or when there is no match --
 * every case where the caller should fall back to a kind tile. Keyed on the name
 * so a re-list of the same folder does not re-fetch.
 */
/** 자동 포스터를 못 찾은 이유 -- 빈 타일을 두 가지로 구분해 그리기 위해. */
enum class PosterMiss { AMBIGUOUS, NO_DATA }

/** 원격 아트 해석 결과: 보여줄 모델(없으면 null)과, 없을 때의 사유(타일 구분용). */
data class RemoteArt(val model: Any?, val miss: PosterMiss? = null)

// The art that needs a network read, in the layered order: the folder's .nfo art
// first (when a resolver is given), then TMDB. Returns a null model while loading,
// when off, or when neither has anything; when TMDB declined, [RemoteArt.miss] says
// why (동명작 다수 vs 자료 없음). The synchronous image sidecar is handled by the
// caller and never reaches here.
@Composable
internal fun rememberRemoteArt(
    // TMDB 제목 질의 후보들(순서대로 시도, 먼저 맞는 것 사용) -- 폴더명과 파일명이 각각
    // 맞는 경우가 달라(한국어 폴더명 vs 영문 파일명) 둘 다 시도한다.
    queries: List<String>,
    folderName: String?,
    attempt: Boolean,
    nfoArt: (suspend () -> Any?)?,
    // 해석된 포스터를 화면(폴더) 단위로 캐시한다. LazyColumn이 화면 밖 항목을 폐기해도
    // 재진입 때 캐시에서 바로 모델을 꺼내 null→타일→포스터 깜빡임을 없앤다. null이면 캐시 미사용.
    cache: SnapshotStateMap<String, Any?>? = null,
    cacheKey: String = queries.joinToString("\u0001") + "|" + folderName,
): RemoteArt {
    val context = LocalContext.current
    // 재진입 시에도 캐시값으로 시작해 포스터가 즉시 보이게 한다(깜빡임 제거).
    var art by remember(cacheKey) { mutableStateOf(RemoteArt(if (attempt) cache?.get(cacheKey) else null)) }
    LaunchedEffect(cacheKey, attempt) {
        if (!attempt) {
            art = RemoteArt(null)
            return@LaunchedEffect
        }
        cache?.get(cacheKey)?.let { art = RemoteArt(it); return@LaunchedEffect }
        nfoArt?.invoke()?.let { art = RemoteArt(it); cache?.put(cacheKey, it); return@LaunchedEffect }
        // TMDB: 후보 순서대로. 히트면 즉시, 아니면 사유를 모은다(Ambiguous가 NoData보다 우선 --
        // 어디선가 후보를 봤다면 수동 선택 여지가 있으므로).
        var miss: PosterMiss? = null
        for (q in queries) {
            when (val lk = runCatching { Posters.get(context).posterLookup(q, folderName) }.getOrNull()) {
                is PosterLookup.Hit -> { art = RemoteArt(lk.url); cache?.put(cacheKey, lk.url); return@LaunchedEffect }
                PosterLookup.Ambiguous -> miss = PosterMiss.AMBIGUOUS
                PosterLookup.NoData -> if (miss == null) miss = PosterMiss.NO_DATA
                PosterLookup.None, null -> {}
            }
        }
        art = RemoteArt(null, miss)
    }
    return art
}

/** 후보 있음 타일의 '선택' 뱃지: 클레이 원 + 흰 돋보기. 탭하면 포스터 변경을 연다(가능할 때). */
@Composable
private fun PickBadge(modifier: Modifier, sizeDp: Int) {
    Box(
        modifier.size(sizeDp.dp).clip(CircleShape).background(OloTheme.colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Search, contentDescription = "포스터 선택", tint = Color.White, modifier = Modifier.size((sizeDp * 0.62f).dp))
    }
}

/** 미디어 모음 폴더로 그릴 때의 성격·개수. 포스터(한 작품) 대신 글리프 타일 + 개수 칩으로
 *  보인다. 영상만/음악만/영상·음악 혼재(혼합)로 글리프와 칩 문구가 갈린다. [capped]면 하위폴더
 *  상한 초과로 실제 개수가 더 많을 수 있어 '+'를 붙인다. */
data class MediaCollection(val videos: Int, val music: Int, val capped: Boolean = false) {
    val isMixed: Boolean get() = videos > 0 && music > 0
    val isMusicOnly: Boolean get() = videos == 0 && music > 0
}

/** 모음 폴더 타일 중앙의 글리프. 영상 모음=영상 라이브러리, 음악 모음=음악 라이브러리, 혼합=
 *  필름지(영상)에 음표(음악) 배지를 얹어 '영상·음악이 한 폴더'임을 한 기호로 전한다(확정안 다-2).
 *  [sizeDp]는 기준(필름/라이브러리) 크기이고 혼합 배지는 그에 맞춰 축소된다. */
@Composable
private fun CollectionGlyph(collection: MediaCollection, sizeDp: Int) {
    when {
        collection.isMixed -> MixedMediaGlyph(sizeDp)
        collection.isMusicOnly ->
            Icon(Icons.Filled.LibraryMusic, contentDescription = "음악 모음 폴더", tint = Color.White, modifier = Modifier.size(sizeDp.dp))
        else ->
            Icon(Icons.Filled.VideoLibrary, contentDescription = "영상 모음 폴더", tint = Color.White, modifier = Modifier.size(sizeDp.dp))
    }
}

// 혼합 글리프(다-2): 흰 필름지 위에 음표를 '짙은 클레이 원형 배지'로 우하단에 얹는다. 흰 필름
// 위 흰 음표가 묻히지 않게 배지 바탕을 타일보다 짙게 깔고 흰 테두리로 띄운다. 배지는 필름 크기에
// 비례(≈0.5x)해 작은 목록 썸네일에서도 비율이 유지된다.
@Composable
private fun MixedMediaGlyph(sizeDp: Int) {
    val badge = (sizeDp * 0.5f).dp
    val note = (sizeDp * 0.3f).dp
    val ring = (sizeDp * 0.045f).coerceAtLeast(1f).dp
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.LocalMovies, contentDescription = "영상·음악 혼합 폴더", tint = Color.White, modifier = Modifier.size(sizeDp.dp))
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = (sizeDp * 0.06f).dp, y = (sizeDp * 0.06f).dp)
                .size(badge).clip(CircleShape).background(Color(0xFF7E3A24)).border(ring, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(note)) }
    }
}

/** 모음 개수 칩("영상 N" / "음악 N" / "영상 V · 음악 M", 상한 초과 시 '+'). 포스터색·타일색 위
 *  어디서나 읽히게 검정 70% 바탕 + 흰 글자. 그리드 셀 우하단에 얹는다(작은 목록 썸네일엔 둘째
 *  줄로 대신). */
@Composable
private fun CollectionCountChip(collection: MediaCollection, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(collectionCountLabel(collection), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// 칩·둘째 줄에 공통으로 쓰는 개수 문구. 혼합은 '영상 V · 음악 M', 상한 초과면 '+'.
internal fun collectionCountLabel(c: MediaCollection): String {
    val plus = if (c.capped) "+" else ""
    return when {
        c.isMixed -> "영상 ${c.videos}$plus · 음악 ${c.music}$plus"
        c.isMusicOnly -> "음악 ${c.music}$plus"
        else -> "영상 ${c.videos}$plus"
    }
}

/**
 * The leading art in a browse row: the folder's own sidecar poster if it has one,
 * else a 2:3 TMDB poster once it resolves, else the kind tile. The tile-to-poster
 * swap fades so a list settling in does not flicker. Only a video file is looked
 * up, and only when posters are on; folders, sound and documents keep their tile.
 * A [sidecar] present means TMDB is never queried -- the person's own art wins.
 */
@Composable
fun MediaThumbnail(
    kind: FileKind,
    folder: Boolean,
    name: String,
    folderName: String?,
    sidecar: Any?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    nfoArt: (suspend () -> Any?)? = null,
    posterName: String? = null,
    // 폴더 카드의 보조 질의(예: 파일명) -- 폴더명 질의가 빗나가면 이걸로 재시도한다.
    posterNameAlt: String? = null,
    overrideUrl: String? = null,
    artCache: SnapshotStateMap<String, Any?>? = null,
    // 폴더 성격 배지(▶ 단일영화 / 겹장 시리즈 / 폴더). null이면 배지 없음(일반 파일 등).
    folderBadge: FolderBadgeKind? = null,
    // '후보 있음' 빈 타일의 선택 뱃지를 탭하면 포스터 변경을 연다. null이면 뱃지는 지시자로만.
    onPickPoster: (() -> Unit)? = null,
    // 설정되면 '미디어 모음 폴더'(영상/음악/혼합)로 그린다 -- 포스터를 해석하지 않고 모음 글리프
    // 타일로. 목록 썸네일은 작아 개수 칩 대신 둘째 줄(subtitle)로 개수를 알린다.
    collection: MediaCollection? = null,
) {
    val c = OloTheme.colors
    // posterName set == a single-film folder shown as its film: fetch the film's
    // poster though the row is a folder, falling back to the folder tile. Else the
    // usual rule -- only a video file is looked up. 모음 폴더는 포스터를 안 본다.
    val queries = buildList { add(posterName ?: name); posterNameAlt?.let { if (it != posterName) add(it) } }
    val attempt = enabled && collection == null && (posterName != null || (!folder && kind == FileKind.VIDEO))
    // 사용자가 고른 포스터(override)가 있으면 그것, 없으면 사이드카, 그다음 TMDB(후보 순서대로).
    val remote = rememberRemoteArt(queries, folderName, attempt && overrideUrl == null && sidecar == null, nfoArt, artCache)
    val model = if (attempt) overrideUrl ?: sidecar ?: remote.model else null
    // 포스터가 없을 때의 사유: 자료 없음(회색) vs 후보 있음(선택 유도). 둘 다 아니면 종전 kind 타일.
    val miss = if (attempt && model == null) remote.miss else null
    val noData = miss == PosterMiss.NO_DATA
    // 썸네일은 항상 같은 2:3 박스(44×66)로 그린다 -- 포스터가 있든(이미지) 없든(타일+글리프)
    // 높이가 같아, 포스터 유무로 행 높이가 들쭉날쭉하지 않는다.
    val box = Modifier.width(44.dp).aspectRatio(POSTER_RATIO).clip(RoundedCornerShape(8.dp))
    Box(modifier) {
        // 타일을 바탕에 깔고 포스터가 준비되면 그 위에 그린다. 캐시 적중 땐 페이드 없이 즉시
        // (타일→포스터 이중 페이드 제거), 네트워크 로드만 CacheAwareCrossfade로 부드럽게.
        // '자료 없음'은 kind hue 대신 중립 색으로 깔아 '후보 있음'과 색으로 구분한다.
        Box(box.background(if (noData) c.outline else tileColorFor(kind)), contentAlignment = Alignment.Center) {
            if (collection != null) {
                CollectionGlyph(collection, 24)
            } else {
                Icon(painterResource(kind.glyph), contentDescription = null, tint = if (noData) c.muted else Color.Unspecified, modifier = Modifier.size(24.dp))
            }
        }
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(model).transitionFactory(CacheAwareCrossfade).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = box,
            )
        }
        // 후보 있음: 우상단 선택 뱃지(작은 썸네일이라 라벨 없이 색+뱃지로 구분). 탭=포스터 변경.
        if (miss == PosterMiss.AMBIGUOUS) {
            val badge = Modifier.align(Alignment.TopEnd).padding(2.dp)
            PickBadge(if (onPickPoster != null) badge.clickable(onClick = onPickPoster) else badge, 15)
        }
        if (folderBadge != null) {
            Box(Modifier.align(Alignment.BottomStart).padding(2.dp)) { FolderBadge(folderBadge, 16) }
        }
    }
}

/**
 * One poster cell for 격자·갤러리 alike (density differs only by column count): a
 * 2:3 poster (or, until it resolves / when there is none, a hue-filled tile with
 * the kind glyph so the grid stays even), the file name, and a short second line.
 * A [folderBadge] (▶ 단일영화 / 겹장 시리즈) sits bottom-start; a [cornerMenu] (⋮: 즐겨찾기·
 * 포스터 변경·상세) sits bottom-end. Tapping the card opens the file like a row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCell(
    entry: RemoteEntry,
    folderName: String?,
    subtitle: String?,
    sidecar: Any?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    nfoArt: (suspend () -> Any?)? = null,
    posterName: String? = null,
    posterNameAlt: String? = null,
    overrideUrl: String? = null,
    folderBadge: FolderBadgeKind? = null,
    cornerMenu: (@Composable () -> Unit)? = null,
    artCache: SnapshotStateMap<String, Any?>? = null,
    // '후보 있음' 빈 셀의 선택 뱃지를 탭하면 포스터 변경을 연다.
    onPickPoster: (() -> Unit)? = null,
    // 설정되면 '미디어 모음 폴더'(영상/음악/혼합)로 그린다 -- 포스터 대신 모음 글리프 타일 + 개수 칩.
    collection: MediaCollection? = null,
) {
    val c = OloTheme.colors
    val kind = kindOf(entry.name, entry.isDirectory)
    // posterName set == a single-film/series folder shown as its art (the badge
    // keeps it readable as a folder). Else only a video file is looked up. 모음
    // 폴더(collection)는 한 작품이 아니므로 포스터를 해석하지 않는다.
    val queries = buildList { add(posterName ?: entry.name); posterNameAlt?.let { if (it != posterName) add(it) } }
    val attempt = enabled && collection == null && (posterName != null || kind == FileKind.VIDEO)
    val remote = rememberRemoteArt(queries, folderName, attempt && overrideUrl == null && sidecar == null, nfoArt, artCache)
    val model = if (attempt) overrideUrl ?: sidecar ?: remote.model else null
    val miss = if (attempt && model == null) remote.miss else null
    val noData = miss == PosterMiss.NO_DATA
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.fillMaxWidth()) {
            // 타일을 바탕에 깔고 포스터가 준비되면 그 위에 그린다(캐시 적중=즉시, 이중 페이드 제거).
            // '자료 없음'은 중립 색으로 깔아 '후보 있음'(kind hue + 뱃지)과 구분한다.
            val cell = Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO).clip(RoundedCornerShape(12.dp))
            Box(cell.background(if (noData) c.outline else tileColorFor(kind)), contentAlignment = Alignment.Center) {
                if (collection != null) {
                    CollectionGlyph(collection, 52)
                } else {
                    Icon(painterResource(kind.glyph), contentDescription = null, tint = if (noData) c.muted else Color.Unspecified, modifier = Modifier.size(44.dp))
                }
            }
            if (model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(model).transitionFactory(CacheAwareCrossfade).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = cell,
                )
            }
            // 후보 있음: 우상단 선택 뱃지(탭=포스터 변경). 자료 없음: 하단 중앙 '자료 없음' 작은 라벨.
            if (miss == PosterMiss.AMBIGUOUS) {
                val badge = Modifier.align(Alignment.TopEnd).padding(6.dp)
                PickBadge(if (onPickPoster != null) badge.clickable(onClick = onPickPoster) else badge, 24)
            }
            if (noData) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) { Text("자료 없음", color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp) }
            }
            if (folderBadge != null) {
                Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) { FolderBadge(folderBadge, 26) }
            }
            // 모음 폴더: 우하단 개수 칩(⋮ 메뉴가 없으므로 자리 겹침 없음).
            if (collection != null) {
                CollectionCountChip(collection, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
            if (cornerMenu != null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { cornerMenu() }
            }
        }
        Text(
            entry.name,
            color = c.text,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
        if (subtitle != null) {
            Text(subtitle, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
        }
    }
}
