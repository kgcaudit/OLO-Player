package org.olo.player.net

import android.net.Uri
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.olo.player.ftp.RemoteEntry

/** An SFTP server to connect to (SSH file transfer). */
data class SftpServer(
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val name: String = "",
    val path: String = "/",
)

/**
 * An SFTP browsing session over JSch: one SSH session and one sftp channel held
 * open for listing, shut when the browser leaves. Streaming a file uses its own
 * fresh channel in SftpDataSource. Host-key checking is disabled -- a home media
 * server rarely has a known-hosts entry, and the alternative is a dead end for
 * the person; the trade is documented rather than hidden.
 */
class SftpSession(private val server: SftpServer) {

    private var session: Session? = null
    private var channel: ChannelSftp? = null

    @Synchronized
    fun list(path: String): List<RemoteEntry> {
        val sftp = ensureConnected()
        val base = if (path.endsWith("/")) path else "$path/"
        val out = ArrayList<RemoteEntry>()
        val entries = sftp.ls(path)
        for (obj in entries) {
            val e = obj as ChannelSftp.LsEntry
            val nm = e.filename
            if (nm == "." || nm == "..") continue
            out += RemoteEntry(nm, e.attrs.isDir, base + nm)
        }
        return out
    }

    @Synchronized
    fun disconnect() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        channel = null
        session = null
    }

    private fun ensureConnected(): ChannelSftp {
        channel?.let { if (it.isConnected) return it }
        val jsch = JSch()
        val s = jsch.getSession(server.user.ifBlank { "anonymous" }, server.host, server.port)
        s.setPassword(server.pass)
        s.setConfig("StrictHostKeyChecking", "no")
        s.connect(CONNECT_TIMEOUT_MS)
        val ch = s.openChannel("sftp") as ChannelSftp
        ch.connect(CONNECT_TIMEOUT_MS)
        session = s
        channel = ch
        return ch
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}

/** The playable uri for an SFTP file: sftp://user:pass@host:port/path. */
fun sftpMediaUri(server: SftpServer, path: String): Uri {
    val userInfo = when {
        server.user.isEmpty() -> null
        server.pass.isNotEmpty() -> Uri.encode(server.user) + ":" + Uri.encode(server.pass)
        else -> Uri.encode(server.user)
    }
    return Uri.Builder()
        .scheme("sftp")
        .encodedAuthority((userInfo?.let { "$it@" } ?: "") + "${server.host}:${server.port}")
        .path(path)
        .build()
}

/** The saved-position key for an SFTP file: host, port and path, no creds. */
fun sftpPrefKey(server: SftpServer, path: String): String =
    "sftp://${server.host}:${server.port}$path"
