package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** LIFE MAPP tasks, and Todoist tasks closed with the user's own API key. */
class TasksApi internal constructor(client: ApiClient) : ApiArea(client) {
    @Serializable private data class CompleteBody(val completed: Boolean)

    /** Marks a LIFE MAPP task completed; the server creates the next instance of recurring tasks. */
    suspend fun completeTask(taskId: String) {
        authorized { token ->
            http.put("${config.webUrl}/api/tasks/$taskId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(CompleteBody(true))
            }
        }
    }

    /**
     * A LIFE MAPP task's whole title and description (GET /api/tasks/{id}): the snapshot cuts long descriptions,
     * so it's no base to edit from. Null when the task no longer exists.
     */
    suspend fun taskContent(taskId: String): StoredTaskContent? {
        val response = authorized(accept = { it.isSuccess() || it == HttpStatusCode.NotFound }) { token ->
            http.get("${config.webUrl}/api/tasks/$taskId") { bearerAuth(token) }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        val task = response.body<JsonObject>()["task"] as? JsonObject ?: return null
        fun text(name: String) = (task[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        return StoredTaskContent(
            title = text("title"),
            encryptedTitle = text("encrypted_title"),
            description = text("description"),
            encryptedDescription = text("encrypted_description"),
        )
    }

    /**
     * Updates fields of a LIFE MAPP task (PUT /api/tasks/{id}, the route the web's task modal saves through); a
     * JsonNull clears one.
     */
    suspend fun updateTask(taskId: String, fields: JsonObject) {
        authorized { token ->
            http.put("${config.webUrl}/api/tasks/$taskId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(fields)
            }
        }
    }

    /**
     * The user's Todoist API key as the web settings store it: the plain column, or end-to-end encrypted
     * (EncryptedField JSON) when encryption is on; legacy rows keep the encrypted one in the encrypted_settings JSONB.
     * The legacy user_settings column is gone from the table: selecting it fails the whole read (400).
     */
    suspend fun todoistApiKey(userId: String): StoredApiKey {
        val response = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_user_settings") {
                supabase(token)
                parameter("select", "task_api_key,encrypted_api_key,encrypted_settings")
                parameter("user_id", "eq.$userId")
            }
        }
        val row = response.body<JsonArray>().firstOrNull() as? JsonObject ?: return StoredApiKey(null, null)
        // An encrypted key can be stored as a JSON object rather than its string form
        fun keyText(element: kotlinx.serialization.json.JsonElement?): String? = when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> element.toString()
            else -> null
        }?.takeIf { it.isNotBlank() }
        return StoredApiKey(
            plain = keyText(row["task_api_key"]),
            encrypted = keyText(row["encrypted_api_key"]) ?: keyText(blob(row["encrypted_settings"])?.get("task_manager_api_key")),
        )
    }

    /**
     * Closes a Todoist task with the user's own API key, straight at Todoist as the web's /api/todoist proxy does
     * (the browser needs the proxy for CORS only). Todoist moves a recurring task to its next date.
     */
    suspend fun closeTodoistTask(apiKey: String, taskId: String) {
        val response = http.post("$TODOIST_API_BASE/tasks/$taskId/close") { bearerAuth(apiKey) }
        if (!response.status.isSuccess()) throw ServerException(response.status.value)
    }

    companion object {
        const val TODOIST_API_BASE = "https://api.todoist.com/api/v1"
    }
}
