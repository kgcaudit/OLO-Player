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
) {
    val attempt = enabled && !folder && kind == FileKind.VIDEO
    val remote = rememberRemoteArt(name, folderName, attempt && sidecar == null, nfoArt)
    val model = if (attempt) sidecar ?: remote else null
    Crossfade(targetState = model, label = "poster") { resolved ->
        if (resolved != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(resolved).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.width(42.dp).aspectRatio(POSTER_RATIO).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            FileTile(kind, modifier)
        }
    }
}

/**
 * One grid cell: the kind tile above the name and a short second line -- the
 * compact icon grid, no posters (그리드 보기). Folders and files alike.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridCell(
    entry: RemoteEntry,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val c = OloTheme.colors
    val kind = kindOf(entry.name, entry.isDirectory)
    Column(
        modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileTile(kind, size = 56, cornerRadius = 16)
        Text(
            entry.name,
            color = c.text,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (subtitle != null) {
            Text(subtitle, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/**
 * One gallery cell: a 2:3 poster (or, until it resolves / when there is none, a
 * hue-filled tile with the kind glyph so the grid stays even), the file name, and
 * a short second line. Tapping it opens the file like a row.
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
) {
    val c = OloTheme.colors
    val kind = kindOf(entry.name, entry.isDirectory)
    val attempt = enabled && kind == FileKind.VIDEO
    val remote = rememberRemoteArt(entry.name, folderName, attempt && sidecar == null, nfoArt)
    val model = if (attempt) sidecar ?: remote else null
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
            // 폴더/파일 구분 배지: a gallery card can carry a folder or a film poster
            // alike, so a folder keeps a small hint at the corner (Step 2 구상안).
            if (entry.isDirectory) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0x66000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("폴더", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
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
