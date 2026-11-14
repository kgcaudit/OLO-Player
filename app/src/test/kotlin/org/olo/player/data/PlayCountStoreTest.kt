package org.olo.player.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 재생 횟수 집계와 '자주 들은' 산출(많이 들은 순, 제거)을 고정한다. (org.json·SharedPreferences라 Robolectric.) */
@RunWith(RobolectricTestRunner::class)
class PlayCountStoreTest {

    private val store = PlaylistStore(ApplicationProvider.getApplicationContext())
    private fun item(k: String) = SavedItem(key = k, name = k, uri = "file:///$k", source = "기기", local = true)

    @Test fun `ranks by count then recency and carries playCount`() {
        repeat(3) { store.recordPlay(item("A")) }
        repeat(1) { store.recordPlay(item("B")) }
        repeat(2) { store.recordPlay(item("C")) }
        val top = store.mostPlayed(10)
        assertEquals(listOf("A", "C", "B"), top.map { it.key })
        assertEquals(3, top[0].playCount)
        assertEquals(2, top[1].playCount)
    }

    @Test fun `removePlay drops a song`() {
        repeat(2) { store.recordPlay(item("X")) }
        repeat(1) { store.recordPlay(item("Y")) }
        store.removePlay("X")
        assertEquals(listOf("Y"), store.mostPlayed(10).map { it.key })
    }

    @Test fun `empty store yields nothing`() {
        assertEquals(emptyList<String>(), store.mostPlayed(10).map { it.key })
    }
}
