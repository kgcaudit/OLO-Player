package org.olo.player.art

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.olo.player.ftp.RemoteEntry

/**
 * The second sidecar layer: the artwork a folder's .nfo points to. Unlike an image
 * sidecar, this needs a network read (open the .nfo, parse it), so it is resolved
 * off the main thread and only when no image sidecar was found. The pure name/text
 * work lives in [SidecarArt]; this is the Android glue that reads and builds a
 * loadable model.
 */
@UnstableApi
object SidecarResolver {

    // A .nfo is text; cap the read so a mislabelled huge file cannot be pulled whole.
    private const val MAX_NFO_BYTES = 512 * 1024

    /**
     * The art a [targetName]'s .nfo points to, as a Coil model, or null. An http(s)
     * reference loads directly; a bare filename resolves to a sibling image in the
     * same folder, read over the same verified connection as the video.
     */
    suspend fun nfoArt(
        context: Context,
        entries: List<RemoteEntry>,
        targetName: String,
        imageUriFor: (String) -> Uri?,
    ): Any? = withContext(Dispatchers.IO) {
        val nfoName = SidecarArt.nfoFor(entries.map { it.name }, targetName) ?: return@withContext null
        val nfoPath = entries.firstOrNull { it.name == nfoName }?.path ?: return@withContext null
        val nfoUri = imageUriFor(nfoPath) ?: return@withContext null

        val text = runCatching {
            readRemote(context, nfoUri, MAX_NFO_BYTES).toString(Charsets.UTF_8)
        }.getOrNull() ?: return@withContext null

        val art = SidecarArt.fromNfo(text) ?: return@withContext null
        if (art.startsWith("http", ignoreCase = true)) return@withContext art

        // A local reference in a .nfo is a file beside it; match it to a sibling and
        // load that over the media connection.
        val base = art.substringAfterLast('/').substringAfterLast('\\')
        val sibling = entries.firstOrNull { it.name.equals(base, ignoreCase = true) }?.path
            ?: return@withContext null
        imageUriFor(sibling)?.let { RemoteImage(it) }
    }
}
