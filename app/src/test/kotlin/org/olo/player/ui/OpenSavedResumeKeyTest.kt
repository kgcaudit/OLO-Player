package org.olo.player.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.olo.player.data.SavedItem
import org.robolectric.RobolectricTestRunner

/**
 * 최근 재생(저장된 SavedItem)으로 다시 열 때, 열린 MediaEntry의 prefKey가 SavedItem.key와
 * 같아야 한다 -- 이어보기 위치는 이 key로 저장되므로, 어긋나면 처음부터 재생된다(회귀 방지).
 * FTP처럼 URI에 자격증명이 섞이면 uri 문자열과 credential-free key가 달라 특히 중요하다.
 */
@RunWith(RobolectricTestRunner::class)
class OpenSavedResumeKeyTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `reopening a network recent keeps the resume key, not the credentialed uri`() {
        val vm = PlayerViewModel(app)
        // 저장 위치가 걸린 key(브라우즈에서 쓴 credential-free prefKey)와, 자격증명이 섞인 재생 URI.
        val item = SavedItem(
            key = "ftp://nas.local:21/Movies/film.mkv",
            name = "film.mkv",
            uri = "ftp://user:secret@nas.local:21/Movies/film.mkv",
            source = "FTP",
        )
        vm.openSaved(item)

        val opened = vm.mediaViewer?.items?.singleOrNull()
        assertEquals("이어보기 key가 보존돼야 한다", item.key, opened?.prefKey)
        assertEquals("재생 URI는 자격증명을 유지해야 한다", item.uri, opened?.uri.toString())
    }
}
