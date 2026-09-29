package org.olo.player.playback

import android.net.Uri
import android.util.Base64
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Streams a webdav:// file by rewriting it to its underlying http(s) URL and
 * handing that to media3's own HTTP source, with the credentials as a Basic
 * Authorization header. WebDAV files are ordinary ranged GETs, so the proven
 * DefaultHttpDataSource does the seeking and buffering; this only translates the
 * scheme and carries the auth, per open (a seek reopens like any HTTP source).
 */
@UnstableApi
class WebDavDataSource : DataSource {

    private val listeners = ArrayList<TransferListener>()
    private var delegate: HttpDataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val tls = uri.getQueryParameter("tls") == "1"
        val userInfo = uri.userInfo
        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(CONNECT_TIMEOUT_MS)
        if (userInfo != null) {
            val user = Uri.decode(userInfo.substringBefore(':'))
            val pass = if (userInfo.contains(':')) Uri.decode(userInfo.substringAfter(':')) else ""
            val cred = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
            factory.setDefaultRequestProperties(mapOf("Authorization" to "Basic $cred"))
        }
        val http = factory.createDataSource()
        listeners.forEach { http.addTransferListener(it) }
        delegate = http

        // Rewrite webdav(+tls) → http(s), dropping the auth/query the header now carries.
        val httpUri = Uri.Builder()
            .scheme(if (tls) "https" else "http")
            .encodedAuthority(uri.host + if (uri.port > 0) ":${uri.port}" else "")
            .path(uri.path)
            .build()
        return http.open(dataSpec.buildUpon().setUri(httpUri).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        (delegate ?: error("read before open")).read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        delegate?.close()
        delegate = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
    }

    @UnstableApi
    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = WebDavDataSource()
    }
}
