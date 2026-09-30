package org.olo.player.art

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import coil.ImageLoader
import coil.decode.DataSource as CoilDataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import org.olo.player.playback.OloDataSourceFactory

/**
 * A sidecar image often lives on the same server as the video -- an ftp/sftp/smb
 * URL Coil's own HTTP loader cannot open. This teaches Coil to read those (and
 * webdav, and plain http) through the very [OloDataSourceFactory] the player
 * streams with, so one code path -- with its host-key/certificate verification --
 * serves both the film and its poster.
 *
 * The image is small, so it is read whole into memory and handed to Coil; there is
 * no ranged streaming to keep open.
 */
class RemoteImage(val uri: Uri)

@UnstableApi
class RemoteImageFetcher(
    private val model: RemoteImage,
    private val appContext: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = withContext(Dispatchers.IO) { readRemote(appContext, model.uri) }
        return SourceResult(
            source = ImageSource(Buffer().apply { write(bytes) }, appContext),
            mimeType = null,
            dataSource = CoilDataSource.NETWORK,
        )
    }

    class Factory(private val appContext: Context) : Fetcher.Factory<RemoteImage> {
        override fun create(data: RemoteImage, options: Options, imageLoader: ImageLoader): Fetcher =
            RemoteImageFetcher(data, appContext.applicationContext)
    }
}

/**
 * The cache key for a remote image: scheme, host and path only. Credentials and
 * pinned fingerprints ride the URL for the fetch but must never enter a cache
 * index; two files at one path are the same image whoever is signed in.
 */
class RemoteImageKeyer : Keyer<RemoteImage> {
    override fun key(data: RemoteImage, options: Options): String {
        val u = data.uri
        return "${u.scheme}://${u.host}:${u.port}${u.path}"
    }
}

/**
 * Reads a whole remote file through the player's data sources, up to [max] bytes.
 * Shared by the image fetcher and the .nfo reader so one verified path serves both.
 * Runs on a caller-supplied background thread.
 */
@UnstableApi
internal fun readRemote(context: Context, uri: Uri, max: Int = Int.MAX_VALUE): ByteArray {
    val source = OloDataSourceFactory(context).createDataSource()
    return try {
        source.open(DataSpec(uri))
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < max) {
            val n = source.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    } finally {
        runCatching { source.close() }
    }
}
