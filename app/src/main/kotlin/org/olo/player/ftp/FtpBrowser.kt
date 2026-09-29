package org.olo.player.ftp

import android.net.Uri
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient

/**
 * An FTP server to connect to. A blank user is taken as anonymous.
 *
 * The advanced fields mirror the "새 서버" form: [encoding] is the control-channel
 * charset for non-ASCII filenames (blank = the client default), [passive] picks
 * passive vs active data connections (passive suits most home networks behind a
 * router), and [ftps] turns on explicit TLS (FTPS). [name] is an optional label
 * for the saved-servers list; [path] is where browsing opens.
 */
data class FtpServer(
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val name: String = "",
    val path: String = "/",
    val encoding: String = "",
    val passive: Boolean = true,
    val ftps: Boolean = false,
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
        // Explicit FTPS when asked, plain FTP otherwise. The control encoding is
        // set before connecting so non-ASCII listings decode correctly.
        val ftp = if (server.ftps) FTPSClient("TLS", /* isImplicit = */ false) else FTPClient()
        ftp.connectTimeout = CONNECT_TIMEOUT_MS
        if (server.encoding.isNotBlank()) ftp.controlEncoding = server.encoding
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
        if (ftp is FTPSClient) {
            // Protect the data channel too (PBSZ 0 / PROT P), or the transfer
            // would fall back to clear text on a server that allows it.
            runCatching { ftp.execPBSZ(0); ftp.execPROT("P") }
        }
        if (server.passive) ftp.enterLocalPassiveMode() else ftp.enterLocalActiveMode()
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
    // The advanced options ride as query params so the FtpDataSource opens the
    // stream the same way the browser listed it (encoding, passive, FTPS).
    return Uri.Builder()
        .scheme("ftp")
        .encodedAuthority("$userInfo@${server.host}:${server.port}")
        .path(path)
        .apply {
            if (server.encoding.isNotBlank()) appendQueryParameter("enc", server.encoding)
            appendQueryParameter("pasv", if (server.passive) "1" else "0")
            if (server.ftps) appendQueryParameter("ftps", "1")
        }
        .build()
}

/**
 * The key a remote file's saved position hangs on -- the same host, port and
 * path, but with no credentials, so a password never lands in the preferences.
 */
fun prefKeyFor(server: FtpServer, path: String): String =
    "ftp://${server.host}:${server.port}$path"
