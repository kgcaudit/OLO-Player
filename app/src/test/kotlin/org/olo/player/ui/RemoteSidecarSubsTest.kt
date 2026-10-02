package org.olo.player.ui

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.olo.player.ftp.RemoteEntry
import org.robolectric.RobolectricTestRunner

/**
 * 네트워크 폴더 목록에서 영상의 사이드카 자막을 골라내는 remoteSubsFor 검증. 서버 자막이
 * 재생 시 안 붙던 회귀를 막는다: 같은 이름(또는 언어 꼬리가 붙은) 자막만 영상의 재생 URI와
 * 같은 규칙으로 실려야 하고, 다른 작품·비자막 파일·폴더는 걸러져야 한다.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteSidecarSubsTest {

    private fun file(name: String) = RemoteEntry(name, false, "/m/$name")
    private fun dir(name: String) = RemoteEntry(name, true, "/m/$name")

    @Test
    fun `same-named subtitle beside the film is attached with the film's uri scheme`() {
        val entries = listOf(
            file("Dust.Bunny.2025.1080p.mkv"),
            file("Dust.Bunny.2025.1080p.srt"),
            file("Other.Movie.2024.mkv"),
            file("Other.Movie.2024.srt"),
            file("readme.txt"),
            dir("Subs"),
        )
        val subs = remoteSubsFor(entries, "Dust.Bunny.2025.1080p.mkv") { p -> Uri.parse("ftp://host$p") }
        assertEquals(1, subs.size)
        assertEquals("Dust.Bunny.2025.1080p.srt", subs[0].fileName)
        assertEquals("ftp://host/m/Dust.Bunny.2025.1080p.srt", subs[0].uri.toString())
    }

    @Test
    fun `a language-tagged and a SAMI subtitle both attach, a different film does not`() {
        val entries = listOf(
            file("Dust.Bunny.2025.1080p.mkv"),
            file("Dust.Bunny.2025.1080p.ko.srt"),
            file("Dust.Bunny.2025.1080p.smi"),
            file("Something.Else.ko.srt"),
        )
        val subs = remoteSubsFor(entries, "Dust.Bunny.2025.1080p.mkv") { p -> Uri.parse("smb://host$p") }
        val names = subs.map { it.fileName }.toSet()
        assertTrue("언어 꼬리 자막 포함", "Dust.Bunny.2025.1080p.ko.srt" in names)
        assertTrue("SAMI 자막 포함", "Dust.Bunny.2025.1080p.smi" in names)
        assertTrue("다른 작품 자막 제외", "Something.Else.ko.srt" !in names)
        assertEquals(2, subs.size)
    }

    @Test
    fun `no subtitle sibling yields an empty list`() {
        val entries = listOf(file("Dust.Bunny.2025.1080p.mkv"), file("poster.jpg"))
        assertTrue(remoteSubsFor(entries, "Dust.Bunny.2025.1080p.mkv") { p -> Uri.parse("ftp://h$p") }.isEmpty())
    }
}
