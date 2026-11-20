package com.example.etikoppakavoterdirectory.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Why the lock screen is shown (never contains a password). */
enum class LockReason(val message: String?) {
    START(null),
    EXPIRED("Your session has ended after 3 hours. Please sign in again."),
    NEW_DAY("The date has changed. Please enter today's password."),
    LOGGED_OUT("You have been signed out."),
}

sealed interface AuthState {
    data class Locked(val reason: LockReason) : AuthState
    /**
     * Signed in as [role]. [session] is new for every sign-in (also after a restart), so screens
     * and search state from an earlier sign-in are never brought back.
     */
    data class Unlocked(val role: Role, val session: Long) : AuthState
}

sealed interface AttemptResult {
    data object Success : AttemptResult
    /** Sign-in for this role is not possible today (Admin from 3 Dec 2026 without an Admin password configured). */
    data object RoleUnavailable : AttemptResult
    /** Wrong password. [attemptsLeft] before a temporary lock-out. */
    data class Wrong(val attemptsLeft: Int) : AttemptResult
    data class LockedOut(val secondsLeft: Long) : AttemptResult
}

/** Persistent failed-attempt counter. Never stores any password. */
interface AuthStore {
    var failures: Int
    var lockoutUntilWallMs: Long
}

/**
 * Session handling for the app password.
 *
 * - A session lives only in memory: a new process (fresh start, restart, process death) is locked.
 * - A session ends 3 hours after sign-in, measured with the monotonic elapsed-time clock (changing
 *   the phone's clock does not extend it).
 * - A session also ends when the Asia/Kolkata calendar date changes (each day may need a new password).
 * - Failed attempts are rate-limited: after [FREE_ATTEMPTS] wrong passwords, a lock-out of 30 s that
 *   doubles with each further failure (max 15 min). The counter survives app restarts.
 */
