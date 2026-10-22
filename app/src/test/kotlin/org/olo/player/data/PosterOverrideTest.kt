package org.olo.player.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 포스터 override 저장소: URL과 고른 작품 식별자(id·영화/TV)를 한 키에 눌러 두되, 식별자
 * 없이 URL만 있던 예전 값도 그대로 읽혀야 한다(하위호환). 파싱이 조용히 틀리면 포스터는
 * 바뀌는데 상세 메타데이터는 안 따라오므로 못으로 박는다. SharedPreferences라 Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class PosterOverrideTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val uri = "ftp://nas:21/MOVIE/motor.mkv"

    @Test
    fun `url and ref round-trip together`() {
        PosterOverride.set(ctx, uri, "https://img/p.jpg", PosterRef(1234, tv = false))
        assertEquals("https://img/p.jpg", PosterOverride.get(ctx, uri))
        assertEquals(PosterRef(1234, false), PosterOverride.getRef(ctx, uri))
    }

    @Test
    fun `tv flag is preserved`() {
        PosterOverride.set(ctx, uri, "https://img/t.jpg", PosterRef(55, tv = true))
        assertEquals(PosterRef(55, true), PosterOverride.getRef(ctx, uri))
    }

    @Test
    fun `legacy url-only value still reads, with no ref`() {
        // 예전 버전이 URL만 저장해 둔 값.
        ctx.getSharedPreferences("olo_poster_override", android.content.Context.MODE_PRIVATE)
            .edit().putString(uri, "https://img/legacy.jpg").apply()
        assertEquals("https://img/legacy.jpg", PosterOverride.get(ctx, uri))
        assertNull(PosterOverride.getRef(ctx, uri))
    }

    @Test
    fun `null url clears both url and ref`() {
        PosterOverride.set(ctx, uri, "https://img/p.jpg", PosterRef(7, false))
        PosterOverride.set(ctx, uri, null, null)
        assertNull(PosterOverride.get(ctx, uri))
        assertNull(PosterOverride.getRef(ctx, uri))
    }

    @Test
    fun `set without a ref stores url only`() {
        PosterOverride.set(ctx, uri, "https://img/p.jpg", null)
        assertEquals("https://img/p.jpg", PosterOverride.get(ctx, uri))
        assertNull(PosterOverride.getRef(ctx, uri))
    }

    @Test
    fun `blank uri is a no-op`() {
        PosterOverride.set(ctx, "", "https://img/x.jpg", PosterRef(1, false))
        assertNull(PosterOverride.get(ctx, ""))
        assertNull(PosterOverride.getRef(ctx, null))
    }
}
