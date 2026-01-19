package com.tyler.selfcontrol.util

/**
 * Result of parsing a URL input into domain and optional path components.
 */
data class ParsedUrl(
    val domain: String,
    val path: String?
)

/**
 * Parses a user-entered URL string into domain and path components.
 *
 * Handles various input formats:
 * - example.com
 * - example.com/path
 * - example.com/path/to/page
 * - https://example.com/path
 * - http://www.example.com/path
 * - www.example.com
 *
 * Strips protocol (http/https) and www prefix.
 * Returns domain in lowercase.
 */
fun parseUrl(input: String): ParsedUrl {
    var url = input.trim().lowercase()

    // Strip protocol
    url = url.removePrefix("https://")
    url = url.removePrefix("http://")

    // Strip www prefix
    url = url.removePrefix("www.")

    // Strip trailing slash
    url = url.trimEnd('/')

    // Split into domain and path
    val slashIndex = url.indexOf('/')

    return if (slashIndex == -1) {
        // No path, just domain
        ParsedUrl(domain = url, path = null)
    } else {
        val domain = url.substring(0, slashIndex)
        val path = url.substring(slashIndex) // Keep the leading slash
        ParsedUrl(domain = domain, path = path.takeIf { it.isNotEmpty() && it != "/" })
    }
}
