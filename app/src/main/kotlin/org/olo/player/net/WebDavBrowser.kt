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
    /**
     * The TLS certificate fingerprint accepted for this HTTPS server, or blank on
     * a first meeting. Only this fingerprint is trusted; a public CA-valid one
     * needs none. Blank + a self-signed cert raises
     * [org.filezilla.ftp.net.CertificateNotTrusted] so the person can pin it.
     */
    val pinnedCertificate: String = "",
)

/**
 * A WebDAV browsing session: one PROPFIND (Depth 1) per directory, parsed
 * leniently for each response's href and whether it is a collection. Runs on a
 * background thread like the FTP session; there is no persistent connection to
 * hold, so [disconnect] is a no-op kept for a uniform browser.
 */
class WebDavSession(private val server: WebDavServer) : RemoteOps {

    // WebDAV만 네이티브 서버측 복사(COPY)가 있다.
    override val caps = RemoteOpsCaps(copy = true)

    fun list(path: String): List<RemoteEntry> {
        val base = if (path.endsWith("/")) path else "$path/"
        val (conn, trust) = open(base, "PROPFIND")
        conn.setRequestProperty("Depth", "1")
        conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
        conn.doOutput = true
        try {
            conn.outputStream.use { it.write(PROPFIND_BODY.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val msg = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
                throw IOException("WebDAV PROPFIND $code${if (msg != null) ": $msg" else ""}")
            }
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(xml, base)
        } catch (e: Exception) {
            // A pinning refusal surfaces as an SSL failure; turn it into the same
            // CertificateNotTrusted the FTPS path raises, for the browser dialog.
            trust?.refusalFor(e)?.let { throw it }
            throw e
        } finally {
            // 성공·실패 어느 쪽이든 keep-alive 소켓을 반드시 닫는다(실패 경로 누수 방지).
            conn.disconnect()
        }
    }

    @Suppress("unused")
    fun disconnect() { /* HTTP is connectionless here; nothing to hold. */ }

    // ── 쓰기 작업(RemoteOps) ─────────────────────────────────────
    // WebDAV는 전용 메서드로 깔끔하게 된다: MOVE(이름변경·이동)·COPY(복사)·DELETE(삭제, 컬렉션은
    // 재귀)·MKCOL(새 폴더). MOVE/COPY는 대상 전체 URL을 Destination 헤더로, Overwrite:F로 덮어쓰기
    // 방지. 코드는 2xx만 성공으로 본다.
    override fun rename(from: String, to: String): Unit = moveOrCopy("MOVE", from, to)
    override fun copy(from: String, to: String): Unit = moveOrCopy("COPY", from, to)

    private fun moveOrCopy(method: String, from: String, to: String) {
        val (conn, trust) = open(from, method)
        conn.setRequestProperty("Destination", baseUrl() + encodePath(to))
        conn.setRequestProperty("Overwrite", "F")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("WebDAV $method $code")
        } catch (e: Exception) {
            trust?.refusalFor(e)?.let { throw it }
            throw e
        } finally {
            conn.disconnect()
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        // 컬렉션(폴더)은 경로 끝에 /를 붙여야 하는 서버가 있다. DELETE는 컬렉션을 재귀로 지운다.
        val target = if (isDir && !path.endsWith("/")) "$path/" else path
        val (conn, trust) = open(target, "DELETE")
        try {
            val code = conn.responseCode
            // 404(이미 없음)는 성공으로 본다 -- 지우려던 것이 없는 것은 목표 달성.
            if (code !in 200..299 && code != 404) throw IOException("WebDAV DELETE $code")
        } catch (e: Exception) {
            trust?.refusalFor(e)?.let { throw it }
            throw e
        } finally {
            conn.disconnect()
        }
    }

    override fun mkdir(path: String) {
        val (conn, trust) = open(if (path.endsWith("/")) path else "$path/", "MKCOL")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("WebDAV MKCOL $code")
        } catch (e: Exception) {
            trust?.refusalFor(e)?.let { throw it }
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun open(path: String, method: String): Pair<HttpURLConnection, org.filezilla.ftp.net.PinningTrustManager?> {
        val url = URL(baseUrl() + encodePath(path))
        val conn = url.openConnection() as HttpURLConnection
        val trust = applyWebDavTls(conn, server.pinnedCertificate)
        conn.connectTimeout = org.olo.player.data.NetConfig.connectTimeoutMs
        conn.readTimeout = org.olo.player.data.NetConfig.connectTimeoutMs
        setMethod(conn, method)
        if (server.user.isNotEmpty()) {
            val cred = Base64.encodeToString("${server.user}:${server.pass}".toByteArray(), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $cred")
        }
        return conn to trust
    }

    // WebDAV 전용 메서드(PROPFIND/MKCOL/MOVE/COPY)를 건다. 안드로이드의 HttpURLConnection은
    // OkHttp 기반이라 그대로 허용돼 이 경로만 탄다(그래서 WebDAV 브라우징이 기기에서 동작한다).
    // 반면 표준 JDK의 HttpURLConnection은 비표준 메서드를 막으므로(ProtocolException), 그 경우에만
    // method 필드를 직접 세팅해 같은 코드가 다른 JVM에서도(테스트 포함) 동작하게 한다 -- 안드로이드
    // 에선 예외가 안 나 이 폴백은 실행되지 않는다(무해).
    private fun setMethod(conn: HttpURLConnection, method: String) {
        try {
            conn.requestMethod = method
        } catch (e: java.net.ProtocolException) {
            runCatching {
                val f = java.net.HttpURLConnection::class.java.getDeclaredField("method")
                f.isAccessible = true
                f.set(conn, method)
                // HttpsURLConnection은 실제 요청을 보내는 내부 delegate를 감싸므로 거기에도 반영.
                runCatching {
                    val df = conn.javaClass.getDeclaredField("delegate")
                    df.isAccessible = true
                    (df.get(conn) as? java.net.HttpURLConnection)?.let { inner ->
                        val mf = java.net.HttpURLConnection::class.java.getDeclaredField("method")
                        mf.isAccessible = true
                        mf.set(inner, method)
                    }
                }
            }.getOrElse { throw e }
        }
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
        var contentLength: Long? = null
        var lastModified: Long? = null
        var event = parser.eventType
        var lastText = ""
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name.substringAfter(':').lowercase()) {
                    "response" -> { inResponse = true; href = null; isCollection = false; contentLength = null; lastModified = null }
                    "collection" -> if (inResponse) isCollection = true
                    else -> {}
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> lastText = parser.text ?: ""
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name.substringAfter(':').lowercase()) {
                    "href" -> if (inResponse && href == null) href = lastText.trim()
                    "getcontentlength" -> if (inResponse) contentLength = lastText.trim().toLongOrNull()
                    "getlastmodified" -> if (inResponse) lastModified = parseHttpDate(lastText.trim())
                    "response" -> {
                        val h = href
                        if (inResponse && h != null) {
                            val abs = hrefToPath(h)
                            // Skip the collection itself (the request path).
                            if (abs != null && abs.trimEnd('/') != requestPath.trimEnd('/')) {
                                val nm = Uri.decode(abs.trimEnd('/').substringAfterLast('/'))
                                if (nm.isNotEmpty()) {
                                    out += RemoteEntry(
                                        name = nm,
                                        isDirectory = isCollection,
                                        path = if (isCollection) "$abs/".replace("//", "/") else abs,
                                        modified = lastModified,
                                        size = if (isCollection) null else contentLength,
                                    )
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

    // WebDAV dates come as RFC 1123 ("Wed, 08 Aug 2026 23:47:00 GMT").
    private fun parseHttpDate(text: String): Long? = if (text.isBlank()) null else runCatching {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }
            .parse(text)?.time
    }.getOrNull()

    // An href may be an absolute URL or an absolute path; reduce to the path.
    private fun hrefToPath(href: String): String? = runCatching {
        if (href.startsWith("http")) URL(href).path else href
    }.getOrNull()?.let { Uri.decode(it) }

    private fun encodePath(path: String): String =
        path.split("/").joinToString("/") { Uri.encode(it) }

    companion object {
        private const val PROPFIND_BODY =
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
    }
}

/** The playable uri for a WebDAV file: webdav(s) creds ride the userInfo, and
 *  the tls flag rides a query param, which WebDavDataSource reads back. */
fun webDavMediaUri(server: WebDavServer, path: String): Uri {
    val userInfo = encodedUserInfo(server.user, server.pass)
    return Uri.Builder()
        .scheme("webdav")
        .encodedAuthority((userInfo?.let { "$it@" } ?: "") + "${server.host}:${server.port}")
        .path(path)
        .appendQueryParameter("tls", if (server.tls) "1" else "0")
        .apply { if (server.pinnedCertificate.isNotBlank()) appendQueryParameter("cert", server.pinnedCertificate) }
        .build()
}

/** The saved-position key for a WebDAV file: host, port and path, no creds. */
fun webDavPrefKey(server: WebDavServer, path: String): String =
    "webdav://${server.host}:${server.port}$path"
