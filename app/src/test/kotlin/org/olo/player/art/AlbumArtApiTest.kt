package org.olo.player.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 앨범아트 검색의 순수부(요청 URL·아트워크 업스케일·응답 파싱)를 네트워크 없이 고정한다.
 * 한국어·일본어 질의가 UTF-8로 안전하게 인코딩되는지, iTunes 국가 스토어·CAA 파싱이 맞는지 확인.
 * (org.json 파싱이 있어 Robolectric 러너로 실제 구현을 쓴다.)
 */
@RunWith(RobolectricTestRunner::class)
class AlbumArtApiTest {

    @Test
    fun `itunes search url carries country and url-encoded korean query`() {
        val url = AlbumArtApi.itunesSearchUrl(artist = "아이유", album = "좋은 날", country = "KR")
        assertTrue(url.startsWith("https://itunes.apple.com/search?term="))
        assertTrue("국가 스토어", url.contains("&country=KR"))
        assertTrue("앨범 엔티티", url.contains("entity=album"))
        // 한글은 퍼센트 인코딩되어 원문이 그대로 노출되지 않는다.
        assertTrue("UTF-8 인코딩", url.contains("%EC%95%84%EC%9D%B4%EC%9C%A0")) // 아이유
        assertTrue(!url.contains("아이유"))
    }

    @Test
    fun `musicbrainz release query joins album and artist in original script`() {
        val url = AlbumArtApi.mbReleaseSearchUrl(artist = "宇多田ヒカル", album = "First Love")
        assertTrue(url.startsWith("https://musicbrainz.org/ws/2/release/?query="))
        assertTrue(url.contains("&fmt=json"))
        // release:"First Love" AND artist:"宇多田ヒカル" 가 인코딩되어 들어간다.
        assertTrue(url.contains("release")) // 인코딩 전 키워드 흔적
        assertTrue("일본어 아티스트 인코딩", url.contains("%E5%AE%87%E5%A4%9A%E7%94%B0")) // 宇多田
        assertTrue(!url.contains("宇多田ヒカル"))
    }

    @Test
    fun `itunes song search url uses song entity and encodes title`() {
        val url = AlbumArtApi.itunesSongSearchUrl(artist = "아이유", title = "밤편지", country = "KR")
        assertTrue(url.startsWith("https://itunes.apple.com/search?term="))
        assertTrue("곡 엔티티(song)", url.contains("entity=song"))
        assertTrue("국가 스토어", url.contains("&country=KR"))
        // 한글 제목이 퍼센트 인코딩된다.
        assertTrue("UTF-8 인코딩", url.contains("%EB%B0%A4%ED%8E%B8%EC%A7%80")) // 밤편지
        assertTrue(!url.contains("밤편지"))
    }

    @Test
    fun `musicbrainz recording query joins recording and artist`() {
        val url = AlbumArtApi.mbRecordingSearchUrl(artist = "宇多田ヒカル", title = "First Love")
        assertTrue(url.startsWith("https://musicbrainz.org/ws/2/recording/?query="))
        assertTrue(url.contains("&fmt=json"))
        assertTrue("레코딩 키워드", url.contains("recording"))
        assertTrue("일본어 아티스트 인코딩", url.contains("%E5%AE%87%E5%A4%9A%E7%94%B0")) // 宇多田
        assertTrue(!url.contains("宇多田ヒカル"))
    }

    @Test
    fun `parse recordings dedups release ids, caps, and carries song meta`() {
        val body = """
            {"recordings":[
              {"title":"一輪花","artist-credit":[{"name":"tuki."}],
               "releases":[{"id":"mbid-1","title":"一輪花","date":"2024-08-21"},{"id":"mbid-2","title":"一輪花 - Single"}]},
              {"title":"別の曲","artist-credit":[{"name":"tuki."}],
               "releases":[{"id":"mbid-2"},{"id":"mbid-3"},{"id":"mbid-4"}]}
            ]}
        """.trimIndent()
        val hits = AlbumArtApi.parseMbRecordings(body, limit = 3)
        // mbid-2는 두 레코딩에 걸쳐 중복 -> 한 번만, 상위 3개로 제한.
        assertEquals(listOf("mbid-1", "mbid-2", "mbid-3"), hits.map { it.mbid })
        assertEquals("一輪花", hits[0].meta.title)
        assertEquals("tuki.", hits[0].meta.artist)
        assertEquals("一輪花", hits[0].meta.album)
        assertEquals("2024", hits[0].meta.year)
    }

    @Test
    fun `parse itunes song result carries track-level meta`() {
        val body = """
            {"resultCount":1,"results":[
              {"wrapperType":"track","kind":"song","trackName":"밤편지","artistName":"아이유",
               "collectionName":"Palette","collectionArtistName":"IU","trackNumber":2,"trackCount":10,
               "discNumber":1,"releaseDate":"2017-04-21T07:00:00Z","primaryGenreName":"K-Pop",
               "artworkUrl100":"https://x/100x100bb.jpg"}
            ]}
        """.trimIndent()
        val m = AlbumArtApi.parseItunes(body).single().meta!!
        assertEquals("밤편지", m.title)
        assertEquals("아이유", m.artist)
        assertEquals("Palette", m.album)
        assertEquals("IU", m.albumArtist)
        assertEquals("2/10", m.track)
        assertEquals("1", m.disc)
        assertEquals("2017", m.year)
        assertEquals("K-Pop", m.genre)
    }

