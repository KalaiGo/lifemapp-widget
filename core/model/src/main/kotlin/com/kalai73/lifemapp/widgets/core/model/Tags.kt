package com.kalai73.lifemapp.widgets.core.model

import kotlinx.serialization.Serializable

/**
 * A user-created context tag, stored in `lm_user_settings.preferences.custom_tags`
 * (mirror of `CustomTag` in `lifeMappWeb/frontend/src/types/domain-map.types.ts`).
 */
@Serializable
data class CustomTag(
    val id: String,
    val name: String,
    val color: String = DEFAULT_COLOR,
) {
    companion object {
        const val TAG_ALL = "all"
        const val DEFAULT_COLOR = "emerald"

        /** Color choices of a user-created tag, in picker order (tags.ts CUSTOM_TAG_COLORS). */
        val COLORS: List<String> = listOf(
            "emerald", "amber", "violet", "teal", "orange", "fuchsia", "lime", "indigo", "slate",
        )
    }
}
