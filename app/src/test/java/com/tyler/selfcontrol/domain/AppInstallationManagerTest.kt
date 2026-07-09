package com.tyler.selfcontrol.domain

import android.content.Context
import android.util.Log
import com.tyler.selfcontrol.data.model.AllowedApp
import com.tyler.selfcontrol.data.model.AllowedAppSource
import com.tyler.selfcontrol.data.model.AppCategory
import com.tyler.selfcontrol.data.model.BlacklistedApp
import com.tyler.selfcontrol.data.model.CooldownRequest
import com.tyler.selfcontrol.data.model.CooldownStatus
import com.tyler.selfcontrol.data.repository.AppInstallationRepository
import io.mockk.Answer
import io.mockk.ConstantAnswer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AppInstallationManagerTest {

    private val parser = mockk<PlayStoreParser>()
    private val repository = mockk<AppInstallationRepository>(relaxUnitFun = true)
    private val context = mockk<Context>(relaxed = true)

    // Fixed clock in UTC so all cooldown-window instants are exactly computable.
    private val zone = ZoneOffset.UTC

    private fun managerAt(instant: Instant) =
        AppInstallationManager(parser, repository, context, Clock.fixed(instant, zone))

    private fun appInfo(
        pkg: String = "com.example.app",
        category: AppCategory = AppCategory.SOCIAL
    ) = PlayStoreParser.ParsedAppInfo(
        packageName = pkg,
        appName = "Example App",
        category = category,
        isGame = false,
        isBrowser = false,
        rawCategory = "Social"
    )

    // MockK 1.13.x double-boxes a kotlin.Result returned from a suspend function
    // (it yields Success(Success(value))), so the caller unboxes the wrong layer and
    // throws ClassCastException. Feeding the already-unboxed representation through a
    // raw ConstantAnswer matches exactly what the caller unboxes.
    @Suppress("UNCHECKED_CAST")
    private fun stubParseSuccess(info: PlayStoreParser.ParsedAppInfo) {
        coEvery { parser.parsePlayStoreUrl(any()) } answers
            (ConstantAnswer(info) as Answer<Result<PlayStoreParser.ParsedAppInfo>>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubParseFailure(error: Throwable) {
        val failureHolder = kotlin.Result::class.java.getDeclaredField("value")
            .apply { isAccessible = true }
            .get(Result.failure<PlayStoreParser.ParsedAppInfo>(error))
        coEvery { parser.parsePlayStoreUrl(any()) } answers
            (ConstantAnswer(failureHolder) as Answer<Result<PlayStoreParser.ParsedAppInfo>>)
    }

    @Before
    fun setUp() {
        // android.util.Log is not available in plain JVM unit tests.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // --- calculateCooldownWindow: 3-6 PM next day ---

    @Test
    fun `window is 3-6 PM the next calendar day for a morning request`() {
        val manager = managerAt(Instant.parse("2026-01-15T08:00:00Z"))
        val (start, end) = manager.calculateCooldownWindow()
        assertEquals(Instant.parse("2026-01-16T15:00:00Z"), start)
        assertEquals(Instant.parse("2026-01-16T18:00:00Z"), end)
    }

    @Test
    fun `window spans exactly three hours`() {
        val manager = managerAt(Instant.parse("2026-01-15T08:00:00Z"))
        val (start, end) = manager.calculateCooldownWindow()
        assertEquals(Duration.ofHours(3), Duration.between(start, end))
    }

    @Test
    fun `an afternoon request after 3 PM still waits until the next day`() {
        // Request made at 4 PM today: the window is tomorrow 3-6 PM, not today.
        val manager = managerAt(Instant.parse("2026-01-15T16:00:00Z"))
        val (start, end) = manager.calculateCooldownWindow()
        assertEquals(Instant.parse("2026-01-16T15:00:00Z"), start)
        assertEquals(Instant.parse("2026-01-16T18:00:00Z"), end)
    }

    @Test
    fun `request just before midnight uses tomorrow relative to that day`() {
        val manager = managerAt(Instant.parse("2026-01-15T23:59:00Z"))
        val (start, _) = manager.calculateCooldownWindow()
        assertEquals(Instant.parse("2026-01-16T15:00:00Z"), start)
    }

    @Test
    fun `request just after midnight rolls the window to the following day`() {
        val manager = managerAt(Instant.parse("2026-01-16T00:01:00Z"))
        val (start, _) = manager.calculateCooldownWindow()
        assertEquals(Instant.parse("2026-01-17T15:00:00Z"), start)
    }

    @Test
    fun `request exactly at midnight counts that new day as today`() {
        val manager = managerAt(Instant.parse("2026-01-16T00:00:00Z"))
        val (start, _) = manager.calculateCooldownWindow()
        assertEquals(Instant.parse("2026-01-17T15:00:00Z"), start)
    }

    @Test
    fun `wait is not strictly 24 hours - a late-night request waits only about 15 hours`() {
        // Documents actual behavior: the rule is "next calendar day 3-6 PM", not "24 hours later".
        val manager = managerAt(Instant.parse("2026-01-15T23:59:00Z"))
        val (start, _) = manager.calculateCooldownWindow()
        val wait = Duration.between(Instant.parse("2026-01-15T23:59:00Z"), start)
        assertEquals(Duration.ofHours(15).plusMinutes(1), wait)
        assertTrue(wait < Duration.ofHours(24))
    }

    @Test
    fun `a request just before 3 PM waits close to 24 hours`() {
        val now = Instant.parse("2026-01-15T14:59:00Z")
        val manager = managerAt(now)
        val (start, _) = manager.calculateCooldownWindow()
        val wait = Duration.between(now, start)
        assertEquals(Duration.ofHours(24).plusMinutes(1), wait)
    }

    // --- evaluateInstallation decisions ---

    @Test
    fun `parse failure yields an Error decision`() = runTest {
        stubParseFailure(PlayStoreParseException("boom"))

        val decision = managerAt(NOW).evaluateInstallation("https://play.google.com/x")

        assertTrue(decision is AppInstallationManager.InstallationDecision.Error)
        assertEquals("boom", (decision as AppInstallationManager.InstallationDecision.Error).message)
    }

    @Test
    fun `blacklisted app is denied and takes priority over a pending request`() = runTest {
        val info = appInfo(pkg = "com.blocked.app")
        stubParseSuccess(info)
        // Even with an existing request present, blacklist is checked first.
        coEvery { repository.getActiveRequestForPackage("com.blocked.app") } returns
            pendingRequest("com.blocked.app")
        coEvery { repository.isBlacklisted("com.blocked.app") } returns true
        coEvery { repository.getBlacklistedAppsOnce() } returns listOf(
            BlacklistedApp(packageName = "com.blocked.app", appName = "Blocked", reason = "Social media")
        )

        val decision = managerAt(NOW).evaluateInstallation("url")

        assertTrue(decision is AppInstallationManager.InstallationDecision.Blacklisted)
        assertEquals(
            "Social media",
            (decision as AppInstallationManager.InstallationDecision.Blacklisted).reason
        )
    }

    @Test
    fun `already allowed app can install immediately`() = runTest {
        val info = appInfo(pkg = "com.ok.app", category = AppCategory.SOCIAL)
        stubParseSuccess(info)
        coEvery { repository.getActiveRequestForPackage("com.ok.app") } returns null
        coEvery { repository.isBlacklisted("com.ok.app") } returns false
        coEvery { repository.isAllowed("com.ok.app") } returns true

        val decision = managerAt(NOW).evaluateInstallation("url")

        assertTrue(decision is AppInstallationManager.InstallationDecision.AlreadyAllowed)
    }

    @Test
    fun `existing pending request yields PendingCooldown`() = runTest {
        val info = appInfo(pkg = "com.pending.app")
        val existing = pendingRequest("com.pending.app")
        stubParseSuccess(info)
        coEvery { repository.getActiveRequestForPackage("com.pending.app") } returns existing
        coEvery { repository.isBlacklisted("com.pending.app") } returns false
        coEvery { repository.isAllowed("com.pending.app") } returns false

        val decision = managerAt(NOW).evaluateInstallation("url")

        assertTrue(decision is AppInstallationManager.InstallationDecision.PendingCooldown)
        assertSame(
            existing,
            (decision as AppInstallationManager.InstallationDecision.PendingCooldown).request
        )
    }

    @Test
    fun `restricted category with no prior request requires cooldown with computed window`() = runTest {
        val info = appInfo(pkg = "com.social.app", category = AppCategory.SOCIAL)
        stubParseSuccess(info)
        coEvery { repository.getActiveRequestForPackage("com.social.app") } returns null
        coEvery { repository.isBlacklisted("com.social.app") } returns false
        coEvery { repository.isAllowed("com.social.app") } returns false
        every { parser.requiresCooldown(AppCategory.SOCIAL) } returns true

        val decision = managerAt(Instant.parse("2026-01-15T08:00:00Z"))
            .evaluateInstallation("url")

        assertTrue(decision is AppInstallationManager.InstallationDecision.RequiresCooldown)
        decision as AppInstallationManager.InstallationDecision.RequiresCooldown
        assertEquals(Instant.parse("2026-01-16T15:00:00Z"), decision.windowStart)
        assertEquals(Instant.parse("2026-01-16T18:00:00Z"), decision.windowEnd)
    }

    @Test
    fun `unrestricted app is allowed immediately`() = runTest {
        val info = appInfo(pkg = "com.tool.app", category = AppCategory.UNRESTRICTED)
        stubParseSuccess(info)
        coEvery { repository.getActiveRequestForPackage("com.tool.app") } returns null
        coEvery { repository.isBlacklisted("com.tool.app") } returns false
        coEvery { repository.isAllowed("com.tool.app") } returns false
        every { parser.requiresCooldown(AppCategory.UNRESTRICTED) } returns false

        val decision = managerAt(NOW).evaluateInstallation("url")

        assertTrue(decision is AppInstallationManager.InstallationDecision.Allowed)
    }

    // --- createCooldownRequest ---

    @Test
    fun `createCooldownRequest persists request with computed window and returned id`() = runTest {
        val info = appInfo(pkg = "com.social.app")
        coEvery { repository.createCooldownRequest(any()) } returns 42L

        val request = managerAt(Instant.parse("2026-01-15T08:00:00Z"))
            .createCooldownRequest(info, "https://play/url")

        assertEquals(42L, request.id)
        assertEquals("com.social.app", request.packageName)
        assertEquals(Instant.parse("2026-01-16T15:00:00Z"), request.windowStart)
        assertEquals(Instant.parse("2026-01-16T18:00:00Z"), request.windowEnd)
        coVerify { repository.createCooldownRequest(any()) }
    }

    // --- approveRequest: window boundaries and expiration ---

    private val windowStart = Instant.parse("2026-01-16T15:00:00Z")
    private val windowEnd = Instant.parse("2026-01-16T18:00:00Z")

    private fun windowOpenRequest() = CooldownRequest(
        id = 7L,
        packageName = "com.social.app",
        appName = "Social App",
        playStoreUrl = "url",
        category = AppCategory.SOCIAL,
        windowStart = windowStart,
        windowEnd = windowEnd,
        status = CooldownStatus.WINDOW_OPEN
    )

    @Test
    fun `approve fails when request does not exist`() = runTest {
        coEvery { repository.getRequestById(7L) } returns null
        val result = managerAt(windowStart.plusSeconds(60)).approveRequest(7L)
        assertTrue(result.isFailure)
    }

    @Test
    fun `approve fails when status is not WINDOW_OPEN even inside the window`() = runTest {
        coEvery { repository.getRequestById(7L) } returns
            windowOpenRequest().copy(status = CooldownStatus.PENDING)
        val result = managerAt(windowStart.plusSeconds(60)).approveRequest(7L)
        assertTrue(result.isFailure)
    }

    @Test
    fun `approve succeeds inside the window and allowlists the app`() = runTest {
        coEvery { repository.getRequestById(7L) } returns windowOpenRequest()
        coEvery { repository.addToAllowlist(any(), any(), any()) } returns 1L

        val result = managerAt(Instant.parse("2026-01-16T16:00:00Z")).approveRequest(7L)

        assertTrue(result.isSuccess)
        assertEquals(CooldownStatus.APPROVED, result.getOrNull()?.status)
        coVerify { repository.updateRequestStatus(7L, CooldownStatus.APPROVED) }
        coVerify {
            repository.addToAllowlist(
                "com.social.app",
                "Social App",
                AllowedAppSource.COOLDOWN_APPROVED
            )
        }
    }

    @Test
    fun `approve succeeds exactly at window start`() = runTest {
        coEvery { repository.getRequestById(7L) } returns windowOpenRequest()
        coEvery { repository.addToAllowlist(any(), any(), any()) } returns 1L

        val result = managerAt(windowStart).approveRequest(7L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `approve succeeds exactly at window end`() = runTest {
        coEvery { repository.getRequestById(7L) } returns windowOpenRequest()
        coEvery { repository.addToAllowlist(any(), any(), any()) } returns 1L

        val result = managerAt(windowEnd).approveRequest(7L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `approve fails one millisecond before the window opens`() = runTest {
        coEvery { repository.getRequestById(7L) } returns windowOpenRequest()

        val result = managerAt(windowStart.minusMillis(1)).approveRequest(7L)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.updateRequestStatus(any(), any()) }
    }

    @Test
    fun `approve fails one millisecond after the window closes - expired`() = runTest {
        coEvery { repository.getRequestById(7L) } returns windowOpenRequest()

        val result = managerAt(windowEnd.plusMillis(1)).approveRequest(7L)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.addToAllowlist(any(), any(), any()) }
    }

    // --- cancelRequest ---

    @Test
    fun `cancelRequest delegates to repository`() = runTest {
        val result = managerAt(NOW).cancelRequest(7L)
        assertTrue(result.isSuccess)
        coVerify { repository.cancelRequest(7L) }
    }

    // --- addToAllowlistImmediate ---

    @Test
    fun `addToAllowlistImmediate rejects a blacklisted app`() = runTest {
        coEvery { repository.isBlacklisted("com.bad.app") } returns true
        val result = managerAt(NOW).addToAllowlistImmediate("com.bad.app", "Bad")
        assertTrue(result.isFailure)
    }

    @Test
    fun `addToAllowlistImmediate returns existing entry when already allowed`() = runTest {
        val existing = AllowedApp(
            id = 3L,
            packageName = "com.ok.app",
            appName = "OK",
            source = AllowedAppSource.USER_ADDED
        )
        coEvery { repository.isBlacklisted("com.ok.app") } returns false
        coEvery { repository.isAllowed("com.ok.app") } returns true
        coEvery { repository.getByPackageName("com.ok.app") } returns existing

        val result = managerAt(NOW).addToAllowlistImmediate("com.ok.app", "OK")

        assertTrue(result.isSuccess)
        assertSame(existing, result.getOrNull())
    }

    @Test
    fun `addToAllowlistImmediate adds a new app to the allowlist`() = runTest {
        coEvery { repository.isBlacklisted("com.new.app") } returns false
        coEvery { repository.isAllowed("com.new.app") } returns false
        coEvery { repository.addToAllowlist("com.new.app", "New", AllowedAppSource.USER_ADDED) } returns 9L

        val result = managerAt(NOW).addToAllowlistImmediate("com.new.app", "New")

        assertTrue(result.isSuccess)
        val app = result.getOrNull()
        assertEquals(9L, app?.id)
        assertEquals("com.new.app", app?.packageName)
        assertEquals(AllowedAppSource.USER_ADDED, app?.source)
    }

    private fun pendingRequest(pkg: String) = CooldownRequest(
        id = 1L,
        packageName = pkg,
        appName = "Pending",
        playStoreUrl = "url",
        category = AppCategory.SOCIAL,
        windowStart = windowStart,
        windowEnd = windowEnd,
        status = CooldownStatus.PENDING
    )

    companion object {
        private val NOW: Instant = Instant.parse("2026-01-15T12:00:00Z")
    }
}
