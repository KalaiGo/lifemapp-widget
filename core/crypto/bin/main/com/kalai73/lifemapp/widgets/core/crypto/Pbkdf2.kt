package com.kalai73.lifemapp.widgets.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256, matching lifeMappWeb `key-derivation.ts` (Web Crypto deriveKey):
 * 100 000 iterations, base64 salt (16 bytes), 32-byte output returned as base64.
 *
 * Implemented on HmacSHA256 directly instead of a SecretKeyFactory: providers differ in how they turn
 * the char[] password into bytes (UTF-8 vs. 8-bit), while Web Crypto always hashes the UTF-8 bytes.
 */
object Pbkdf2 {
    const val ITERATIONS = 100_000
    private const val KEY_BYTES = 32

    fun deriveKey(passphrase: String, base64Salt: String, iterations: Int = ITERATIONS): String =
        Aes.b64(derive(passphrase.toByteArray(Charsets.UTF_8), Aes.unb64(base64Salt), iterations, KEY_BYTES))

    internal fun derive(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // An empty HMAC key is legal in PBKDF2 but SecretKeySpec rejects it; HMAC zero-pads keys,
        // so a single zero byte is equivalent
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, "HmacSHA256"))
        val hLen = mac.macLength
        val blocks = (length + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            t.copyInto(out, (block - 1) * hLen)
        }
        return out.copyOf(length)
    }
}
