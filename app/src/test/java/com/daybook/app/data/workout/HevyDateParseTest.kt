package com.daybook.app.data.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class HevyDateParseTest {

    @Test fun sampleFileExactFormat_parses() {
        val millis = HevyCsvParser.parseHevyDate("8 Sep 2026, 07:29")
        assertNotNull(millis)
        val zdt = java.time.Instant.ofEpochMilli(millis!!).atZone(ZoneId.systemDefault())
        assertEquals(2026, zdt.year)
        assertEquals(9, zdt.monthValue)
        assertEquals(8, zdt.dayOfMonth)
        assertEquals(7, zdt.hour)
        assertEquals(29, zdt.minute)
    }

    @Test fun iso8601_parses() {
        val millis = HevyCsvParser.parseHevyDate("2026-09-08T07:29:00")
        assertNotNull(millis)
    }

    @Test fun frenchDefaultLocale_stillParsesEnglishMonthAbbreviation() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRENCH)
            val millis = HevyCsvParser.parseHevyDate("8 Sep 2026, 07:29")
            assertNotNull(millis)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun unparseableString_returnsNull() {
        assertNull(HevyCsvParser.parseHevyDate("not a date"))
        assertNull(HevyCsvParser.parseHevyDate(""))
        assertNull(HevyCsvParser.parseHevyDate("32 Foo 2026, 07:29"))
    }
}
