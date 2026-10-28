package org.olo.player.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 실서버 대조(WebDAV): 실제 HTTP 서버를 띄우고 [WebDavDataSource]를 그대로 붙여 접속→읽기→
 * 탐색(Range)→인증 실패 경로가 기기 없이도 올바른지 고정한다. WebDAV 재생은 Range GET + Basic
 * 인증이므로, (1) 전체 GET, (2) 206 부분 응답 탐색, (3) 서버가 Range를 무시하고 200을 줄 때의
 * 앞부분 건너뛰기 보정, (4) 401 인증 실패를 실제 서버로 밟는다. python3만 있으면 되고(표준
 * 라이브러리 HTTP 서버) 없으면 건너뛴다. TLS 핀·ExoPlayer 디코딩은 실기기/실서버 몫.
 */
@RunWith(RobolectricTestRunner::class)
class WebDavDataSourceLiveTest {

    private lateinit var root: File
    private var server: Process? = null
    private var port: Int = 0
    private val user = "olo"
    private val pass = "pw@/ord"  // 특수문자: URI userInfo 디코딩 경로도 밟는다

    @Before
    fun setUp() {
        assumeTrue("python3가 없어 실서버 대조를 건너뜀", python3Available())
        root = createTempDirectory("olo-webdav-root").toFile()
        port = freePort()
        server = startServer(ignoreRange = false)
    }

    @After
    fun tearDown() {
        stopServer()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun `reads a file whole, byte-for-byte`() {
        val expected = contentOf(200_000)
        File(root, "movie.bin").writeBytes(expected)
        val ds = WebDavDataSource()
        val length = ds.open(DataSpec(uriFor("/movie.bin")))
        try {
            assertEquals("Content-Length가 파일 크기와 같아야 한다", expected.size.toLong(), length)
            assertArrayEquals(expected, drain(ds))
        } finally {
            ds.close()
        }
    }

    @Test
    fun `seek is served by a 206 partial response`() {
        val expected = contentOf(120_000)
        File(root, "movie.bin").writeBytes(expected)
        val offset = 50_000
        val ds = WebDavDataSource()
        val remaining = ds.open(DataSpec.Builder().setUri(uriFor("/movie.bin")).setPosition(offset.toLong()).build())
        try {
            assertEquals((expected.size - offset).toLong(), remaining)
            assertArrayEquals(expected.copyOfRange(offset, expected.size), drain(ds))
        } finally {
            ds.close()
        }
    }

    // 서버가 Range를 무시하고 200(전체)을 줄 때: DataSource가 앞 position 바이트를 버려
    // media3가 기대하는 시작 지점에 맞춰야 한다. 그 보정 경로를 실제로 밟는다.
    @Test
    fun `seek still lands right when the server ignores Range`() {
        val expected = contentOf(120_000)
        File(root, "movie.bin").writeBytes(expected)
        stopServer()
        server = startServer(ignoreRange = true)
        val offset = 40_000
        val ds = WebDavDataSource()
        ds.open(DataSpec.Builder().setUri(uriFor("/movie.bin")).setPosition(offset.toLong()).build())
        try {
            assertArrayEquals(expected.copyOfRange(offset, expected.size), drain(ds))
        } finally {
            ds.close()
        }
    }

    @Test
    fun `wrong password fails with 401`() {
        File(root, "movie.bin").writeBytes(contentOf(1_000))
        val badUri = Uri.parse("webdav://$user:${Uri.encode("nope")}@127.0.0.1:$port/movie.bin?tls=0")
        val ds = WebDavDataSource()
        try {
            ds.open(DataSpec(badUri))
            fail("틀린 비밀번호인데 open이 성공했다")
        } catch (e: Exception) {
            assertTrue("인증 실패가 재생 오류로 전달돼야 한다: ${e.message}", e.message?.contains("WebDAV") == true)
        } finally {
            ds.close()
        }
    }

    private fun uriFor(path: String): Uri =
        Uri.parse("webdav://$user:${Uri.encode(pass)}@127.0.0.1:$port$path?tls=0")

    private fun drain(ds: WebDavDataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) break
            if (n > 0) out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun stopServer() {
        server?.let { p ->
            p.destroy()
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        server = null
    }

    private fun startServer(ignoreRange: Boolean): Process {
        val script = File(root.parentFile, "olo_webdav_server_$port.py")
        script.writeText(SERVER_PY)
        val pb = ProcessBuilder(
            "python3", script.absolutePath,
            "127.0.0.1", port.toString(), user, pass, root.absolutePath, if (ignoreRange) "1" else "0",
        )
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val reader = proc.inputStream.bufferedReader()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (!proc.isAlive) break
            val line = reader.readLine() ?: break
            if (line.trim() == "READY") return proc
        }
        proc.destroyForcibly()
        throw IllegalStateException("WebDAV 테스트 서버가 뜨지 않았다")
    }

    companion object {
        private fun contentOf(size: Int): ByteArray =
            ByteArray(size) { i -> ((i.toLong() * 31 + 7) % 251).toByte() }

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        private fun python3Available(): Boolean = runCatching {
            val p = ProcessBuilder("python3", "-c", "import http.server").redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

        // 최소 Range 지원 HTTP 서버(표준 라이브러리). Basic 인증을 검사하고, Range 요청에
        // 206(부분)로 응답한다. ignore_range=1이면 Range를 무시하고 200(전체)을 돌려줘,
        // DataSource의 '서버가 Range를 무시한 경우' 보정 경로를 시험한다. 준비되면 "READY".
        private val SERVER_PY = """
            import base64, os, sys
            from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

            host, port, user, password, root = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5]
            ignore_range = sys.argv[6] == "1"
            expected_auth = "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()

            class Handler(BaseHTTPRequestHandler):
                def log_message(self, *a): pass
                def do_GET(self):
                    if self.headers.get("Authorization") != expected_auth:
                        self.send_response(401); self.send_header("Content-Length", "0"); self.end_headers(); return
                    path = os.path.join(root, self.path.lstrip("/").split("?")[0])
                    if not os.path.isfile(path):
                        self.send_response(404); self.send_header("Content-Length", "0"); self.end_headers(); return
                    data = open(path, "rb").read()
                    total = len(data)
                    rng = self.headers.get("Range")
                    if rng and not ignore_range:
                        spec = rng.split("=")[1]
                        start_s, end_s = spec.split("-")
                        start = int(start_s)
                        end = int(end_s) if end_s else total - 1
                        chunk = data[start:end + 1]
                        self.send_response(206)
                        self.send_header("Content-Range", f"bytes {start}-{end}/{total}")
                        self.send_header("Content-Length", str(len(chunk)))
                        self.end_headers()
                        self.wfile.write(chunk)
                    else:
                        self.send_response(200)
                        self.send_header("Content-Length", str(total))
                        self.end_headers()
                        self.wfile.write(data)

            httpd = ThreadingHTTPServer((host, port), Handler)
            print("READY", flush=True)
            httpd.serve_forever()
        """.trimIndent()
    }
}
