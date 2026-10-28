package org.olo.player.ui

import java.util.Locale

/**
 * 재생 위치·길이를 사람이 읽는 시계 표기로. 1시간 이상이면 "H:MM:SS", 아니면 "M:SS".
 *
 * 설정·상세·보관함·플레이어가 제각기 같은 로직을 네 벌로 두던 것을 하나로 모은다. 음수는
 * 0으로 막아(탐색 중 음수 위치 등) 깨진 표기를 피하고, Locale.ROOT로 고정해 지역 설정에
 * 상관없이 숫자 포맷이 일정하도록 한다.
 */
fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

/**
 * 초 단위의 짧은 "M:SS" 표기. 가사 싱크·태그 편집의 타임스탬프에서 쓰던 같은 한 줄 함수를
 * 두 화면에 각각 두던 것을 모은다. Locale.ROOT로 고정해 지역 설정과 무관하게 일정하게 찍는다.
 */
fun mmss(sec: Int): String = String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60)
