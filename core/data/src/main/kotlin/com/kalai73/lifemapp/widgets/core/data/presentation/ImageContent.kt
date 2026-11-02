package com.kalai73.lifemapp.widgets.core.data.presentation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json

/**
 * An image item's (is_image subsubdomain's) content, as the web stores it (lib/images/image-content-utils.ts):
 * `[IMG:<uri>]`, or the legacy web JSON `{"type":"image","uri":"<uri>"}`.
 */
object ImageContent {
    /** The picture's URI, null when [content] isn't image content */
    fun uri(content: String?): String? {
        val trimmed = content?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("[IMG:") && trimmed.endsWith("]")) return trimmed.substring(5, trimmed.length - 1).trim().ifEmpty { null }
        val json = runCatching { Json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull() ?: return null
        if ((json["type"] as? JsonPrimitive)?.contentOrNull != "image") return null
        return (json["uri"] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null }
    }

    /** Text content (HTML) that embeds a picture, which the web's item header marks with an image icon too */
    fun embedsImage(html: String?): Boolean = html != null && IMG_TAG.containsMatchIn(html)

    private val IMG_TAG = Regex("<img\\b", RegexOption.IGNORE_CASE)
}
