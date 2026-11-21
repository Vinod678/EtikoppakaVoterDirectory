package com.example.etikoppakavoterdirectory

import com.example.etikoppakavoterdirectory.auth.AttemptResult
import com.example.etikoppakavoterdirectory.auth.AuthManager
import com.example.etikoppakavoterdirectory.auth.AuthState
import com.example.etikoppakavoterdirectory.auth.AuthStore
import com.example.etikoppakavoterdirectory.auth.LockReason
import com.example.etikoppakavoterdirectory.auth.PasswordPolicy
import com.example.etikoppakavoterdirectory.auth.Role
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Admin / Viewer sign-in (the person picks the role; only that role's credential is checked), the
 * daily formulas until 2 Dec 2026, the fixed passwords from 3 Dec 2026, the 3-hour session, the
 * midnight change and rate limiting.
 *
 * The real passwords are not in this public source: the rules are tested with stand-in passwords
 * through the same code ([PasswordPolicy.Rules]). The app's own verifiers are checked only when the
 * passwords are supplied as environment variables (ETK_ADMIN_BASE, ETK_VIEWER_BASE, ETK_VIEWER_FIXED).
 */
class AuthTest {

    private class FakeStore : AuthStore {
        override var failures = 0
        override var lockoutUntilWallMs = 0L
    }

    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, s: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, s, 0, ist).toInstant().toEpochMilli()

    private val adminBase = "Test_Admin_"
    private val viewerBase = "Test_Viewer_"
    private val viewerFixed = "TestViewerFixed"
    private val adminFixed = "TestAdminFixed"
    private fun verifier(pw: String): PasswordPolicy.Verifier {
        val salt = ByteArray(16) { it.toByte() }
        return PasswordPolicy.Verifier(salt, PasswordPolicy.derive(pw.toCharArray(), salt))
    }
    /** Like the app today: no separate Admin password after 2 Dec 2026. */
    private val rules = PasswordPolicy.Rules(verifier(adminBase), verifier(viewerBase), verifier(viewerFixed), adminFixed = null)
    /** After the developer has configured a separate Admin password. */
    private val rulesWithAdmin = PasswordPolicy.Rules(verifier(adminBase), verifier(viewerBase), verifier(viewerFixed), verifier(adminFixed))

    private fun ok(pw: String, role: Role, date: LocalDate, r: PasswordPolicy.Rules = rules) =
        PasswordPolicy.verify(pw.toCharArray(), role, date, r)
    private fun manager(store: AuthStore = FakeStore(), elapsed: () -> Long, wall: () -> Long, r: PasswordPolicy.Rules = rules) =
        AuthManager(store, elapsed, wall, { pw, role, d -> PasswordPolicy.verify(pw, role, d, r) }, { role, d -> PasswordPolicy.isAvailable(role, d, r) })

    private val nov20 = LocalDate.of(2026, 11, 20)
    private val dec2 = LocalDate.of(2026, 12, 2)
    private val dec3 = LocalDate.of(2026, 12, 3)

    @Test
    fun untilDec2_adminAndViewerFormulas() {
        // Day x 9 for Admin, "111" + day x 19 for Viewer.
        assertEquals("180", PasswordPolicy.adminSuffix(nov20))
        assertEquals("111380", PasswordPolicy.viewerSuffix(nov20))
        assertTrue(ok(adminBase + "180", Role.ADMIN, nov20))
        assertTrue(ok(viewerBase + "111380", Role.VIEWER, nov20))
        assertFalse(ok(viewerBase + "380", Role.VIEWER, nov20)) // the old rule without "111"
        assertTrue(ok(adminBase + "18", Role.ADMIN, dec2))
        assertTrue(ok(viewerBase + "11138", Role.VIEWER, dec2))
        assertTrue(ok(adminBase + "279", Role.ADMIN, LocalDate.of(2026, 10, 31)))  // 31 x 9
        assertTrue(ok(viewerBase + "111589", Role.VIEWER, LocalDate.of(2026, 10, 31))) // 111 + 31 x 19
    }

    @Test
    fun onlyTheSelectedRolesCredentialIsChecked() {
        // The correct password for the other role is refused.
        assertFalse(ok(adminBase + "180", Role.VIEWER, nov20))
        assertFalse(ok(viewerBase + "111380", Role.ADMIN, nov20))
        assertFalse(ok(viewerFixed, Role.ADMIN, dec3, rulesWithAdmin))
        assertFalse(ok(adminFixed, Role.VIEWER, dec3, rulesWithAdmin))
    }

    @Test
    fun untilDec2_wrongCombinationsAreRefused() {
        assertFalse(ok(adminBase + "111380", Role.ADMIN, nov20))   // Admin base with the Viewer digits
        assertFalse(ok(viewerBase + "180", Role.VIEWER, nov20)) // Viewer base with the Admin digits
        assertFalse(ok(adminBase + "189", Role.ADMIN, nov20))   // another day's digits
        assertFalse(ok(adminBase, Role.ADMIN, nov20))
        assertFalse(ok("test_admin_180", Role.ADMIN, nov20))    // case-sensitive
        assertFalse(ok(adminBase + "180 ", Role.ADMIN, nov20))
        assertFalse(ok(viewerFixed, Role.VIEWER, dec2))         // fixed password not valid yet
        assertFalse(ok(adminFixed, Role.ADMIN, dec2, rulesWithAdmin))
        assertFalse(ok("", Role.VIEWER, nov20))
        assertTrue(PasswordPolicy.isAvailable(Role.ADMIN, dec2, rules))
    }

    @Test
    fun fromDec3_viewerFixedPassword_formulasStop() {
        listOf(dec3, LocalDate.of(2026, 12, 31), LocalDate.of(2027, 6, 15)).forEach { d ->
            assertTrue("viewer fixed on $d", ok(viewerFixed, Role.VIEWER, d))
            assertFalse(ok(viewerFixed + PasswordPolicy.viewerSuffix(d), Role.VIEWER, d))
            assertFalse("formulas stop on $d", ok(adminBase + PasswordPolicy.adminSuffix(d), Role.ADMIN, d))
            assertFalse(ok(viewerBase + PasswordPolicy.viewerSuffix(d), Role.VIEWER, d))
            assertFalse(ok("testviewerfixed", Role.VIEWER, d))
        }
        assertFalse(PasswordPolicy.isDaily(dec3))
        assertTrue(PasswordPolicy.isDaily(dec2))
    }

    @Test
    fun fromDec3_viewerPasswordNeverOpensAdmin_andAdminIsUnavailableUntilConfigured() {
        assertFalse(PasswordPolicy.isAvailable(Role.ADMIN, dec3, rules))
        assertTrue(PasswordPolicy.isAvailable(Role.VIEWER, dec3, rules))
        assertFalse(ok(viewerFixed, Role.ADMIN, dec3))
        assertFalse(ok(adminFixed, Role.ADMIN, dec3))
        // Once a separate Admin password is configured, it (and only it) opens Admin.
        assertTrue(PasswordPolicy.isAvailable(Role.ADMIN, dec3, rulesWithAdmin))
        assertTrue(ok(adminFixed, Role.ADMIN, dec3, rulesWithAdmin))
    }

    @Test
    fun fromDec3_adminAttempt_isRefusedWithoutCountingAFailure() = runBlocking<Unit> {
        val store = FakeStore()
        val auth = manager(store, { 0L }, { at(2026, 12, 3, 9) })
        assertFalse(auth.isRoleAvailable(Role.ADMIN))
        assertTrue(auth.isRoleAvailable(Role.VIEWER))
        assertEquals(AttemptResult.RoleUnavailable, auth.attempt(viewerFixed.toCharArray(), Role.ADMIN))
        assertFalse(auth.isUnlocked)
        assertEquals(0, store.failures)
        assertEquals(AttemptResult.Success, auth.attempt(viewerFixed.toCharArray(), Role.VIEWER))
        assertEquals(Role.VIEWER, auth.role)
        assertFalse(auth.canEdit)
    }

    @Test
    fun datesUseAsiaKolkata_midnightBoundary() {
        // 2 Dec 2026 23:59:59 IST = 18:29:59 UTC; one second later it is 3 Dec in India.
        assertEquals(dec2, PasswordPolicy.dateAt(at(2026, 12, 2, 23, 59, 59)))
        assertEquals(dec3, PasswordPolicy.dateAt(at(2026, 12, 3, 0, 0, 0)))
        assertEquals(at(2026, 11, 24, 0, 0, 0), PasswordPolicy.nextMidnight(at(2026, 11, 23, 15, 0)))
    }

    @Test
    fun selectedRoleIsTheRoleThatOpens_withNewSessionEachTime() = runBlocking<Unit> {
        val auth = manager(elapsed = { 0L }, wall = { at(2026, 11, 20) })
        assertEquals(AttemptResult.Success, auth.attempt((viewerBase + "111380").toCharArray(), Role.VIEWER))
        val first = auth.state.value as AuthState.Unlocked
        assertEquals(Role.VIEWER, first.role)
        assertFalse(auth.canEdit)
        auth.logout()
        assertNull(auth.role)
        assertFalse(auth.canEdit)
        // The Admin password with the Viewer card selected is just a wrong password.
        assertTrue(auth.attempt((adminBase + "180").toCharArray(), Role.VIEWER) is AttemptResult.Wrong)
        assertFalse(auth.isUnlocked)
        assertEquals(AttemptResult.Success, auth.attempt((adminBase + "180").toCharArray(), Role.ADMIN))
        val second = auth.state.value as AuthState.Unlocked
        assertEquals(Role.ADMIN, second.role)
        assertTrue(auth.canEdit)
        assertNotEquals(first.session, second.session)
    }

    @Test
    fun session_lastsThreeHours_onElapsedClock() = runBlocking<Unit> {
        var elapsed = 1_000L
        val auth = manager(elapsed = { elapsed }, wall = { at(2026, 11, 20, 9) })
        assertEquals(AuthState.Locked(LockReason.START), auth.state.value)
        assertEquals(AttemptResult.Success, auth.attempt((adminBase + "180").toCharArray(), Role.ADMIN))
        elapsed += AuthManager.SESSION_MS - 1
        auth.check()
        assertTrue("still valid just before 3 h", auth.isUnlocked)
        elapsed += 1
        auth.check()
        assertEquals(AuthState.Locked(LockReason.EXPIRED), auth.state.value)
    }

    @Test
    fun fixedPasswordSession_alsoEndsAfterThreeHours_butNotAtMidnight() = runBlocking<Unit> {
        var elapsed = 0L
        var wall = at(2026, 12, 10, 23, 0)
        val auth = manager(elapsed = { elapsed }, wall = { wall })
        assertEquals(AttemptResult.Success, auth.attempt(viewerFixed.toCharArray(), Role.VIEWER))
        elapsed += 2 * 60 * 60 * 1000L; wall += 2 * 60 * 60 * 1000L // 01:00 on 11 Dec
        auth.check()
        assertTrue("same fixed password after midnight", auth.isUnlocked)
        elapsed += 60 * 60 * 1000L
        auth.check()
        assertEquals(AuthState.Locked(LockReason.EXPIRED), auth.state.value)
    }

    @Test
    fun passwordChangesAtMidnight_sessionEnds_newPasswordNeeded() = runBlocking<Unit> {
        var elapsed = 0L
        var wall = at(2026, 11, 23, 23, 50)
        val auth = manager(elapsed = { elapsed }, wall = { wall })
        assertEquals(AttemptResult.Success, auth.attempt((adminBase + "207").toCharArray(), Role.ADMIN))
        assertTrue(auth.millisUntilExpiry() <= 10 * 60 * 1000L) // next check is at midnight
        elapsed += 11 * 60 * 1000L; wall += 11 * 60 * 1000L     // 00:01 on 24 Nov (IST)
        auth.check()
        assertEquals(AuthState.Locked(LockReason.NEW_DAY), auth.state.value)
        assertTrue(auth.attempt((adminBase + "207").toCharArray(), Role.ADMIN) is AttemptResult.Wrong) // yesterday's
        assertEquals(AttemptResult.Success, auth.attempt((adminBase + "216").toCharArray(), Role.ADMIN))
    }

    @Test
    fun transitionAtMidnightOfDec3_bothRoles() = runBlocking<Unit> {
        var elapsed = 0L
        var wall = at(2026, 12, 2, 23, 55)
        val auth = manager(elapsed = { elapsed }, wall = { wall })
        // 2 Dec: both daily passwords work.
        assertEquals(AttemptResult.Success, auth.attempt((adminBase + "18").toCharArray(), Role.ADMIN))
        auth.logout()
        assertEquals(AttemptResult.Success, auth.attempt((viewerBase + "11138").toCharArray(), Role.VIEWER))
        // Midnight IST: the session ends, the formulas stop.
        elapsed += 10 * 60 * 1000L; wall += 10 * 60 * 1000L
        auth.check()
        assertEquals(AuthState.Locked(LockReason.NEW_DAY), auth.state.value)
        assertTrue(auth.attempt((viewerBase + "11157").toCharArray(), Role.VIEWER) is AttemptResult.Wrong)
        assertEquals(AttemptResult.RoleUnavailable, auth.attempt((adminBase + "27").toCharArray(), Role.ADMIN))
        assertEquals(AttemptResult.Success, auth.attempt(viewerFixed.toCharArray(), Role.VIEWER))
        assertEquals(Role.VIEWER, auth.role)
    }

    @Test
    fun logout_and_freshStart_lock() = runBlocking<Unit> {
        val auth = manager(elapsed = { 0L }, wall = { at(2026, 11, 20) })
        auth.attempt((adminBase + "180").toCharArray(), Role.ADMIN)
        auth.logout()
        assertEquals(AuthState.Locked(LockReason.LOGGED_OUT), auth.state.value)
        auth.attempt((adminBase + "180").toCharArray(), Role.ADMIN)
        auth.lock(LockReason.START)
        assertFalse(auth.isUnlocked)
    }

    @Test
    fun wrongPasswords_areRateLimited_andCounterSurvivesRestart() = runBlocking<Unit> {
        var elapsed = 0L
        var wall = at(2026, 11, 20)
        val store = FakeStore()
        val auth = manager(store, { elapsed }, { wall })
        repeat(AuthManager.FREE_ATTEMPTS - 1) { i ->
            assertEquals(AttemptResult.Wrong(AuthManager.FREE_ATTEMPTS - 1 - i), auth.attempt("nope$i".toCharArray(), Role.VIEWER))
        }
        assertTrue(auth.attempt("nope".toCharArray(), Role.ADMIN) is AttemptResult.LockedOut)
        // Even the right password is refused during the wait, and a new process keeps the wait.
        assertTrue(auth.attempt((adminBase + "180").toCharArray(), Role.ADMIN) is AttemptResult.LockedOut)
        val restarted = AuthManager(store, { 0L }, { wall })
        assertTrue(restarted.lockoutSecondsLeft() > 0)
        elapsed += 31_000; wall += 31_000
        assertEquals(AttemptResult.Success, auth.attempt((adminBase + "180").toCharArray(), Role.ADMIN))
        assertEquals(0, store.failures)
    }

    @Test
    fun passwordArray_isClearedAfterUse() = runBlocking<Unit> {
        val auth = manager(elapsed = { 0L }, wall = { at(2026, 11, 20) })
        val chars = (adminBase + "180").toCharArray()
        auth.attempt(chars, Role.ADMIN)
        assertTrue(chars.all { it == '\u0000' })
        val unavailable = manager(elapsed = { 0L }, wall = { at(2026, 12, 3) })
        val chars2 = viewerFixed.toCharArray()
        unavailable.attempt(chars2, Role.ADMIN)
        assertTrue(chars2.all { it == '\u0000' })
    }

    /** The app's real verifiers - only when the passwords are given locally (never committed). */
    @Test
    fun appVerifiers_matchTheConfiguredPasswords() {
        val admin = System.getenv("ETK_ADMIN_BASE")
        val viewer = System.getenv("ETK_VIEWER_BASE")
        val viewerFixedPw = System.getenv("ETK_VIEWER_FIXED")
        assumeTrue("set ETK_ADMIN_BASE, ETK_VIEWER_BASE and ETK_VIEWER_FIXED to check the app's verifiers", admin != null && viewer != null && viewerFixedPw != null)
        val app = PasswordPolicy.APP_RULES
        fun v(pw: String, role: Role, d: LocalDate) = PasswordPolicy.verify(pw.toCharArray(), role, d, app)
        for (day in 1..30) {
            val d = LocalDate.of(2026, 11, day)
            assertTrue(v(admin + day * 9, Role.ADMIN, d))
            assertTrue(v(viewer + "111" + day * 19, Role.VIEWER, d))
            assertFalse(v(admin + day * 9, Role.VIEWER, d))
            assertFalse(v(viewer + "111" + day * 19, Role.ADMIN, d))
            assertFalse(v(viewerFixedPw!!, Role.VIEWER, d))
        }
        assertTrue(v(admin + 18, Role.ADMIN, dec2))
        assertTrue(v(viewer + "11138", Role.VIEWER, dec2))
        assertTrue(v(viewerFixedPw!!, Role.VIEWER, dec3))
        assertFalse(v(viewerFixedPw, Role.ADMIN, dec3))
        assertFalse(v(admin + 27, Role.ADMIN, dec3))
        assertFalse("no Admin password is configured after 2 Dec yet", PasswordPolicy.isAvailable(Role.ADMIN, dec3, app))
    }
}
