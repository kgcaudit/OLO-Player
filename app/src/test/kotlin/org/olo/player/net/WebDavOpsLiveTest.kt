package org.olo.player.net

import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 실서버 대조(WebDAV 쓰기 작업): 실제 HTTP 서버(MKCOL/MOVE/COPY/DELETE 처리)에 [WebDavSession]의
 * RemoteOps를 돌려 고정한다. WebDAV만 네이티브 COPY가 있어 복사도 확인한다. python3가 없으면 건너뛴다.
 */
@RunWith(RobolectricTestRunner::class)
class WebDavOpsLiveTest {

    private lateinit var root: File
    private var server: Process? = null
    private var port: Int = 0
    private lateinit var session: WebDavSession
    private val user = "olo"; private val pass = "pw@x"

    @Before
    fun setUp() {
        assumeTrue("python3가 없어 건너뜀", python3Available())
        root = createTempDirectory("olo-davops-root").toFile()
        port = freePort()
        server = startServer()
        session = WebDavSession(WebDavServer(host = "127.0.0.1", port = port, user = user, pass = pass, tls = false))
    }

    @After
    fun tearDown() {
        server?.let { p -> p.destroy(); if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly() }
        server = null
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test fun `mkcol creates a folder`() {
        session.mkdir("/새폴더")
        assertTrue(File(root, "새폴더").isDirectory)
    }

    @Test fun `move renames a file in place`() {
        File(root, "movies").mkdirs(); File(root, "movies/a.mkv").writeText("x")
        session.rename("/movies/a.mkv", "/movies/b.mkv")
        assertFalse(File(root, "movies/a.mkv").exists()); assertTrue(File(root, "movies/b.mkv").exists())
    }

    @Test fun `move across folders moves the file`() {
        File(root, "movies").mkdirs(); File(root, "dest").mkdirs(); File(root, "movies/a.mkv").writeText("x")
        session.rename("/movies/a.mkv", "/dest/a.mkv")
        assertFalse(File(root, "movies/a.mkv").exists()); assertTrue(File(root, "dest/a.mkv").exists())
    }

    @Test fun `copy keeps the original`() {
        File(root, "a.mkv").writeText("hello")
        session.copy("/a.mkv", "/a_copy.mkv")
        assertTrue("원본 유지", File(root, "a.mkv").exists())
        assertTrue("사본 생성", File(root, "a_copy.mkv").exists())
    }

    @Test fun `delete removes a file`() {
        File(root, "a.mkv").writeText("x")
        session.delete("/a.mkv", isDir = false)
        assertFalse(File(root, "a.mkv").exists())
    }

    @Test fun `delete removes a folder recursively`() {
        File(root, "movies/sub").mkdirs(); File(root, "movies/a.mkv").writeText("x"); File(root, "movies/sub/b.mkv").writeText("y")
        session.delete("/movies", isDir = true)
        assertFalse(File(root, "movies").exists())
    }

    private fun startServer(): Process {
        val script = File(root.parentFile, "olo_davops_server_$port.py")
        script.writeText(SERVER_PY)
        val pb = ProcessBuilder("python3", script.absolutePath, "127.0.0.1", port.toString(), user, pass, root.absolutePath)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val reader = proc.inputStream.bufferedReader()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (!proc.isAlive) break
            if (reader.readLine()?.trim() == "READY") return proc
        }
        proc.destroyForcibly(); throw IllegalStateException("WebDAV 테스트 서버가 뜨지 않았다")
    }

    companion object {
        private fun freePort(): Int = ServerSocket(0).use { it.localPort }
        private fun python3Available(): Boolean = runCatching {
            val p = ProcessBuilder("python3", "-c", "import http.server").redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

        // 최소 WebDAV 서버: Basic 인증 + MKCOL/DELETE/MOVE/COPY. Destination 헤더의 경로로 이동/복사.
        private val SERVER_PY = """
            import base64, os, shutil, sys
            from urllib.parse import urlparse, unquote
            from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
            host, port, user, password, root = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5]
            expected = "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()

            class H(BaseHTTPRequestHandler):
                def log_message(self, *a): pass
                def _auth(self):
                    if self.headers.get("Authorization") != expected:
                        self.send_response(401); self.send_header("Content-Length","0"); self.end_headers(); return False
                    return True
                def _local(self, p): return os.path.join(root, unquote(p).lstrip("/"))
                def _dest(self):
                    d = self.headers.get("Destination")
                    return self._local(urlparse(d).path)
                def _done(self, code):
                    self.send_response(code); self.send_header("Content-Length","0"); self.end_headers()
                def do_MKCOL(self):
                    if not self._auth(): return
                    try: os.makedirs(self._local(self.path), exist_ok=False); self._done(201)
                    except Exception: self._done(409)
                def do_DELETE(self):
                    if not self._auth(): return
                    p = self._local(self.path)
                    try:
                        if os.path.isdir(p): shutil.rmtree(p)
                        elif os.path.exists(p): os.remove(p)
                        else: return self._done(404)
                        self._done(204)
                    except Exception: self._done(409)
                def do_MOVE(self):
                    if not self._auth(): return
                    try: shutil.move(self._local(self.path), self._dest()); self._done(201)
                    except Exception: self._done(409)
                def do_COPY(self):
                    if not self._auth(): return
                    src = self._local(self.path); dst = self._dest()
                    try:
                        if os.path.isdir(src): shutil.copytree(src, dst)
                        else: shutil.copy2(src, dst)
                        self._done(201)
                    except Exception: self._done(409)

            httpd = ThreadingHTTPServer((host, port), H)
            print("READY", flush=True)
            httpd.serve_forever()
        """.trimIndent()
    }
}
