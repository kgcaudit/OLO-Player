package org.olo.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 소스 계층별 포스터 정책 파생 고정. 포스터 로딩을 '어느 저장소냐'에 맞춰 가르는 근간이므로,
 * 각 계층의 선택(적극/지연·영구캐시·이름우선·동시성)이 의도대로 나오는지 못박는다.
 */
class PosterPolicyTest {

    @Test
    fun `local is eager with no caching, no name-first, unlimited concurrency`() {
        val p = PosterPolicy.of(SourceTier.LOCAL)
        assertTrue("기기 내부는 LIST가 공짜라 적극적으로 다 판별", p.cheapListing)
        assertFalse("로컬은 재스캔이 싸서 영구 캐시 불필요", p.persistClassification)
        assertFalse("로컬은 즉시 판별되므로 이름우선 불필요", p.nameFirstPoster)
        assertEquals("로컬은 동시성 제한 없음(0=무제한)", 0, p.probeConcurrency)
    }

    @Test
    fun `connected network is lazy, persisted, name-first, serialized`() {
        val p = PosterPolicy.of(SourceTier.CONNECTED_NETWORK)
        assertFalse("FTP/SMB/SFTP는 LIST가 비쌈", p.cheapListing)
        assertTrue("네트워크는 재스캔이 비싸 판별을 영구 캐시", p.persistClassification)
        assertTrue("네트워크는 이름우선으로 포스터를 먼저 붙임", p.nameFirstPoster)
        assertEquals("한 연결을 직렬화해야 안전 -> 동시성 1", 1, p.probeConcurrency)
    }

    @Test
    fun `http cloud is lazy, persisted, name-first, small parallelism`() {
        val p = PosterPolicy.of(SourceTier.HTTP_CLOUD)
        assertFalse(p.cheapListing)
        assertTrue(p.persistClassification)
        assertTrue(p.nameFirstPoster)
        assertEquals("상태 없는 HTTP라 소수 병렬 허용", 4, p.probeConcurrency)
    }
}
