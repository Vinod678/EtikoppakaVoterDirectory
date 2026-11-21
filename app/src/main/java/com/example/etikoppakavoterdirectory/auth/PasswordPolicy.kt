package com.example.etikoppakavoterdirectory.auth

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** What a signed-in person may do. Admin: everything. Viewer: read-only voter browsing. */
enum class Role { ADMIN, VIEWER }

/**
 * Which password opens which role on a given day (Asia/Kolkata calendar date). The person picks
 * Admin or Viewer on the lock screen and only that role's credential is checked.
 *
 *  - Up to and including 2 December 2026 (date-based passwords):
 *      Admin  = admin base  + (day of month x 9),  e.g. 20 Nov -> base + "180"
 *      Viewer = viewer base + "111" + (day of month x 19), e.g. 20 Nov -> base + "111380"
 *  - From 3 December 2026 (no date part):
 *      Viewer = the fixed Viewer password.
 *      Admin  = a separate fixed Admin password ([Rules.adminFixed]) - the Viewer password never
 *               opens Admin. (With no Admin password configured, Admin sign-in is unavailable.)
 *
 * No password is stored in the app or the source code: only a salted PBKDF2-HMAC-SHA256 verifier of
 * each fixed part (made with tools/make_password_verifier.py); the date digits are checked
 * separately. LIMITATION (by design of the requested rules): the date formulas are public in the
 * code, so anyone who learns a base - or reverse-engineers the APK and brute-forces a verifier - can
 * work out every daily password. This protects against casual access, not a determined attacker.
 */
object PasswordPolicy {
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    /** Last day of the date-based passwords. */
    val LAST_DAILY_DATE: LocalDate = LocalDate.of(2026, 12, 2)

    const val ITERATIONS = 20_000

    /** Salted hash of one fixed password part. */
    class Verifier(val salt: ByteArray, val hash: ByteArray) {
        constructor(saltHex: String, hashHex: String) : this(hex(saltHex), hex(hashHex))
    }

    /**
     * [adminBase] / [viewerBase]: fixed parts of the daily passwords (until 2 Dec 2026).
     * [viewerFixed]: the Viewer password from 3 Dec 2026.
     * [adminFixed]: the Admin password from 3 Dec 2026; null = not configured (Admin unavailable).
     */
    class Rules(val adminBase: Verifier, val viewerBase: Verifier, val viewerFixed: Verifier, val adminFixed: Verifier?)

    /** The app's rules (see tools/make_password_verifier.py to set or change a password). */
    val APP_RULES = Rules(
        adminBase = Verifier("21f4a72060a5421cb11194d9098e7833", "82313a8c998cfb5043eeb5aa8d453541af0e2a6fa7309f589cad22e92d4b6832"),
        viewerBase = Verifier("4d4441373f61af0ffe276559e1558bfd", "6391c224d445221183a69b0c30fc870b283842855da6ec12e0c568ae08e01e0d"),
        viewerFixed = Verifier("622055b4c35cbcae5e1bf144a6255b9e", "cfc177cfd22ab1d587aa62ed9485ea4c01f06acdf8ff1123609572a70ccf7ac3"),
        // The separate Admin password from 3 Dec 2026.
        adminFixed = Verifier("6114f3c11adc5002fb712b0289738826", "64bf967a0ce9f3c2444d80e5cc301421beefdd95cdbaac3b0f9366718c8f636b"),
    )

    fun dateAt(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDate()

    /** Epoch millis of the next midnight in Asia/Kolkata after [epochMillis]. */
    fun nextMidnight(epochMillis: Long): Long =
        dateAt(epochMillis).plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** True while the passwords change every day (up to and including [LAST_DAILY_DATE]). */
    fun isDaily(date: LocalDate): Boolean = !date.isAfter(LAST_DAILY_DATE)

    fun adminSuffix(date: LocalDate): String = "${date.dayOfMonth * 9}"
    fun viewerSuffix(date: LocalDate): String = "111${date.dayOfMonth * 19}"

    /** False only for Admin from 3 Dec 2026 while no separate Admin password is configured. */
    fun isAvailable(role: Role, date: LocalDate, rules: Rules = APP_RULES): Boolean =
        isDaily(date) || role == Role.VIEWER || rules.adminFixed != null

    /**
     * True when [input] is the [role] password on [date] (case-sensitive). Only that role's
     * credential is checked. [input] is not kept or logged; the caller clears it. Runs PBKDF2 (tens
     * of milliseconds) - call off the main thread.
     */
    fun verify(input: CharArray, role: Role, date: LocalDate, rules: Rules = APP_RULES): Boolean {
        if (input.isEmpty() || input.size > 128) return false
        return if (isDaily(date)) {
            when (role) {
                Role.ADMIN -> matches(input, adminSuffix(date), rules.adminBase)
                Role.VIEWER -> matches(input, viewerSuffix(date), rules.viewerBase)
            }
        } else {
            val fixed = when (role) {
                Role.ADMIN -> rules.adminFixed ?: return false
                Role.VIEWER -> rules.viewerFixed
            }
            matches(input, "", fixed)
        }
    }

    /** [input] = a fixed part matching [base] followed by exactly [suffix]. */
    private fun matches(input: CharArray, suffix: String, base: Verifier): Boolean {
        if (input.size <= suffix.length) return false
        val split = input.size - suffix.length
        var suffixOk = true
        for (i in suffix.indices) if (input[split + i] != suffix[i]) suffixOk = false
        val fixed = input.copyOfRange(0, split)
        return try {
            // Always compute the verifier, then compare in constant time.
            MessageDigest.isEqual(derive(fixed, base.salt), base.hash) && suffixOk
        } finally {
            fixed.fill('\u0000')
        }
    }

    /** PBKDF2-HMAC-SHA256, [ITERATIONS] rounds, 32 bytes (same as tools/make_password_verifier.py). */
    fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
