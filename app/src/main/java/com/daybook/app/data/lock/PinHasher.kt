package com.daybook.app.data.lock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN hashing for the app lock (v0.5.1 §K).
 *
 * A **pure JVM object** with no Android dependency, deliberately — that is what makes
 * `PinHasherTest` a plain JUnit test, the same shape as `ContentHash` / `PayloadCodec`.
 *
 * The plaintext PIN is never stored, never logged, and never leaves the stack of the function it
 * was passed to. Only [hash]'s hex digest and the per-install salt are persisted, and both live in
 * `EncryptedSharedPreferences` (see [AppLockRepository]).
 *
 * Decision 8: **unlimited attempts, no cooldown, no lockout counter.** The 120k-iteration PBKDF2 is
 * the only rate limit, and that is by design — a lockout on a 4-digit PIN protects nothing a
 * determined local attacker cannot wait out, and it does lock out the real user.
 */
object PinHasher {

    /** ~150 ms on a mid-range 2020 device. Changing this invalidates every stored hash. */
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    /** `PBKDF2WithHmacSHA256` is available from API 26, which matches the app's minSdk. */
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /** A PIN is exactly four digits — anything else is rejected before it reaches the KDF. */
    private val PIN_PATTERN = Regex("^\\d{4}$")

    fun isValidPin(pin: String): Boolean = PIN_PATTERN.matches(pin)

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /** Lowercase hex of the derived key. */
    fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        try {
            return toHex(SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded)
        } finally {
            // PBEKeySpec copies the char[]; clearing it is the only handle we have on the copy.
            spec.clearPassword()
        }
    }

    /**
     * Constant-time verification. The comparison is [MessageDigest.isEqual] over the **decoded
     * bytes**, not `String.equals` on the hex — `String.equals` short-circuits on the first
     * differing character and leaks the length of the matching prefix through timing.
     */
    fun verify(pin: String, salt: ByteArray, expectedHex: String): Boolean {
        if (!isValidPin(pin)) return false
        val expected = fromHex(expectedHex) ?: return false
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-1): was `fromHex(hash(pin, salt))!!` — the
        // only `!!` in the whole tree, on the one screen a user cannot escape from (the App Lock
        // PIN gate). `hash()`'s own `toHex` output is always a valid even-length hex string, so
        // this is safe by construction today, but costs nothing to harden here specifically.
        val actual = fromHex(hash(pin, salt)) ?: return false
        return MessageDigest.isEqual(actual, expected)
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** Null for anything that is not an even-length string of hex digits. */
    fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0 || hex.isEmpty()) return null
        return runCatching {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}
