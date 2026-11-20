package com.kalai73.lifemapp.widgets.core.presentation.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases from the web's lib/links/__tests__/extract-domain-links.test.ts and link-titles.ts, so both agree. */
class LinkExtractorTest {
    @Test
    fun `accepts http and https URLs and strips trailing punctuation`() {
        assertEquals("https://example.com/path?a=1&b=2", LinkExtractor.normalizeExternalUrl("https://example.com/path?a=1&amp;b=2."))
        assertEquals("http://localhost:3000/test", LinkExtractor.normalizeExternalUrl("http://localhost:3000/test"))
        assertEquals("https://a.com/", LinkExtractor.normalizeExternalUrl("https://A.com"))
    }

    @Test
    fun `rejects non-http URLs and relative paths`() {
        assertNull(LinkExtractor.normalizeExternalUrl("mailto:user@example.com"))
        assertNull(LinkExtractor.normalizeExternalUrl("javascript:alert(1)"))
        assertNull(LinkExtractor.normalizeExternalUrl("/today/123"))
        assertNull(LinkExtractor.normalizeExternalUrl(""))
    }

    @Test
    fun `extracts anchors with labels and bare URLs without duplicates`() {
        val html = """
            <p>Check <a href="https://github.com/vercel/next.js">Next.js Repo</a> and
            also https://react.dev/learn for docs. Duplicate: <a href="https://github.com/vercel/next.js">Again</a></p>
        """
        assertEquals(
            listOf(
                FoundUrl("https://github.com/vercel/next.js", "Next.js Repo", emptyList(), emptyList()),
                FoundUrl("https://react.dev/learn", "", emptyList(), emptyList()),
            ),
            LinkExtractor.extractUrls(html),
        )
    }

    @Test
    fun `records the heading trail above each link`() {
        val html = """
            <p><a href="https://a.com">A</a></p>
            <h2>Media</h2>
            <p>https://b.com</p>
            <h3>News <strong>TV</strong></h3>
            <p><a href="https://c.com">C</a></p>
            <h2>Tools</h2>
            <p><a href="https://d.com">D</a> and again <a href="https://c.com">C</a></p>
        """
        assertEquals(
            listOf(
                FoundUrl("https://a.com/", "A", emptyList(), emptyList()),
                FoundUrl("https://b.com/", "", listOf("Media"), listOf(2)),
                FoundUrl("https://c.com/", "C", listOf("Media", "News TV"), listOf(2, 3)),
                FoundUrl("https://d.com/", "D", listOf("Tools"), listOf(2)),
            ),
            LinkExtractor.extractUrls(html),
        )
    }

    @Test
    fun `ignores non-external anchors`() {
        assertTrue(LinkExtractor.extractUrls("""<p><a href="mailto:hi@example.com">Email</a> <a href="/today/sub1">Internal</a></p>""").isEmpty())
    }

    @Test
    fun `labels join hostname and decoded path segments`() {
        assertEquals("soundcloud.com › you › following", LinkExtractor.labelFromUrl("https://soundcloud.com/you/following", "soundcloud.com"))
        assertEquals("example.com › café", LinkExtractor.labelFromUrl("https://example.com/caf%C3%A9/?q=1#top", "example.com"))
        assertEquals("example.com", LinkExtractor.labelFromUrl("https://example.com/", "example.com"))
        assertEquals("typescriptlang.org", LinkExtractor.hostname("https://www.typescriptlang.org/docs/"))
    }

    @Test
    fun `an anchor that only repeats its URL has no own label`() {
        assertEquals("", LinkExtractor.ownAnchorLabel("https://www.typescriptlang.org/docs/", "https://www.typescriptlang.org/docs/"))
        assertEquals("Raindrop", LinkExtractor.ownAnchorLabel(" Raindrop ", "https://raindrop.io/"))
    }

    @Test
    fun `known shapes get names and media links can fetch titles`() {
        assertEquals("YouTube video", LinkExtractor.knownLinkLabel("https://www.youtube.com/watch?v=abc"))
        assertEquals("YouTube · @someone", LinkExtractor.knownLinkLabel("https://youtube.com/@someone"))
        assertEquals("Spotify track", LinkExtractor.knownLinkLabel("https://open.spotify.com/intl-de/track/1"))
        assertNull(LinkExtractor.knownLinkLabel("https://example.com/watch"))
        assertTrue(LinkExtractor.canFetchTitle("https://youtu.be/abc"))
        assertTrue(LinkExtractor.canFetchTitle("https://www.youtube.com/@someone"))
        assertFalse(LinkExtractor.canFetchTitle("https://soundcloud.com/you/likes"))
        assertFalse(LinkExtractor.canFetchTitle("https://example.com/"))
    }

    @Test
    fun `updates an anchor href and label`() {
        val html = """<p>Watch <a href="https://youtube.com/watch?v=1">Old Title</a> later</p>"""
        assertEquals(
            """<p>Watch <a href="https://youtube.com/watch?v=2" target="_blank" rel="noopener noreferrer">New Title</a> later</p>""",
            LinkExtractor.updateLinkInHtml(html, "https://youtube.com/watch?v=1", "https://youtube.com/watch?v=2", "New Title"),
        )
    }

    @Test
    fun `upgrades a bare URL to an anchor`() {
        assertEquals(
            """<p>Visit <a href="https://example.org/" target="_blank" rel="noopener noreferrer">Example</a> for details</p>""",
            LinkExtractor.updateLinkInHtml("<p>Visit https://example.com/page for details</p>", "https://example.com/page", "https://example.org/", "Example"),
        )
    }

    @Test
    fun `removes a link`() {
        assertEquals(
            """<p>Keep <a href="https://a.com/">A</a> and drop </p>""",
            LinkExtractor.removeLinkFromHtml("""<p>Keep <a href="https://a.com/">A</a> and drop <a href="https://b.com/">B</a></p>""", "https://b.com/"),
        )
    }
}
