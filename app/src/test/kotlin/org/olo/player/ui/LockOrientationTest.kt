package org.olo.player.ui

import android.content.pm.ActivityInfo
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 화면 잠금이 '지금 보이는 방향'을 구체 상수로 집어야, 홈에 갔다 돌아와도 그 방향으로 다시
 * 잠긴다(SCREEN_ORIENTATION_LOCKED가 복귀 순간의 물리 방향으로 되돌아가던 회귀 방지).
 * 가로/세로는 Configuration이 확실하고, rotation 0·90=정 / 180·270=역으로 가린다.
 */
class LockOrientationTest {

    @Test fun `landscape maps to landscape or its reverse by rotation`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, lockOrientationFor(true, Surface.ROTATION_0))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, lockOrientationFor(true, Surface.ROTATION_90))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, lockOrientationFor(true, Surface.ROTATION_180))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, lockOrientationFor(true, Surface.ROTATION_270))
    }

    @Test fun `portrait maps to portrait or its reverse by rotation`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, lockOrientationFor(false, Surface.ROTATION_0))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, lockOrientationFor(false, Surface.ROTATION_90))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, lockOrientationFor(false, Surface.ROTATION_180))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, lockOrientationFor(false, Surface.ROTATION_270))
    }

    @Test fun `the locked value is concrete, never SENSOR or LOCKED`() {
        // 구체 상수여야 복귀 때 '지금 물리 방향'으로 재평가되지 않는다.
        val concrete = setOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        )
        for (land in listOf(true, false)) {
            for (rot in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
                assertEquals(true, lockOrientationFor(land, rot) in concrete)
            }
        }
    }
}
