package com.kalai73.lifemapp.widgets.core.presentation.panel

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.PlanLimits
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.presentation.WidgetPresenter
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** The side panel's life map tree, links and alerts, decrypted; built by [SidePanelPresenter.present]. */
data class PanelUi(
    val domains: List<PanelDomain> = emptyList(),
    /** Every link of every item, in tree order (extractDomainLinks) */
    val links: List<PanelLink> = emptyList(),
    /** Encryption is on and this device is locked: names and texts are null */
    val locked: Boolean = false,
    /** The account is end-to-end encrypted */
    val encrypted: Boolean = false,
    /** User tags' names by id */
    val customTags: Map<String, String> = emptyMap(),
    val limits: PlanLimits = PlanLimits.forTier("basic"),
) {
    val linkCount: Int get() = links.size

    fun domain(id: String?): PanelDomain? = id?.let { domains.firstOrNull { d -> d.id == it } }

    fun topic(id: String?): PanelTopic? = id?.let { domains.asSequence().flatMap { d -> d.topics }.firstOrNull { t -> t.id == it } }

    fun item(id: String?): PanelItem? = id?.let { domains.asSequence().flatMap { d -> d.topics }.flatMap { t -> t.items }.firstOrNull { i -> i.id == it } }

    /** Where an item lives now (domain and topic ids), after a move on another device */
    fun locate(itemId: String): Pair<String, String>? {
        for (d in domains) for (t in d.topics) if (t.items.any { it.id == itemId }) return d.id to t.id
        return null
    }

    /** Links per tag over all links, most used first (LinksContent tagCounts) */
    val tagCounts: List<Pair<String, Int>> by lazy {
        links.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}

data class PanelDomain(
    val id: String,
    val position: Int,
    /** Null when encrypted and locked */
    val name: String?,
    val infoBox: String?,
    val rating: Int?,
    val color: String?,
    val topics: List<PanelTopic>,
) {
    val linkCount: Int get() = topics.sumOf { it.linkCount }
}

data class PanelTopic(
    val id: String,
    val domainId: String,
    /** 1-based, as the web numbers topics */
    val number: Int,
    val name: String?,
    val tags: List<String>,
    /** Integrity's Reminders topic */
    val reminders: Boolean,
    val items: List<PanelItem>,
) {
    val linkCount: Int get() = items.sumOf { it.linkCount }
}

data class PanelItem(
    val id: String,
    val topicId: String,
    val domainId: String,
    val number: Int,
    val name: String?,
    val isImage: Boolean,
    /** HTML, null when it has none or is encrypted and locked */
    val content: String?,
    val hasContent: Boolean,
    val warning: Boolean,
    /** Effective tags: the topic's, then the item's own */
    val tags: List<String>,
    val reminder: PanelReminder?,
    val updatedAt: String,
    val linkCount: Int,
)

data class PanelReminder(
    /** As stored (ISO timestamp) */
    val value: String,
    val date: LocalDate,
    val rule: String?,
    val leadDays: Int,
    val taskCreated: Boolean,
    val status: ReminderStatus,
)

data class PanelLink(
    val id: String,
    val url: String,
    val hostname: String,
    /** The link's own text, else a known-shape name, else one built from the URL */
    val label: String,
    /** The link's own text ("" when none, or just the URL): untitled links may get a fetched title */
    val anchorLabel: String,
    val domainId: String,
    val domainPosition: Int,
    val topicId: String,
    val topicName: String?,
    val itemId: String,
    val itemName: String?,
    val headings: List<String>,
    val headingLevels: List<Int>,
    val tags: List<String>,
    val updatedAt: String,
)

/** What the stream is scoped to: nothing (all), a domain, a topic in it, or an item in that, plus a tag. */
data class PanelScope(
    val domainId: String? = null,
    val topicId: String? = null,
    val itemId: String? = null,
    val tag: String? = null,
    val query: String = "",
)

enum class PanelSort { STRUCTURE, NEWEST, ALPHA }

/**
 * Builds [PanelUi] from the cached snapshot, as the web's LinksContent does from its stores: the full domain list
 * (monthlyDomains, else Integrity and the Today domains), BUSINESS MAPP (20+) left out, the plan's limits applied
 * (applySubscriptionLimitsToTree), then links extracted from item HTML.
 */
object SidePanelPresenter {
    /** lib/feeds/domain-folder-merge.ts BUSINESS_DOMAIN_MIN_POSITION */
    const val BUSINESS_MIN_POSITION = 20
    private val REMINDERS_NAMES = listOf("reminders", "recordatorios", "erinnerungen")

    fun present(
        snapshot: Snapshot?,
        dataKey: String?,
        limits: PlanLimits,
        customTags: Map<String, String> = emptyMap(),
        titles: Map<String, String> = emptyMap(),
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): PanelUi {
        snapshot ?: return PanelUi(limits = limits, customTags = customTags)
        val encrypted = snapshot.settings.encryptionEnabled
        val source = snapshot.monthlyDomains.ifEmpty { listOfNotNull(snapshot.integrity) + snapshot.todayDomains }
        val cards = source.distinctBy { it.domainId }.filter { it.position < BUSINESS_MIN_POSITION }.sortedBy { it.position }
        val tree = cards.map { domain(it, dataKey, limits, today, zone) }
        val (links, counts) = extractLinks(tree, titles)
        return PanelUi(
            domains = withCounts(tree, counts),
            links = links,
            locked = encrypted && dataKey == null,
            encrypted = encrypted,
            customTags = customTags,
            limits = limits,
        )
    }

    private fun isRemindersTopic(position: Int, name: String?): Boolean =
        position == 1 && name != null && REMINDERS_NAMES.any { name.lowercase().contains(it) }

    private fun domain(card: Integrity, key: String?, limits: PlanLimits, today: LocalDate, zone: ZoneId): PanelDomain {
        fun text(plain: String?, encrypted: String?): String? =
            if (encrypted != null) key?.let { EncryptedField.decrypt(encrypted, it) } else plain
        val rating = if (card.encryptedRating != null) key?.let { EncryptedField.decrypt(card.encryptedRating, it) }?.toDoubleOrNull() else card.rating
        val topics = card.topics.take(limits.maxTopicsPerDomain).mapIndexed { index, topic ->
            val topicName = text(topic.name, topic.encryptedName)
            val reminders = topic.reminders || isRemindersTopic(card.position, topicName)
            val cap = if (isRemindersTopic(card.position, topicName)) limits.maxReminders else limits.maxItemsPerTopic
            PanelTopic(
                id = topic.id,
                domainId = card.domainId,
                number = index + 1,
                name = topicName,
                tags = ItemTags.topicTags(topic.tags),
                reminders = reminders,
                items = topic.items.take(cap).mapIndexed { itemIndex, item ->
                    val content = if (item.isImage) null else text(item.content, item.encryptedContent)?.takeIf { it.isNotBlank() }
                    val reminder = ReminderDates.parse(item.reminderDate, zone)?.let { date ->
                        PanelReminder(
                            value = item.reminderDate!!, date = date, rule = item.reminderRule,
                            leadDays = ReminderDates.normalizeLeadDays(item.reminderLeadDays),
                            taskCreated = item.reminderTaskCreated,
                            status = ReminderDates.status(date, item.reminderTaskCreated, today),
                        )
                    }
                    PanelItem(
                        id = item.id, topicId = topic.id, domainId = card.domainId, number = itemIndex + 1,
                        name = text(item.name, item.encryptedName),
                        isImage = item.isImage,
                        content = content,
                        hasContent = content != null || (!item.isImage && !item.encryptedContent.isNullOrBlank()),
                        warning = item.warning,
                        tags = ItemTags.effective(topic.tags, item.tags),
                        reminder = reminder,
                        updatedAt = item.updatedAt.orEmpty(),
                        linkCount = 0,
                    )
                },
            )
        }
        return PanelDomain(
            id = card.domainId,
            position = card.position,
            name = WidgetPresenter.renamedDefault(card.position, text(card.name, card.encryptedName)),
            infoBox = text(card.infoBox, card.encryptedInfoBox)?.takeIf { it.isNotBlank() },
            rating = rating?.roundToInt()?.coerceIn(0, 10),
            color = card.color,
            topics = topics,
        )
    }

    /** extractDomainLinks over the decrypted tree; a URL is listed once per topic. Item link counts are filled in. */
    private fun extractLinks(domains: List<PanelDomain>, titles: Map<String, String>): Pair<List<PanelLink>, Map<String, Int>> {
        val links = mutableListOf<PanelLink>()
        val counts = mutableMapOf<String, Int>()
        for (domain in domains) for (topic in domain.topics) {
            val seen = mutableSetOf<String>()
            for (item in topic.items) {
                if (item.isImage || item.content == null) continue
                LinkExtractor.extractUrls(item.content).forEachIndexed { index, found ->
                    if (!seen.add(found.url)) return@forEachIndexed
                    val hostname = LinkExtractor.hostname(found.url)
                    val anchor = LinkExtractor.ownAnchorLabel(found.label, found.url)
                    links += PanelLink(
                        id = "${item.id}::$index::${found.url}",
                        url = found.url,
                        hostname = hostname,
                        label = anchor.ifEmpty { titles[found.url] ?: LinkExtractor.knownLinkLabel(found.url) ?: LinkExtractor.labelFromUrl(found.url, hostname) },
                        anchorLabel = anchor,
                        domainId = domain.id, domainPosition = domain.position,
                        topicId = topic.id, topicName = topic.name,
                        itemId = item.id, itemName = item.name,
                        headings = found.headings, headingLevels = found.headingLevels,
                        tags = item.tags,
                        updatedAt = item.updatedAt,
                    )
                    counts[item.id] = (counts[item.id] ?: 0) + 1
                }
            }
        }
        return links to counts
    }

    /** The tree with each item's link count, so the sidebar can show them */
    private fun withCounts(domains: List<PanelDomain>, counts: Map<String, Int>): List<PanelDomain> =
        if (counts.isEmpty()) domains
        else domains.map { d -> d.copy(topics = d.topics.map { t -> t.copy(items = t.items.map { i -> i.copy(linkCount = counts[i.id] ?: 0) }) }) }

    /** URLs of untitled media links whose real title /api/links/titles can look up (useLinkTitles) */
    fun untitledUrls(ui: PanelUi): List<String> =
        ui.links.asSequence().filter { it.anchorLabel.isEmpty() && LinkExtractor.canFetchTitle(it.url) }.map { it.url }.distinct().toList()

    // ── Filters (LinksContent filteredLinks, LinksStream filteredReminders) ─────

    private fun inScope(scope: PanelScope, domainId: String, topicId: String, itemId: String, tags: List<String>): Boolean =
        (scope.domainId == null || domainId == scope.domainId) &&
            (scope.topicId == null || topicId == scope.topicId) &&
            (scope.itemId == null || itemId == scope.itemId) &&
            (scope.tag == null || scope.tag in tags)

    /** The links in [scope], matched against label, URL, host, names and headings, in [sort] order. */
    fun filterLinks(ui: PanelUi, scope: PanelScope, sort: PanelSort, domainName: (PanelDomain) -> String): List<PanelLink> {
        val query = scope.query.trim().lowercase()
        val names = ui.domains.associate { it.id to domainName(it).lowercase() }
        val filtered = ui.links.filter { link ->
            inScope(scope, link.domainId, link.topicId, link.itemId, link.tags) && (query.isEmpty() ||
                listOf(link.label, link.url, link.hostname, names[link.domainId].orEmpty(), link.topicName.orEmpty(), link.itemName.orEmpty())
                    .plus(link.headings).joinToString(" ").lowercase().contains(query))
        }
        return when (sort) {
            PanelSort.STRUCTURE -> filtered
            PanelSort.NEWEST -> filtered.sortedByDescending { it.updatedAt }
            PanelSort.ALPHA -> filtered.sortedBy { it.label.lowercase() }
        }
    }

    /** One part of a section header's context: a topic name, or the item's ([isItem]) */
    data class SectionContext(val label: String, val isItem: Boolean)

    /**
     * A run of links from one item under one heading path (LinksStream linkSections). [title] is blank for an
     * item's links above its first heading when the stream is scoped to that item: they get no header.
     */
    data class LinkSection(
        val key: String,
        val domainId: String,
        val topicId: String,
        val itemId: String,
        val title: String,
        val context: List<SectionContext>,
        val links: List<PanelLink>,
    )

    /**
     * Structure order keeps an item's links in content order, so the headings written in its rich text split
     * the stream into sections; null (a flat list) in other orders or when no link sits under a heading.
     * Whatever the breadcrumb already shows is left out: inside one item, sections need no item name; inside
     * a topic or an item, no topic name. [untitled] names an item without a name.
     */
    fun linkSections(links: List<PanelLink>, sort: PanelSort, scope: PanelScope, untitled: String): List<LinkSection>? {
        if (sort != PanelSort.STRUCTURE || links.none { it.headings.isNotEmpty() }) return null
        val sections = mutableListOf<LinkSection>()
        for (link in links) {
            val key = "${link.itemId}::${link.headings.joinToString("\u0000")}"
            val last = sections.lastOrNull()
            if (last?.key == key) {
                sections[sections.lastIndex] = last.copy(links = last.links + link)
                continue
            }
            val itemName = link.itemName?.takeIf { it.isNotBlank() } ?: untitled
            val headingTitle = link.headings.joinToString(" › ")
            val title = if (scope.itemId != null) headingTitle else headingTitle.ifEmpty { itemName }
            val context = buildList {
                if (scope.topicId == null && scope.itemId == null) link.topicName?.takeIf { it.isNotBlank() }?.let { add(SectionContext(it, false)) }
                if (scope.itemId == null && headingTitle.isNotEmpty()) add(SectionContext(itemName, true))
            }
            sections += LinkSection(key, link.domainId, link.topicId, link.itemId, title, context, listOf(link))
        }
        return sections
    }

    /** An item with an alert, with where it lives */
    data class Alert(val item: PanelItem, val domain: PanelDomain, val topic: PanelTopic)

    /** Items with a reminder in [scope]; by reminder date unless [sort] says otherwise. */
    fun filterAlerts(ui: PanelUi, scope: PanelScope, sort: PanelSort, domainName: (PanelDomain) -> String): List<Alert> {
        val query = scope.query.trim().lowercase()
        val alerts = ui.domains.flatMap { d ->
            d.topics.flatMap { t ->
                t.items.filter { it.reminder != null }.map { Alert(it, d, t) }
            }
        }.filter { (item, d, t) ->
            inScope(scope, d.id, t.id, item.id, item.tags) && (query.isEmpty() ||
                listOf(item.name.orEmpty(), domainName(d), t.name.orEmpty()).joinToString(" ").lowercase().contains(query))
        }
        return when (sort) {
            PanelSort.NEWEST -> alerts.sortedByDescending { it.item.updatedAt }
            PanelSort.ALPHA -> alerts.sortedBy { it.item.name.orEmpty().lowercase() }
            PanelSort.STRUCTURE -> alerts.sortedWith(compareBy({ it.item.reminder!!.date }, { it.item.reminder!!.value }))
        }
    }
}
