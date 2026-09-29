package org.olo.player.net

import android.net.Uri
import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.olo.player.ftp.RemoteEntry

/**
 * A WebDAV server to connect to. WebDAV is HTTP(S) underneath, so browsing is a
 * PROPFIND and playback is a plain ranged GET -- no third-party library, and the
 * proven media3 HTTP source does the streaming (see WebDavDataSource).
 */
data class WebDavServer(
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val tls: Boolean = false,
    val name: String = "",
    val path: String = "/",
)

/**
 * A WebDAV browsing session: one PROPFIND (Depth 1) per directory, parsed
 * leniently for each response's href and whether it is a collection. Runs on a
 * background thread like the FTP session; there is no persistent connection to
 * hold, so [disconnect] is a no-op kept for a uniform browser.
 */
class WebDavSession(private val server: WebDavServer) {

    fun list(path: String): List<RemoteEntry> {
        val base = if (path.endsWith("/")) path else "$path/"
        val conn = open(base, "PROPFIND")
        conn.setRequestProperty("Depth", "1")
        conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
        conn.doOutput = true
        conn.outputStream.use { it.write(PROPFIND_BODY.toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val msg = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
            throw IOException("WebDAV PROPFIND $code${if (msg != null) ": $msg" else ""}")
        }
        val xml = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return parse(xml, base)
    }

    @Suppress("unused")
    fun disconnect() { /* HTTP is connectionless here; nothing to hold. */ }

    private fun open(path: String, method: String): HttpURLConnection {
        val url = URL(baseUrl() + encodePath(path))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = CONNECT_TIMEOUT_MS
        conn.requestMethod = method
        if (server.user.isNotEmpty()) {
            val cred = Base64.encodeToString("${server.user}:${server.pass}".toByteArray(), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $cred")
        }
        return conn
    }

    private fun baseUrl(): String {
        val scheme = if (server.tls) "https" else "http"
        return "$scheme://${server.host}:${server.port}"
    }

    // Parse each <response>: its <href> and whether a <collection/> marks it a
    // directory. Namespaces vary (D:, d:, lp1:), so match on the local name.
    private fun parse(xml: String, requestPath: String): List<RemoteEntry> {
        val parser = android.util.Xml.newPullParser()
        parser.setInput(xml.reader())
        val out = ArrayList<RemoteEntry>()
        var href: String? = null
        var isCollection = false
        var inResponse = false
        var event = parser.eventType
        var lastText = ""
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name.substringAfter(':').lowercase()) {
                    "response" -> { inResponse = true; href = null; isCollection = false }
                    "collection" -> if (inResponse) isCollection = true
                    else -> {}
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> lastText = parser.text ?: ""
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name.substringAfter(':').lowercase()) {
                    "href" -> if (inResponse && href == null) href = lastText.trim()
                    "response" -> {
                        val h = href
                        if (inResponse && h != null) {
                            val abs = hrefToPath(h)
                            // Skip the collection itself (the request path).
                            if (abs != null && abs.trimEnd('/') != requestPath.trimEnd('/')) {
                                val nm = Uri.decode(abs.trimEnd('/').substringAfterLast('/'))
                                if (nm.isNotEmpty()) {
                                    out += RemoteEntry(nm, isCollection, if (isCollection) "$abs/".replace("//", "/") else abs)
                                }
                            }
                        }
                        inResponse = false
                    }
                    else -> {}
                }
            }
            event = parser.next()
        }
        return out
    }

    // An href may be an absolute URL or an absolute path; reduce to the path.
    private fun hrefToPath(href: String): String? = runCatching {
        if (href.startsWith("http")) URL(href).path else href
    }.getOrNull()?.let { Uri.decode(it) }

    private fun encodePath(path: String): String =
        path.split("/").joinToString("/") { Uri.encode(it) }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val PROPFIND_BODY =
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>"""
    }
}

/** The playable uri for a WebDAV file: webdav(s) creds ride the userInfo, and
 *  the tls flag rides a query param, which WebDavDataSource reads back. */
fun webDavMediaUri(server: WebDavServer, path: String): Uri {
    val userInfo = when {
        server.user.isEmpty() -> null
        server.pass.isNotEmpty() -> Uri.encode(server.user) + ":" + Uri.encode(server.pass)
        else -> Uri.encode(server.user)
    }
    return Uri.Builder()
        .scheme("webdav")
        .encodedAuthority((userInfo?.let { "$it@" } ?: "") + "${server.host}:${server.port}")
        .path(path)
        .appendQueryParameter("tls", if (server.tls) "1" else "0")
        .build()
}

/** The saved-position key for a WebDAV file: host, port and path, no creds. */
fun webDavPrefKey(server: WebDavServer, path: String): String =
    "webdav://${server.host}:${server.port}$path"
