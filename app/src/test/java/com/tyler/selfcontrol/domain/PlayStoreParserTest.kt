package com.tyler.selfcontrol.domain

import com.tyler.selfcontrol.data.model.AppCategory
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreParserTest {

    private val parser = PlayStoreParser()

    private fun parse(html: String, packageName: String = "com.example.app") =
        parser.parseDocument(Jsoup.parse(html), packageName)

    // --- Package name extraction ---

    @Test
    fun `extracts package name from standard url`() {
        assertEquals(
            "com.example.app",
            parser.extractPackageName("https://play.google.com/store/apps/details?id=com.example.app")
        )
    }

    @Test
    fun `extracts package name when followed by other query params`() {
        assertEquals(
            "com.example.app",
            parser.extractPackageName("https://play.google.com/store/apps/details?id=com.example.app&hl=en")
        )
        assertEquals(
            "com.example.app",
            parser.extractPackageName("https://play.google.com/store/apps/details?hl=en&id=com.example.app")
        )
    }

    @Test
    fun `handles package names with digits and underscores`() {
        assertEquals(
            "com.example_2.app3",
            parser.extractPackageName("https://play.google.com/store/apps/details?id=com.example_2.app3")
        )
    }

    @Test
    fun `returns null for urls without a package id`() {
        assertNull(parser.extractPackageName("https://play.google.com/store/apps"))
        assertNull(parser.extractPackageName("https://example.com"))
    }

    // --- Categorization from page content ---

    @Test
    fun `social category app requires cooldown`() {
        val info = parse(
            """
            <h1 itemprop="name"><span>Photogram</span></h1>
            <a itemprop="genre">Social</a>
            <h2>About this app</h2>
            """
        )
        assertEquals(AppCategory.SOCIAL, info.category)
        assertEquals("Photogram", info.appName)
        assertEquals("Social", info.rawCategory)
        assertTrue(parser.requiresCooldown(info.category))
    }

    @Test
    fun `entertainment category app requires cooldown`() {
        val info = parse(
            """
            <h1 itemprop="name"><span>StreamFlix</span></h1>
            <a itemprop="genre">Entertainment</a>
            """
        )
        assertEquals(AppCategory.ENTERTAINMENT, info.category)
    }

    @Test
    fun `video players category app requires cooldown`() {
        val info = parse(
            """
            <h1 itemprop="name"><span>MediaPlay</span></h1>
            <a itemprop="genre">Video Players &amp; Editors</a>
            """
        )
        assertEquals(AppCategory.VIDEO_PLAYERS, info.category)
    }

    @Test
    fun `game detected via about this game section`() {
        val info = parse(
            """
            <h1 itemprop="name"><span>Puzzle Quest</span></h1>
            <h2>About this game</h2>
            """
        )
        assertTrue(info.isGame)
        assertEquals(AppCategory.GAMES, info.category)
    }

    @Test
    fun `game detected via game category link`() {
        val info = parse(
            """
            <h1 itemprop="name"><span>Puzzle Quest</span></h1>
            <a href="/store/apps/category/GAME_PUZZLE">Puzzle</a>
            """
        )
        assertTrue(info.isGame)
        assertEquals(AppCategory.GAMES, info.category)
    }

    @Test
    fun `browser detected by name keyword regardless of category`() {
        val info = parse(
            """
            <h1 itemprop="name"><span>Firefox Fast &amp; Private Browser</span></h1>
            <a itemprop="genre">Communication</a>
            """
        )
        assertTrue(info.isBrowser)
        assertEquals(AppCategory.BROWSERS, info.category)
    }

    @Test
    fun `browser takes priority over game detection`() {
        val info = parse(
            """
            <h1 itemprop="name"><span>Opera Browser</span></h1>
            <h2>About this game</h2>
            """
        )
        assertEquals(AppCategory.BROWSERS, info.category)
    }

    @Test
    fun `utility app is unrestricted`() {
        val info = parse(
            """
            <h1 itemprop="name"><span>Simple Calculator</span></h1>
            <a itemprop="genre">Tools</a>
            <h2>About this app</h2>
            """
        )
        assertEquals(AppCategory.UNRESTRICTED, info.category)
        assertFalse(info.isGame)
        assertFalse(info.isBrowser)
        assertFalse(parser.requiresCooldown(info.category))
    }

    @Test
    fun `app with no detectable category is unrestricted`() {
        val info = parse("<h1 itemprop=\"name\"><span>Mystery App</span></h1>")
        assertEquals(AppCategory.UNRESTRICTED, info.category)
        assertNull(info.rawCategory)
    }

    @Test
    fun `missing app name falls back to unknown`() {
        val info = parse("<p>Empty page</p>")
        assertEquals("Unknown App", info.appName)
    }

    // --- Cooldown policy ---

    @Test
    fun `only unrestricted apps skip the cooldown`() {
        AppCategory.entries.forEach { category ->
            val expected = category != AppCategory.UNRESTRICTED
            assertEquals("cooldown for $category", expected, parser.requiresCooldown(category))
        }
    }
}
