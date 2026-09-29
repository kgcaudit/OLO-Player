package org.olo.player.ui.screens

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.olo.player.ui.PlayerViewModel
import org.olo.player.ui.theme.OloTheme

/** One row of the aggregated local library: a MediaStore video or sound. */
data class LibraryItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** The real file path when MediaStore exposes it, so playback reuses the
     *  proven local-File path (folder siblings, sidecar subtitles). */
    val data: String?,
) {
    val resumeKey: String get() = data ?: uri.toString()
}

/**
 * The whole phone's videos or songs in one list, newest first -- the "비디오" and
 * "오디오" categories. Each row shows a 16:9 kind tile with the duration, the
 * resolution/size, and (if the file was left part-way) a resume bar. Tapping a
 * file hands its real path to the player so siblings and sidecar subtitles still
 * work; only if MediaStore hides the path does it fall back to the content uri.
 */
@Composable
fun LocalLibrary(model: PlayerViewModel, video: Boolean) {
    val context = LocalContext.current
    val items by produceState(initialValue = emptyList<LibraryItem>(), video) {
        value = queryLibrary(context, video)
    }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (video) "영상이 없습니다." else "음악이 없습니다.",
                color = OloTheme.colors.muted,
                fontSize = 14.sp,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.uri.toString() }) { item ->
            LibraryRow(
                item = item,
                video = video,
                resumeMs = model.savedPosition(item.resumeKey),
                onOpen = {
                    val f = item.data?.let { File(it) }
                    if (f != null && f.exists()) model.openLocalMedia(f)
                    else model.openEntries(
                        listOf(org.olo.player.ui.MediaEntry(item.uri, item.name)), 0,
                    )
                },
            )
        }
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, video: Boolean, resumeMs: Long, onOpen: () -> Unit) {
    val c = OloTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A 16:9 tile standing in for a frame, with the duration in the corner.
        Box(
            Modifier
                .width(66.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (video) c.tileVideo else c.tileAudio),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (video) Icons.Outlined.Movie else Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Color(0xFFF4F1EC),
                modifier = Modifier.size(20.dp),
            )
            if (item.durationMs > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(formatDuration(item.durationMs), color = Color.White, fontSize = 9.sp, lineHeight = 10.sp)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = c.text, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitleFor(item, video), color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (resumeMs > 0 && item.durationMs > 0) {
                val frac = (resumeMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.progressTrack),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .height(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.accent),
                    )
                }
            }
        }
    }
}

private fun subtitleFor(item: LibraryItem, video: Boolean): String {
    val size = formatSize(item.sizeBytes)
    return if (video && item.height > 0) "$size · ${item.width}×${item.height}" else size
}

private suspend fun queryLibrary(context: Context, video: Boolean): List<LibraryItem> =
    withContext(Dispatchers.IO) {
        val collection = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DURATION)
            add(MediaStore.MediaColumns.SIZE)
            @Suppress("DEPRECATION")
            add(MediaStore.MediaColumns.DATA)
            if (video) {
                add(MediaStore.Video.Media.WIDTH)
                add(MediaStore.Video.Media.HEIGHT)
            }
        }.toTypedArray()
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val out = ArrayList<LibraryItem>()
        runCatching {
            context.contentResolver.query(collection, projection, null, null, sort)?.use { cur ->
                val idC = cur.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameC = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val durC = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val sizeC = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                @Suppress("DEPRECATION")
                val dataC = cur.getColumnIndex(MediaStore.MediaColumns.DATA)
                val wC = if (video) cur.getColumnIndex(MediaStore.Video.Media.WIDTH) else -1
                val hC = if (video) cur.getColumnIndex(MediaStore.Video.Media.HEIGHT) else -1
                while (cur.moveToNext()) {
                    val id = cur.getLong(idC)
                    out += LibraryItem(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cur.getString(nameC) ?: "",
                        durationMs = if (cur.isNull(durC)) 0 else cur.getLong(durC),
                        sizeBytes = if (cur.isNull(sizeC)) 0 else cur.getLong(sizeC),
                        width = if (wC >= 0 && !cur.isNull(wC)) cur.getInt(wC) else 0,
                        height = if (hC >= 0 && !cur.isNull(hC)) cur.getInt(hC) else 0,
                        data = if (dataC >= 0 && !cur.isNull(dataC)) cur.getString(dataC) else null,
                    )
                }
            }
        }
        out // already newest-first from the DATE_MODIFIED sort
    }

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1L shl 30 -> "%.1fGB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0fMB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0fKB".format(bytes / 1024.0)
}
