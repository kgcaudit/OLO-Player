package org.olo.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.IOException
import java.io.InputStream

/**
 * A media3 DataSource that streams an sftp:// file over SSH with no local copy.
 *
 * A fresh SSH session and sftp channel are opened per open and torn down on
 * close, so a seek -- which media3 does by reopening at a byte offset -- gets a
 * clean channel. The offset is served by ChannelSftp.get(path, monitor, skip),
 * which starts the transfer partway in, and the length comes from lstat.
 */
@UnstableApi
class SftpDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var dataSpec: DataSpec? = null
    private var session: Session? = null
    private var channel: ChannelSftp? = null
    private var input: InputStream? = null
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val host = uri.host ?: throw err("sftp uri has no host: $uri", null)
        val port = if (uri.port > 0) uri.port else 22
        val userInfo = uri.userInfo
        val user = (userInfo?.substringBefore(':')?.let { Uri.decode(it) }) ?: "anonymous"
        val pass = if (userInfo != null && userInfo.contains(':')) Uri.decode(userInfo.substringAfter(':')) else ""
        val remote = uri.path ?: throw err("sftp uri has no path: $uri", null)

        try {
            val jsch = JSch()
            val s = jsch.getSession(user, host, port)
            s.setPassword(pass)
            s.setConfig("StrictHostKeyChecking", "no")
            s.connect(CONNECT_TIMEOUT_MS)
            val ch = s.openChannel("sftp") as ChannelSftp
            ch.connect(CONNECT_TIMEOUT_MS)

            val size = runCatching { ch.lstat(remote).size }.getOrDefault(-1L)
            val stream = ch.get(remote, null, dataSpec.position)
                ?: throw err("sftp cannot open $remote", null)

            session = s
            channel = ch
            input = stream
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                size >= 0 -> (size - dataSpec.position).coerceAtLeast(0L)
                else -> C.LENGTH_UNSET.toLong()
            }
        } catch (e: Exception) {
            closeQuietly()
            throw err("sftp open failed: ${e.message}", e)
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
            throw err("sftp read failed: ${e.message}", e)
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
        runCatching { channel?.disconnect() }
        channel = null
        runCatching { session?.disconnect() }
        session = null
    }

    private fun err(message: String, cause: Throwable?): IOException =
        DataSourceException(IOException(message, cause), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
    }

    @UnstableApi
    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = SftpDataSource()
    }
}
