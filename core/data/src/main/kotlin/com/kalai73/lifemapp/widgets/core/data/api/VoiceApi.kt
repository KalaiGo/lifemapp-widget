package com.kalai73.lifemapp.widgets.core.data.api

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Voice input: splitting a transcript into tasks and reminders, and filing them. */
class VoiceApi internal constructor(client: ApiClient) : ApiArea(client) {
    @Serializable private data class ParseVoiceBody(val transcript: String, val chargeCredits: Boolean)
    @Serializable private data class ParseVoiceResponse(val items: List<VoiceItem> = emptyList(), val limitReached: Boolean = false)

    /**
     * Splits a transcript recognized on the device into tasks and reminders (Gemini, on the server). Charged as
     * the voice input's AI credits — the device skipped the web's /api/transcribe, which charges them otherwise.
     * An empty list means nothing was understood (not charged).
     */
    suspend fun parseVoiceItems(transcript: String): VoiceParse {
        val response = authorized(accept = { it.isSuccess() || it == HttpStatusCode.TooManyRequests }) { token ->
            http.post("${config.webUrl}/api/parse-voice-items") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(ParseVoiceBody(transcript.trim().take(MAX_VOICE_TRANSCRIPT_CHARS), chargeCredits = true))
            }
        }
        val body = response.body<ParseVoiceResponse>()
        if (response.status == HttpStatusCode.TooManyRequests) {
            if (body.limitReached) return VoiceParse.NoCredits
            throw ServerException(response.status.value)
        }
        return VoiceParse.Items(body.items.filter { it.text.isNotBlank() })
    }

    @Serializable private data class VoiceItemsBody(val items: List<VoiceItemToFile>)
    @Serializable private data class VoiceItemsResponse(val results: List<VoiceItemResult> = emptyList())

    /** Files voice items in the user's task manager and Reminders (/api/widget/voice-items); one result per item. */
    suspend fun fileVoiceItems(items: List<VoiceItemToFile>): List<VoiceItemResult> {
        val response = authorized { token ->
            http.post("${config.webUrl}/api/widget/voice-items") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(VoiceItemsBody(items))
            }
        }
        return response.body<VoiceItemsResponse>().results
    }

    companion object {
        /** As /api/parse-voice-items accepts */
        const val MAX_VOICE_TRANSCRIPT_CHARS = 5000
    }
}
