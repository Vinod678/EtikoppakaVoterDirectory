package com.example.etikoppakavoterdirectory.auth

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Which password opens the app on a given day (Asia/Kolkata calendar date).
 *
 *  - Up to and including 23 November 2026: the base password.
 *  - From 24 November 2026: base password + (day of month x 9) + "9",
 *    e.g. 24 Nov -> base + "2169", 30 Nov -> base + "2709", 1 Dec -> base + "99".
 *
 * The base password is not stored in the app. Only a salted PBKDF2-HMAC-SHA256 verifier of it is
 * kept, and the date suffix is checked separately. LIMITATION (by design of the requested rule):
 * the suffix formula is public in the app code, so anyone who learns the base password - or
 * reverse-engineers the APK and brute-forces the verifier - can work out every future password.
 * This protects against casual access, not against a determined attacker with the APK.
 */
object PasswordPolicy {
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    val SWITCH_DATE: LocalDate = LocalDate.of(2026, 11, 24)

    private const val ITERATIONS = 20_000
    private val SALT = hex("f6025774eeba890eba74ff407a69d70c")
    private val VERIFIER = hex("7041cfe9081f17d6a7d735fca05c1e416e14bfdefad95d2636763718e2b9fa02")

    fun dateAt(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDate()

    /** Epoch millis of the next midnight in Asia/Kolkata after [epochMillis]. */
    fun nextMidnight(epochMillis: Long): Long =
        dateAt(epochMillis).plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** The digits that must follow the base password on [date] ("" before the switch date). */
    fun suffixFor(date: LocalDate): String =
        if (date.isBefore(SWITCH_DATE)) "" else "${date.dayOfMonth * 9}9"

    /**
     * Case-sensitive check of [input] for [date]. [input] is not kept or logged; the caller clears it.
     * Runs PBKDF2 (tens of milliseconds) - call off the main thread.
     */
    fun verify(input: CharArray, date: LocalDate): Boolean {
        val suffix = suffixFor(date)
        if (input.size <= suffix.length || input.size > 128) return false
        val split = input.size - suffix.length
        var suffixOk = true
        for (i in suffix.indices) if (input[split + i] != suffix[i]) suffixOk = false
        val base = input.copyOfRange(0, split)
        return try {
            val spec = PBEKeySpec(base, SALT, ITERATIONS, 256)
            val derived = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
            // Always compute the verifier (no early exit), then compare in constant time.
            MessageDigest.isEqual(derived, VERIFIER) && suffixOk
        } finally {
            base.fill('\u0000')
        }
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
