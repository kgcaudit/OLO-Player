package org.olo.player.ftp

import android.net.Uri
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

/** An FTP server to connect to. A blank user is taken as anonymous. */
data class FtpServer(
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
)

/** One entry in a remote directory listing. */
data class RemoteEntry(
    val name: String,
    val isDirectory: Boolean,
    /** The absolute remote path, so navigating and opening need no path maths. */
    val path: String,
)

/**
 * A live FTP connection, used only to browse -- list directories and walk them.
 *
 * Streaming a file uses its own fresh connection in the player's FtpDataSource;
 * this one stays open just for the browser, on a background thread, and is shut
 * when the browser leaves. Calls are synchronized because a slow listing and a
 * quick tap could otherwise reach the one client at once.
 */
class FtpSession(private val server: FtpServer) {

    private var client: FTPClient? = null

    /** Connects if needed and lists [path], directories and files alike. */
    @Synchronized
    fun list(path: String): List<RemoteEntry> {
        val ftp = ensureConnected()
        val base = if (path.endsWith("/")) path else "$path/"
        val files = ftp.listFiles(path) ?: emptyArray()
        return files
            .filter { it.name != "." && it.name != ".." }
            .map { RemoteEntry(it.name, it.isDirectory, base + it.name) }
    }

    @Synchronized
    fun disconnect() {
        runCatching { client?.logout() }
        runCatching { client?.disconnect() }
        client = null
    }

    private fun ensureConnected(): FTPClient {
        client?.let { if (it.isConnected) return it }
        val ftp = FTPClient()
        ftp.connectTimeout = CONNECT_TIMEOUT_MS
        ftp.connect(server.host, server.port)
        if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
            ftp.disconnect()
            throw java.io.IOException("connect refused (${ftp.replyCode})")
        }
        val user = server.user.ifBlank { "anonymous" }
        if (!ftp.login(user, server.pass)) {
            ftp.disconnect()
            throw java.io.IOException("login failed for $user")
        }
        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)
        client = ftp
        return ftp
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}

/** The parent of a remote path, or "/" at the root. */
fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty()) return "/"
    val cut = trimmed.lastIndexOf('/')
    return if (cut <= 0) "/" else trimmed.substring(0, cut)
}

/**
 * The playable uri for a remote file, credentials and all, so the player's
 * FtpDataSource can open it. Built with a Uri.Builder so the parts round-trip:
 * the data source reads them back with Uri.getUserInfo/getHost/getPath, which
 * decode exactly what encodedAuthority/path encoded here.
 */
fun mediaUri(server: FtpServer, path: String): Uri {
    val user = server.user.ifBlank { "anonymous" }
    val userInfo = if (server.pass.isNotEmpty()) {
        Uri.encode(user) + ":" + Uri.encode(server.pass)
    } else {
        Uri.encode(user)
    }
    return Uri.Builder()
        .scheme("ftp")
        .encodedAuthority("$userInfo@${server.host}:${server.port}")
        .path(path)
        .build()
}

/**
 * The key a remote file's saved position hangs on -- the same host, port and
 * path, but with no credentials, so a password never lands in the preferences.
 */
fun prefKeyFor(server: FtpServer, path: String): String =
    "ftp://${server.host}:${server.port}$path"
