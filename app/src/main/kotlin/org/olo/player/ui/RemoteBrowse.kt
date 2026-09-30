package org.olo.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.ftp.RemoteEntry
import org.olo.player.ui.components.CpDivider
import org.olo.player.ui.components.CpTile
import org.olo.player.ui.theme.OloTheme

/**
 * The one list every network browser draws, so FTP·SFTP·SMB·WebDAV read as one
 * screen and are styled in one place (after the OLO Explorer list): a breadcrumb
 * of the current path, rows with a rounded kind tile, the name, and a second line
 * of 날짜 · 크기, hairline dividers between them, and a 폴더/파일 count at the foot.
 *
 * Only folders and playable media show; a folder opens, a file starts playback
 * through [onEntry]. The rows carry [RemoteEntry.modified]/[RemoteEntry.size]
 * when the server gave them, and simply omit the second line when it did not.
 */
@Composable
fun RemoteBrowseList(
    path: String,
    entries: List<RemoteEntry>,
    loading: Boolean,
    error: String?,
    atRoot: Boolean,
    onUp: () -> Unit,
    onEntry: (RemoteEntry) -> Unit,
) {
    val c = OloTheme.colors
    val shown = entries.filter { it.isDirectory || looksMedia(it.name) }
    val folders = shown.count { it.isDirectory }
    val files = shown.size - folders

    Column(Modifier.fillMaxSize()) {
        Breadcrumb(path = path, loading = loading)
        if (error != null) {
            Text(
                text = "접속 실패: $error",
                color = c.accent,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (!atRoot) {
                item("..") {
                    UpRow(onUp)
                    CpDivider()
                }
            }
            if (shown.isEmpty() && !loading) {
                item("empty") {
                    Text(
                        "이 폴더에 미디어가 없습니다.",
                        color = c.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
            itemsIndexed(shown, key = { _, e -> e.path }) { index, entry ->
                BrowseRow(
                    kind = kindOf(entry.name, entry.isDirectory),
                    folder = entry.isDirectory,
                    name = entry.name,
                    subtitle = entrySubtitle(entry),
                    onClick = { onEntry(entry) },
                )
                if (index < shown.lastIndex) CpDivider()
            }
            if (shown.isNotEmpty()) {
                item("count") {
                    CpDivider()
                    Text(
                        "폴더 ${folders}개 · 파일 ${files}개",
                        color = c.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** The path as a breadcrumb: earlier segments muted, the current folder in ink. */
@Composable
private fun Breadcrumb(path: String, loading: Boolean) {
    val c = OloTheme.colors
    val segments = path.split("/").filter { it.isNotBlank() }
    val text = buildAnnotatedString {
        if (segments.isEmpty()) {
            withStyle(SpanStyle(color = c.text, fontWeight = FontWeight.SemiBold)) { append("/") }
        } else {
            segments.forEachIndexed { i, seg ->
                val last = i == segments.lastIndex
                withStyle(
                    SpanStyle(
                        color = if (last) c.text else c.muted,
                        fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                ) { append(seg) }
                if (!last) withStyle(SpanStyle(color = c.outline)) { append("  /  ") }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp).height(16.dp))
    }
    CpDivider()
}

/**
 * One browse row (ported from OLO Explorer's EntryRow): the kind tile, the name
 * (a folder in Medium, a file in Normal -- the first folder/file cue, with the
 * tile hue and folder glyph), and an optional 날짜  ·  크기 line.
 */
@Composable
private fun BrowseRow(
    kind: FileKind,
    folder: Boolean,
    name: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val c = OloTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FileTile(kind)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = c.text,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = if (folder) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The "상위 폴더" row: a back-arrow tile, kept plain (not a file kind). */
@Composable
private fun UpRow(onUp: () -> Unit) {
    val c = OloTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onUp)
            .heightIn(min = 64.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CpTile(Icons.AutoMirrored.Outlined.ArrowBack, c.tileOther)
        Text("상위 폴더", color = c.text, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    }
}

/** The second line: 날짜, and 크기 for a file, each shown only when known. */
private fun entrySubtitle(entry: RemoteEntry): String? {
    val date = entry.modified?.takeIf { it > 0 }?.let { formatDate(it) }
    val size = entry.size?.takeIf { it >= 0 && !entry.isDirectory }?.let { humanSize(it) }
    return when {
        date != null && size != null -> "$date  ·  $size"
        date != null -> date
        size != null -> size
        else -> null
    }
}

private fun formatDate(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
