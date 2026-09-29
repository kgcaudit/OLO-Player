package org.olo.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener

/**
 * The player's data-source factory: everything media3 reads on its own (file,
 * http(s), content) goes through its DefaultDataSource; ftp goes through
 * [FtpDataSource]. Which one handles a source is decided by the uri's scheme
 * when it opens, so a single playlist can mix a local file, a web stream and an
 * FTP file without the caller caring.
 */
@UnstableApi
class OloDataSourceFactory(context: Context) : DataSource.Factory {
    private val baseFactory = DefaultDataSource.Factory(context)
    private val ftpFactory = FtpDataSource.Factory()
    private val webDavFactory = WebDavDataSource.Factory()

    override fun createDataSource(): DataSource =
        SchemeRoutingDataSource(
            baseFactory.createDataSource(),
            ftpFactory.createDataSource(),
            webDavFactory.createDataSource(),
        )
}

/**
 * Routes each open to the delegate that speaks its scheme -- ftp to the FTP
 * source, everything else to the default -- and forwards reads, the uri and
 * transfer listeners to whichever one is open. Both delegates are built up
 * front (they are cheap until opened) so a transfer listener added before the
 * first open reaches the one that ends up serving it.
 */
@UnstableApi
private class SchemeRoutingDataSource(
    private val defaultSource: DataSource,
    private val ftpSource: DataSource,
    private val webDavSource: DataSource,
) : DataSource {

    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        defaultSource.addTransferListener(transferListener)
        ftpSource.addTransferListener(transferListener)
        webDavSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = when (dataSpec.uri.scheme?.lowercase()) {
            "ftp" -> ftpSource
            "webdav" -> webDavSource
            else -> defaultSource
        }
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        (active ?: error("read before open")).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
