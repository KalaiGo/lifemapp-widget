package com.kalai73.lifemapp.widgets.core.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// What LifeMappApi reads and sends, besides the snapshot (core:model)

/** Key material for unlocking end-to-end encryption (see KeyEnvelope). */
data class KeyMaterial(val encryptionEnabled: Boolean, val salt: String?, val envelope: JsonObject?)

/** An item's stored text: [content] (HTML) or, end-to-end encrypted, [encryptedContent] (EncryptedField JSON). */
data class StoredItemContent(
    val content: String?,
    val encryptedContent: String?,
    val isImage: Boolean,
    /** ISO timestamp */
    val createdAt: String? = null,
)

/**
 * A task's stored title and description (HTML): plain, or end-to-end encrypted (EncryptedField JSON), the plain
 * column then holding the web's placeholder.
 */
data class StoredTaskContent(
    val title: String?,
    val encryptedTitle: String?,
    val description: String?,
    val encryptedDescription: String?,
)

/** A life domain's stored rating: [rating], or (end-to-end encrypted) [encryptedRating] with the column at 0. */
data class StoredRating(val id: String, val position: Int, val rating: Double, val encryptedRating: String?)

/** An API key from the settings row: [plain], or [encrypted] with the data key. */
data class StoredApiKey(val plain: String?, val encrypted: String?)

/** A task or reminder in what was said, as /api/parse-voice-items splits it. */
@Serializable
data class VoiceItem(val text: String, val intent: VoiceIntent)

@Serializable
enum class VoiceIntent {
    @SerialName("task") TASK,
    @SerialName("reminder") REMINDER,
}

sealed interface VoiceParse {
    data class Items(val items: List<VoiceItem>) : VoiceParse
    /** The month's AI credits are used up; nothing was parsed */
    data object NoCredits : VoiceParse
}

/** [encryptedText]: the text encrypted with the data key (EncryptedField), for accounts with encryption on. */
@Serializable
data class VoiceItemToFile(val text: String, val intent: VoiceIntent, val encryptedText: String? = null)

@Serializable
data class VoiceItemResult(
    val ok: Boolean,
    /** Where it went: a reminder becomes a task when the account has no Reminders topic */
    val kind: VoiceIntent = VoiceIntent.TASK,
    val reason: VoiceFailure? = null,
)

@Serializable
enum class VoiceFailure {
    /** Encryption is on and this device isn't unlocked */
    @SerialName("locked") LOCKED,
    /** The plan's reminders limit is reached */
    @SerialName("limit") LIMIT,
    /** Todoist / Google Tasks isn't connected (or its access expired) */
    @SerialName("not-connected") NOT_CONNECTED,
    /** A task manager the widget can't file into yet (Microsoft To Do, TickTick) */
    @SerialName("unsupported") UNSUPPORTED,
    @SerialName("failed") FAILED,
}
