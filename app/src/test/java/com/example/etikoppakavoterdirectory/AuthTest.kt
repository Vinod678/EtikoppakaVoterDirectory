package com.example.etikoppakavoterdirectory

import com.example.etikoppakavoterdirectory.auth.AttemptResult
import com.example.etikoppakavoterdirectory.auth.AuthManager
import com.example.etikoppakavoterdirectory.auth.AuthState
import com.example.etikoppakavoterdirectory.auth.AuthStore
import com.example.etikoppakavoterdirectory.auth.LockReason
import com.example.etikoppakavoterdirectory.auth.PasswordPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Password rules (Asia/Kolkata dates), 3-hour session, midnight change and rate limiting. */
class AuthTest {

    private class FakeStore : AuthStore {
        override var failures = 0
        override var lockoutUntilWallMs = 0L
    }

    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, s: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, s, 0, ist).toInstant().toEpochMilli()

    private fun ok(pw: String, date: LocalDate) = PasswordPolicy.verify(pw.toCharArray(), date)
    private val base = "Vinod_Yedla"

    @Test
    fun untilNov23_basePassword_caseSensitive() {
        val d = LocalDate.of(2026, 11, 23)
        assertTrue(ok(base, d))
        assertFalse(ok("vinod_yedla", d))
        assertFalse(ok("VINOD_YEDLA", d))
        assertFalse(ok("Vinod_Yedla ", d))
        assertFalse(ok("Vinod_Yedla2079", d)) // the date formula does not apply yet
        assertFalse(ok("", d))
        assertTrue(ok(base, LocalDate.of(2026, 11, 19))) // today (app build date)
    }

    @Test
    fun fromNov24_dateFormula_exactTableFromRequirements() {
        val expected = mapOf(24 to "2169", 25 to "2259", 26 to "2349", 27 to "2439", 28 to "2529", 30 to "2709")
        expected.forEach { (day, suffix) ->
            val d = LocalDate.of(2026, 11, day)
            assertEquals(suffix, PasswordPolicy.suffixFor(d))
            assertTrue("Nov $day", ok(base + suffix, d))
            assertFalse("base alone on Nov $day", ok(base, d))
            assertFalse("lower case on Nov $day", ok("vinod_yedla$suffix", d))
        }
        assertFalse(ok(base + "2259", LocalDate.of(2026, 11, 24))) // tomorrow's password is not today's
        // The formula repeats by day of month in later months.
        assertTrue(ok(base + "99", LocalDate.of(2026, 12, 1)))
        assertTrue(ok(base + "2169", LocalDate.of(2026, 12, 24)))
        assertTrue(ok(base + "2799", LocalDate.of(2027, 1, 31)))
    }

    @Test
    fun datesUseAsiaKolkata_midnightBoundary() {
        // 23 Nov 2026 23:59:59 IST = 18:29:59 UTC; one second later it is 24 Nov in India.
        assertEquals(LocalDate.of(2026, 11, 23), PasswordPolicy.dateAt(at(2026, 11, 23, 23, 59, 59)))
        assertEquals(LocalDate.of(2026, 11, 24), PasswordPolicy.dateAt(at(2026, 11, 24, 0, 0, 0)))
        assertEquals(at(2026, 11, 24, 0, 0, 0), PasswordPolicy.nextMidnight(at(2026, 11, 23, 15, 0)))
    }

    @Test
    fun session_lastsThreeHours_onElapsedClock() = runBlocking<Unit> {
        var elapsed = 1_000L
        val wall = at(2026, 11, 20, 9)
        val auth = AuthManager(FakeStore(), { elapsed }, { wall })
        assertEquals(AuthState.Locked(LockReason.START), auth.state.value)
        assertEquals(AttemptResult.Success, auth.attempt(base.toCharArray()))
        assertTrue(auth.isUnlocked)
        elapsed += AuthManager.SESSION_MS - 1
        auth.check()
        assertTrue("still valid just before 3 h", auth.isUnlocked)
        elapsed += 1
        auth.check()
        assertEquals(AuthState.Locked(LockReason.EXPIRED), auth.state.value)
    }

    @Test
    fun passwordChangesAtMidnight_sessionEnds_newPasswordNeeded() = runBlocking<Unit> {
        var elapsed = 0L
        var wall = at(2026, 11, 23, 23, 50)
        val auth = AuthManager(FakeStore(), { elapsed }, { wall })
        assertEquals(AttemptResult.Success, auth.attempt(base.toCharArray()))
        assertTrue(auth.millisUntilExpiry() <= 10 * 60 * 1000L) // next check is at midnight
        elapsed += 11 * 60 * 1000L; wall += 11 * 60 * 1000L     // 00:01 on 24 Nov (IST)
        auth.check()
        assertEquals(AuthState.Locked(LockReason.NEW_DAY), auth.state.value)
        assertTrue(auth.attempt(base.toCharArray()) is AttemptResult.Wrong) // yesterday's password
        assertEquals(AttemptResult.Success, auth.attempt((base + "2169").toCharArray()))
    }

    @Test
    fun logout_and_freshStart_lock() = runBlocking<Unit> {
        val auth = AuthManager(FakeStore(), { 0L }, { at(2026, 11, 20) })
        auth.attempt(base.toCharArray())
        auth.logout()
        assertEquals(AuthState.Locked(LockReason.LOGGED_OUT), auth.state.value)
        auth.attempt(base.toCharArray())
        auth.lock(LockReason.START)
        assertFalse(auth.isUnlocked)
    }

    @Test
    fun wrongPasswords_areRateLimited_andCounterSurvivesRestart() = runBlocking<Unit> {
        var elapsed = 0L
        var wall = at(2026, 11, 20)
        val store = FakeStore()
        val auth = AuthManager(store, { elapsed }, { wall })
        repeat(AuthManager.FREE_ATTEMPTS - 1) { i ->
            assertEquals(AttemptResult.Wrong(AuthManager.FREE_ATTEMPTS - 1 - i), auth.attempt("nope$i".toCharArray()))
        }
        assertTrue(auth.attempt("nope".toCharArray()) is AttemptResult.LockedOut)
        // Even the right password is refused during the wait, and a new process keeps the wait.
        assertTrue(auth.attempt(base.toCharArray()) is AttemptResult.LockedOut)
        val restarted = AuthManager(store, { 0L }, { wall })
        assertTrue(restarted.lockoutSecondsLeft() > 0)
        elapsed += 31_000; wall += 31_000
        assertEquals(AttemptResult.Success, auth.attempt(base.toCharArray()))
        assertEquals(0, store.failures)
    }

    @Test
    fun passwordArray_isClearedAfterUse() = runBlocking<Unit> {
        val auth = AuthManager(FakeStore(), { 0L }, { at(2026, 11, 20) })
        val chars = base.toCharArray()
        auth.attempt(chars)
        assertTrue(chars.all { it == '\u0000' })
    }
}
