package org.olo.player.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 폴더형 즐겨찾기(드라마 시리즈·영상 모음)를 고정한다: SavedItem의 folder 플래그와 열기 경로
 * (openPath)가 저장·복원(JSON·SharedPreferences)을 왕복해도 그대로 남아야, 보관함에서 눌렀을
 * 때 '재생'이 아니라 그 폴더로 '열기'가 되고 어느 폴더로 진입할지 안다. 옛 데이터(필드 없음)는
 * 파일 즐겨찾기로 안전하게 읽혀야 한다(하위호환). (org.json·SharedPreferences라 Robolectric.)
 */
@RunWith(RobolectricTestRunner::class)
class FolderFavoriteTest {

    private val store = PlaylistStore(ApplicationProvider.getApplicationContext())

    @Test fun `folder flag and open path survive a save-reload round trip`() {
        val series = SavedItem(
            key = "smb://nas/share/DRAMA/오징어 게임", name = "오징어 게임",
            uri = "smb://user:secret@nas/share/DRAMA/오징어 게임", source = "SMB",
            folder = true, openPath = "/DRAMA/오징어 게임",
        )
        store.toggleFavorite(series)
        val back = store.favorites().single()
        assertTrue("폴더 즐겨찾기임이 유지돼야 한다", back.folder)
        assertEquals("열기 경로(서버 루트 상대)가 유지돼야 한다", "/DRAMA/오징어 게임", back.openPath)
        assertTrue("즐겨찾기 조회가 key로 맞아야 한다", store.isFavorite(series.key))
    }

    @Test fun `a plain file favorite stays a file (not a folder)`() {
        val file = SavedItem(key = "/sd/Movies/film.mkv", name = "film.mkv", uri = "file:///sd/Movies/film.mkv", source = "기기", local = true)
        store.toggleFavorite(file)
        val back = store.favorites().single()
        assertFalse("파일은 폴더가 아니다", back.folder)
        assertNull("파일은 열기 경로가 없다", back.openPath)
    }

    @Test fun `legacy json without folder fields reads as a file favorite`() {
        // 업데이트 전 저장분: folder·openPath 키가 아예 없는 JSON. 빠짐없이 파일로 읽혀야 한다.
        val legacy = org.json.JSONObject()
            .put("key", "ftp://nas/Movies/old.mkv").put("name", "old.mkv")
            .put("uri", "ftp://nas/Movies/old.mkv").put("source", "FTP").put("savedAt", 1L)
        val item = SavedItem.fromJson(legacy)
        assertFalse(item.folder)
        assertNull(item.openPath)
    }
}
