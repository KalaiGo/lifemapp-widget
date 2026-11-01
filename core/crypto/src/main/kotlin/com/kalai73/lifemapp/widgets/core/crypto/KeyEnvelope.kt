package com.kalai73.lifemapp.widgets.core.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Port of lifeMappWeb `key-envelope.ts` (read side only — this app never rewrites the envelope).
 *
 * The data encryption key (DEK) is a random AES-256 key, stored in `lm_user_settings.encryption_metadata`
 * wrapped under PBKDF2(password, passphrase_salt) as `password_wrap`, with a `key_check` (a constant
 * encrypted with the DEK) to verify a candidate DEK. The wrap's plaintext is the DEK's base64 string.
 *
 * Legacy accounts (before `password_wrap` existed) use PBKDF2(password, salt) itself as the DEK.
 */
object KeyEnvelope {
    const val KEY_CHECK_PLAINTEXT = "life-mapp-key-check-v1"

    sealed interface Result {
        data class Unlocked(val dataKey: String) : Result
        data object WrongPassword : Result
        data object NotConfigured : Result
    }

    /**
     * Resolves the DEK from the password, like `resolveDataKey` on the web.
     *
     * @param salt `lm_user_settings.passphrase_salt`
     * @param envelope `lm_user_settings.encryption_metadata`
     * @param legacySamples ciphertexts written with the user's key, only consulted for legacy accounts without
     *   a key_check (same fallback as `verifyLegacyKey`)
     */
    fun unlock(
        password: String,
        salt: String?,
        envelope: JsonObject?,
        legacySamples: () -> List<Aes.EncryptedData> = { emptyList() },
    ): Result {
        if (salt.isNullOrEmpty()) return Result.NotConfigured
        val passwordKey = Pbkdf2.deriveKey(password, salt)

        parseWrap(envelope?.get("password_wrap"))?.let { wrap ->
            val dataKey = Aes.decryptOrNull(wrap, passwordKey)
            return if (dataKey != null) Result.Unlocked(dataKey) else Result.WrongPassword
        }

        // Legacy account: the password-derived key is the DEK
        val keyCheck = parseEncrypted(envelope?.get("key_check"))
        val valid = if (keyCheck != null) {
            verifyKeyCheck(passwordKey, keyCheck)
        } else {
            val samples = legacySamples()
            samples.isEmpty() || samples.any { Aes.decryptOrNull(it, passwordKey) != null }
        }
        return if (valid) Result.Unlocked(passwordKey) else Result.WrongPassword
    }

    fun verifyKeyCheck(dataKey: String, check: Aes.EncryptedData): Boolean =
        Aes.decryptOrNull(check, dataKey) == KEY_CHECK_PLAINTEXT

    /** The envelope's key_check, to re-verify a stored DEK after a password change on the web. */
    fun keyCheckOf(envelope: JsonObject?): Aes.EncryptedData? = parseEncrypted(envelope?.get("key_check"))

    private fun parseWrap(value: Any?): Aes.EncryptedData? {
        val obj = value as? JsonObject ?: return null
        val key = obj.string("encrypted_key") ?: return null
        val iv = obj.string("iv") ?: return null
        val tag = obj.string("tag") ?: return null
        return Aes.EncryptedData(key, iv, tag)
    }

    private fun parseEncrypted(value: Any?): Aes.EncryptedData? {
        val obj = value as? JsonObject ?: return null
        val encrypted = obj.string("encrypted") ?: return null
        val iv = obj.string("iv") ?: return null
        val tag = obj.string("tag") ?: return null
        return Aes.EncryptedData(encrypted, iv, tag)
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}

/**
 * Self-contained encrypted fields as the web stores them (tasks' encrypted_title, encrypted_quick_links,
 * domains' encrypted_rating, …): a JSON string `{"encrypted":"…","iv":"…","tag":"…"}`. Some writers
 * stringify that JSON twice; both forms are accepted (like `cryptoHelper.decrypt` on the web).
 */
object EncryptedField {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(value: String?): Aes.EncryptedData? {
        if (value.isNullOrBlank()) return null
        return try {
            var element = json.parseToJsonElement(value)
            if (element is JsonPrimitive && element.isString) element = json.parseToJsonElement(element.content)
            val obj = element.jsonObject
            val encrypted = (obj["encrypted"] as? JsonPrimitive)?.contentOrNull ?: return null
            val iv = (obj["iv"] as? JsonPrimitive)?.contentOrNull ?: return null
            val tag = (obj["tag"] as? JsonPrimitive)?.contentOrNull ?: return null
            Aes.EncryptedData(encrypted, iv, tag)
        } catch (_: Exception) {
            null
        }
    }

    /** Plaintext, or null when the value isn't an encrypted field or doesn't open with this key. */
    fun decrypt(value: String?, dataKey: String): String? = parse(value)?.let { Aes.decryptOrNull(it, dataKey) }
}
