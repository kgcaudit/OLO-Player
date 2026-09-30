package org.olo.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.util.EnumSet

/**
 * A media3 DataSource that streams an smb:// file off a Windows/NAS share with
 * no local copy, over smbj. SMB files are random-access, so instead of a stream
 * this holds an open handle and reads each block at an absolute file offset --
 * a seek just moves the offset. A fresh connection is made per open and torn
 * down on close, matching how media3 reopens on a seek.
 */
@UnstableApi
class SmbDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var dataSpec: DataSpec? = null
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var file: com.hierynomus.smbj.share.File? = null
    private var position = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)
        val uri = dataSpec.uri
        val host = uri.host ?: throw err("smb uri has no host: $uri", null)
        val port = if (uri.port > 0) uri.port else 445
        val userInfo = uri.userInfo
        val user = userInfo?.substringBefore(':')?.let { Uri.decode(it) } ?: ""
        val pass = if (userInfo != null && userInfo.contains(':')) Uri.decode(userInfo.substringAfter(':')) else ""
        val domain = uri.getQueryParameter("domain")
        val encrypt = uri.getQueryParameter("crypt") == "1"
        val segments = uri.pathSegments
        if (segments.isEmpty()) throw err("smb uri has no share: $uri", null)
        val shareName = segments.first()
        val relative = segments.drop(1).joinToString("\\")

        try {
            // Same hardened config the browser used (signing required, optional
            // SMB3 encryption carried on the uri).
            val c = SMBClient(org.olo.player.net.smbConfig(encrypt))
            val conn = c.connect(host, port)
            val s = conn.authenticate(AuthenticationContext(user, pass.toCharArray(), domain?.ifBlank { null }))
            val disk = s.connectShare(shareName) as DiskShare
            val f = disk.openFile(
                relative,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            val size = runCatching { f.fileInformation.standardInformation.endOfFile }.getOrDefault(-1L)
            client = c; connection = conn; session = s; share = disk; file = f
            position = dataSpec.position
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                size >= 0 -> (size - dataSpec.position).coerceAtLeast(0L)
                else -> C.LENGTH_UNSET.toLong()
            }
        } catch (e: Exception) {
            closeQuietly()
            throw err("smb open failed: ${e.message}", e)
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
            file?.read(buffer, position, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        } catch (e: IOException) {
            throw err("smb read failed: ${e.message}", e)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        position += read
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
        runCatching { file?.close() }
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        file = null; share = null; session = null; connection = null; client = null
    }

    private fun err(message: String, cause: Throwable?): IOException =
        DataSourceException(IOException(message, cause), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)

    @UnstableApi
    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource()
    }
}
