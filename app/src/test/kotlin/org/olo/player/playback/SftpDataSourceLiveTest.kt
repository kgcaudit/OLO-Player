package org.olo.player.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.filezilla.ftp.sftp.SshHostKey
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
 * 실서버 대조(SFTP): 실제 asyncssh SSH/SFTP 서버를 띄우고 [SftpDataSource]를 그대로 붙여
 * 접속→읽기→탐색(skip)→호스트키 검증 경로가 기기 없이도 올바른지 고정한다.
 *
 * 특히 이 앱은 호스트키 핀(SHA256 지문)을 URI의 hk 파라미터로 받아 JSch로 검증하고, 핀이
 * 없거나 어긋나면 비밀번호를 보내기 전에 연결을 거부한다(검증 우회 금지, CLAUDE.md). 그
 * 판정을 실서버로 실제로 밟는다. 핀은 프로젝트 코드([SshHostKey.of])로 뽑아 형식이 드리프트할
 * 여지를 없앴다. python3/asyncssh가 없으면 스스로 건너뛴다. ExoPlayer 디코딩·자막은 실기기 몫.
 */
@RunWith(RobolectricTestRunner::class)
class SftpDataSourceLiveTest {

    private lateinit var root: File
    private var server: Process? = null
    private var port: Int = 0
    private lateinit var pin: String
    private val user = "olo"
    private val pass = "olo-pass:9/@"  // 특수문자 포함: userInfo 디코딩 경로도 함께 밟는다

    @Before
    fun setUp() {
        assumeTrue("python3 + asyncssh가 없어 실서버 대조를 건너뜀", asyncsshAvailable())
        root = createTempDirectory("olo-sftp-root").toFile()
        port = freePort()
        val pub = File(root.parentFile, "olo-sftp-host-$port.pub")
        server = startServer(pub)
        // 서버가 제시하는 호스트키의 지문을 프로젝트 코드 그대로 계산해 핀으로 쓴다.
        val blob = Base64.getDecoder().decode(pub.readText().trim().split(" ")[1])
        pin = SshHostKey.of(blob).fingerprint
        pub.delete()
    }

    @After
    fun tearDown() {
        server?.let { p ->
            p.destroy()
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        server = null
        if (::root.isInitialized) root.deleteRecursively()
    }

    // ── 올바른 핀으로 전체 읽기: 서버 파일과 바이트 완전 일치. ──
    @Test
    fun `reads a file whole, byte-for-byte`() {
        val expected = contentOf(200_000)
        File(root, "movie.bin").writeBytes(expected)
        val ds = SftpDataSource()
        val length = ds.open(DataSpec(uriFor("/movie.bin", pin)))
        try {
            assertEquals("lstat 길이가 파일 크기와 같아야 한다", expected.size.toLong(), length)
            assertArrayEquals(expected, drain(ds))
        } finally {
            ds.close()
        }
    }

    // ── 탐색(스크러빙): 오프셋부터 끝까지 원본과 일치. ──
    @Test
    fun `seek starts the transfer at a byte offset`() {
        val expected = contentOf(120_000)
        File(root, "movie.bin").writeBytes(expected)
        val offset = 50_000
        val ds = SftpDataSource()
        val remaining = ds.open(DataSpec.Builder().setUri(uriFor("/movie.bin", pin)).setPosition(offset.toLong()).build())
        try {
            assertEquals((expected.size - offset).toLong(), remaining)
            assertArrayEquals(expected.copyOfRange(offset, expected.size), drain(ds))
        } finally {
            ds.close()
        }
    }

    // ── 어긋난 핀: 비밀번호를 보내기 전에 거부돼야 한다(MITM 방지). ──
    @Test
    fun `a wrong host-key pin is refused`() {
        File(root, "movie.bin").writeBytes(contentOf(1_000))
        val wrongPin = "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val ds = SftpDataSource()
        try {
            ds.open(DataSpec(uriFor("/movie.bin", wrongPin)))
            fail("호스트키가 어긋났는데 open이 성공했다")
        } catch (e: Exception) {
            assertTrue("호스트키 불일치가 재생 오류로 전달돼야 한다: ${e.message}", e.message?.contains("sftp") == true)
        } finally {
            ds.close()
        }
    }

    // ── 핀이 없으면: 아무 호스트키나 수용하지 않고 연결을 거부한다(검증 우회 금지). ──
    @Test
    fun `an unpinned connection is refused`() {
        File(root, "movie.bin").writeBytes(contentOf(1_000))
        val ds = SftpDataSource()
        try {
            ds.open(DataSpec(Uri.parse("sftp://$user:${Uri.encode(pass)}@127.0.0.1:$port/movie.bin")))
            fail("핀이 없는데 open이 성공했다")
        } catch (e: Exception) {
            assertTrue("핀 없는 연결은 거부돼야 한다: ${e.message}", e.message?.contains("sftp") == true)
        } finally {
            ds.close()
        }
    }

    private fun uriFor(path: String, hostKeyPin: String): Uri =
        Uri.parse("sftp://$user:${Uri.encode(pass)}@127.0.0.1:$port$path?hk=${Uri.encode(hostKeyPin)}")

    private fun drain(ds: SftpDataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) break
            if (n > 0) out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun startServer(pub: File): Process {
        val script = File(root.parentFile, "olo_sftp_server_$port.py")
        script.writeText(SERVER_PY)
        val pb = ProcessBuilder(
            "python3", script.absolutePath,
            "127.0.0.1", port.toString(), user, pass, root.absolutePath, pub.absolutePath,
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
        throw IllegalStateException("SFTP 테스트 서버가 뜨지 않았다")
    }

    companion object {
        private fun contentOf(size: Int): ByteArray =
            ByteArray(size) { i -> ((i.toLong() * 31 + 7) % 251).toByte() }

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        private fun asyncsshAvailable(): Boolean = runCatching {
            val p = ProcessBuilder("python3", "-c", "import asyncssh").redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

        // 최소 SFTP 서버(asyncssh): ed25519 호스트키를 생성해 공개키를 파일로 쓰고, 비밀번호
        // 인증으로 chroot 루트를 제공한다. 준비되면 stdout에 "READY". 인자: host port user pass root pubpath.
        private val SERVER_PY = """
            import asyncio, asyncssh, sys
            host, port, user, password, root, pubpath = (
                sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6]
            )

            class Server(asyncssh.SSHServer):
                def begin_auth(self, username): return True
                def password_auth_supported(self): return True
                def validate_password(self, username, pw): return username == user and pw == password

            async def main():
                key = asyncssh.generate_private_key('ssh-ed25519')
                with open(pubpath, 'wb') as f:
                    f.write(key.export_public_key('openssh'))
                await asyncssh.listen(
                    host, port, server_factory=Server, server_host_keys=[key],
                    sftp_factory=lambda chan: asyncssh.SFTPServer(chan, chroot=root),
                )
                print("READY", flush=True)
                await asyncio.Event().wait()

            asyncio.run(main())
        """.trimIndent()
    }
}
