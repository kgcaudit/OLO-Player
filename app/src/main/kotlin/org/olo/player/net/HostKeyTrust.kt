package org.olo.player.net

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.io.IOException
import org.filezilla.ftp.sftp.SshHostKey

/**
 * The server presented an SSH host key that is not the one pinned for it.
 *
 * Carried out of the connect so the browser can put a dialog on it: only the
 * person can settle a host key, and a key that has *changed* (a server that was
 * trusted now presenting a different key) is the loud case -- a rotated key and
 * someone in the middle look identical from here.
 */
class HostKeyUnverified(
    val fingerprint: String,
    val algorithm: String,
    val changed: Boolean,
) : IOException(
    if (changed) {
        "서버의 호스트 키가 이전에 신뢰한 것과 다릅니다."
    } else {
        "서버의 호스트 키를 아직 신뢰하지 않았습니다."
    },
)

/**
 * A JSch host-key store that accepts only the one fingerprint pinned for a
 * server, reusing core-ftp's [SshHostKey] to fingerprint exactly as OLO Explorer
 * does (SHA-256, the form `ssh-keygen -l` prints).
 *
 * With `StrictHostKeyChecking=yes`, JSch consults this before authenticating, so
 * an unrecognised or changed key fails the connect *before* the password is
 * ever sent -- the whole point of verifying. A blank pin is the first meeting:
 * every key reads as NOT_INCLUDED, the connect fails, and the browser asks the
 * person, then pins what they accept ([seen]).
 */
class PinningHostKeyRepository(private val pinned: String) : HostKeyRepository {

    /** What the server last presented, whether or not it matched the pin. */
    @Volatile
    var seen: SshHostKey? = null
        private set

    override fun check(host: String?, key: ByteArray?): Int {
        val k = key?.let { SshHostKey.of(it) } ?: return HostKeyRepository.NOT_INCLUDED
        seen = k
        return when {
            pinned.isBlank() -> HostKeyRepository.NOT_INCLUDED
            k.fingerprint.equals(pinned, ignoreCase = true) -> HostKeyRepository.OK
            else -> HostKeyRepository.CHANGED
        }
    }

    // Verification is pin-only; nothing is written to a known-hosts file.
    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = ""
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
