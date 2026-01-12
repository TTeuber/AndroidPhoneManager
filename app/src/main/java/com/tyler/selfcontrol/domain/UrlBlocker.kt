package com.tyler.selfcontrol.domain

import android.net.Uri
import com.tyler.selfcontrol.data.model.WebsiteRule
import com.tyler.selfcontrol.data.repository.BlockRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles URL blocking logic based on active WebsiteRules.
 *
 * URL matching rules:
 * - Domain matching: blocking "youtube.com" blocks all subdomains (m.youtube.com, www.youtube.com)
 * - Path matching: prefix-based ("youtube.com/shorts" blocks "youtube.com/shorts?v=123")
 * - Case insensitive matching
 * - HTTP and HTTPS both blocked
 * - Allowlist entries are checked AFTER blocklist (allowlist overrides block)
 * - Wildcard support: "*.reddit.com" blocks all Reddit subdomains
 */
@Singleton
class UrlBlocker @Inject constructor(
    private val blockRepository: BlockRepository
) {
    /**
     * Check if a URL should be blocked based on active WebsiteRules.
     *
     * @param url The URL to check
     * @return true if the URL should be blocked, false otherwise
     */
    suspend fun shouldBlockUrl(url: String): Boolean {
        val parsedUrl = parseUrl(url) ?: return false

        val rules = blockRepository.getActiveWebsiteRules().first()
        if (rules.isEmpty()) return false

        // Separate blocklist and allowlist rules
        val blockRules = rules.filter { !it.isAllowed }
        val allowRules = rules.filter { it.isAllowed }

        // First check if URL matches any block rule
        val matchingBlockRule = blockRules.find { rule ->
            matchesRule(rule, parsedUrl)
        }

        if (matchingBlockRule == null) {
            // URL is not blocked
            return false
        }

        // URL matches a block rule - check if it's allowed by an allowlist entry
        val matchingAllowRule = allowRules.find { rule ->
            matchesRule(rule, parsedUrl)
        }

        // If there's a matching allow rule, don't block
        return matchingAllowRule == null
    }

    /**
     * Check if a URL matches a specific rule.
     */
    private fun matchesRule(rule: WebsiteRule, parsedUrl: ParsedUrl): Boolean {
        // Check domain match
        if (!matchesDomain(rule.domain, parsedUrl.host)) {
            return false
        }

        // Check path match (if rule has a path)
        if (rule.path != null) {
            if (!matchesPath(rule.path, parsedUrl.path)) {
                return false
            }
        }

        return true
    }

    /**
     * Parse a URL into components for matching.
     *
     * @param url The URL to parse
     * @return ParsedUrl or null if parsing fails
     */
    private fun parseUrl(url: String): ParsedUrl? {
        return try {
            // Handle URLs without scheme
            val urlWithScheme = if (!url.contains("://")) {
                "https://$url"
            } else {
                url
            }

            val uri = Uri.parse(urlWithScheme)
            val host = uri.host?.lowercase() ?: return null
            val path = uri.path?.lowercase() ?: ""

            ParsedUrl(host = host, path = path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Match a rule domain against a URL domain.
     *
     * Supports:
     * - Exact match: "example.com" matches "example.com"
     * - Subdomain match: "example.com" matches "www.example.com", "m.example.com"
     * - Wildcard match: "*.example.com" matches any subdomain of example.com
     */
    private fun matchesDomain(ruleDomain: String, urlDomain: String): Boolean {
        val normalizedRule = ruleDomain.lowercase().trim()
        val normalizedUrl = urlDomain.lowercase().trim()

        // Handle wildcard pattern: *.example.com
        if (normalizedRule.startsWith("*.")) {
            val baseDomain = normalizedRule.substring(2)
            // Match the base domain itself or any subdomain
            return normalizedUrl == baseDomain ||
                    normalizedUrl.endsWith(".$baseDomain")
        }

        // Exact match
        if (normalizedUrl == normalizedRule) {
            return true
        }

        // Subdomain match: rule "example.com" matches "www.example.com"
        // The URL domain ends with ".ruleDomain"
        if (normalizedUrl.endsWith(".$normalizedRule")) {
            return true
        }

        return false
    }

    /**
     * Match a rule path against a URL path using prefix matching.
     *
     * The rule path "/shorts" matches:
     * - "/shorts"
     * - "/shorts/"
     * - "/shorts/abc"
     * - "/shorts?v=123"
     *
     * But NOT:
     * - "/shortsvideo"
     */
    private fun matchesPath(rulePath: String, urlPath: String): Boolean {
        val normalizedRule = rulePath.lowercase().trim()
        val normalizedUrl = urlPath.lowercase().trim()

        // Ensure rule path starts with /
        val rulePathWithSlash = if (normalizedRule.startsWith("/")) {
            normalizedRule
        } else {
            "/$normalizedRule"
        }

        // Ensure url path starts with /
        val urlPathWithSlash = if (normalizedUrl.startsWith("/")) {
            normalizedUrl
        } else {
            "/$normalizedUrl"
        }

        // Exact match
        if (urlPathWithSlash == rulePathWithSlash) {
            return true
        }

        // Prefix match with path boundary
        // "/shorts" should match "/shorts/abc" but not "/shortsvideo"
        if (urlPathWithSlash.startsWith(rulePathWithSlash)) {
            // Check that the character after the rule path is a boundary character
            if (urlPathWithSlash.length > rulePathWithSlash.length) {
                val nextChar = urlPathWithSlash[rulePathWithSlash.length]
                // Valid path boundaries: /, ?, #
                if (nextChar == '/' || nextChar == '?' || nextChar == '#') {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Parsed URL components for matching.
     */
    private data class ParsedUrl(
        val host: String,
        val path: String
    )
}
