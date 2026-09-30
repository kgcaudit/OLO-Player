package org.olo.player.net

import com.hierynomus.smbj.SmbConfig

/**
 * The hardened SMB client configuration, shared by the browser and the streamer
 * so both connect the same way.
 *
 * SMB has no certificate or host key to recognise a server by -- its security is
 * authentication plus message signing plus (SMB3) encryption -- so "verifying" an
 * SMB server is not a pin dialog but a connection that cannot be silently
 * tampered with:
 *
 * - **Signing is required.** Every SMB2/3 server supports it, so requiring it
 *   costs nothing on a real server but refuses a man-in-the-middle that cannot
 *   sign. This is the integrity/authenticity guarantee the FTPS/WebDAV/SFTP pins
 *   give on those protocols.
 * - **Encryption** (SMB3) is turned on only when the saved server asks for it:
 *   it needs SMB 3.x on both ends, so forcing it would lock out an SMB2-only NAS.
 *   When on, the transfer is confidential in the way a TLS stream is.
 */
fun smbConfig(encrypt: Boolean): SmbConfig = SmbConfig.builder()
    .withSigningRequired(true)
    .withEncryptData(encrypt)
    .build()
