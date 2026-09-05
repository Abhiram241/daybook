package com.daybook.app.data.lock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** How long the app may sit in the background before the lock re-arms (decision 7). */
enum class LockTimeout(val label: String, val millis: Long) {
    IMMEDIATELY("Immediately", 0L),
    ONE_MIN("After 1 minute", 60_000L),
    FIVE_MIN("After 5 minutes", 5 * 60_000L),
    FIFTEEN_MIN("After 15 minutes", 15 * 60_000L);

    companion object {
        fun fromNameOrDefault(name: String?): LockTimeout =
            entries.firstOrNull { it.name == name } ?: IMMEDIATELY
    }
}

/**
 * App lock backend (v0.5.1 §K, decisions 5–9). **No new Room table — the DB stays at v7.**
 *
 * Storage is its own `EncryptedSharedPreferences` file, `daybook_lock`. Deliberately *not*
 * `daybook_prefs`: that file is shared by [com.daybook.app.data.sync.SyncStateStore] and
 * MainActivity's exact-alarm flag, and `SyncStateStore.clearForSignOut()` edits it — a PIN hash
 * has no business in a file another component wipes on sign-out.
 *
 * `lastBackgroundedAt` is persisted rather than held in memory: a process death between background
 * and foreground would reset an in-memory field to "never backgrounded" and silently skip the lock.
 *
 * The UI layer binds to [isEnabled] / [timeout] / [isLocked] and the suspend mutators; see
 * `ui/lock/LockViewModel`.
 */
@Singleton
class AppLockRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences = openPrefs(context)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _timeout = MutableStateFlow(LockTimeout.fromNameOrDefault(prefs.getString(KEY_TIMEOUT, null)))
    val timeout: StateFlow<LockTimeout> = _timeout.asStateFlow()

    /** Decision 5: a cold start always locks, so this is seeded from `enabled`, not from `false`. */
    private val _isLocked = MutableStateFlow(_isEnabled.value)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // ------------------------------------------------------------------ enable / disable

    /**
     * Sets [pin] as the lock PIN and arms the lock. Returns false for anything that is not a
     * 4-digit PIN. A PIN always exists as the biometric fallback, so the enable flow can never
     * be completed biometric-only.
     */
    suspend fun enable(pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
        prefs.edit()
            .putString(KEY_PIN_SALT, PinHasher.toHex(salt))
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        _isEnabled.value = true
        // Enabling from inside the app must not immediately black out the screen the user is on.
        _isLocked.value = false
        return true
    }

    /**
     * Turns the lock off. Requires the current PIN — or pass [bypassPin] = true when a
     * [BiometricGate] prompt has just succeeded (decision 6 makes biometric primary, so it must be
     * sufficient for a settings change too).
     */
    suspend fun disable(pin: String, bypassPin: Boolean = false): Boolean {
        if (!bypassPin && !verifyPin(pin)) return false
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_BG_AT)
            .putBoolean(KEY_ENABLED, false)
            .apply()
        _isEnabled.value = false
        _isLocked.value = false
        return true
    }

    /** Current → new. Returns false if the current PIN is wrong or the new one is not 4 digits. */
    suspend fun changePin(current: String, new: String, bypassCurrent: Boolean = false): Boolean {
        if (!bypassCurrent && !verifyPin(current)) return false
        if (!PinHasher.isValidPin(new)) return false
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(new, salt) }
        prefs.edit()
            .putString(KEY_PIN_SALT, PinHasher.toHex(salt))
            .putString(KEY_PIN_HASH, hash)
            .apply()
        return true
    }

    /** Decision 8: unlimited attempts, no cooldown. Do not add a counter here. */
    suspend fun verifyPin(pin: String): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let { PinHasher.fromHex(it) } ?: return false
        return withContext(Dispatchers.Default) { PinHasher.verify(pin, salt, hash) }
    }

    /** True once [enable] has stored a PIN — the lock screen shows the pad only when this holds. */
    fun hasPin(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    // ------------------------------------------------------------------ timeout + lock state

    fun setTimeout(t: LockTimeout) {
        prefs.edit().putString(KEY_TIMEOUT, t.name).apply()
        _timeout.value = t
    }

    /** Called by the lock screen after a correct PIN or a successful biometric prompt. */
    fun unlock() {
        _isLocked.value = false
    }

    /** Manual re-lock (not currently reachable from the UI, but the state machine needs it). */
    fun lockNow() {
        if (_isEnabled.value) _isLocked.value = true
    }

    fun onAppBackgrounded() {
        if (!_isEnabled.value) return
        prefs.edit().putLong(KEY_BG_AT, System.currentTimeMillis()).apply()
    }

    /**
     * `IMMEDIATELY` has `millis = 0`, so any measurable gap locks — which is the intent. A
     * `lastBackgroundedAt` of 0 (never backgrounded on this install) also locks, which is the
     * correct conservative answer.
     */
    fun onAppForegrounded() {
        if (!_isEnabled.value) return
        if (_isLocked.value) return
        val since = System.currentTimeMillis() - prefs.getLong(KEY_BG_AT, 0L)
        if (since > _timeout.value.millis) _isLocked.value = true
    }

    private companion object {
        const val TAG = "AppLockRepository"
        const val FILE = "daybook_lock"
        /**
         * Fallback file used only when the Android keystore refuses to produce a master key (a
         * genuinely broken or badly-provisioned device). What lands there is still the PBKDF2
         * digest and the salt — never a plaintext PIN — so the worst case is a weaker at-rest
         * story, not a disclosed PIN. Bricking the app on such a device would be worse.
         */
        const val FILE_FALLBACK = "daybook_lock_plain"

        const val KEY_ENABLED = "enabled"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_TIMEOUT = "timeout"
        const val KEY_BG_AT = "last_backgrounded_at"

        fun openPrefs(context: Context): SharedPreferences = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as SharedPreferences
        }.getOrElse {
            Log.w(TAG, "EncryptedSharedPreferences unavailable — falling back to plain prefs", it)
            context.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE)
        }
    }
}
