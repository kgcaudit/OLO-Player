package org.olo.player.ui

/**
 * 저장 위치(소스)별 포스터·폴더판별 로딩 비용 계층.
 *
 * 포스터 한 장을 붙이는 비용은 소스에 따라 자릿수로 다르다. 폴더가 '단일영화'인지 판별하려면
 * 그 폴더를 LIST 해야 하는데, 로컬은 거의 공짜지만 네트워크는 왕복(FTP는 PASV 재연결까지)이고
 * 클라우드/HTTP는 요율 제한까지 걸린다. 그래서 '적극적으로 다 훑을지 / 지연·절제할지', '판별을
 * 디스크에 영구 캐시할 가치가 있는지', '이름만으로 포스터를 먼저 붙일지', '동시에 몇 개까지
 * 네트워크를 때릴지'를 소스 계층에서 가른다.
 *
 * 물리적으로는 로컬 1 + 네트워크 4(FTP/SFTP/SMB/WebDAV)뿐이지만, 비용 특성상 셋으로 나눈다:
 *  - LOCAL: 기기 내부 저장소 -- LIST·사이드카·내장아트가 모두 거의 공짜.
 *  - CONNECTED_NETWORK: 한 연결을 붙잡는 FTP/SFTP/SMB -- LIST가 비싸고(FTP는 데이터 연결 재개폐로
 *    최악) 한 연결로 직렬화해야 안전하다.
 *  - HTTP_CLOUD: 상태 없는 HTTP인 WebDAV(및 추후 Nextcloud/Drive/Dropbox 등) -- 연결을 붙잡지
 *    않아 소수 병렬이 가능하고, 제공자 썸네일 엔드포인트를 쓸 수 있는 여지가 있다.
 */
enum class SourceTier { LOCAL, CONNECTED_NETWORK, HTTP_CLOUD }

/**
 * 한 소스의 포스터 로딩 정책. [SourceTier]에서 파생한다([of]). 화면(RemoteBrowseList)은 이 정책만
 * 보고 적극/지연·영구캐시·이름우선·동시성을 정한다 -- 소스별 if 분기를 한곳으로 모은다.
 */
data class PosterPolicy(
    val tier: SourceTier,
    /** 폴더 LIST가 거의 공짜라 모든 폴더를 바로 판별해도 되는가(로컬만 true). */
    val cheapListing: Boolean,
    /** 폴더 판별 결과를 디스크에 영구 캐시할 가치가 있는가(재스캔이 비싼 네트워크만 true). */
    val persistClassification: Boolean,
    /** 폴더 이름이 미디어로 보이면 LIST가 끝나기 전에 포스터를 먼저 붙일지(네트워크만 true). */
    val nameFirstPoster: Boolean,
    /**
     * 판별·포스터 네트워크 작업을 동시에 몇 개까지 허용할지(소스 1개 기준). 0 = 무제한(로컬).
     * 연결형은 한 연결을 직렬화하므로 1, HTTP/클라우드는 연결을 안 붙잡아 소수 병렬(4)이 안전하다.
     */
    val probeConcurrency: Int,
) {
    companion object {
        fun of(tier: SourceTier): PosterPolicy = when (tier) {
            SourceTier.LOCAL -> PosterPolicy(
                tier, cheapListing = true, persistClassification = false,
                nameFirstPoster = false, probeConcurrency = 0,
            )
            SourceTier.CONNECTED_NETWORK -> PosterPolicy(
                tier, cheapListing = false, persistClassification = true,
                nameFirstPoster = true, probeConcurrency = 1,
            )
            SourceTier.HTTP_CLOUD -> PosterPolicy(
                tier, cheapListing = false, persistClassification = true,
                nameFirstPoster = true, probeConcurrency = 4,
            )
        }
    }
}
