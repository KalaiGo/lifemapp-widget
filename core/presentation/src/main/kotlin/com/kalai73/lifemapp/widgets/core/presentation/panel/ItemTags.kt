package com.kalai73.lifemapp.widgets.core.presentation.panel

/** Port of the web's lib/domains/tags.ts: built-in tags, user tags ("c-…") and an item's effective tags. */
object ItemTags {
    val BUILTIN = listOf("me", "work", "family")
    const val DEFAULT_TAG = "me"
    private val CUSTOM_ID = Regex("^c-[a-z0-9]{4,24}$")

    fun isItemTag(tag: String): Boolean = tag in BUILTIN || CUSTOM_ID.matches(tag)

    private fun clean(tags: List<String>): List<String> = tags.filter(::isItemTag).distinct()

    /** A topic's tags, never empty: its own list, else "me" (normalizeSubdomainTags) */
    fun topicTags(tags: List<String>): List<String> = clean(tags).ifEmpty { listOf(DEFAULT_TAG) }

    /** effectiveItemTags: the topic's tags, then the item's own */
    fun effective(topicTags: List<String>, itemTags: List<String>): List<String> = clean(topicTags(topicTags) + itemTags)
}
