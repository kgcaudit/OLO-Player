package org.olo.player.net

import android.net.Uri

/**
 * 원격 미디어 URI의 userInfo(자격증명) 조각을 만든다: `user`, `user:pass`, 또는 없으면 null.
 *
 * SFTP·SMB·WebDAV의 재생 URI 빌더가 똑같은 식을 세 벌 두던 것을 한곳으로 모은다. 사용자명·
 * 비밀번호는 `@`·`:`·`/` 같은 글자가 섞여도 authority가 깨지지 않도록 반드시 퍼센트 인코딩한다.
 * 호출부는 `userInfo?.let { "$it@" } ?: ""`로 authority 앞에 붙인다(포트 기본값은 프로토콜마다 달라
 * authority 조립은 호출부에 남긴다).
 */
fun encodedUserInfo(user: String, pass: String): String? = when {
    user.isEmpty() -> null
    pass.isNotEmpty() -> Uri.encode(user) + ":" + Uri.encode(pass)
    else -> Uri.encode(user)
}
