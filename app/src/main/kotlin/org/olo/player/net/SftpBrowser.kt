package org.olo.player.net

import android.net.Uri
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import org.olo.player.ftp.RemoteEntry

/**
 * An SFTP server to connect to (SSH file transfer).
 *
 * [knownHostKey] is the SHA-256 host-key fingerprint already accepted for this
 * server, or blank on a first meeting. It is what the connection verifies the
 * server against; blank makes the first connect raise [HostKeyUnverified] so the
 * person can recognise the key and pin it.
 */
data class SftpServer(
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val name: String = "",
    val path: String = "/",
    val knownHostKey: String = "",
)

/**
 * An SFTP browsing session over JSch: one SSH session and one sftp channel held
 * open for listing, shut when the browser leaves. Streaming a file uses its own
 * fresh channel in SftpDataSource.
 *
 * The host key is verified against [SftpServer.knownHostKey] before the password
 * is sent ([PinningHostKeyRepository] + StrictHostKeyChecking): an unrecognised
 * or changed key raises [HostKeyUnverified] rather than logging in to a server
 * nobody vouched for.
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
            // mTime is in seconds; size is meaningful only for a file.
            out += RemoteEntry(
                name = nm,
                isDirectory = e.attrs.isDir,
                path = base + nm,
                modified = e.attrs.mTime.toLong() * 1000L,
                size = if (e.attrs.isDir) null else e.attrs.size,
            )
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
        val hostKeys = PinningHostKeyRepository(server.knownHostKey)
        jsch.hostKeyRepository = hostKeys
        val s = jsch.getSession(server.user.ifBlank { "anonymous" }, server.host, server.port)
        s.setPassword(server.pass)
        // "yes" turns an unrecognised/changed key into a failed connect before
        // auth, which we translate into a HostKeyUnverified the browser can ask
        // about, rather than prompting a console nobody watches.
        s.setConfig("StrictHostKeyChecking", "yes")
        try {
            s.connect(CONNECT_TIMEOUT_MS)
        } catch (e: JSchException) {
            val presented = hostKeys.seen
            if (presented != null && !presented.fingerprint.equals(server.knownHostKey, ignoreCase = true)) {
                runCatching { s.disconnect() }
                throw HostKeyUnverified(
                    fingerprint = presented.fingerprint,
                    algorithm = presented.algorithm,
                    changed = server.knownHostKey.isNotBlank(),
                )
            }
            throw e
        }
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
        .apply { if (server.knownHostKey.isNotBlank()) appendQueryParameter("hk", server.knownHostKey) }
        .build()
}

/** The saved-position key for an SFTP file: host, port and path, no creds. */
fun sftpPrefKey(server: SftpServer, path: String): String =
    "sftp://${server.host}:${server.port}$path"
