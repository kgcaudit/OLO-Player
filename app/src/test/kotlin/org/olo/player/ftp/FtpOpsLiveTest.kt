package org.olo.player.ftp

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

/**
 * 실서버 대조(FTP 쓰기 작업): 실제 pyftpdlib 서버를 띄우고 [FtpSession]의 RemoteOps(이름변경·이동·
 * 삭제·재귀삭제·새 폴더)를 그대로 돌려 기기 없이 올바른지 고정한다. 서버 루트는 테스트가 들고 있는
 * 임시 폴더라, FTP로 바꾼 결과를 디스크에서 직접 확인한다(모의가 아니라 실제 FTP 명령 왕복).
 * python3/pyftpdlib가 없으면 건너뛴다.
 */
class FtpOpsLiveTest {

    private lateinit var root: File
    private var server: Process? = null
    private var port: Int = 0
    private val user = "olo"
    private val pass = "olo-pass"
    private lateinit var session: FtpSession

    @Before
    fun setUp() {
        assumeTrue("python3 + pyftpdlib가 없어 건너뜀", pyftpdlibAvailable())
        root = createTempDirectory("olo-ftpops-root").toFile()
        port = freePort()
        server = startServer(freePort())
        session = FtpSession(FtpServer(host = "127.0.0.1", port = port, user = user, pass = pass))
    }

    @After
    fun tearDown() {
        runCatching { if (::session.isInitialized) session.disconnect() }
        server?.let { p -> p.destroy(); if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly() }
        server = null
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun `mkdir creates a folder on the server`() {
        session.mkdir("/새폴더")
        assertTrue(File(root, "새폴더").isDirectory)
    }

    @Test
    fun `rename changes a file name in place`() {
        File(root, "movies").mkdirs()
        File(root, "movies/a.mkv").writeText("x")
        session.rename("/movies/a.mkv", "/movies/b.mkv")
        assertFalse(File(root, "movies/a.mkv").exists())
        assertTrue(File(root, "movies/b.mkv").exists())
    }

    @Test
    fun `rename across folders moves the file`() {
        File(root, "movies").mkdirs()
        File(root, "dest").mkdirs()
        File(root, "movies/a.mkv").writeText("x")
        session.rename("/movies/a.mkv", "/dest/a.mkv")
        assertFalse(File(root, "movies/a.mkv").exists())
        assertTrue(File(root, "dest/a.mkv").exists())
    }

    @Test
    fun `delete removes a file`() {
        File(root, "a.mkv").writeText("x")
        session.delete("/a.mkv", isDir = false)
        assertFalse(File(root, "a.mkv").exists())
    }

    @Test
    fun `delete removes a folder recursively`() {
        File(root, "movies/sub").mkdirs()
        File(root, "movies/a.mkv").writeText("x")
        File(root, "movies/sub/b.mkv").writeText("y")
        session.delete("/movies", isDir = true)
        assertFalse("폴더와 안의 내용이 모두 지워져야 한다", File(root, "movies").exists())
    }

    // 파일작업의 사이드카 동반을 실서버로 밟는다: 폴더 조회 -> 사이드카 계산 -> 영상+사이드카 이동.
    // (호스트 Composable의 move 흐름과 같은 순서. 다른 영상은 따라가지 않아야 한다.)
    @Test
    fun `move bundles the video's sidecars but not other media`() {
        File(root, "movies").mkdirs(); File(root, "dest").mkdirs()
        File(root, "movies/Film.2026.mkv").writeText("v")
        File(root, "movies/Film.2026.ko.srt").writeText("s")
        File(root, "movies/Film.2026.nfo").writeText("n")
        File(root, "movies/Other.mkv").writeText("o")

        val names = session.list("/movies").map { it.name }
        val sidecars = org.olo.player.ui.remoteSidecarNames("Film.2026.mkv", names)
        session.rename("/movies/Film.2026.mkv", "/dest/Film.2026.mkv")
        sidecars.forEach { session.rename("/movies/$it", "/dest/$it") }

        assertTrue(File(root, "dest/Film.2026.mkv").exists())
        assertTrue("자막이 함께 이동", File(root, "dest/Film.2026.ko.srt").exists())
        assertTrue("nfo가 함께 이동", File(root, "dest/Film.2026.nfo").exists())
        assertFalse("다른 영상은 따라가지 않는다", File(root, "dest/Other.mkv").exists())
        assertTrue(File(root, "movies/Other.mkv").exists())
    }

    private fun startServer(pasvLow: Int): Process {
        val script = File(root.parentFile, "olo_ftpops_server_$port.py")
        script.writeText(SERVER_PY)
        val pb = ProcessBuilder(
            "python3", script.absolutePath,
            "127.0.0.1", port.toString(), user, pass, root.absolutePath, "$pasvLow", "${pasvLow + 20}",
        )
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val reader = proc.inputStream.bufferedReader()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (!proc.isAlive) break
            if (reader.readLine()?.trim() == "READY") return proc
        }
        proc.destroyForcibly()
        throw IllegalStateException("FTP 테스트 서버가 뜨지 않았다")
    }

    companion object {
        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        private fun pyftpdlibAvailable(): Boolean = runCatching {
            val p = ProcessBuilder("python3", "-c", "import pyftpdlib").redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

        // 쓰기 권한(elradfmw: 삭제·이름변경·새폴더 포함) 평문 FTP 서버. 준비되면 "READY".
        private val SERVER_PY = """
            import sys
            from pyftpdlib.authorizers import DummyAuthorizer
            from pyftpdlib.handlers import FTPHandler
            from pyftpdlib.servers import FTPServer
            host, port, user, password, root = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5]
            pasv_low, pasv_high = int(sys.argv[6]), int(sys.argv[7])
            auth = DummyAuthorizer()
            auth.add_user(user, password, root, perm="elradfmw")
            handler = FTPHandler
            handler.authorizer = auth
            handler.passive_ports = range(pasv_low, pasv_high + 1)
            handler.masquerade_address = "127.0.0.1"
            server = FTPServer((host, port), handler)
            print("READY", flush=True)
            server.serve_forever()
        """.trimIndent()
    }
}
