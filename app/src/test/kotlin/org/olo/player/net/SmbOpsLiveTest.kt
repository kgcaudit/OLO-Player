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

/**
 * 실서버 대조(SMB 쓰기 작업): 실제 SMB2 서버(impacket)에 [SmbSession]의 RemoteOps를 돌려 고정한다.
 * 서명 필수 구성 그대로 쓴다. python3/impacket이 없으면 건너뛴다.
 */
class SmbOpsLiveTest {

    private lateinit var root: File
    private var server: Process? = null
    private var port: Int = 0
    private lateinit var session: SmbSession
    private val user = "olo"; private val pass = "olopw"; private val share = "MEDIA"

    @Before
    fun setUp() {
        assumeTrue("python3 + impacket이 없어 건너뜀", impacketAvailable())
        root = createTempDirectory("olo-smbops-root").toFile()
        port = freePort()
        server = startServer()
        session = SmbSession(SmbServer(host = "127.0.0.1", port = port, user = user, pass = pass, share = share))
    }

    @After
    fun tearDown() {
        runCatching { if (::session.isInitialized) session.disconnect() }
        server?.let { p -> p.destroy(); if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly() }
        server = null
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test fun `mkdir creates a folder`() {
        session.mkdir("/새폴더")
        assertTrue(File(root, "새폴더").isDirectory)
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

    @Test fun `rename changes a file name in place`() {
        File(root, "movies").mkdirs(); File(root, "movies/a.mkv").writeText("x")
        session.rename("/movies/a.mkv", "/movies/b.mkv")
        assertFalse(File(root, "movies/a.mkv").exists()); assertTrue(File(root, "movies/b.mkv").exists())
    }

    @Test fun `rename across folders moves the file`() {
        File(root, "movies").mkdirs(); File(root, "dest").mkdirs(); File(root, "movies/a.mkv").writeText("x")
        session.rename("/movies/a.mkv", "/dest/a.mkv")
        assertFalse(File(root, "movies/a.mkv").exists()); assertTrue(File(root, "dest/a.mkv").exists())
    }

    private fun startServer(): Process {
        val script = File(root.parentFile, "olo_smbops_server_$port.py")
        script.writeText(SERVER_PY)
        val pb = ProcessBuilder("python3", script.absolutePath, "127.0.0.1", port.toString(), user, pass, root.absolutePath, share)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val reader = proc.inputStream.bufferedReader()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (!proc.isAlive) break
            if (reader.readLine()?.trim() == "READY") return proc
        }
        proc.destroyForcibly(); throw IllegalStateException("SMB 테스트 서버가 뜨지 않았다")
    }

    companion object {
        private fun freePort(): Int = ServerSocket(0).use { it.localPort }
        private fun impacketAvailable(): Boolean = runCatching {
            val p = ProcessBuilder("python3", "-c", "from impacket.smbserver import SimpleSMBServer").redirectErrorStream(true).start()
            p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

        private val SERVER_PY = """
            import sys, logging
            from impacket.smbserver import SimpleSMBServer
            from impacket.ntlm import compute_lmhash, compute_nthash
            host, port, user, password, root, share = (
                sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6])
            logging.getLogger('impacket').setLevel(logging.CRITICAL)
            srv = SimpleSMBServer(listenAddress=host, listenPort=port)
            srv.addShare(share, root, 'olo media')
            srv.setSMB2Support(True)
            srv.addCredential(user, 0, compute_lmhash(password), compute_nthash(password))
            print("READY", flush=True)
            srv.start()
        """.trimIndent()
    }
}
