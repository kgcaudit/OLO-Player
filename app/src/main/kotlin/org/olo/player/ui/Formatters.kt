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
