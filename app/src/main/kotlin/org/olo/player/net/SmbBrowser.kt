package org.olo.player.net

import android.net.Uri
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import org.olo.player.ftp.RemoteEntry

/**
 * An SMB/CIFS server (a Windows or NAS share). [share] is the top-level share
 * name; [path] is a folder within it. Browsing and playback both address files
 * by a share-relative path.
 */
data class SmbServer(
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val domain: String = "",
    val share: String = "",
    val name: String = "",
    val path: String = "/",
    /** Require SMB3 encryption of the transfer (needs SMB 3.x on both ends). */
    val encrypt: Boolean = false,
)

/** Turns our "/a/b" convention into smbj's share-relative "a\b" (root = ""). */
internal fun smbRelative(path: String): String =
    path.trim('/').replace('/', '\\')

/**
 * An SMB browsing session over smbj: one connection, session and share held open
 * for listing, shut when the browser leaves. Streaming uses its own connection
 * in SmbDataSource.
 */
class SmbSession(private val server: SmbServer) {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    // Lists [path]; a reused share the server idle-closed still reads connected,
    // so the next call fails -- drop it and reconnect once. A fresh connect's
    // failure is a real error and is not retried.
    @Synchronized
    fun list(path: String): List<RemoteEntry> {
        val reused = share != null
        return try {
            listOnce(path)
        } catch (e: Exception) {
            if (!reused) throw e
            runCatching { share?.close() }
            runCatching { session?.close() }
            runCatching { connection?.close() }
            runCatching { client?.close() }
            share = null; session = null; connection = null; client = null
            listOnce(path)
        }
    }

    private fun listOnce(path: String): List<RemoteEntry> {
        val disk = ensureConnected()
        val base = if (path.endsWith("/")) path else "$path/"
        val out = ArrayList<RemoteEntry>()
        for (info in disk.list(smbRelative(path))) {
            val nm = info.fileName
            if (nm == "." || nm == "..") continue
            val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            out += RemoteEntry(
                name = nm,
                isDirectory = isDir,
                path = base + nm,
                modified = runCatching { info.lastWriteTime?.toEpochMillis() }.getOrNull(),
                size = if (isDir) null else runCatching { info.endOfFile }.getOrNull(),
            )
        }
        return out
    }

    @Synchronized
    fun disconnect() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null; session = null; connection = null; client = null
    }

    private fun ensureConnected(): DiskShare {
        share?.let { if (it.isConnected) return it }
        // Signing required (+ optional SMB3 encryption): the SMB equivalent of
        // verifying the peer, since SMB has no certificate/host key to pin.
        val c = SMBClient(smbConfig(server.encrypt))
        val conn = c.connect(server.host, if (server.port > 0) server.port else 445)
        val ac = AuthenticationContext(
            server.user,
            server.pass.toCharArray(),
            server.domain.ifBlank { null },
        )
        val s = conn.authenticate(ac)
        val disk = s.connectShare(server.share) as DiskShare
        client = c; connection = conn; session = s; share = disk
        return disk
    }
}

/** The playable uri for an SMB file: smb://user:pass@host:port/share/path?domain=. */
fun smbMediaUri(server: SmbServer, path: String): Uri {
    val userInfo = when {
        server.user.isEmpty() -> null
        server.pass.isNotEmpty() -> Uri.encode(server.user) + ":" + Uri.encode(server.pass)
        else -> Uri.encode(server.user)
    }
    return Uri.Builder()
        .scheme("smb")
        .encodedAuthority((userInfo?.let { "$it@" } ?: "") + "${server.host}:${if (server.port > 0) server.port else 445}")
        .appendPath(server.share)
        .apply { path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { appendPath(it) } }
        .apply { if (server.domain.isNotBlank()) appendQueryParameter("domain", server.domain) }
        .apply { if (server.encrypt) appendQueryParameter("crypt", "1") }
        .build()
}

/** The saved-position key for an SMB file: host, share and path, no creds. */
fun smbPrefKey(server: SmbServer, path: String): String =
    "smb://${server.host}/${server.share}${if (path.startsWith("/")) path else "/$path"}"
