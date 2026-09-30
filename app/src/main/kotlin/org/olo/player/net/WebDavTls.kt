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
 * Identity rests on that pin and on the connection's own host name check, which is
 * left at the JVM default -- never switched off. An earlier version relaxed the
 * name check for a pinned certificate (a self-signed NAS reached by IP), but a
 * verifier that says yes to every name is a bypass this project forbids
 * (NoBlanketTrustTest), so the default check stays. A pinned certificate must
 * therefore also carry the name it is reached by in its SAN, exactly as the FTPS
 * path already requires.
 *
 * Returns the trust manager (for [PinningTrustManager.refusalFor]) on an HTTPS
 * connection, or null on plain HTTP.
 */
fun applyWebDavTls(conn: HttpURLConnection, pinnedCertificate: String): PinningTrustManager? {
    if (conn !is HttpsURLConnection) return null
    val tm = PinningTrustManager(pinnedCertificate.ifBlank { null })
    val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
    conn.sslSocketFactory = ctx.socketFactory
    return tm
}
