package org.olo.player.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.olo.player.art.RemoteImage
import org.olo.player.ftp.RemoteEntry
import org.robolectric.RobolectricTestRunner

/**
 * 폴더 분류 영구 캐시 왕복 고정. 네트워크 재방문 때 LIST 없이 같은 판별을 되살리는 게 목적이므로,
 * 디스크에 넣었다 꺼낸 분류가 그대로인지(특히 단일영화의 재생대상·사이드카·자막, 그리고 nfo는
 * 되살리지 않음)와 자격증명 없는 경로만 저장해 읽을 때 imageUriFor로 URI를 다시 만드는지 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
class FolderClassStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val imageUriFor: (String) -> Uri? = { path -> Uri.parse("ftp://nas/$path") }

    @Before fun clean() {
        context.getSharedPreferences("olo_folder_class", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `single-film media round-trips with play, subs, sidecar art and no nfo`() {
        val probe = FolderProbe.Media(
            posterName = "메이데이(2026)",
            posterNameAlt = "Mayday.2026.mkv",
            art = RemoteImage(Uri.parse("ftp://nas//MOVIE/메이데이/poster.jpg")), // 저장 안 됨(경로로만)
            nfo = { "should-not-persist" },
            play = RemoteEntry("Mayday.2026.mkv", isDirectory = false, path = "/MOVIE/메이데이/Mayday.2026.mkv"),
            count = 1,
            subs = listOf(RemoteEntry("Mayday.ko.srt", false, "/MOVIE/메이데이/Mayday.ko.srt")),
            sidecarPath = "/MOVIE/메이데이/poster.jpg",
        )
        FolderClassStore.put(context, "k1", probe)
        val got = FolderClassStore.get(context, "k1", imageUriFor) as FolderProbe.Media

        assertEquals("메이데이(2026)", got.posterName)
        assertEquals("Mayday.2026.mkv", got.posterNameAlt)
        assertEquals(1, got.count)
        assertEquals("/MOVIE/메이데이/Mayday.2026.mkv", got.play?.path)
        assertEquals("Mayday.2026.mkv", got.play?.name)
        assertEquals(1, got.subs.size)
        assertEquals("/MOVIE/메이데이/Mayday.ko.srt", got.subs[0].path)
        assertEquals("/MOVIE/메이데이/poster.jpg", got.sidecarPath)
        // 사이드카 art는 저장된 '경로'를 읽을 때 imageUriFor로 다시 싼 것이어야 한다(비밀번호 없는 경로만 저장).
        assertEquals(Uri.parse("ftp://nas//MOVIE/메이데이/poster.jpg"), (got.art as RemoteImage).uri)
        assertNull("nfo 지연 로더는 캐시 히트 때 되살리지 않는다", got.nfo)
    }

    @Test
    fun `series media has null play`() {
        val probe = FolderProbe.Media("Dark S01E01", "Dark.S01E01.mkv", art = null, nfo = null, play = null, count = 8)
        FolderClassStore.put(context, "k2", probe)
        val got = FolderClassStore.get(context, "k2", imageUriFor) as FolderProbe.Media
        assertNull(got.play)
        assertEquals(8, got.count)
        assertNull(got.art)
    }

    @Test
    fun `collection and plain variants round-trip`() {
        FolderClassStore.put(context, "v", FolderProbe.Videos(12))
        FolderClassStore.put(context, "m", FolderProbe.Music(30))
        FolderClassStore.put(context, "x", FolderProbe.Mixed(videoCount = 3, musicCount = 5))
        FolderClassStore.put(context, "p", FolderProbe.Plain)

        assertEquals(12, (FolderClassStore.get(context, "v", imageUriFor) as FolderProbe.Videos).count)
        assertEquals(30, (FolderClassStore.get(context, "m", imageUriFor) as FolderProbe.Music).count)
        val mixed = FolderClassStore.get(context, "x", imageUriFor) as FolderProbe.Mixed
        assertEquals(3, mixed.videoCount); assertEquals(5, mixed.musicCount)
        assertTrue(FolderClassStore.get(context, "p", imageUriFor) is FolderProbe.Plain)
    }

    @Test
    fun `a missing key and a removed key read back null`() {
        assertNull(FolderClassStore.get(context, "nope", imageUriFor))
        FolderClassStore.put(context, "temp", FolderProbe.Plain)
        FolderClassStore.remove(context, "temp")
        assertNull(FolderClassStore.get(context, "temp", imageUriFor))
    }
}
