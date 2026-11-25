package com.kalai73.lifemapp.widgets.core.presentation.panel

import com.kalai73.lifemapp.widgets.core.model.CustomTag
import kotlin.random.Random

/** Validation error for a user-created tag name (tags.ts TagNameError). */
enum class TagNameError { EMPTY, TOO_LONG, DUPLICATE }

/**
 * Full port of the web's `lib/domains/tags.ts`: built-in tags ("me", "work", "family"), user-created
 * tags ("c-…"), inheritance, Today/Monthly visibility filtering, and task label mapping.
 */
object ItemTags {
    val BUILTIN: List<String> = listOf("me", "work", "family")
    const val DEFAULT_TAG: String = "me"
    const val FILTER_ALL: String = CustomTag.TAG_ALL

    const val MAX_CUSTOM_TAGS: Int = 12
    const val MAX_TAG_NAME_LENGTH: Int = 20

    val CUSTOM_TAG_COLORS: List<String> = CustomTag.COLORS
    const val DEFAULT_TAG_COLOR: String = CustomTag.DEFAULT_COLOR

    private val CUSTOM_ID = Regex("^c-[a-z0-9]{4,24}$")
    private val LEADING_SIGIL = Regex("^[@#]")

    /** Task label names (any case, optional leading @ or #) that tag a task, in every supported language. */
    private val TASK_TAG_LABELS: Map<String, List<String>> = mapOf(
        "me" to listOf("me", "ich", "yo"),
        "work" to listOf("work", "arbeit", "trabajo"),
        "family" to listOf("family", "familie", "familia"),
    )

    fun isBuiltinTag(tag: String?): Boolean = tag != null && tag in BUILTIN

    fun isCustomTagId(tag: String?): Boolean = tag != null && CUSTOM_ID.matches(tag)

    fun isItemTag(tag: String): Boolean = isBuiltinTag(tag) || isCustomTagId(tag)

    /** Color for a new tag: the first one none of the user's tags uses yet. */
    fun nextTagColor(customTags: List<CustomTag>): String {
        val used = customTags.mapTo(mutableSetOf()) { it.color }
        return CUSTOM_TAG_COLORS.firstOrNull { it !in used } ?: DEFAULT_TAG_COLOR
    }

