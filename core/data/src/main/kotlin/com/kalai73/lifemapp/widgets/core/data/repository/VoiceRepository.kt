package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.VoiceApi
import com.kalai73.lifemapp.widgets.core.data.api.ServerException
import com.kalai73.lifemapp.widgets.core.data.api.VoiceIntent
import com.kalai73.lifemapp.widgets.core.data.api.VoiceItem
import com.kalai73.lifemapp.widgets.core.data.api.VoiceItemResult
import com.kalai73.lifemapp.widgets.core.data.api.VoiceItemToFile
import com.kalai73.lifemapp.widgets.core.data.api.VoiceParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The widget's voice input, without the browser: what the web's mic does after transcribing
 * (floating-action-buttons.tsx), with speech recognized on the device.
 *
 * 1. [understand]: /api/parse-voice-items splits the transcript into tasks and reminders and charges the AI credits.
 *    If the parser fails, the whole transcript becomes one reminder, as on the web.
 * 2. The caller lets the user review a single item (as the web's dialog does); several are filed at once.
 * 3. [file]: /api/widget/voice-items files them; with encryption unlocked on this device, each text goes
 *    encrypted too, and the server stores only that.
 *
 * Network trouble ([java.io.IOException]) and signed-out sessions propagate to the caller.
 */
class VoiceRepository(
    private val api: VoiceApi,
    private val dataKey: suspend () -> String?,
) {
    sealed interface Understood {
        data class Items(val items: List<VoiceItem>) : Understood
        data object NoCredits : Understood
        /** Silence or nothing usable */
        data object Nothing : Understood
    }

    /** [asReminders]: the Integrity card's Reception, where everything said becomes reminders. */
    suspend fun understand(transcript: String, asReminders: Boolean): Understood {
        val text = transcript.trim()
        if (text.isEmpty()) return Understood.Nothing
        val parsed = try {
            api.parseVoiceItems(text)
        } catch (_: ServerException) {
            VoiceParse.Items(emptyList())
        }
        if (parsed is VoiceParse.NoCredits) return Understood.NoCredits
        var items = (parsed as VoiceParse.Items).items
        // Parser unavailable or nothing split out: one reminder, as the web does
        if (items.isEmpty()) items = listOf(VoiceItem(stripCommandPhrases(text), VoiceIntent.REMINDER))
        if (asReminders) items = items.map { it.copy(intent = VoiceIntent.REMINDER) }
        return Understood.Items(items)
    }

    suspend fun file(items: List<VoiceItem>): List<VoiceItemResult> {
        val key = dataKey()
        val toFile = withContext(Dispatchers.Default) {
            items.filter { it.text.isNotBlank() }.map {
                val text = it.text.trim()
                VoiceItemToFile(text, it.intent, encryptedText = key?.let { k -> EncryptedField.encrypt(text, k) })
            }
        }
        if (toFile.isEmpty()) return emptyList()
        return api.fileVoiceItems(toFile)
    }

    companion object {
        private val COMMAND = Regex("^(create|add|make|set|new)\\s+(a\\s+)?(reminder|task|todo|to-do)\\s*(to\\s+)?", RegexOption.IGNORE_CASE)
        private val REMIND_ME = Regex("^remind\\s+me\\s+to\\s+", RegexOption.IGNORE_CASE)

        /** "Remind me to call John" → "call John", as the web's fallback does */
        fun stripCommandPhrases(text: String): String =
            text.replace(COMMAND, "").replace(REMIND_ME, "").trim().ifEmpty { text }
    }
}