class AuthManager(
    private val store: AuthStore,
    private val elapsed: () -> Long,
    private val wall: () -> Long = System::currentTimeMillis,
    private val verifier: (CharArray, Role, LocalDate) -> Boolean = { pw, r, d -> PasswordPolicy.verify(pw, r, d) },
    private val available: (Role, LocalDate) -> Boolean = { r, d -> PasswordPolicy.isAvailable(r, d) },
) {
    private class Session(val startElapsed: Long, val date: LocalDate)

    @Volatile private var session: Session? = null
    private val _state = MutableStateFlow<AuthState>(AuthState.Locked(LockReason.START))
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val mutex = Mutex()
    /** Lock-out end on the elapsed clock (so moving the wall clock back does not shorten it in this run). */
    private var lockoutUntilElapsed = 0L

    val isUnlocked get() = _state.value is AuthState.Unlocked

    /** Role of the current session; null while locked. */
    val role: Role? get() = (_state.value as? AuthState.Unlocked)?.role

    /** True only for a signed-in Admin. Every data-changing operation checks this. */
    val canEdit: Boolean get() = role == Role.ADMIN

    /** Whether [role] can sign in today (see [PasswordPolicy.isAvailable]). */
    fun isRoleAvailable(role: Role): Boolean = available(role, PasswordPolicy.dateAt(wall()))

    /** Re-checks the session; locks the app if it is no longer valid. Cheap; call often. */
    fun check() {
        val s = session ?: return
        val reason = when {
            elapsed() - s.startElapsed >= SESSION_MS || elapsed() < s.startElapsed -> LockReason.EXPIRED
            // Daily passwords: a new day needs the new password (also on the switch to the fixed
            // password on 3 Dec). With the fixed password only the 3-hour limit applies.
            PasswordPolicy.isDaily(s.date) && PasswordPolicy.dateAt(wall()) != s.date -> LockReason.NEW_DAY
            else -> null
        }
        if (reason != null) lock(reason)
    }

    fun lock(reason: LockReason) {
        session = null
        _state.value = AuthState.Locked(reason)
    }

    fun logout() = lock(LockReason.LOGGED_OUT)

    /** Milliseconds until the session must be checked again (3-hour limit or next midnight IST). */
    fun millisUntilExpiry(): Long {
        val s = session ?: return Long.MAX_VALUE
        val bySession = SESSION_MS - (elapsed() - s.startElapsed)
        val byDate = if (PasswordPolicy.isDaily(s.date)) PasswordPolicy.nextMidnight(wall()) - wall() else Long.MAX_VALUE
        return maxOf(0L, minOf(bySession, byDate))
    }

    fun lockoutSecondsLeft(): Long {
        val byWall = store.lockoutUntilWallMs - wall()
        val byElapsed = lockoutUntilElapsed - elapsed()
        val ms = maxOf(byWall, byElapsed)
        // A lock-out far in the future can only come from a changed clock: cap it.
        return if (ms <= 0) 0 else (minOf(ms, MAX_LOCKOUT_MS) + 999) / 1000
    }

    /**
     * Checks [password] (cleared afterwards) against the credential of [role] only, and on success
     * signs in as exactly that role. Runs the slow verifier off the main thread.
     */
    suspend fun attempt(password: CharArray, role: Role): AttemptResult = mutex.withLock {
        try {
            val wait = lockoutSecondsLeft()
            if (wait > 0) return@withLock AttemptResult.LockedOut(wait)
            val today = PasswordPolicy.dateAt(wall())
            // Not a wrong password: nothing is checked and no failure is counted.
            if (!available(role, today)) return@withLock AttemptResult.RoleUnavailable
            val ok = withContext(Dispatchers.Default) { verifier(password, role, today) }
            if (ok) {
                store.failures = 0
                store.lockoutUntilWallMs = 0
                lockoutUntilElapsed = 0
                session = Session(elapsed(), today)
                _state.value = AuthState.Unlocked(role, sessionIds.nextLong())
                AttemptResult.Success
            } else {
                val failures = store.failures + 1
                store.failures = failures
                if (failures >= FREE_ATTEMPTS) {
                    val ms = minOf(MAX_LOCKOUT_MS, BASE_LOCKOUT_MS shl minOf(10, failures - FREE_ATTEMPTS))
                    store.lockoutUntilWallMs = wall() + ms
                    lockoutUntilElapsed = elapsed() + ms
                    AttemptResult.LockedOut(ms / 1000)
                } else {
                    AttemptResult.Wrong(FREE_ATTEMPTS - failures)
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private val sessionIds = java.security.SecureRandom()

    companion object {
        const val SESSION_MS = 3L * 60 * 60 * 1000
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15L * 60 * 1000
    }
}

/**
 * [AuthStore] in app-private SharedPreferences, encrypted with an AES-GCM key held in the Android
 * Keystore (the key never leaves secure hardware where available). If the Keystore is unavailable
 * the values are kept unencrypted - they contain only a counter and a time, never a password.
 */
class KeystoreAuthStore(context: Context) : AuthStore {
    private val prefs = context.getSharedPreferences("auth_state", Context.MODE_PRIVATE)

    override var failures: Int
        get() = read("f")?.toInt() ?: 0
        set(v) = write("f", v.toLong())

    override var lockoutUntilWallMs: Long
        get() = read("l") ?: 0L
        set(v) = write("l", v)

    private fun key(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    } catch (e: Exception) {
        null
    }

    private fun write(name: String, value: Long) {
        val plain = ByteBuffer.allocate(8).putLong(value).array()
        val k = key()
        val stored = if (k == null) "p:$value" else try {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
            "e:" + Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(plain), Base64.NO_WRAP)
        } catch (e: Exception) {
            "p:$value"
        }
        prefs.edit().putString(name, stored).apply()
    }

    private fun read(name: String): Long? {
        val s = prefs.getString(name, null) ?: return null
        return try {
            when {
                s.startsWith("p:") -> s.removePrefix("p:").toLong()
                s.startsWith("e:") -> {
                    val parts = s.split(":")
                    val k = key() ?: return null
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP)))
                    ByteBuffer.wrap(c.doFinal(Base64.decode(parts[2], Base64.NO_WRAP))).long
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val ALIAS = "etikoppaka_auth_state"
    }
}
