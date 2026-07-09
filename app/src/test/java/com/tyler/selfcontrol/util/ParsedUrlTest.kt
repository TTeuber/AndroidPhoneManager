package com.tyler.selfcontrol.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsedUrlTest {

    // --- Bare domains ---

    @Test
    fun `bare domain has no path`() {
        val result = parseUrl("example.com")
        assertEquals("example.com", result.domain)
        assertNull(result.path)
    }

    @Test
    fun `subdomain is preserved`() {
        val result = parseUrl("sub.example.com")
        assertEquals("sub.example.com", result.domain)
        assertNull(result.path)
    }

    // --- Protocol stripping ---

    @Test
    fun `https scheme is stripped`() {
        val result = parseUrl("https://example.com/path")
        assertEquals("example.com", result.domain)
        assertEquals("/path", result.path)
    }

    @Test
    fun `http scheme is stripped`() {
        val result = parseUrl("http://example.com/path")
        assertEquals("example.com", result.domain)
        assertEquals("/path", result.path)
    }

    // --- www prefix ---

    @Test
    fun `www prefix is stripped from bare domain`() {
        val result = parseUrl("www.example.com")
        assertEquals("example.com", result.domain)
        assertNull(result.path)
    }

    @Test
    fun `scheme and www prefix are both stripped`() {
        val result = parseUrl("http://www.example.com/path")
        assertEquals("example.com", result.domain)
        assertEquals("/path", result.path)
    }

    @Test
    fun `www is only stripped as a leading prefix`() {
        // "www" appearing mid-domain must not be touched.
        val result = parseUrl("example.www.com")
        assertEquals("example.www.com", result.domain)
    }

    // --- Paths ---

    @Test
    fun `single path segment is kept with leading slash`() {
        val result = parseUrl("example.com/path")
        assertEquals("example.com", result.domain)
        assertEquals("/path", result.path)
    }

    @Test
    fun `multi segment path is preserved`() {
        val result = parseUrl("example.com/path/to/page")
        assertEquals("example.com", result.domain)
        assertEquals("/path/to/page", result.path)
    }

    @Test
    fun `trailing slash after domain yields null path`() {
        val result = parseUrl("example.com/")
        assertEquals("example.com", result.domain)
        assertNull(result.path)
    }

    @Test
    fun `trailing slash after path is stripped`() {
        val result = parseUrl("example.com/path/")
        assertEquals("example.com", result.domain)
        assertEquals("/path", result.path)
    }

    @Test
    fun `multiple trailing slashes are all stripped`() {
        val result = parseUrl("example.com///")
        assertEquals("example.com", result.domain)
        assertNull(result.path)
    }

    // --- Query strings and fragments ---

    @Test
    fun `query string stays attached to the path`() {
        val result = parseUrl("example.com/search?q=test")
        assertEquals("example.com", result.domain)
        assertEquals("/search?q=test", result.path)
    }

    @Test
    fun `fragment stays attached to the path`() {
        val result = parseUrl("example.com/page#section")
        assertEquals("example.com", result.domain)
        assertEquals("/page#section", result.path)
    }

    // --- Ports ---

    @Test
    fun `port stays part of the domain`() {
        val result = parseUrl("example.com:8080/path")
        assertEquals("example.com:8080", result.domain)
        assertEquals("/path", result.path)
    }

    // --- Casing and whitespace ---

    @Test
    fun `uppercase input is lowercased`() {
        val result = parseUrl("HTTPS://WWW.EXAMPLE.COM/PATH")
        assertEquals("example.com", result.domain)
        assertEquals("/path", result.path)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        val result = parseUrl("   example.com/path   ")
        assertEquals("example.com", result.domain)
        assertEquals("/path", result.path)
    }

    // --- IP addresses ---

    @Test
    fun `ip address is treated as a domain`() {
        val result = parseUrl("192.168.1.1")
        assertEquals("192.168.1.1", result.domain)
        assertNull(result.path)
    }

    @Test
    fun `ip address with path splits on the slash`() {
        val result = parseUrl("192.168.1.1/admin")
        assertEquals("192.168.1.1", result.domain)
        assertEquals("/admin", result.path)
    }

    // --- Degenerate / garbage input ---

    @Test
    fun `empty input yields empty domain and null path`() {
        val result = parseUrl("")
        assertEquals("", result.domain)
        assertNull(result.path)
    }

    @Test
    fun `blank input yields empty domain and null path`() {
        val result = parseUrl("     ")
        assertEquals("", result.domain)
        assertNull(result.path)
    }

    @Test
    fun `lone slash yields empty domain and null path`() {
        val result = parseUrl("/")
        assertEquals("", result.domain)
        assertNull(result.path)
    }
}
