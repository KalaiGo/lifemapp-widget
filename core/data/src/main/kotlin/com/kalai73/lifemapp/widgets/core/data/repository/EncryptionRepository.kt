package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.crypto.KeyEnvelope
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface UnlockResult {
    data object Unlocked : UnlockResult
    data object WrongPassword : UnlockResult
    /** The account has no end-to-end encryption, or it hasn't been set up on the web yet */
    data object NotConfigured : UnlockResult
}

/**
 * Unlocks end-to-end encryption on this device with the account password (the same one the web asks for).
 * The data key is kept in [SecretStore] — encrypted under an Android Keystore key — so background syncs and
 * widget renders can decrypt titles without asking again.
 */
class EncryptionRepository(
    private val api: LifeMappApi,
    private val secrets: SecretStore,
) {
    suspend fun dataKey(): String? = runCatching { secrets.read().dataKey }.getOrNull()

    suspend fun unlock(password: String): UnlockResult {
        val userId = secrets.read().session?.userId ?: throw NotSignedInException()
        val material = api.keyMaterial(userId)
        if (!material.encryptionEnabled && material.salt == null) return UnlockResult.NotConfigured

        // Only legacy accounts without a wrap or key_check are verified against existing ciphertext
        val envelope = material.envelope
        val samples = if (envelope?.get("password_wrap") == null && envelope?.get("key_check") == null) {
            api.encryptedSamples(userId).mapNotNull(EncryptedField::parse)
        } else {
            emptyList()
        }
        // PBKDF2 with 100 000 iterations: keep it off the main thread
        val result = withContext(Dispatchers.Default) {
            KeyEnvelope.unlock(password, material.salt, envelope) { samples }
        }
        return when (result) {
            is KeyEnvelope.Result.Unlocked -> {
                secrets.update { it.copy(dataKey = result.dataKey, dataKeyUserId = userId) }
                UnlockResult.Unlocked
            }
            KeyEnvelope.Result.WrongPassword -> UnlockResult.WrongPassword
            KeyEnvelope.Result.NotConfigured -> UnlockResult.NotConfigured
        }
    }

    /**
     * Drops the stored key when it no longer matches the account's key_check (the password was reset with the
     * recovery key on the web, which keeps the DEK — but a full re-key would not). Offline: keeps the key.
     * @return false when the key was discarded
     */
    suspend fun verifyStoredKey(): Boolean {
        val stored = secrets.read()
        val key = stored.dataKey ?: return true
        val userId = stored.session?.userId ?: return true
        val material = runCatching { api.keyMaterial(userId) }.getOrNull() ?: return true
        val check = KeyEnvelope.keyCheckOf(material.envelope) ?: return true
        if (KeyEnvelope.verifyKeyCheck(key, check)) return true
        lock()
        return false
    }

    suspend fun lock() {
        secrets.update { it.copy(dataKey = null, dataKeyUserId = null) }
    }
}
