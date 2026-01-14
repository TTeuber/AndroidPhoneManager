package com.tyler.selfcontrol.domain

import com.tyler.selfcontrol.data.model.AppCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses Play Store pages to extract app information and categorize apps.
 *
 * Used to determine whether an app requires cooldown before installation:
 * - Social, Entertainment, Video Players categories require cooldown
 * - Games (detected via "About this game" section) require cooldown
 * - Browsers (detected via keywords in title) require cooldown
 * - All other apps can be installed immediately
 */
@Singleton
class PlayStoreParser @Inject constructor() {

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // Restricted categories that require cooldown
        private val RESTRICTED_CATEGORIES = setOf(
            "social",
            "entertainment",
            "video players & editors"
        )

        // Keywords in app title that indicate a browser
        private val BROWSER_KEYWORDS = listOf(
            "browser",
            "firefox",
            "duck",
            "chrome",
            "opera",
            "brave",
            "edge",
            "safari",
            "vivaldi",
            "kiwi"
        )
    }

    /**
     * Parsed information about an app from its Play Store page.
     */
    data class ParsedAppInfo(
        val packageName: String,
        val appName: String,
        val category: AppCategory,
        val isGame: Boolean,
        val isBrowser: Boolean,
        val rawCategory: String?
    )

    /**
     * Parse a Play Store URL and extract app information.
     *
     * @param url The full Play Store URL (e.g., https://play.google.com/store/apps/details?id=com.example.app)
     * @return Result containing ParsedAppInfo on success, or an error
     */
    suspend fun parsePlayStoreUrl(url: String): Result<ParsedAppInfo> = withContext(Dispatchers.IO) {
        try {
            // Extract package name from URL
            val packageName = extractPackageName(url)
                ?: return@withContext Result.failure(PlayStoreParseException("Invalid Play Store URL: could not extract package name"))

            // Normalize URL to include English locale
            val normalizedUrl = normalizeUrl(url, packageName)

            // Fetch and parse page
            val doc = Jsoup.connect(normalizedUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()

            val appName = parseAppName(doc)
            val rawCategory = parseCategory(doc)
            val isGame = detectGame(doc)
            val isBrowser = detectBrowser(appName)

            val category = categorize(rawCategory, isGame, isBrowser)

            Result.success(
                ParsedAppInfo(
                    packageName = packageName,
                    appName = appName,
                    category = category,
                    isGame = isGame,
                    isBrowser = isBrowser,
                    rawCategory = rawCategory
                )
            )
        } catch (e: Exception) {
            // Default to requiring cooldown if parsing fails (fail-safe)
            Result.failure(PlayStoreParseException("Failed to parse Play Store page: ${e.message}", e))
        }
    }

    /**
     * Extract the package name from a Play Store URL.
     *
     * Handles formats like:
     * - https://play.google.com/store/apps/details?id=com.example.app
     * - https://play.google.com/store/apps/details?id=com.example.app&hl=en
     * - play.google.com/store/apps/details?id=com.example.app
     */
    fun extractPackageName(url: String): String? {
        val regex = """[?&]id=([a-zA-Z0-9._]+)""".toRegex()
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Normalize the URL to ensure we get the English version of the page.
     */
    private fun normalizeUrl(url: String, packageName: String): String {
        return "https://play.google.com/store/apps/details?id=$packageName&hl=en-US"
    }

    /**
     * Parse the app name from the Play Store page.
     */
    private fun parseAppName(doc: Document): String {
        // Try multiple selectors as Play Store HTML structure may vary
        return doc.select("h1[itemprop=name] span").firstOrNull()?.text()
            ?: doc.select("h1 span[itemprop=name]").firstOrNull()?.text()
            ?: doc.select("h1").firstOrNull()?.text()
            ?: "Unknown App"
    }

    /**
     * Parse the category from the Play Store page.
     */
    private fun parseCategory(doc: Document): String? {
        // Look for the category link with itemprop="genre"
        val categoryElement = doc.select("[itemprop=genre]").firstOrNull()
        if (categoryElement != null) {
            // Try to get the text from the aria-label or the link text
            return categoryElement.attr("aria-label").takeIf { it.isNotBlank() }
                ?: categoryElement.text().takeIf { it.isNotBlank() }
        }

        // Fallback: look for category in the href pattern
        val categoryLink = doc.select("a[href*='/store/apps/category/']").firstOrNull()
        if (categoryLink != null) {
            return categoryLink.text().takeIf { it.isNotBlank() }
        }

        return null
    }

    /**
     * Detect if the app is a game based on page content.
     *
     * Games have "About this game" section instead of "About this app".
     * Also check for game category links.
     */
    private fun detectGame(doc: Document): Boolean {
        // Check for "About this game" heading
        val hasAboutGameSection = doc.select("h2:containsOwn(About this game)").isNotEmpty()
        if (hasAboutGameSection) return true

        // Check for game category in URL
        val hasGameCategory = doc.select("a[href*='/store/apps/category/GAME']").isNotEmpty()
        if (hasGameCategory) return true

        return false
    }

    /**
     * Detect if the app is a browser based on its name.
     */
    private fun detectBrowser(appName: String): Boolean {
        val nameLower = appName.lowercase()
        return BROWSER_KEYWORDS.any { keyword -> nameLower.contains(keyword) }
    }

    /**
     * Categorize the app based on parsed information.
     *
     * Priority:
     * 1. Browsers are always restricted
     * 2. Games are always restricted
     * 3. Social/Entertainment/Video Players categories are restricted
     * 4. Everything else is unrestricted
     */
    private fun categorize(rawCategory: String?, isGame: Boolean, isBrowser: Boolean): AppCategory {
        return when {
            isBrowser -> AppCategory.BROWSERS
            isGame -> AppCategory.GAMES
            rawCategory == null -> AppCategory.UNRESTRICTED
            else -> {
                val categoryLower = rawCategory.lowercase().trim()
                when {
                    categoryLower == "social" -> AppCategory.SOCIAL
                    categoryLower == "entertainment" -> AppCategory.ENTERTAINMENT
                    categoryLower.contains("video players") -> AppCategory.VIDEO_PLAYERS
                    RESTRICTED_CATEGORIES.contains(categoryLower) -> AppCategory.ENTERTAINMENT
                    else -> AppCategory.UNRESTRICTED
                }
            }
        }
    }

    /**
     * Check if an app category requires cooldown.
     */
    fun requiresCooldown(category: AppCategory): Boolean {
        return category != AppCategory.UNRESTRICTED
    }
}

/**
 * Exception thrown when Play Store parsing fails.
 */
class PlayStoreParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