    @Test
    fun `parse itunes album result carries album-level meta only`() {
        val body = """
            {"resultCount":1,"results":[
              {"wrapperType":"collection","collectionType":"Album","collectionName":"Real",
               "artistName":"아이유","releaseDate":"2010-12-09T08:00:00Z","primaryGenreName":"K-Pop",
               "artworkUrl100":"https://x/100x100bb.jpg"}
            ]}
        """.trimIndent()
        val m = AlbumArtApi.parseItunes(body).single().meta!!
        assertEquals("Real", m.album)
        assertEquals("아이유", m.artist)
        assertEquals("2010", m.year)
        assertEquals("K-Pop", m.genre)
        assertEquals(null, m.title) // 앨범 결과엔 곡 제목·트랙이 없다
        assertEquals(null, m.track)
    }

    @Test
    fun `source meta toFields maps only provided fields in editor order`() {
        val m = SourceMeta(title = "밤편지", artist = "아이유", track = "2/10", year = "2017")
        val f = m.toFields()
        assertEquals(listOf(TagField.TITLE, TagField.ARTIST, TagField.TRACK, TagField.YEAR), f.keys.toList())
        assertEquals("2/10", f[TagField.TRACK])
        assertTrue(!f.containsKey(TagField.ALBUM)) // null은 빠진다
    }

    @Test
    fun `itunes artwork upscales the size token`() {
        val art = "https://is1-ssl.mzstatic.com/image/thumb/Music/abc/100x100bb.jpg"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/abc/600x600bb.jpg",
            AlbumArtApi.upscaleItunesArtwork(art, 600),
        )
        // 다른 기본 크기(170x170bb)도 바뀐다.
        assertEquals(
            "https://x/200x200bb.png",
            AlbumArtApi.upscaleItunesArtwork("https://x/170x170bb.png", 200),
        )
    }

    @Test
    fun `parse itunes builds thumb and full candidates`() {
        val body = """
            {"resultCount":1,"results":[
              {"artistName":"아이유","collectionName":"Real","artworkUrl100":"https://x/100x100bb.jpg"}
            ]}
        """.trimIndent()
        val c = AlbumArtApi.parseItunes(body, thumbPx = 200, fullPx = 600)
        assertEquals(1, c.size)
        assertEquals("https://x/200x200bb.jpg", c[0].thumbUrl)
        assertEquals("https://x/600x600bb.jpg", c[0].fullUrl)
        assertEquals("Real", c[0].title)
        assertEquals("아이유", c[0].artist)
        assertEquals("iTunes", c[0].source)
    }

    @Test
    fun `parse musicbrainz releases caps to limit and carries album meta`() {
        val body = """
            {"releases":[
              {"id":"mbid-1","title":"좋은 날","date":"2010-12-09","artist-credit":[{"name":"아이유"}]},
              {"id":"mbid-2","title":"Real","date":"2010","artist-credit":[{"name":"아이유"}]},
              {"id":"mbid-3","title":"C"}
            ]}
        """.trimIndent()
        val hits = AlbumArtApi.parseMbReleases(body, limit = 2)
        assertEquals(listOf("mbid-1", "mbid-2"), hits.map { it.mbid })
        assertEquals("좋은 날", hits[0].meta.album)
        assertEquals("아이유", hits[0].meta.artist)
        assertEquals("2010", hits[0].meta.year)
        assertEquals(null, hits[0].meta.title) // 앨범 단위라 곡 제목은 없다
    }

    @Test
    fun `parse cover art archive prefers front and picks thumbnails`() {
        val body = """
            {"images":[
              {"front":false,"image":"https://caa/back.jpg","thumbnails":{"250":"https://caa/b250.jpg","500":"https://caa/b500.jpg","1200":"https://caa/b1200.jpg"}},
              {"front":true,"image":"https://caa/front.jpg","thumbnails":{"250":"https://caa/f250.jpg","500":"https://caa/f500.jpg","1200":"https://caa/f1200.jpg"}}
            ]}
        """.trimIndent()
        val c = AlbumArtApi.parseCoverArtArchive(body, title = "좋은 날", artist = "아이유")
        assertEquals(2, c.size)
        // 앞표지가 먼저 오고, 썸네일은 빠른 250px를 우선한다(원본 full은 1200px).
        assertEquals("https://caa/f250.jpg", c[0].thumbUrl)
        assertEquals("https://caa/f1200.jpg", c[0].fullUrl)
        assertEquals("좋은 날", c[0].title)
        assertEquals("CoverArtArchive", c[0].source)
    }

    @Test
    fun `parsers return empty on garbage, never throw`() {
        assertTrue(AlbumArtApi.parseItunes("not json").isEmpty())
        assertTrue(AlbumArtApi.parseMbReleases("{}").isEmpty())
        assertTrue(AlbumArtApi.parseMbRecordings("nope").isEmpty())
        assertTrue(AlbumArtApi.parseCoverArtArchive("{}", null, null).isEmpty())
    }
}
