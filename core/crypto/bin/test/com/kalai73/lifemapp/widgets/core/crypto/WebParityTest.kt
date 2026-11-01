package com.kalai73.lifemapp.widgets.core.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Decrypts values produced by the web app's Web Crypto code (lifeMappWeb
 * frontend/src/lib/encryption/__tests__/fixtures/android-parity.json, copied to src/test/resources).
 */
class WebParityTest {
    private val fixture: JsonObject = run {
        val dir = System.getProperty("fixtures.dir")
        val text = if (dir != null) File(dir, "android-parity.json").readText()
        else javaClass.getResource("/android-parity.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject
    }
    private val password = fixture.str("password")
    private val wrapped = fixture["wrapped"]!!.jsonObject
    private val legacy = fixture["legacy"]!!.jsonObject
    private val plaintexts = fixture["plaintexts"]!!.jsonObject

    @Test
    fun `PBKDF2 derives the same key as Web Crypto`() {
        assertEquals(wrapped.str("passwordDerivedKey"), Pbkdf2.deriveKey(password, wrapped.str("passphrase_salt")))
        assertEquals(legacy.str("passwordDerivedKey"), Pbkdf2.deriveKey(password, legacy.str("passphrase_salt")))
    }

    @Test
    fun `password unwraps the data key and every field decrypts`() {
        val result = KeyEnvelope.unlock(password, wrapped.str("passphrase_salt"), wrapped["encryption_metadata"]!!.jsonObject)
        val dataKey = assertInstanceOf(KeyEnvelope.Result.Unlocked::class.java, result).dataKey
        assertEquals(wrapped.str("dataKey"), dataKey)

        val fields = wrapped["fields"]!!.jsonObject
        for ((name, text) in plaintexts) {
            assertEquals(text.jsonPrimitive.content, EncryptedField.decrypt(fields.str(name), dataKey), name)
        }
    }

    @Test
    fun `wrong password is rejected`() {
        val result = KeyEnvelope.unlock("not-the-password", wrapped.str("passphrase_salt"), wrapped["encryption_metadata"]!!.jsonObject)
        assertEquals(KeyEnvelope.Result.WrongPassword, result)
    }

    @Test
    fun `legacy account uses the password-derived key, verified by key_check`() {
        val result = KeyEnvelope.unlock(password, legacy.str("passphrase_salt"), legacy["encryption_metadata"]!!.jsonObject)
        val dataKey = assertInstanceOf(KeyEnvelope.Result.Unlocked::class.java, result).dataKey
        assertEquals(legacy.str("passwordDerivedKey"), dataKey)
        assertEquals(plaintexts.str("asciiTitle"), EncryptedField.decrypt(legacy.str("sample"), dataKey))
    }

    @Test
    fun `legacy account without key_check is verified against existing ciphertext`() {
        val sample = EncryptedField.parse(legacy.str("sample"))!!
        val ok = KeyEnvelope.unlock(password, legacy.str("passphrase_salt"), JsonObject(emptyMap())) { listOf(sample) }
        assertInstanceOf(KeyEnvelope.Result.Unlocked::class.java, ok)
        val wrong = KeyEnvelope.unlock("nope", legacy.str("passphrase_salt"), JsonObject(emptyMap())) { listOf(sample) }
        assertEquals(KeyEnvelope.Result.WrongPassword, wrong)
    }

    @Test
    fun `missing salt means encryption is not configured`() {
        assertEquals(KeyEnvelope.Result.NotConfigured, KeyEnvelope.unlock(password, null, null))
    }

    @Test
    fun `values this app encrypts open with the web layout`() {
        val dataKey = wrapped.str("dataKey")
        val e = Aes.encrypt("round trip ✓", dataKey)
        assertEquals("round trip ✓", Aes.decrypt(e, dataKey))
        assertEquals(16, Aes.unb64(e.tag).size)
        assertEquals(12, Aes.unb64(e.iv).size)
    }

    @Test
    fun `fields this app writes are self-contained JSON like the web's`() {
        val dataKey = wrapped.str("dataKey")
        val field = EncryptedField.encrypt("Call Ana ✓", dataKey)
        assertEquals(setOf("encrypted", "iv", "tag"), Json.parseToJsonElement(field).jsonObject.keys)
        assertEquals("Call Ana ✓", EncryptedField.decrypt(field, dataKey))
    }

    @Test
    fun `double-stringified fields and garbage are handled`() {
        val dataKey = wrapped.str("dataKey")
        val field = wrapped["fields"]!!.jsonObject.str("asciiTitle")
        val doubled = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(field))
        assertEquals(plaintexts.str("asciiTitle"), EncryptedField.decrypt(doubled, dataKey))
        assertNull(EncryptedField.decrypt("plain text", dataKey))
        assertNull(EncryptedField.decrypt(null, dataKey))
        val tampered = EncryptedField.parse(field)!!.copy(tag = Aes.b64(ByteArray(16)))
        assertNull(Aes.decryptOrNull(tampered, dataKey))
    }

    @Test
    fun `PBKDF2 matches RFC 7914 test vector`() {
        // RFC 7914 §11: PBKDF2-HMAC-SHA256, P="passwd", S="salt", c=1, dkLen=64
        val out = Pbkdf2.derive("passwd".toByteArray(), "salt".toByteArray(), 1, 64)
        val hex = out.joinToString("") { "%02x".format(it) }
        assertTrue(hex.startsWith("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"), hex)
    }

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
}
