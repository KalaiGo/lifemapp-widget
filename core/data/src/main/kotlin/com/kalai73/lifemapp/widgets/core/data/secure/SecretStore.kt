package com.kalai73.lifemapp.widgets.core.data.secure

import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kalai73.lifemapp.widgets.core.model.LifeMappJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Supabase session of the signed-in user. */
@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds */
    val expiresAt: Long,
    val userId: String,
    val email: String? = null,
)

/** Everything secret this app keeps. Never leaves the device; excluded from backups. */
@Serializable
data class Secrets(
    val session: Session? = null,
    /** The unwrapped end-to-end data key (base64), present once the user unlocked encryption on this device */
    val dataKey: String? = null,
    /** User the data key belongs to; a different sign-in discards it */
    val dataKeyUserId: String? = null,
    /** Epoch millis of the last web sign-in handoff (see WebLauncher) */
    val lastWebHandoffAt: Long = 0,
    /** PKCE verifier of a Google sign-in in progress (survives the process being killed behind the Custom Tab) */
    val pendingPkceVerifier: String? = null,
)

/** Encrypts the secrets file. The Android Keystore implementation is swapped for a fake in JVM tests. */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM key held in the Android Keystore (hardware-backed where available). It doesn't require user
 * authentication, so background WorkManager jobs can decrypt titles while the phone is locked — but it lives
 * in credential-encrypted storage, so nothing here works before the first unlock after a reboot.
 */
class KeystoreSecretCipher(private val alias: String = "lifemapp.secrets.v1") : SecretCipher {
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
}

class DeviceLockedException : IllegalStateException("Device not unlocked since boot")

class SecretStore(
    private val context: Context,
    private val cipher: SecretCipher = KeystoreSecretCipher(),
    private val file: File = File(context.noBackupFilesDir, "secrets.bin"),
) {
    private val mutex = Mutex()
    @Volatile private var cached: Secrets? = null

    /** False between a reboot and the first unlock (Direct Boot): the Keystore key and our files are unavailable. */
    fun isDeviceUnlocked(): Boolean {
        return context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
    }

    suspend fun read(): Secrets = mutex.withLock { readLocked() }

    suspend fun update(transform: (Secrets) -> Secrets): Secrets = mutex.withLock {
        val next = transform(readLocked())
        withContext(Dispatchers.IO) {
            val bytes = cipher.encrypt(LifeMappJson.encodeToString(Secrets.serializer(), next).toByteArray())
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        cached = next
        next
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        cached = Secrets()
    }

    private suspend fun readLocked(): Secrets {
        cached?.let { return it }
        if (!isDeviceUnlocked()) throw DeviceLockedException()
        val secrets = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext Secrets()
            try {
                LifeMappJson.decodeFromString(Secrets.serializer(), String(cipher.decrypt(file.readBytes())))
            } catch (e: Exception) {
                // Key lost (app data restored to another device, Keystore reset): start signed out
                file.delete()
                Secrets()
            }
        }
        cached = secrets
        return secrets
    }
}
