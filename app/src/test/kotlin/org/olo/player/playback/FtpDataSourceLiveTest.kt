package org.olo.player.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
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
 * 실서버 대조: 실제 pyftpdlib FTP 서버를 띄우고 [FtpDataSource]를 그대로 붙여 접속→읽기→
 * 탐색(REST)→오류 경로가 기기 없이도 올바른지 고정한다. 모의 서버는 우리 코드와 늘 '합의'하지만
 * 실서버는 그러지 않으므로, 재생 바이트 전송 계층은 이렇게 실서버로 증명한다.
 *
 * 파이썬(또는 pyftpdlib)이 없는 체크아웃에서는 스스로 건너뛴다(프로젝트의 서버 하니스와 같은 약속).
 * ExoPlayer 디코딩·자막 렌더는 실기기 몫이라 여기서 다루지 않는다 -- 여기서 증명하는 것은
 * "서버에서 바이트를 올바른 오프셋으로 정확히 읽어 온다"까지다.
 */
@RunWith(RobolectricTestRunner::class)
class FtpDataSourceLiveTest {

    private lateinit var root: File
    private var server: Process? = null
    private var port: Int = 0
    private val user = "olo"
    private val pass = "olo-pass"

    @Before
    fun setUp() {
        assumeTrue("python3 + pyftpdlib가 없어 실서버 대조를 건너뜀", pyftpdlibAvailable())
        root = createTempDirectory("olo-ftp-root").toFile()
        port = freePort()
        val pasvLow = freePort()
        server = startServer(pasvLow)
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

    // ── 접속 + 전체 읽기: 서버 파일 바이트와 완전히 일치해야 한다. ──
    @Test
    fun `reads a file whole, byte-for-byte`() {
        val expected = contentOf(200_000) // 여러 번 read()로 나뉘도록 충분히 크게
        File(root, "movie.bin").writeBytes(expected)

        val ds = FtpDataSource()
        val length = ds.open(DataSpec(uriFor("/movie.bin")))
        try {
            assertEquals("SIZE로 받은 길이가 파일 크기와 같아야 한다", expected.size.toLong(), length)
            assertArrayEquals("서버에서 읽은 전체 바이트가 원본과 같아야 한다", expected, drain(ds))
        } finally {
            ds.close()
        }
    }

    // ── 탐색(스크러빙): media3는 오프셋에서 다시 연다(REST). 그 지점부터 끝까지 일치해야 한다. ──
    @Test
    fun `seek reopens at a byte offset via REST`() {
        val expected = contentOf(120_000)
        File(root, "movie.bin").writeBytes(expected)
        val offset = 50_000

        val ds = FtpDataSource()
        val remaining = ds.open(DataSpec.Builder().setUri(uriFor("/movie.bin")).setPosition(offset.toLong()).build())
        try {
            assertEquals("남은 길이는 size - offset", (expected.size - offset).toLong(), remaining)
            assertArrayEquals(
                "오프셋부터 끝까지가 원본의 같은 구간과 일치해야 한다",
                expected.copyOfRange(offset, expected.size),
                drain(ds),
            )
        } finally {
            ds.close()
        }
    }

    // ── 자격증명은 URI userInfo로 전달: 틀린 비밀번호는 깔끔히 실패(연결 누수 없이). ──
    @Test
    fun `wrong password fails cleanly`() {
        File(root, "movie.bin").writeBytes(contentOf(1_000))
        val badUri = Uri.parse("ftp://$user:wrong@127.0.0.1:$port/movie.bin")
        val ds = FtpDataSource()
        try {
            ds.open(DataSpec(badUri))
            fail("틀린 비밀번호인데 open이 성공했다")
        } catch (e: Exception) {
            assertTrue("로그인 실패가 재생 오류로 전달돼야 한다: ${e.message}", e.message?.contains("ftp") == true)
        } finally {
            ds.close() // 두 번 닫아도(실패 후) 안전해야 한다 -- 누수 방지 경로.
        }
    }

    // ── 서버가 꺼진 포트: 접속 자체가 실패해도 예외로 깔끔히 떨어져야 한다(소켓 누수 방지 catch). ──
    @Test
    fun `connecting to a dead port throws instead of hanging`() {
        val dead = freePort() // 아무도 듣지 않는 포트
        val ds = FtpDataSource()
        try {
            ds.open(DataSpec(Uri.parse("ftp://$user:$pass@127.0.0.1:$dead/movie.bin")))
            fail("죽은 포트인데 open이 성공했다")
        } catch (e: Exception) {
            assertTrue("접속 실패가 예외로 전달돼야 한다", e.message?.contains("ftp") == true)
        } finally {
            ds.close()
        }
    }

    private fun uriFor(path: String) = Uri.parse("ftp://$user:$pass@127.0.0.1:$port$path")

    /** open 뒤 EOF까지 모두 읽어 모은다(media3 read 계약대로 RESULT_END_OF_INPUT에서 종료). */
    private fun drain(ds: FtpDataSource): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) break
            if (n > 0) out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun startServer(pasvLow: Int): Process {
        val script = File(root.parentFile, "olo_ftp_server_${port}.py")
        script.writeText(SERVER_PY)
        val pb = ProcessBuilder(
            "python3", script.absolutePath,
            "127.0.0.1", port.toString(), user, pass, root.absolutePath,
            "$pasvLow", "${pasvLow + 20}",
        )
        pb.redirectErrorStream(true)
        val proc = pb.start()
        // "READY" 한 줄을 기다린다(sleep 대신 준비 신호로 동기화).
        val reader = proc.inputStream.bufferedReader()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (!proc.isAlive) break
            val line = reader.readLine() ?: break
            if (line.trim() == "READY") return proc
        }
        proc.destroyForcibly()
        throw IllegalStateException("FTP 테스트 서버가 뜨지 않았다")
    }

    companion object {
        /** 위치로 결정되는 바이트(i*31+7 mod 251) -- 오프셋이 어긋나면 우연히 일치하지 않는다. */
        private fun contentOf(size: Int): ByteArray =
            ByteArray(size) { i -> ((i.toLong() * 31 + 7) % 251).toByte() }

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        private fun pyftpdlibAvailable(): Boolean = runCatching {
            val p = ProcessBuilder("python3", "-c", "import pyftpdlib").redirectErrorStream(true).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

        // 최소 평문 FTP 서버(pyftpdlib). 인자: host port user pass root pasvLow pasvHigh.
        // 준비되면 stdout에 "READY"를 찍어 테스트가 sleep 없이 동기화한다.
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
