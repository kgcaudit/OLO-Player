package org.olo.player.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts and decrypts the one secret this app keeps: a saved server's password.
 *
 * Ported from OLO Explorer. An interface because the real implementation needs
 * the Android keystore, which cannot run on a plain JVM.
 */
interface PasswordCipher {

    fun encrypt(plaintext: String): String

    /**
     * The plaintext, or null when the stored value cannot be read back.
     *
     * Null is a real outcome: a keystore key is destroyed when the user clears
     * the app's data, and on some devices when the screen lock is removed. The
     * password is then gone, and the honest response is to ask again -- not to
     * crash, and not to pretend the empty string was what the user typed.
     */
    fun decrypt(stored: String): String?
}

/**
 * The real one: AES-256-GCM under a key held by the Android keystore.
 *
 * The key material never leaves the keystore -- on most devices never leaves
 * secure hardware -- so the preferences file alone is useless. That is the
 * property worth having: the SharedPreferences XML is what ends up in a backup
 * or on the data partition of a device taken apart; on its own it no longer
 * spills passwords.
 *
 * What it does not defend against is code running as this app's own uid: that
 * can ask the keystore to decrypt, exactly as the app does. Keystore raises the
 * cost of stealing the stored file; it does not make the secret unreadable to a
 * rooted device.
 *
 * The key is not bound to user authentication, so a saved server can reconnect
 * (and background playback resume) with the screen locked.
 */
class KeystorePasswordCipher(private val alias: String = DEFAULT_ALIAS) : PasswordCipher {

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // The IV is generated per encryption, so it travels with the ciphertext.
        // It is not a secret.
        val iv = cipher.iv
        val packed = ByteArray(1 + iv.size + ciphertext.size)
        packed[0] = iv.size.toByte()
        iv.copyInto(packed, 1)
        ciphertext.copyInto(packed, 1 + iv.size)
        return MARKER + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    override fun decrypt(stored: String): String? {
        if (!stored.startsWith(MARKER)) return null
        return runCatching {
            val packed = Base64.decode(stored.removePrefix(MARKER), Base64.NO_WRAP)
            val ivSize = packed[0].toInt()
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val ciphertext = packed.copyOfRange(1 + ivSize, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keystore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // A saved server reconnects from the UI, and playback resumes from
                // a service with the screen locked, so the key must be usable
                // without the user being present.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "olo-net-passwords"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        // Marks a value this cipher produced, so a legacy plaintext password
        // (stored before encryption existed) is told apart and migrated rather
        // than mis-decrypted into gibberish.
        const val MARKER = "enc1:"
    }
}
