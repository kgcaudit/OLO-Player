package org.olo.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
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
import coil.request.ImageRequest
import org.olo.player.art.Posters
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ui.theme.OloTheme

// The poster aspect ratio everywhere it is shown: the standard 2:3 sheet.
private const val POSTER_RATIO = 2f / 3f

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
// The art that needs a network read, in the layered order: the folder's .nfo art
// first (when a resolver is given), then TMDB. Returns null while loading, when off,
// or when neither has anything. The synchronous image sidecar is handled by the
// caller and never reaches here.
@Composable
private fun rememberRemoteArt(
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
): Any? {
    val context = LocalContext.current
    // 재진입 시에도 캐시값으로 시작해 포스터가 즉시 보이게 한다(깜빡임 제거).
    var model by remember(cacheKey) { mutableStateOf(if (attempt) cache?.get(cacheKey) else null) }
    LaunchedEffect(cacheKey, attempt) {
        if (!attempt) {
            model = null
            return@LaunchedEffect
        }
        val cached = cache?.get(cacheKey)
        if (cached != null) {
            model = cached
            return@LaunchedEffect
        }
        val resolved = nfoArt?.invoke() ?: run {
            var hit: Any? = null
            for (q in queries) {
                hit = runCatching { Posters.get(context).posterUrl(q, folderName) }.getOrNull()
                if (hit != null) break
            }
            hit
        }
        model = resolved
        if (resolved != null) cache?.put(cacheKey, resolved)
    }
    return model
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
) {
    // posterName set == a single-film folder shown as its film: fetch the film's
    // poster though the row is a folder, falling back to the folder tile. Else the
    // usual rule -- only a video file is looked up.
    val queries = buildList { add(posterName ?: name); posterNameAlt?.let { if (it != posterName) add(it) } }
    val attempt = enabled && (posterName != null || (!folder && kind == FileKind.VIDEO))
    // 사용자가 고른 포스터(override)가 있으면 그것, 없으면 사이드카, 그다음 TMDB(후보 순서대로).
    val remote = rememberRemoteArt(queries, folderName, attempt && overrideUrl == null && sidecar == null, nfoArt, artCache)
    val model = if (attempt) overrideUrl ?: sidecar ?: remote else null
    // 썸네일은 항상 같은 2:3 박스(44×66)로 그린다 -- 포스터가 있든(이미지) 없든(타일+글리프)
    // 높이가 같아, 포스터 유무로 행 높이가 들쭉날쭉하지 않는다.
    val box = Modifier.width(44.dp).aspectRatio(POSTER_RATIO).clip(RoundedCornerShape(8.dp))
    Box(modifier) {
        Crossfade(targetState = model, label = "poster") { resolved ->
            if (resolved != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(resolved).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = box,
                )
            } else {
                Box(box.background(tileColorFor(kind)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(kind.glyph), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
                }
            }
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
) {
    val c = OloTheme.colors
    val kind = kindOf(entry.name, entry.isDirectory)
    // posterName set == a single-film/series folder shown as its art (the badge
    // keeps it readable as a folder). Else only a video file is looked up.
    val queries = buildList { add(posterName ?: entry.name); posterNameAlt?.let { if (it != posterName) add(it) } }
    val attempt = enabled && (posterName != null || kind == FileKind.VIDEO)
    val remote = rememberRemoteArt(queries, folderName, attempt && overrideUrl == null && sidecar == null, nfoArt, artCache)
    val model = if (attempt) overrideUrl ?: sidecar ?: remote else null
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.fillMaxWidth()) {
            Crossfade(targetState = model, label = "poster-cell") { resolved ->
                if (resolved != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(resolved).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO).clip(RoundedCornerShape(12.dp))
                            .background(tileColorFor(kind)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(kind.glyph), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(44.dp))
                    }
                }
            }
            if (folderBadge != null) {
                Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) { FolderBadge(folderBadge, 26) }
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
