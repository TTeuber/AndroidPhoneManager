package com.tyler.selfcontrol.domain

import com.tyler.selfcontrol.data.model.WebsiteRule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [toChromeFormat], the pure transformation that turns a [WebsiteRule]
 * row into a Chrome URL-filter blocklist/allowlist entry.
 */
class WebsiteRuleChromeFormatTest {

    private fun rule(domain: String, path: String? = null) =
        WebsiteRule(blockId = 1, domain = domain, path = path)

    @Test
    fun `domain only rule maps to bare domain`() {
        assertEquals("example.com", rule("example.com").toChromeFormat())
    }

    @Test
    fun `domain with path is concatenated`() {
        assertEquals("example.com/videos", rule("example.com", "/videos").toChromeFormat())
    }

    @Test
    fun `path without leading slash gets one added`() {
        assertEquals("example.com/videos", rule("example.com", "videos").toChromeFormat())
    }

    @Test
    fun `wildcard subdomain prefix is stripped so all subdomains are blocked`() {
        // Chrome blocks subdomains by default, so *.example.com == example.com.
        assertEquals("example.com", rule("*.example.com").toChromeFormat())
    }

    @Test
    fun `wildcard subdomain prefix is stripped with a path`() {
        assertEquals("example.com/x", rule("*.example.com", "/x").toChromeFormat())
    }

    @Test
    fun `explicit subdomain is preserved`() {
        assertEquals("sub.example.com", rule("sub.example.com").toChromeFormat())
    }

    @Test
    fun `uppercase domain is lowercased`() {
        assertEquals("example.com", rule("EXAMPLE.COM").toChromeFormat())
    }

    @Test
    fun `surrounding whitespace in domain is trimmed`() {
        assertEquals("example.com", rule("  example.com  ").toChromeFormat())
    }

    @Test
    fun `path casing is preserved while domain is lowercased`() {
        // Only the domain is normalized; the path keeps its original casing.
        assertEquals("example.com/Videos", rule("EXAMPLE.COM", "/Videos").toChromeFormat())
    }

    @Test
    fun `multi segment path is preserved`() {
        assertEquals(
            "example.com/a/b/c",
            rule("example.com", "/a/b/c").toChromeFormat()
        )
    }
}
