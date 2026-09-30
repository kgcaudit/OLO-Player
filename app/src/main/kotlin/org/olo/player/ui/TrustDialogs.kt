package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.ui.theme.OloTheme

/**
 * The dialog that settles an SSH host key or a TLS certificate: a fingerprint
 * nobody signed, so only the person can recognise it. Shown on a first meeting
 * (recognise, then it is pinned) and -- loudly -- when a pinned one has changed,
 * which a rotated key and someone in the middle both look like from here.
 *
 * Deliberately not a plain yes/no: the fingerprint is the whole point, shown in
 * monospace so it can be compared against what the server's admin page or
 * `ssh-keygen -l` prints.
 */
@Composable
private fun TrustDialog(
    title: String,
    message: String,
    fingerprint: String,
    detail: String?,
    changed: Boolean,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = OloTheme.colors
    OloCardDialog(
        title = title,
        onDismiss = onCancel,
        titleAccent = changed,
        actions = {
            OloDialogButton("취소", onClick = onCancel, primary = false)
            OloDialogButton(if (changed) "그래도 신뢰" else "신뢰", onClick = onTrust)
        },
    ) {
        Text(message, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
        if (detail != null) {
            Text(detail, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            fingerprint,
            color = c.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(c.progressTrack)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** Trust prompt for an SFTP server's SSH host key. */
@Composable
fun HostKeyDialog(
    fingerprint: String,
    algorithm: String,
    changed: Boolean,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
) {
    TrustDialog(
        title = if (changed) "호스트 키가 변경되었습니다" else "새 SSH 서버",
        message = if (changed) {
            "이 서버의 SSH 호스트 키가 이전에 신뢰한 것과 다릅니다. 서버 관리자와 지문을 확인하기 전에는 접속하지 마세요."
        } else {
            "이 서버를 처음 접속합니다. 아래 지문이 서버의 것과 일치하는지 확인한 뒤 신뢰하세요."
        },
        fingerprint = fingerprint,
        detail = "알고리즘: $algorithm",
        changed = changed,
        onTrust = onTrust,
        onCancel = onCancel,
    )
}

/** Trust prompt for an FTPS server's TLS certificate. */
@Composable
fun CertificateDialog(
    fingerprint: String,
    subject: String,
    issuer: String,
    changed: Boolean,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
) {
    TrustDialog(
        title = if (changed) "인증서가 변경되었습니다" else "새 FTPS 서버",
        message = if (changed) {
            "이 서버의 TLS 인증서가 이전에 신뢰한 것과 다릅니다. 서버 관리자와 지문을 확인하기 전에는 접속하지 마세요."
        } else {
            "이 서버의 인증서를 신뢰할 수 있는 기관이 보증하지 않습니다. 아래 지문이 서버의 것과 일치하는지 확인한 뒤 신뢰하세요."
        },
        fingerprint = fingerprint,
        detail = "서버: $subject\n발급자: $issuer",
        changed = changed,
        onTrust = onTrust,
        onCancel = onCancel,
    )
}
