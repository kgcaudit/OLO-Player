package org.olo.player.playback

import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Streams a webdav:// file as a ranged HTTP(S) GET, with no local copy.
 *
 * A plain HttpURLConnection rather than media3's DefaultHttpDataSource, because
 * the certificate has to be pinned the way the browser pinned it -- and
 * DefaultHttpDataSource gives no way to install an SSLSocketFactory. The `cert`
 * the browser put on the uri verifies the TLS certificate (see applyWebDavTls),
 * so playback never opens a connection the browse step would have refused. Seeks
 * are served with a Range header; the credentials ride a Basic auth header.
 */
@UnstableApi
class WebDavDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var dataSpec: DataSpec? = null
    private var connection: HttpURLConnection? = null
    private var input: InputStream? = null
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val tls = uri.getQueryParameter("tls") == "1"
        val pinnedCert = uri.getQueryParameter("cert").orEmpty()
        val userInfo = uri.userInfo

        val httpUrl = Uri.Builder()
            .scheme(if (tls) "https" else "http")
            .encodedAuthority(uri.host + if (uri.port > 0) ":${uri.port}" else "")
            .path(uri.path)
            .build()
            .toString()

        try {
            val conn = URL(httpUrl).openConnection() as HttpURLConnection
            org.olo.player.net.applyWebDavTls(conn, pinnedCert)
            conn.connectTimeout = org.olo.player.data.NetConfig.connectTimeoutMs
            conn.readTimeout = org.olo.player.data.NetConfig.connectTimeoutMs
            conn.requestMethod = "GET"
            // Keep ranges honest: a gzipped body has no meaningful byte offsets.
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (userInfo != null) {
                val user = Uri.decode(userInfo.substringBefore(':'))
                val pass = if (userInfo.contains(':')) Uri.decode(userInfo.substringAfter(':')) else ""
                val cred = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $cred")
            }
            // A seek reopens at an offset: ask for exactly the bytes still wanted.
            val position = dataSpec.position
            val length = dataSpec.length
            if (position != 0L || length != C.LENGTH_UNSET.toLong()) {
                val end = if (length != C.LENGTH_UNSET.toLong()) (position + length - 1).toString() else ""
                conn.setRequestProperty("Range", "bytes=$position-$end")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                throw err("WebDAV GET $code", null)
            }
            val stream = conn.inputStream
            connection = conn
            input = stream
            // 206 returns the range length in Content-Length; 200 the whole file.
            val contentLength = conn.contentLengthLong
            bytesRemaining = when {
                length != C.LENGTH_UNSET.toLong() -> length
                contentLength >= 0 -> contentLength
                else -> C.LENGTH_UNSET.toLong()
            }
        } catch (e: Exception) {
            closeQuietly()
            throw err("WebDAV open failed: ${e.message}", e)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(bytesRemaining, length.toLong()).toInt()
        val read = try {
            input?.read(buffer, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        } catch (e: IOException) {
            throw err("WebDAV read failed: ${e.message}", e)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        closeQuietly()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeQuietly() {
        runCatching { input?.close() }
        input = null
        runCatching { connection?.disconnect() }
        connection = null
    }

    private fun err(message: String, cause: Throwable?): IOException =
        DataSourceException(IOException(message, cause), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)


    @UnstableApi
    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = WebDavDataSource()
    }
}
