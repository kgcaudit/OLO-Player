package org.olo.player.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.olo.player.art.MediaTitle
import org.olo.player.art.Posters
import org.olo.player.art.TitleParser
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ui.theme.OloTheme

/**
 * The detail a long-press opens for a file: its artwork large, what it is, the
 * plain file facts, and a play button. A drama episode leads with its 16:9 still;
 * a film with its 2:3 poster beside the facts. The art reuses the browse layers --
 * the folder's own [sidecar] first, then TMDB -- so what the row showed, the sheet
 * shows larger, and only an episode additionally fetches its still.
 *
 * 버튼/롱프레스로 여는 것은 바텀시트가 아니라 중앙 다이얼로그로 통일한다(끌어올리는
 * 제스처가 아니므로 손잡이를 없앤다). 작품명·재생 버튼을 자체로 갖고 있어 카드로 감싸기만
 * 한다; 바깥 탭으로 닫는다.
 */
@Composable
fun MediaDetailSheet(
    entry: RemoteEntry,
    folderName: String?,
    sidecar: Any?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val c = OloTheme.colors

    var tmdbPoster by remember(entry.name) { mutableStateOf<String?>(null) }
    var still by remember(entry.name) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.name, folderName) {
        if (sidecar == null) {
            tmdbPoster = runCatching { Posters.get(context).posterUrl(entry.name, folderName) }.getOrNull()
        }
        still = runCatching { Posters.get(context).stillUrl(entry.name, folderName) }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.dialog)) {
            MediaDetailContent(
                entry = entry,
                folderName = folderName,
                still = still,
                poster = sidecar ?: tmdbPoster,
                onPlay = onPlay,
            )
        }
    }
}

/**
 * The sheet's body, art already resolved: a 16:9 still hero for an episode that has
 * one, else the 2:3 poster (or kind tile) beside the name. Split out from the
 * bottom-sheet frame so it renders on its own for a screenshot.
 */
@Composable
internal fun MediaDetailContent(
    entry: RemoteEntry,
    folderName: String?,
    still: Any?,
    poster: Any?,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val c = OloTheme.colors
    val kind = kindOf(entry.name, entry.isDirectory)
    val parsed = remember(entry.name, folderName) { TitleParser.parse(entry.name, folderName) }
    val named = remember(parsed, entry.name) { nameFor(parsed, entry.name) }

    // 중앙 다이얼로그 카드 안에서 사방 균일 여백으로 그린다.
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        if (still != null) {
            // 회차 with a still: the 16:9 frame leads, the name sits beneath it.
            AsyncImage(
                model = ImageRequest.Builder(context).data(still).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.height(14.dp))
            TitleBlock(named)
            MetaBlock(entry, showAll = true)
        } else {
            // Everything else: the 2:3 poster (or tile) beside the name and the
            // first facts, matching the film card.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroPoster(poster, kind, context)
                Column(Modifier.weight(1f)) {
                    TitleBlock(named)
                    Spacer(Modifier.height(10.dp))
                    entry.size?.takeIf { it >= 0 && !entry.isDirectory }?.let { MetaLine("용량", humanSize(it)) }
                    entry.modified?.takeIf { it > 0 }?.let { MetaLine("수정", formatDate(it)) }
                }
            }
            MetaBlock(entry, showAll = false)
        }

        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(c.accent).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Text("재생", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeroPoster(poster: Any?, kind: FileKind, context: android.content.Context) {
    val mod = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
    if (poster != null) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(poster).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = mod,
        )
    } else {
        Box(mod.background(tileColorFor(kind)), contentAlignment = Alignment.Center) {
            Icon(painterResource(kind.glyph), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun TitleBlock(named: Named) {
    val c = OloTheme.colors
    Text(named.title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (named.sub != null) {
        Text(named.sub, color = c.accent, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MetaBlock(entry: RemoteEntry, showAll: Boolean) {
    val c = OloTheme.colors
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    Spacer(Modifier.height(10.dp))
    MetaLine("파일", entry.name)
    if (showAll) {
        entry.size?.takeIf { it >= 0 && !entry.isDirectory }?.let { MetaLine("용량", humanSize(it)) }
        entry.modified?.takeIf { it > 0 }?.let { MetaLine("수정", formatDate(it)) }
    }
    MetaLine("경로", entry.path.substringBeforeLast('/').ifBlank { "/" })
}

@Composable
private fun MetaLine(k: String, v: String) {
    val c = OloTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
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
