package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 상세 응답 파싱을 못으로 박는다. 영화와 시리즈는 키가 달라(title/name, runtime/
 * episode_run_time, release_dates/content_ratings) 조용히 엉뚱한 칸으로 새기 쉽다.
 *
 * org.json은 android.jar에선 예외만 던지는 스텁이라, 진짜 구현을 주는 Robolectric 위에서
 * 돌린다(TmdbMatchTest처럼 JSON을 안 쓰는 순수 테스트와 달리 여기선 실제 파싱을 검증한다).
 */
@RunWith(RobolectricTestRunner::class)
class TmdbDetailsTest {

    @Test
    fun `movie details pull title, meta, cert, director and cast`() {
        val json = """
            {
              "title": "모터 시티", "original_title": "Motor City",
              "release_date": "2026-08-25", "vote_average": 7.2, "runtime": 108,
              "overview": "도시의 밤을 질주하는 레이서들의 이야기.",
              "poster_path": "/p.jpg", "backdrop_path": "/b.jpg",
              "genres": [{"name":"액션"},{"name":"범죄"}],
              "release_dates": {"results": [
                {"iso_3166_1":"US","release_dates":[{"certification":"R"}]},
                {"iso_3166_1":"KR","release_dates":[{"certification":"15세"}]}
              ]},
              "credits": {
                "cast": [{"name":"알폰소","profile_path":"/a.jpg"},{"name":"인디아","profile_path":null}],
                "crew": [{"job":"Writer","name":"누구"},{"job":"Director","name":"라이언 쿠글러"}]
              }
            }
        """.trimIndent()
        val d = TmdbDetails.parse(json, tv = false)!!
        assertEquals("모터 시티", d.title)
        assertEquals("Motor City", d.originalTitle)
        assertEquals(2026, d.year)
        assertEquals(7.2, d.rating!!, 0.001)
        assertEquals(108, d.runtimeMinutes)
        assertEquals(listOf("액션", "범죄"), d.genres)
        assertEquals("15세", d.certification) // 한국을 미국보다 우선
        assertEquals("라이언 쿠글러", d.director)
        assertEquals(2, d.cast.size)
        assertEquals("알폰소", d.cast[0].name)
        assertTrue(d.cast[0].profileUrl!!.endsWith("/a.jpg"))
        assertNull(d.cast[1].profileUrl) // 사진 없는 배우도 이름은 유지
        assertTrue(d.posterUrl!!.endsWith("/p.jpg"))
        assertTrue(d.backdropUrl!!.endsWith("/b.jpg"))
        assertEquals(false, d.tv)
    }

    @Test
    fun `tv details read name, episode runtime, rating and creator`() {
        val json = """
            {
              "name": "다크", "original_name": "Dark",
              "first_air_date": "2017-12-01", "vote_average": 8.4,
              "episode_run_time": [60], "overview": "줄거리",
              "genres": [{"name":"미스터리"}],
              "created_by": [{"name":"바란 보 오다르"}],
              "content_ratings": {"results":[
                {"iso_3166_1":"US","rating":"TV-MA"},
                {"iso_3166_1":"KR","rating":"청불"}
              ]},
              "credits": {"cast":[{"name":"루이스","profile_path":"/l.jpg"}], "crew":[]}
            }
        """.trimIndent()
        val d = TmdbDetails.parse(json, tv = true)!!
        assertEquals("다크", d.title)
        assertEquals("Dark", d.originalTitle)
        assertEquals(2017, d.year)
        assertEquals(60, d.runtimeMinutes)
        assertEquals("청불", d.certification)
        assertEquals("바란 보 오다르", d.director) // created_by를 감독 칸에
        assertEquals(1, d.cast.size)
        assertEquals(true, d.tv)
    }

    @Test
    fun `original title hidden when it equals the local title`() {
        val json = """{"title":"Twins","original_title":"Twins","credits":{}}"""
        val d = TmdbDetails.parse(json, tv = false)!!
        assertNull(d.originalTitle)
    }

    @Test
    fun `missing optional fields degrade to empty, not crash`() {
        val json = """{"title":"제목만","credits":{}}"""
        val d = TmdbDetails.parse(json, tv = false)!!
        assertEquals("제목만", d.title)
        assertNull(d.year)
        assertNull(d.rating)
        assertNull(d.runtimeMinutes)
        assertNull(d.certification)
        assertNull(d.director)
        assertTrue(d.genres.isEmpty())
        assertTrue(d.cast.isEmpty())
    }

    @Test
    fun `zero vote average is treated as no rating`() {
        val json = """{"title":"무평점","vote_average":0.0,"credits":{}}"""
        assertNull(TmdbDetails.parse(json, tv = false)!!.rating)
    }

    @Test
    fun `no title at all yields null`() {
        assertNull(TmdbDetails.parse("""{"overview":"x"}""", tv = false))
        assertNull(TmdbDetails.parse("not json", tv = false))
    }
}
