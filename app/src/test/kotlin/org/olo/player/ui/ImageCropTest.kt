package org.olo.player.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** 자르기 결과가 항상 정사각 500×500 JPEG인지, 경계를 벗어난 좌표도 비트맵 안으로 가두는지 고정. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageCropTest {

    private fun sample(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    @Test
    fun `crop outputs 500x500 jpeg`() {
        val src = sample(1200, 800)
        val bytes = cropToJpeg(src, left = 200f, top = 100f, sizeF = 600f, outPx = 500)
        val out = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(500, out.width)
        assertEquals(500, out.height)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `crop clamps out-of-bounds region into the bitmap`() {
        val src = sample(400, 400)
        // 음수 좌표·과대 크기여도 예외 없이 비트맵 안으로 가둬 500×500을 낸다.
        val bytes = cropToJpeg(src, left = -50f, top = -50f, sizeF = 9999f, outPx = 500)
        val out = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(500, out.width)
        assertEquals(500, out.height)
    }
}
