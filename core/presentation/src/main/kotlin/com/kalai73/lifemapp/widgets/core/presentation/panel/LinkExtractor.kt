package com.kalai73.lifemapp.widgets.core.presentation.panel

import java.net.URI

/** A URL found in an item's HTML, with the heading trail it sits under. */
data class FoundUrl(val url: String, val label: String, val headings: List<String>, val headingLevels: List<Int>)

/**
 * Port of the web's lib/links/extract-domain-links.ts and link-titles.ts: finds external links in item HTML (anchors
 * first, then bare URLs), names them, and edits or removes them in the HTML. Output matches the web's tests.
 */
object LinkExtractor {
    private val ANCHOR = Regex("""<a\b[^>]*\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
    private val BARE_URL = Regex("""https?://[^\s<>"'`)\]]+""", RegexOption.IGNORE_CASE)
    private val HTML_TAG = Regex("<[^>]*>")
    private val HEADING = Regex("""<h([1-6])\b[^>]*>([\s\S]*?)</h\1\s*>""", RegexOption.IGNORE_CASE)
    private val SEGMENT = Regex("(<[^>]*>)|([^<]+)")
    private val TRAILING_PUNCTUATION = Regex("[.,;:!?]+$")
    private val YOUTUBE_HOSTS = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

    private fun decodeBasicEntities(text: String): String = text
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&nbsp;", " ", ignoreCase = true)

    fun stripHtmlTags(html: String): String =
        decodeBasicEntities(html.replace(HTML_TAG, " ")).replace(Regex("\\s+"), " ").trim()

    /** Characters a URI may hold as they are; others (spaces, non-ASCII) are percent-encoded, as WHATWG URL does */
    private fun escapeIllegal(value: String): String = buildString {
        for (ch in value) {
            if (ch.code in 33..126 && ch !in "\"<>\\^`{|}") append(ch)
            else ch.toString().toByteArray(Charsets.UTF_8).forEach { append('%').append("%02X".format(it.toInt() and 0xff)) }
        }
    }

    private fun parse(raw: String): URI? = runCatching { URI(raw) }.getOrNull() ?: runCatching { URI(escapeIllegal(raw)) }.getOrNull()

    /** An http(s) URL in WHATWG form (lower-case scheme and host, "/" for an empty path), or null for other schemes. */
    fun normalizeExternalUrl(raw: String): String? {
        val trimmed = decodeBasicEntities(raw).trim().replace(TRAILING_PUNCTUATION, "")
        if (trimmed.isEmpty()) return null
        val uri = parse(trimmed) ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val port = uri.port.takeIf { it != -1 && !(scheme == "http" && it == 80) && !(scheme == "https" && it == 443) }
        return buildString {
            append(scheme).append("://")
            uri.rawUserInfo?.let { append(it).append('@') }
            append(host)
            port?.let { append(':').append(it) }
            append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    fun hostname(url: String): String = parse(url)?.host?.lowercase()?.replace(Regex("^www\\.", RegexOption.IGNORE_CASE), "") ?: url

    private fun splitByHeadings(html: String): List<Triple<String, List<String>, List<Int>>> {
        val sections = mutableListOf<Triple<String, List<String>, List<Int>>>()
        val stack = ArrayDeque<Pair<Int, String>>()
        var start = 0
        var headings = emptyList<String>()
        var levels = emptyList<Int>()
        for (match in HEADING.findAll(html)) {
            sections += Triple(html.substring(start, match.range.first), headings, levels)
            val level = match.groupValues[1].toInt()
            while (stack.isNotEmpty() && stack.last().first >= level) stack.removeLast()
            val text = stripHtmlTags(match.groupValues[2])
            if (text.isNotEmpty()) stack.addLast(level to text)
            headings = stack.map { it.second }
            levels = stack.map { it.first }
            start = match.range.first
        }
        sections += Triple(html.substring(start), headings, levels)
        return sections
    }

    private fun anchorHref(match: MatchResult): String =
        match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value ?: ""

    /** Every external URL in [html] once, anchors before bare URLs within each heading section. */
    fun extractUrls(html: String?): List<FoundUrl> {
        if (html.isNullOrBlank()) return emptyList()
        val seen = mutableSetOf<String>()
        val results = mutableListOf<FoundUrl>()
        for ((section, headings, levels) in splitByHeadings(html)) {
            if (section.isBlank()) continue
            for (match in ANCHOR.findAll(section)) {
                val url = normalizeExternalUrl(anchorHref(match)) ?: continue
                if (!seen.add(url)) continue
                results += FoundUrl(url, stripHtmlTags(match.groupValues[4]), headings, levels)
            }
            val plain = stripHtmlTags(section.replace(ANCHOR, " "))
            for (match in BARE_URL.findAll(plain)) {
                val url = normalizeExternalUrl(match.value) ?: continue
                if (!seen.add(url)) continue
                results += FoundUrl(url, "", headings, levels)
            }
        }
        return results
    }

    /** The anchor text, or "" when it is empty or just the URL itself */
    fun ownAnchorLabel(anchorLabel: String, url: String): String {
        val clean = anchorLabel.trim()
        fun strip(value: String) = value.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").removeSuffix("/")
        return if (clean.isEmpty() || strip(clean) == strip(url)) "" else clean
    }

    /** "soundcloud.com › you › following": hostname plus decoded path segments; query and hash dropped. */
    fun labelFromUrl(url: String, hostname: String): String {
        val path = parse(url)?.path ?: return hostname
        return (listOf(hostname) + path.split('/').filter { it.isNotEmpty() }).joinToString(" › ")
    }

    private fun queryParam(uri: URI, name: String): String? =
        uri.rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            if (key == name) part.substringAfter('=', "").takeIf { it.isNotEmpty() } else null
        }

    private fun segments(uri: URI) = (uri.path ?: "").split('/').filter { it.isNotEmpty() }

    private fun host(uri: URI) = uri.host?.lowercase()?.removePrefix("www.").orEmpty()

    /** A meaningful name for well-known URL shapes whose path says nothing ("YouTube video"), or null */
    fun knownLinkLabel(rawUrl: String): String? {
        val uri = parse(rawUrl) ?: return null
        val host = host(uri)
        val seg = segments(uri)
        if (host in YOUTUBE_HOSTS) {
            if (host == "youtu.be" && seg.isNotEmpty()) return "YouTube video"
            val first = seg.getOrNull(0)
            val second = seg.getOrNull(1)
            return when {
                first == "watch" && queryParam(uri, "v") != null -> "YouTube video"
                first == "shorts" && second != null -> "YouTube Short"
                first == "live" && second != null -> "YouTube live"
                first == "playlist" && queryParam(uri, "list") != null -> "YouTube playlist"
                first?.startsWith("@") == true -> "YouTube · $first"
                (first == "channel" || first == "c" || first == "user") && second != null -> "YouTube · $second"
                first == null -> "YouTube"
                else -> null
            }
        }
        if (host == "vimeo.com" && seg.firstOrNull()?.all(Char::isDigit) == true) return "Vimeo video"
        if (host == "open.spotify.com") {
            seg.firstOrNull { it in setOf("track", "album", "playlist", "artist", "episode", "show") }?.let { return "Spotify $it" }
        }
        return null
    }

    /** Whether /api/links/titles can look up a real title for this URL (oEmbed providers, YouTube channels) */
    fun canFetchTitle(rawUrl: String): Boolean {
        val uri = parse(rawUrl) ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = host(uri)
        val seg = segments(uri)
        val first = seg.firstOrNull()
        return when {
            host in YOUTUBE_HOSTS ->
                (host == "youtu.be" && first != null) ||
                    (first == "watch" && queryParam(uri, "v") != null) ||
                    ((first == "shorts" || first == "live") && seg.size > 1) ||
                    (first == "playlist" && queryParam(uri, "list") != null) ||
                    (host != "music.youtube.com" && host != "youtu.be" &&
                        (first?.startsWith("@") == true || ((first == "channel" || first == "c" || first == "user") && seg.size > 1)))
            host == "vimeo.com" || host == "player.vimeo.com" -> seg.isNotEmpty()
            host == "soundcloud.com" || host == "m.soundcloud.com" -> seg.isNotEmpty() && first != "you"
            host == "open.spotify.com" -> seg.isNotEmpty()
            else -> false
        }
    }

    private fun escapeAttr(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
    private fun escapeText(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Replaces the first occurrence of [oldUrl] (an anchor, else a bare URL in text) with [newUrl] labelled [newLabel]. */
    fun updateLinkInHtml(html: String, oldUrl: String, newUrl: String, newLabel: String): String {
        if (html.isEmpty()) return html
        val normalizedNew = normalizeExternalUrl(newUrl) ?: return html
        val anchor = """<a href="${escapeAttr(normalizedNew)}" target="_blank" rel="noopener noreferrer">${escapeText(newLabel.trim().ifEmpty { normalizedNew })}</a>"""
        var replaced = false
        val afterAnchor = ANCHOR.replace(html) { match ->
            if (!replaced && normalizeExternalUrl(anchorHref(match)) == oldUrl) { replaced = true; anchor } else match.value
        }
        if (replaced) return afterAnchor
        return SEGMENT.replace(html) { segment ->
            if (segment.groups[1] != null || replaced) segment.value
            else BARE_URL.replace(segment.value) { url ->
                if (!replaced && normalizeExternalUrl(url.value) == oldUrl) { replaced = true; anchor } else url.value
            }
        }
    }

    /** Removes every occurrence of [targetUrl] from [html], anchors and bare URLs alike. */
    fun removeLinkFromHtml(html: String, targetUrl: String): String {
        if (html.isEmpty()) return html
        val afterAnchors = ANCHOR.replace(html) { match -> if (normalizeExternalUrl(anchorHref(match)) == targetUrl) "" else match.value }
        return SEGMENT.replace(afterAnchors) { segment ->
            if (segment.groups[1] != null) segment.value
            else BARE_URL.replace(segment.value) { url -> if (normalizeExternalUrl(url.value) == targetUrl) "" else url.value }
        }
    }
}
