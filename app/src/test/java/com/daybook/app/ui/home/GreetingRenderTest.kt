package com.daybook.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** rec 2 (H1/H2) — the pure greeting-line renderer. */
class GreetingRenderTest {

    private val tod = "Good morning"

    @Test
    fun `WARM with a name uses the named pool`() {
        assertEquals("Hi, Alex", render(0, "Alex"))
        assertEquals("Welcome back, Alex", render(1, "Alex"))
    }

    @Test
    fun `WARM without a name uses the anon pool`() {
        assertEquals("Hi there", render(0, ""))
        assertEquals("Welcome back", render(1, ""))
    }

    @Test
    fun `WARM index 5 surfaces the time-of-day word when enabled`() {
        assertEquals("Good morning", render(5, "", timeWord = true))
        assertEquals("Good morning, Alex", render(5, "Alex", timeWord = true))
    }

    @Test
    fun `WARM index 5 falls back to a neutral phrase when the time word is off`() {
        assertEquals("Hi there", render(5, "", timeWord = false))
        assertEquals("Hi, Alex", render(5, "Alex", timeWord = false))
    }

    @Test
    fun `PLAIN is one fixed line`() {
        assertEquals("Hi, Alex", render(3, "Alex", tone = "PLAIN"))
        assertEquals("Hi there", render(3, "", tone = "PLAIN"))
    }

    @Test
    fun `MINIMAL is the empty string`() {
        assertEquals("", render(2, "Alex", tone = "MINIMAL"))
        assertEquals("", render(2, "", tone = "MINIMAL"))
    }

    @Test
    fun `time word off never surfaces a Good phrase for any index`() {
        for (i in 0..7) {
            for (name in listOf("", "Alex")) {
                val out = render(i, name, timeWord = false)
                assertFalse("index $i name='$name' -> '$out'", out.contains("Good morning"))
            }
        }
    }

    @Test
    fun `defaults match WARM plus time word on`() {
        assertEquals(
            render(5, "Alex", tone = "WARM", timeWord = true),
            renderGreeting(5, "Alex", tod)
        )
    }

    @Test
    fun `WARM with time word on is unchanged from a bare pool lookup`() {
        // A non-index-5 template is identical with the time word on or off.
        assertTrue(render(2, "Alex", timeWord = true) == render(2, "Alex", timeWord = false))
    }

    private fun render(i: Int, name: String, tone: String = "WARM", timeWord: Boolean = true) =
        renderGreeting(i, name, tod, tone, timeWord)
}
