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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    name: String,
    folderName: String?,
    attempt: Boolean,
    nfoArt: (suspend () -> Any?)?,
): Any? {
    val context = LocalContext.current
    var model by remember(name, folderName) { mutableStateOf<Any?>(null) }
    LaunchedEffect(name, folderName, attempt) {
        model = if (!attempt) {
            null
        } else {
            nfoArt?.invoke() ?: runCatching { Posters.get(context).posterUrl(name, folderName) }.getOrNull()
        }
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
    overrideUrl: String? = null,
) {
    // posterName set == a single-film folder shown as its film: fetch the film's
    // poster though the row is a folder, falling back to the folder tile. Else the
    // usual rule -- only a video file is looked up.
    val query = posterName ?: name
    val attempt = enabled && (posterName != null || (!folder && kind == FileKind.VIDEO))
    // 사용자가 고른 포스터(override)가 있으면 그것, 없으면 사이드카, 그다음 TMDB.
    val remote = rememberRemoteArt(query, folderName, attempt && overrideUrl == null && sidecar == null, nfoArt)
    val model = if (attempt) overrideUrl ?: sidecar ?: remote else null
    // 썸네일은 항상 같은 2:3 박스(44×66)로 그린다 -- 포스터가 있든(이미지) 없든(타일+글리프)
    // 높이가 같아, 포스터 유무로 행 높이가 들쭉날쭉하지 않는다.
    val box = modifier.width(44.dp).aspectRatio(POSTER_RATIO).clip(RoundedCornerShape(8.dp))
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
}

/**
 * One poster cell for 격자·갤러리 alike (density differs only by column count): a
 * 2:3 poster (or, until it resolves / when there is none, a hue-filled tile with
 * the kind glyph so the grid stays even), the file name, and a short second line.
 * A [badge] (폴더/시리즈) sits bottom-start; a [cornerMenu] (⋮: 즐겨찾기·포스터 변경·
 * 상세) sits bottom-end. Tapping the card opens the file like a row.
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
    overrideUrl: String? = null,
    badge: String? = null,
    cornerMenu: (@Composable () -> Unit)? = null,
) {
    val c = OloTheme.colors
    val kind = kindOf(entry.name, entry.isDirectory)
    // posterName set == a single-film/series folder shown as its art (the badge
    // keeps it readable as a folder). Else only a video file is looked up.
    val query = posterName ?: entry.name
    val attempt = enabled && (posterName != null || kind == FileKind.VIDEO)
    val remote = rememberRemoteArt(query, folderName, attempt && overrideUrl == null && sidecar == null, nfoArt)
    val model = if (attempt) overrideUrl ?: sidecar ?: remote else null
    // 배지: 지정되면 그대로(시리즈 등), 없으면 폴더만 "폴더". 파일은 배지 없음.
    val badgeText = badge ?: if (entry.isDirectory) "폴더" else null
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
            if (badgeText != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0x66000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(badgeText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
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