    /** New id for a user-created tag (`c-` + base36 timestamp tail + 8 random base36 chars). */
    fun newCustomTagId(nowMs: Long = System.currentTimeMillis(), random: Random = Random.Default): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
        val ts = nowMs.toString(36).takeLast(4).padStart(4, '0')
        val rand = buildString(8) { repeat(8) { append(chars[random.nextInt(chars.length)]) } }
        return "c-$ts$rand"
    }

    internal fun labelKey(label: String): String =
        label.trim().replaceFirst(LEADING_SIGIL, "").lowercase()

    /**
     * Stored list of user-created tags, cleaned: invalid ids/names dropped, duplicate ids and names dropped,
     * names trimmed and cut to [MAX_TAG_NAME_LENGTH], unknown colors reset to [DEFAULT_TAG_COLOR], capped at [MAX_CUSTOM_TAGS].
     */
    fun normalizeCustomTags(tags: List<CustomTag>?): List<CustomTag> {
        if (tags.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<CustomTag>()
        val ids = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        for (entry in tags) {
            if (!isCustomTagId(entry.id) || entry.id in ids) continue
            val trimmed = entry.name.trim().take(MAX_TAG_NAME_LENGTH)
            val key = labelKey(trimmed)
            if (key.isEmpty() || key in names) continue
            val safeColor = if (entry.color in CUSTOM_TAG_COLORS) entry.color else DEFAULT_TAG_COLOR
            ids.add(entry.id)
            names.add(key)
            result.add(CustomTag(id = entry.id, name = trimmed, color = safeColor))
            if (result.size >= MAX_CUSTOM_TAGS) break
        }
        return result
    }

    /** Every tag the user can pick, in display order: the predefined ones, then their own. */
    fun allTags(customTags: List<CustomTag>): List<String> = BUILTIN + customTags.map { it.id }

    /** Filter choices in display order: "all", the predefined tags, then the user's own. */
    fun tagFilters(customTags: List<CustomTag>): List<String> = listOf(FILTER_ALL) + allTags(customTags)

    /** Whether a tag still exists for the user: a predefined one or one of their own. */
    fun isKnownTag(tag: String?, customTags: List<CustomTag>): Boolean =
        isBuiltinTag(tag) || (tag != null && customTags.any { it.id == tag })

    /** Filter choice to store: a valid tag or "all". */
    fun normalizeTagFilter(value: String?): String =
        if (isBuiltinTag(value) || isCustomTagId(value)) value!! else FILTER_ALL

    /** Resolved active tag filter, or null for "all" or a deleted custom tag. */
    fun resolveFilter(activeTag: String?, customTags: List<CustomTag>): String? {
        val normalized = normalizeTagFilter(activeTag)
        return if (normalized != FILTER_ALL && isKnownTag(normalized, customTags)) normalized else null
    }

    /**
     * Problem with a name for a user-created tag, or null when it can be used. Names may not repeat
     * a predefined tag (in any supported language) or another of the user's tags, ignoring case.
     */
    fun tagNameError(name: String, customTags: List<CustomTag>, editingId: String? = null): TagNameError? {
        val key = labelKey(name)
        if (key.isEmpty()) return TagNameError.EMPTY
        if (name.trim().length > MAX_TAG_NAME_LENGTH) return TagNameError.TOO_LONG
        if (TASK_TAG_LABELS.values.any { key in it }) return TagNameError.DUPLICATE
        if (customTags.any { it.id != editingId && labelKey(it.name) == key }) return TagNameError.DUPLICATE
        return null
    }

    /** Resolve a single tag value to a valid tag, "me" by default. */
    fun normalizeTag(tag: String?): String = if (tag != null && isItemTag(tag)) tag else DEFAULT_TAG

    private fun clean(tags: List<String>?): List<String> =
        tags.orEmpty().filter(::isItemTag).distinct()

    /** A topic's tags, never empty: its own list, else [legacyTag], else "me" (normalizeSubdomainTags). */
    fun topicTags(tags: List<String>?, legacyTag: String? = null): List<String> {
        val list = if (tags != null) clean(tags) else if (legacyTag != null && isItemTag(legacyTag)) listOf(legacyTag) else emptyList()
        return list.ifEmpty { listOf(DEFAULT_TAG) }
    }

    /** Own tags of a stored item (on top of its topic's): its list, else [legacyTag], else empty (normalizeItemTags). */
    fun itemTags(tags: List<String>?, legacyTag: String? = null): List<String> = when {
        tags != null -> clean(tags)
        legacyTag != null && isItemTag(legacyTag) -> listOf(legacyTag)
        else -> emptyList()
    }

    /** effectiveItemTags: the topic's tags, then the item's own. */
    fun effective(topicTags: List<String>?, itemTags: List<String>?): List<String> =
        clean(topicTags(topicTags) + itemTags(itemTags))

    /** Whether two tag lists hold the same tags, in any order. */
    fun sameTags(a: List<String>?, b: List<String>?): Boolean =
        a.orEmpty().toSet() == b.orEmpty().toSet()

    /** List with [tag] added, or removed when it is already in it. */
    fun toggleTag(tags: List<String>, tag: String): List<String> =
        if (tag in tags) tags.filterNot { it == tag } else tags + tag

    /**
     * Items of a topic to show under [filter].
     * A topic carrying [filter] shows all its items; otherwise only items tagged with it.
     */
    fun <T> filterItemsByTag(topicTags: List<String>?, items: List<T>, itemTagsOf: (T) -> List<String>?, filter: String): List<T> {
        if (filter in topicTags(topicTags)) return items
        return items.filter { filter in itemTags(itemTagsOf(it)) }
    }

    /** Whether one item is shown under [filter]: its topic carries the tag, or the item itself does. */
    fun isItemVisibleForTag(topicTags: List<String>?, itemTags: List<String>?, filter: String): Boolean =
        filter in effective(topicTags, itemTags)

    /** A topic is shown when it carries [filter] or holds at least one item tagged with it. */
    fun <T> isTopicVisibleForTag(topicTags: List<String>?, items: List<T>, itemTagsOf: (T) -> List<String>?, filter: String): Boolean =
        filter in topicTags(topicTags) || items.any { filter in itemTags(itemTagsOf(it)) }

    /** Tags of a new topic: "me". */
    fun tagsForNewTopic(): List<String> = listOf(DEFAULT_TAG)

    /** Own tags of a new item, so it counts as "me": none when its topic carries "me", else ["me"]. */
    fun tagsForNewItem(topicTags: List<String>?): List<String> =
        if (DEFAULT_TAG in topicTags(topicTags)) emptyList() else listOf(DEFAULT_TAG)

    /** Own tags to store for an item whose tags the user picked: those its topic does not already give it. */
    fun itemTagsToStore(topicTags: List<String>?, picked: List<String>): List<String> {
        val inherited = topicTags(topicTags)
        return clean(picked).filterNot { it in inherited }
    }

    /** Alias kept for existing callers (ItemEditorModal). */
    fun toStore(topicTags: List<String>?, picked: List<String>): List<String> = itemTagsToStore(topicTags, picked)

    /** Whether a task label is a tag label: a predefined one (in any supported language) or a user-created tag's name. */
    fun isTaskTagLabel(label: String, customTags: List<CustomTag> = emptyList()): Boolean {
        val key = labelKey(label)
        return TASK_TAG_LABELS.values.any { key in it } || customTags.any { labelKey(it.name) == key }
    }

    /**
     * Tag of a task, read from its labels: "work", "family", or a user-created tag (labelled with its name),
     * checked in that order, else "me". Tasks without labels (e.g. Google Tasks) are "me".
     */
    fun taskTag(labels: List<String>?, customTags: List<CustomTag> = emptyList()): String {
        val names = labels.orEmpty().mapTo(mutableSetOf(), ::labelKey)
        for (tag in listOf("work", "family")) {
            if (TASK_TAG_LABELS.getValue(tag).any { it in names }) return tag
        }
        return customTags.firstOrNull { labelKey(it.name) in names }?.id ?: DEFAULT_TAG
    }

    /** Context tags carried as labels on a task, for rendering TagBadges on task rows. */
    fun taskContextTags(labels: List<String>?, customTags: List<CustomTag> = emptyList()): List<String> =
        labels.orEmpty()
            .filter { isTaskTagLabel(it, customTags) }
            .map { taskTag(listOf(it), customTags) }
            .distinct()

    private fun taskLabelFor(tag: String, customTags: List<CustomTag>): String? = when {
        isBuiltinTag(tag) -> tag
        else -> customTags.firstOrNull { it.id == tag }?.name
    }

    /**
     * Labels of a task set to one tag: any tag label is replaced by the tag's own ("work", or a custom tag's name),
     * first in the list; "me" just removes them, since unlabelled tasks are "me". Keeps at most [max] labels.
     */
    fun withTaskTag(labels: List<String>, tag: String, customTags: List<CustomTag> = emptyList(), max: Int = 3): List<String> {
        val others = labels.filterNot { isTaskTagLabel(it, customTags) }
        val label = if (tag == DEFAULT_TAG) null else taskLabelFor(tag, customTags)
        return (if (label != null) listOf(label) + others else others).take(max)
    }
}
