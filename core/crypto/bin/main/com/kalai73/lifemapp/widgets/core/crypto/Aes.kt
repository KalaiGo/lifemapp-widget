package com.kalai73.lifemapp.widgets.core.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM, byte-compatible with lifeMappWeb `frontend/src/lib/encryption/aes.ts`:
 * 12-byte IV, 128-bit tag stored separately from the ciphertext, every part base64 (standard alphabet),
 * keys passed around as base64 strings of 32 raw bytes, plaintext UTF-8.
 */
object Aes {
    private const val TAG_BYTES = 16
    private const val IV_BYTES = 12
    private val random = SecureRandom()

    data class EncryptedData(val encrypted: String, val iv: String, val tag: String)

    class DecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun encrypt(plaintext: String, base64Key: String, iv: ByteArray = ByteArray(IV_BYTES).also(random::nextBytes)): EncryptedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(base64Key), GCMParameterSpec(TAG_BYTES * 8, iv))
        val out = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertext = out.copyOfRange(0, out.size - TAG_BYTES)
        val tag = out.copyOfRange(out.size - TAG_BYTES, out.size)
        return EncryptedData(b64(ciphertext), b64(iv), b64(tag))
    }

    /** @throws DecryptionException on a wrong key, a tampered value or malformed input */
    fun decrypt(encrypted: String, base64Key: String, iv: String, tag: String): String {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec(base64Key), GCMParameterSpec(TAG_BYTES * 8, unb64(iv)))
            return String(cipher.doFinal(unb64(encrypted) + unb64(tag)), Charsets.UTF_8)
        } catch (e: Exception) {
            throw DecryptionException("AES decryption failed: ${e.message}", e)
        }
    }

    fun decrypt(data: EncryptedData, base64Key: String): String = decrypt(data.encrypted, base64Key, data.iv, data.tag)

    fun decryptOrNull(data: EncryptedData, base64Key: String): String? =
        try { decrypt(data, base64Key) } catch (_: DecryptionException) { null }

    private fun keySpec(base64Key: String): SecretKeySpec {
        val raw = unb64(base64Key)
        require(raw.size == 32) { "AES-256 key must be 32 bytes, got ${raw.size}" }
        return SecretKeySpec(raw, "AES")
    }

    internal fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    internal fun unb64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
