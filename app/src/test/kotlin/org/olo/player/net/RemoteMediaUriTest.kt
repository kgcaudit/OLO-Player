package org.olo.player.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 재생 URI 조립 고정: SFTP·SMB·WebDAV의 자격증명(userInfo)을 한 헬퍼([encodedUserInfo])로 모은 뒤,
 * 세 프로토콜 모두 host·port는 그대로, user/pass에 `@`·`:`·`/`·공백이 섞여도 반드시 퍼센트
 * 인코딩돼 authority가 깨지지 않는지 확인한다. 리팩터링(중복 제거)이 동작을 바꾸지 않았음을 고정.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteMediaUriTest {

    // 특수문자가 든 비밀번호: 인코딩이 없으면 '@'가 authority 구분자로 오인돼 host가 틀어진다.
    private val trickyPass = "p@ss:w/ord"

    @Test fun `sftp uri keeps host and encodes credentials`() {
        val uri = sftpMediaUri(SftpServer(host = "nas.local", port = 2222, user = "kim", pass = trickyPass), "/영화/movie.mkv")
        assertEquals("sftp", uri.scheme)
        assertEquals("nas.local", uri.host)
        assertEquals(2222, uri.port)
        assertEquals("kim:$trickyPass", uri.userInfo) // Uri가 디코드해 돌려주므로 원문과 같아야
        assertEquals("/영화/movie.mkv", uri.path)
    }

    @Test fun `smb uri keeps host and encodes credentials`() {
        val uri = smbMediaUri(SmbServer(host = "192.168.0.9", port = 0, user = "kim", pass = trickyPass, share = "media"), "/드라마/ep1.mp4")
        assertEquals("smb", uri.scheme)
        assertEquals("192.168.0.9", uri.host)
        assertEquals("kim:$trickyPass", uri.userInfo)
    }

    @Test fun `webdav uri keeps host and encodes credentials`() {
        val uri = webDavMediaUri(WebDavServer(host = "dav.example.com", port = 8080, user = "kim", pass = trickyPass), "/음악/song.flac")
        assertEquals("webdav", uri.scheme)
        assertEquals("dav.example.com", uri.host)
        assertEquals(8080, uri.port)
        assertEquals("kim:$trickyPass", uri.userInfo)
    }

    @Test fun `empty user yields no userInfo`() {
        val uri = sftpMediaUri(SftpServer(host = "h", port = 22, user = "", pass = "x"), "/a")
        assertNull("사용자명이 없으면 userInfo를 붙이지 않는다", uri.userInfo)
    }

    @Test fun `user without password carries just the user`() {
        val uri = sftpMediaUri(SftpServer(host = "h", port = 22, user = "kim", pass = ""), "/a")
        assertEquals("kim", uri.userInfo)
    }
}
