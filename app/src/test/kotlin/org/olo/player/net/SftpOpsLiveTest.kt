package org.olo.player.net

import java.io.File
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.filezilla.ftp.sftp.SshHostKey
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 실서버 대조(SFTP 쓰기 작업): 실제 asyncssh 서버를 띄우고 [SftpSession]의 RemoteOps(이름변경·이동·
 * 삭제·재귀삭제·새 폴더)를 돌려 고정한다. 호스트키 핀은 프로젝트 코드(SshHostKey)로 뽑는다.
 * python3/asyncssh가 없으면 건너뛴다.
 */
class SftpOpsLiveTest {

    private lateinit var root: File
    private var server: Process? = null
    private var port: Int = 0
    private lateinit var session: SftpSession
    private val user = "olo"
    private val pass = "olo-pass"

    @Before
    fun setUp() {
        assumeTrue("python3 + asyncssh가 없어 건너뜀", asyncsshAvailable())
        root = createTempDirectory("olo-sftpops-root").toFile()
        port = freePort()
        val pub = File(root.parentFile, "olo-sftpops-host-$port.pub")
        server = startServer(pub)
        val pin = SshHostKey.of(Base64.getDecoder().decode(pub.readText().trim().split(" ")[1])).fingerprint
        pub.delete()
        session = SftpSession(SftpServer(host = "127.0.0.1", port = port, user = user, pass = pass, knownHostKey = pin))
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

    @Test fun `rename changes a name in place`() {
        File(root, "movies").mkdirs(); File(root, "movies/a.mkv").writeText("x")
        session.rename("/movies/a.mkv", "/movies/b.mkv")
        assertFalse(File(root, "movies/a.mkv").exists()); assertTrue(File(root, "movies/b.mkv").exists())
    }

    @Test fun `rename across folders moves`() {
        File(root, "movies").mkdirs(); File(root, "dest").mkdirs(); File(root, "movies/a.mkv").writeText("x")
        session.rename("/movies/a.mkv", "/dest/a.mkv")
        assertFalse(File(root, "movies/a.mkv").exists()); assertTrue(File(root, "dest/a.mkv").exists())
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

    private fun startServer(pub: File): Process {
        val script = File(root.parentFile, "olo_sftpops_server_$port.py")
        script.writeText(SERVER_PY)
        val pb = ProcessBuilder("python3", script.absolutePath, "127.0.0.1", port.toString(), user, pass, root.absolutePath, pub.absolutePath)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val reader = proc.inputStream.bufferedReader()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (!proc.isAlive) break
            if (reader.readLine()?.trim() == "READY") return proc
        }
        proc.destroyForcibly(); throw IllegalStateException("SFTP 테스트 서버가 뜨지 않았다")
    }

    companion object {
        private fun freePort(): Int = ServerSocket(0).use { it.localPort }
        private fun asyncsshAvailable(): Boolean = runCatching {
            val p = ProcessBuilder("python3", "-c", "import asyncssh").redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

        private val SERVER_PY = """
            import asyncio, asyncssh, sys
            host, port, user, password, root, pubpath = (
                sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6])
            class Server(asyncssh.SSHServer):
                def begin_auth(self, username): return True
                def password_auth_supported(self): return True
                def validate_password(self, username, pw): return username == user and pw == password
            async def main():
                key = asyncssh.generate_private_key('ssh-ed25519')
                with open(pubpath, 'wb') as f: f.write(key.export_public_key('openssh'))
                await asyncssh.listen(host, port, server_factory=Server, server_host_keys=[key],
                    sftp_factory=lambda chan: asyncssh.SFTPServer(chan, chroot=root))
                print("READY", flush=True)
                await asyncio.Event().wait()
            asyncio.run(main())
        """.trimIndent()
    }
}
