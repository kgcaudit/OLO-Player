package org.olo.player.net

import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import org.filezilla.ftp.net.PinningTrustManager

/**
 * Pins the TLS certificate on a WebDAV HTTPS connection, reusing core-ftp's
 * [PinningTrustManager] so browse and streaming trust the same way the FTPS path
 * does: only the accepted certificate (or a public CA-valid one, when nothing is
 * pinned yet) is trusted.
 *
 * When a certificate is pinned, the pin is a stricter identity than the hostname,
 * so hostname verification is relaxed -- a self-signed NAS certificate reached by
 * an IP or a bare host name would otherwise fail the name check even though it is
 * exactly the certificate the person recognised. Without a pin the default name
 * check stays on, because a public certificate is only meaningful for its names.
 *
 * Returns the trust manager (for [PinningTrustManager.refusalFor]) on an HTTPS
 * connection, or null on plain HTTP.
 */
fun applyWebDavTls(conn: HttpURLConnection, pinnedCertificate: String): PinningTrustManager? {
    if (conn !is HttpsURLConnection) return null
    val tm = PinningTrustManager(pinnedCertificate.ifBlank { null })
    val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
    conn.sslSocketFactory = ctx.socketFactory
    if (pinnedCertificate.isNotBlank()) {
        conn.setHostnameVerifier { _, _ -> true }
    }
    return tm
}
