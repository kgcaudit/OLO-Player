package org.olo.player.net

import android.net.Uri
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.util.EnumSet
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
class SmbSession(private val server: SmbServer) : RemoteOps {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    // 복사는 WebDAV만 네이티브로 둔다(SMB도 서버측 복사가 있으나 범위를 좁혀 일관되게 가린다).
    override val caps = RemoteOpsCaps(copy = false)

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

    // ── 쓰기 작업(RemoteOps) ─────────────────────────────────────
    private fun <T> withReconnect(block: (DiskShare) -> T): T {
        val reused = share != null
        return try {
            block(ensureConnected())
        } catch (e: Exception) {
            if (!reused) throw e
            runCatching { share?.close() }; runCatching { session?.close() }
            runCatching { connection?.close() }; runCatching { client?.close() }
            share = null; session = null; connection = null; client = null
            block(ensureConnected())
        }
    }

    // smbj rename은 열어 둔 핸들에 FileRenameInformation을 건다. 파일/폴더를 미리 가리지 않고
    // 범용 open(둘 다 여는 DiskEntry)으로 열어 공유 상대경로(백슬래시)로 옮긴다 -- 다른 폴더
    // 경로면 이동이 된다. (folderExists로 가르면 서버가 파일을 폴더로 오인해 캐스팅이 깨진다.)
    @Synchronized
    override fun rename(from: String, to: String): Unit = withReconnect { disk ->
        disk.open(
            smbRelative(from),
            EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
            null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null,
        ).use { it.rename(smbRelative(to), true) }
    }

    @Synchronized
    override fun delete(path: String, isDir: Boolean): Unit = withReconnect { disk ->
        val rel = smbRelative(path)
        if (isDir) disk.rmdir(rel, /* recursive = */ true) else disk.rm(rel)
    }

    @Synchronized
    override fun mkdir(path: String): Unit = withReconnect { disk -> disk.mkdir(smbRelative(path)) }

    override fun copy(from: String, to: String): Unit =
        throw UnsupportedOperationException("SMB 복사는 지원 범위 밖입니다.")

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
    val userInfo = encodedUserInfo(server.user, server.pass)
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
