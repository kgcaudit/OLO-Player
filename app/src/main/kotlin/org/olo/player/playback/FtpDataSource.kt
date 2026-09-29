package org.olo.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import java.io.IOException
import java.io.InputStream
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

/**
 * A media3 DataSource that streams an ftp:// file straight off the server, with
 * no local copy.
 *
 * media3's own DefaultDataSource reads file, http(s) and content, but not FTP,
 * so a great many home media servers (NAS boxes, routers) could not be played
 * without downloading first. This fills that gap: it opens an FTP data
 * connection, seeks with REST when the player asks for a byte offset (which is
 * what lets a film be scrubbed rather than only played from the top), and reads
 * the stream through.
 *
 * The credentials ride in the uri's userInfo (ftp://user:pass@host/path); a
 * fresh connection is made per open and torn down on close, so a seek -- which
 * media3 does by closing and reopening at the new offset -- always gets a clean
 * connection rather than trying to reuse one mid-transfer.
 */
@UnstableApi
class FtpDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var dataSpec: DataSpec? = null
    private var client: FTPClient? = null
    private var input: InputStream? = null
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val host = uri.host ?: throw ftpError("ftp uri has no host: $uri", null)
        val port = if (uri.port > 0) uri.port else 21
        val userInfo = uri.userInfo
        val user: String
        val pass: String
        if (userInfo != null && userInfo.contains(':')) {
            user = userInfo.substringBefore(':')
            pass = userInfo.substringAfter(':')
        } else {
            user = userInfo ?: "anonymous"
            pass = ""
        }
        // The decoded path, which is the remote file. Uri already decoded it.
        val remote = uri.path ?: throw ftpError("ftp uri has no path: $uri", null)
        // Advanced options the browser encoded onto the uri (see mediaUri).
        val encoding = uri.getQueryParameter("enc")
        val passive = uri.getQueryParameter("pasv") != "0"
        val ftps = uri.getQueryParameter("ftps") == "1"

        val ftp = if (ftps) {
            org.apache.commons.net.ftp.FTPSClient("TLS", /* isImplicit = */ false)
        } else {
            FTPClient()
        }
        ftp.connectTimeout = CONNECT_TIMEOUT_MS
        // Same charset rule as the browser used to list the file, so a UTF-8 name
        // is retrieved with the same bytes it was shown with (see applyEncoding).
        org.olo.player.ftp.applyEncoding(ftp, encoding ?: "")
        try {
            ftp.connect(host, port)
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                throw ftpError("ftp connect refused (${ftp.replyCode})", null)
            }
            if (!ftp.login(user, pass)) {
                throw ftpError("ftp login failed for $user@$host", null)
            }
            if (ftp is org.apache.commons.net.ftp.FTPSClient) {
                runCatching { ftp.execPBSZ(0); ftp.execPROT("P") }
            }
            if (passive) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            // Keep the control channel alive while the data channel streams, so a
            // long film is not dropped by an idle-timeout on the server.
            ftp.setControlKeepAliveTimeout(java.time.Duration.ofSeconds(KEEP_ALIVE_SECONDS))

            // The file's length, from SIZE (valid in binary mode). A server that
            // withholds it leaves the length unknown -- the stream still plays,
            // but cannot be scrubbed past what has been read.
            val size = sizeOf(ftp, remote)

            // Scrubbing: media3 reopens at a byte offset, restored with REST.
            if (dataSpec.position > 0) ftp.setRestartOffset(dataSpec.position)

            val stream = ftp.retrieveFileStream(remote)
                ?: throw ftpError("ftp cannot open $remote (${ftp.replyCode})", null)

            client = ftp
            input = stream
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                size >= 0 -> (size - dataSpec.position).coerceAtLeast(0L)
                else -> C.LENGTH_UNSET.toLong()
            }
        } catch (e: IOException) {
            runCatching { ftp.disconnect() }
            throw ftpError("ftp open failed: ${e.message}", e)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }
        val read = try {
            input?.read(buffer, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        } catch (e: IOException) {
            throw ftpError("ftp read failed: ${e.message}", e)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        // The stream and the connection are dropped hard -- disconnect kills the
        // data connection at once. completePendingCommand is deliberately not
        // called: a seek closes mid-transfer, where it would block waiting for a
        // transfer that will never finish, so the connection is simply cut and a
        // fresh one is made on the next open.
        try {
            input?.close()
        } catch (_: IOException) {
            // Ignore -- being torn down anyway.
        } finally {
            input = null
            runCatching { client?.disconnect() }
            client = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    /** The file's size via the SIZE command, or -1 when the server withholds it. */
    private fun sizeOf(ftp: FTPClient, remote: String): Long = try {
        if (ftp.sendCommand("SIZE", remote) == 213) {
            // "213 <bytes>" -- the reply string carries the code and the number.
            ftp.replyStrings.firstOrNull()
                ?.removePrefix("213")?.trim()?.toLongOrNull() ?: -1L
        } else {
            -1L
        }
    } catch (_: IOException) {
        -1L
    }

    private fun ftpError(message: String, cause: Throwable?): IOException =
        DataSourceException(
            IOException(message, cause),
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val KEEP_ALIVE_SECONDS = 30L
    }

    /** Builds an [FtpDataSource] for each media source that needs one. */
    @UnstableApi
    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = FtpDataSource()
    }
}
