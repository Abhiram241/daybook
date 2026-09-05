package com.daybook.app.data.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.5.1 §K. Pure JVM — [PinHasher] has no Android dependency, which is the point of it. */
class PinHasherTest {

    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun `hash is deterministic for the same pin and salt`() {
        assertEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", salt))
    }

    @Test
    fun `the salt is actually mixed in`() {
        val otherSalt = ByteArray(16) { (it + 1).toByte() }
        assertNotEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", otherSalt))
    }

    @Test
    fun `different pins hash differently under the same salt`() {
        assertNotEquals(PinHasher.hash("1234", salt), PinHasher.hash("0000", salt))
    }

    @Test
    fun `verify accepts the correct pin and rejects everything else`() {
        val stored = PinHasher.hash("1234", salt)
        assertTrue(PinHasher.verify("1234", salt, stored))
        assertFalse("wrong pin", PinHasher.verify("0000", salt, stored))
        assertFalse("five digits", PinHasher.verify("12345", salt, stored))
        assertFalse("three digits", PinHasher.verify("123", salt, stored))
        assertFalse("empty", PinHasher.verify("", salt, stored))
        assertFalse("non-numeric", PinHasher.verify("12a4", salt, stored))
    }

    @Test
    fun `verify rejects a malformed stored hash instead of throwing`() {
        assertFalse(PinHasher.verify("1234", salt, "not-hex"))
        assertFalse(PinHasher.verify("1234", salt, ""))
    }

    @Test
    fun `the stored hash never contains the plaintext pin`() {
        // A hex digest can contain "1234" by chance, so pin-shaped substrings are checked against
        // several pins: a leak would show up for every one of them, chance for at most a few.
        val leaks = listOf("1234", "0000", "9876", "5150", "8080")
            .count { pin -> PinHasher.hash(pin, salt).contains(pin) }
        assertTrue("stored hashes look like they embed the plaintext PIN", leaks <= 1)
    }

    @Test
    fun `isValidPin accepts exactly four digits`() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertTrue(PinHasher.isValidPin("9999"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12345"))
        assertFalse(PinHasher.isValidPin(""))
        assertFalse(PinHasher.isValidPin("12 4"))
        assertFalse(PinHasher.isValidPin("１２３４")) // full-width digits
    }

    @Test
    fun `newSalt is 16 random bytes`() {
        val a = PinHasher.newSalt()
        val b = PinHasher.newSalt()
        assertEquals(16, a.size)
        assertNotEquals(PinHasher.toHex(a), PinHasher.toHex(b))
    }

    @Test
    fun `hex round-trips and rejects malformed input`() {
        val bytes = PinHasher.newSalt()
        assertEquals(PinHasher.toHex(bytes), PinHasher.toHex(PinHasher.fromHex(PinHasher.toHex(bytes))!!))
        assertNull(PinHasher.fromHex("abc"))   // odd length
        assertNull(PinHasher.fromHex(""))
        assertNull(PinHasher.fromHex("zz"))
    }

    @Test
    fun `a single hash completes well under two seconds`() {
        // Catches an ITERATIONS constant typo'd by an order of magnitude, which would make the
        // lock screen feel broken on a real device.
        val start = System.nanoTime()
        PinHasher.hash("1234", salt)
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue("PBKDF2 took ${millis}ms — check ITERATIONS", millis < 2_000)
    }
}
