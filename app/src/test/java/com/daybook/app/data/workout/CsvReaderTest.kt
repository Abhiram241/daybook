package com.daybook.app.data.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvReaderTest {

    @Test fun quotedAndUnquotedFieldsOnSameLine() {
        val rows = CsvReader.parse("a,b\n\"x\",5\n")
        assertEquals(listOf(mapOf("a" to "x", "b" to "5")), rows)
    }

    @Test fun commaInsideQuotes() {
        val rows = CsvReader.parse("a,b\n\"1,2\",3\n")
        assertEquals("1,2", rows.first()["a"])
    }

    @Test fun escapedQuoteInsideQuotedField() {
        val rows = CsvReader.parse("a\n\"say \"\"hi\"\"\"\n")
        assertEquals("say \"hi\"", rows.first()["a"])
    }

    @Test fun completelyEmptyFieldBetweenCommas() {
        val rows = CsvReader.parse("a,b,c\n1,,3\n")
        assertEquals("", rows.first()["b"])
    }

    @Test fun crlfLineEndings() {
        val rows = CsvReader.parse("a,b\r\n1,2\r\n3,4\r\n")
        assertEquals(2, rows.size)
        assertEquals("2", rows[0]["b"])
        assertEquals("4", rows[1]["b"])
    }

    @Test fun lfLineEndings() {
        val rows = CsvReader.parse("a,b\n1,2\n3,4\n")
        assertEquals(2, rows.size)
    }

    @Test fun leadingUtf8Bom_doesNotBreakFirstHeaderName() {
        val rows = CsvReader.parse("﻿a,b\n1,2\n")
        assertEquals("1", rows.first()["a"])
    }

    @Test fun trailingNewline_doesNotProduceAPhantomRow() {
        val rows = CsvReader.parse("a,b\n1,2\n")
        assertEquals(1, rows.size)
    }

    @Test fun noTrailingNewline_stillReadsLastRow() {
        val rows = CsvReader.parse("a,b\n1,2")
        assertEquals(1, rows.size)
        assertEquals("2", rows.first()["b"])
    }

    @Test fun emptyInput_isEmptyList() {
        assertTrue(CsvReader.parse("").isEmpty())
    }

    @Test fun columnReorderDoesNotShiftFields() {
        val rows = CsvReader.parse("b,a\n2,1\n")
        assertEquals("1", rows.first()["a"])
        assertEquals("2", rows.first()["b"])
    }

    @Test fun nonAsciiContentSurvives() {
        val rows = CsvReader.parse("title\n\"Morning workout ☀️\"\n")
        assertEquals("Morning workout ☀️", rows.first()["title"])
    }

    @Test fun realSampleFile_firstThreeLinesParseVerbatim() {
        val text = HevyFixtures.sampleText()
        val header = CsvReader.parseHeader(text)
        assertEquals(
            listOf(
                "title", "start_time", "end_time", "description", "exercise_title", "superset_id",
                "exercise_notes", "set_index", "set_type", "weight_kg", "reps", "distance_km",
                "duration_seconds", "rpe"
            ),
            header
        )
        val rows = CsvReader.parse(text)
        assertEquals("Morning workout ☀️", rows[0]["title"])
        assertEquals("8 Sep 2026, 07:29", rows[0]["start_time"])
        assertEquals("Cable Crunch", rows[0]["exercise_title"])
        assertEquals("25", rows[0]["weight_kg"])
        assertEquals("10", rows[0]["reps"])
        assertEquals("", rows[0]["distance_km"])
    }
}
