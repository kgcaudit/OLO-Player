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
 * 실서버 대조(SMB): 실제 SMB2 서버(impacket)를 띄우고 [SmbDataSource]를 그대로 붙여 접속→읽기→
 * 탐색(절대 오프셋 read)→인증 실패 경로가 기기 없이도 올바른지 고정한다. 이 앱의 SMB는
 * '서명 필수'(smbConfig: withSigningRequired(true)) 구성이라, 서버가 SMB2 서명을 제대로 하는지까지
 * 실제로 밟는다. python3/impacket이 없으면 건너뛴다. SMB3 암호화·ExoPlayer 디코딩은 실기기 몫.
 */
@RunWith(RobolectricTestRunner::class)
class SmbDataSourceLiveTest {

    private lateinit var root: File
    private var server: Process? = null
    private var port: Int = 0
    private val user = "olo"
    private val pass = "olopw"
    private val share = "MEDIA"

    @Before
    fun setUp() {
        assumeTrue("python3 + impacket이 없어 실서버 대조를 건너뜀", impacketAvailable())
        root = createTempDirectory("olo-smb-root").toFile()
        port = freePort()
        server = startServer()
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

    @Test
    fun `reads a file whole, byte-for-byte over a signed SMB2 session`() {
        val expected = contentOf(200_000)
        File(root, "movie.bin").writeBytes(expected)
        val ds = SmbDataSource()
        val length = ds.open(DataSpec(uriFor("/$share/movie.bin")))
        try {
            assertEquals("endOfFile 길이가 파일 크기와 같아야 한다", expected.size.toLong(), length)
            assertArrayEquals(expected, drain(ds))
        } finally {
            ds.close()
        }
    }

    @Test
    fun `seek reads from an absolute file offset`() {
        val expected = contentOf(120_000)
        File(root, "movie.bin").writeBytes(expected)
        val offset = 50_000
        val ds = SmbDataSource()
        val remaining = ds.open(DataSpec.Builder().setUri(uriFor("/$share/movie.bin")).setPosition(offset.toLong()).build())
        try {
            assertEquals((expected.size - offset).toLong(), remaining)
            assertArrayEquals(expected.copyOfRange(offset, expected.size), drain(ds))
        } finally {
            ds.close()
        }
    }

    @Test
    fun `wrong password fails cleanly`() {
        File(root, "movie.bin").writeBytes(contentOf(1_000))
        val badUri = Uri.parse("smb://$user:wrong@127.0.0.1:$port/$share/movie.bin")
        val ds = SmbDataSource()
        try {
            ds.open(DataSpec(badUri))
            fail("틀린 비밀번호인데 open이 성공했다")
        } catch (e: Exception) {
            assertTrue("인증 실패가 재생 오류로 전달돼야 한다: ${e.message}", e.message?.contains("smb") == true)
        } finally {
            ds.close()
        }
    }

    private fun uriFor(path: String): Uri =
        Uri.parse("smb://$user:${Uri.encode(pass)}@127.0.0.1:$port$path")

    private fun drain(ds: SmbDataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) break
            if (n > 0) out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun startServer(): Process {
        val script = File(root.parentFile, "olo_smb_server_$port.py")
        script.writeText(SERVER_PY)
        val pb = ProcessBuilder(
            "python3", script.absolutePath,
            "127.0.0.1", port.toString(), user, pass, root.absolutePath, share,
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
        throw IllegalStateException("SMB 테스트 서버가 뜨지 않았다")
    }

    companion object {
        private fun contentOf(size: Int): ByteArray =
            ByteArray(size) { i -> ((i.toLong() * 31 + 7) % 251).toByte() }

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        private fun impacketAvailable(): Boolean = runCatching {
            val p = ProcessBuilder("python3", "-c", "from impacket.smbserver import SimpleSMBServer")
                .redirectErrorStream(true).start()
            p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

        // 최소 SMB2 서버(impacket). NTLM 자격증명 1개와 디스크 공유 1개를 두고 SMB2(서명 포함)로
        // 응답한다. 생성자가 소켓을 바인드하므로 start() 직전에 "READY"를 찍는다.
        private val SERVER_PY = """
            import sys, logging
            from impacket.smbserver import SimpleSMBServer
            from impacket.ntlm import compute_lmhash, compute_nthash
            host, port, user, password, root, share = (
                sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6]
            )
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
