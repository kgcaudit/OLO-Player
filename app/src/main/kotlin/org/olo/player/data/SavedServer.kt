package org.olo.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.olo.player.ftp.FtpServer
import org.olo.player.net.SftpServer
import org.olo.player.net.SmbServer
import org.olo.player.net.WebDavServer

/**
 * One remembered network server, of any protocol, so a connection can be
 * reopened without retyping host/계정/설정 every time.
 *
 * Why one flat record instead of four: the four protocol models
 * ([FtpServer]/[SftpServer]/[SmbServer]/[WebDavServer]) each carry only their
 * own fields, but the saved-servers list has to hold all kinds side by side and
 * serialise to a single shelf. So this is their union with a [protocol] tag; the
 * `to*`/`from*` helpers convert both ways at the form boundary. [id] is derived
 * from what actually identifies a server (protocol·host·port·계정·공유·경로), so
 * re-saving the same server — an edit — replaces its entry instead of piling up
 * duplicates, while name/비밀번호/인코딩 are free to change.
 *
 * The password is kept here as-is: the shelf lives in the app-private
 * SharedPreferences file, the same place resume positions live, reachable only
 * by this app. Reconnecting one-tap needs it stored, so it is.
 */
data class SavedServer(
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val path: String = "/",
    // FTP
    val encoding: String = "",
    val passive: Boolean = true,
    val ftps: Boolean = false,
    // SMB
    val share: String = "",
    val domain: String = "",
    // WebDAV
    val tls: Boolean = false,
    // Trust pins (fingerprints, not secrets): the SSH host key accepted for an
    // SFTP server, the TLS certificate accepted for an FTPS server.
    val pinnedHostKey: String = "",
    val pinnedCertificate: String = "",
    // SMB has no pin; instead it can require SMB3 encryption of the transfer.
    val smbEncrypt: Boolean = false,
    val savedAt: Long = System.currentTimeMillis(),
) {
    /** Identity for dedup: everything that makes it a different server. */
    val id: String get() = "$protocol|$host|$port|$user|$share|$path"

    /** A label for the row: the given name, else host (with the share for SMB). */
    val label: String
        get() = name.ifBlank { if (protocol == PROTO_SMB && share.isNotBlank()) "$host/$share" else host }

    fun toFtp() = FtpServer(host, port, user, pass, name, path, encoding, passive, ftps, pinnedCertificate)
    fun toSftp() = SftpServer(host, port, user, pass, name, path, pinnedHostKey)
    fun toSmb() = SmbServer(host, port, user, pass, domain, share, name, path, smbEncrypt)
    fun toWebDav() = WebDavServer(host, port, user, pass, tls, name, path, pinnedCertificate)

    fun toJson(): JSONObject = JSONObject()
        .put("protocol", protocol).put("name", name).put("host", host).put("port", port)
        .put("user", user).put("pass", pass).put("path", path)
        .put("encoding", encoding).put("passive", passive).put("ftps", ftps)
        .put("share", share).put("domain", domain).put("tls", tls)
        .put("pinnedHostKey", pinnedHostKey).put("pinnedCertificate", pinnedCertificate)
        .put("smbEncrypt", smbEncrypt)
        .put("savedAt", savedAt)

    companion object {
        const val PROTO_FTP = "ftp"
        const val PROTO_SFTP = "sftp"
        const val PROTO_SMB = "smb"
        const val PROTO_WEBDAV = "webdav"

        fun of(s: FtpServer) = SavedServer(
            PROTO_FTP, s.name, s.host, s.port, s.user, s.pass, s.path,
            encoding = s.encoding, passive = s.passive, ftps = s.ftps,
            pinnedCertificate = s.pinnedCertificate,
        )

        fun of(s: SftpServer) = SavedServer(
            PROTO_SFTP, s.name, s.host, s.port, s.user, s.pass, s.path,
            pinnedHostKey = s.knownHostKey,
        )

        fun of(s: SmbServer) = SavedServer(
            PROTO_SMB, s.name, s.host, s.port, s.user, s.pass, s.path,
            share = s.share, domain = s.domain, smbEncrypt = s.encrypt,
        )

        fun of(s: WebDavServer) = SavedServer(
            PROTO_WEBDAV, s.name, s.host, s.port, s.user, s.pass, s.path, tls = s.tls,
            pinnedCertificate = s.pinnedCertificate,
        )

        fun fromJson(o: JSONObject) = SavedServer(
            protocol = o.optString("protocol", PROTO_FTP),
            name = o.optString("name", ""),
            host = o.optString("host", ""),
            port = o.optInt("port", 0),
            user = o.optString("user", ""),
            pass = o.optString("pass", ""),
            path = o.optString("path", "/"),
            encoding = o.optString("encoding", ""),
            passive = o.optBoolean("passive", true),
            ftps = o.optBoolean("ftps", false),
            share = o.optString("share", ""),
            domain = o.optString("domain", ""),
            tls = o.optBoolean("tls", false),
            pinnedHostKey = o.optString("pinnedHostKey", ""),
            pinnedCertificate = o.optString("pinnedCertificate", ""),
            smbEncrypt = o.optBoolean("smbEncrypt", false),
            savedAt = o.optLong("savedAt", 0L),
        )
    }
}

/**
 * The saved-servers shelf, on the same SharedPreferences file as the rest.
 *
 * Most-recent first, deduped by [SavedServer.id] so re-saving an edited server
 * updates in place, capped so the file never grows without end.
 */
class SavedServerStore(
    context: Context,
    private val cipher: PasswordCipher = KeystorePasswordCipher(),
) {

    private val prefs = context.applicationContext
        .getSharedPreferences("olo_player", Context.MODE_PRIVATE)

    fun list(): List<SavedServer> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { decrypted(SavedServer.fromJson(arr.getJSONObject(it))) }
    }.getOrDefault(emptyList())

    /** Adds the server at the front, replacing any earlier entry with the same id. */
    fun save(server: SavedServer) {
        val list = list().filterNot { it.id == server.id }.toMutableList()
        list.add(0, server)
        write(list.take(CAP))
    }

    fun remove(id: String) = write(list().filterNot { it.id == id })

    private fun write(items: List<SavedServer>) {
        // The password is encrypted at rest; everything else stays plain so the
        // row and reconnect need no key. A blank password stays blank (anonymous).
        val arr = JSONArray().apply {
            items.forEach {
                val stored = if (it.pass.isEmpty()) it else it.copy(pass = cipher.encrypt(it.pass))
                put(stored.toJson())
            }
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /**
     * Restores the in-memory (plaintext) password. A value this cipher wrote
     * decrypts; a legacy plaintext one (stored before encryption existed) fails
     * the marker check and is kept as-is, then re-encrypted on the next save. A
     * value that was encrypted but is now unreadable (keystore key gone) becomes
     * blank, so the form asks for it again instead of trying a wrong secret.
     */
    private fun decrypted(server: SavedServer): SavedServer {
        if (server.pass.isEmpty()) return server
        val plain = cipher.decrypt(server.pass) ?: if (isEncrypted(server.pass)) "" else server.pass
        return server.copy(pass = plain)
    }

    private fun isEncrypted(value: String): Boolean = value.startsWith("enc1:")

    companion object {
        private const val KEY = "net_saved_servers"
        private const val CAP = 60
    }
}
