package org.olo.player.data

/**
 * 네트워크 연결 제한시간(모든 프로토콜 공통). 세션·데이터소스는 context를 들고 있지 않아
 * 매번 prefs를 읽기 어렵기 때문에, 설정값을 앱 시작 때 여기로 한 번 적재해 두고 접속 때마다
 * 읽는다. 절전 NAS가 깨어나는 데 시간이 걸려 기본 15초로는 접속이 튕기던 문제를 설정으로
 * 늘릴 수 있게 한다(기본 30초).
 */
object NetConfig {
    @Volatile var connectTimeoutMs: Int = 30_000

    fun load(prefs: AppPreferences) {
        connectTimeoutMs = prefs.connectTimeoutSec() * 1000
    }
}
