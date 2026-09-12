package com.daybook.app.data.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HevyCsvValidatorTest {

    private val validHeaders = listOf(
        "title", "start_time", "end_time", "description", "exercise_title", "superset_id",
        "exercise_notes", "set_index", "set_type", "weight_kg", "reps", "distance_km",
        "duration_seconds", "rpe"
    )

    @Test fun realHevyHeader_isOk() {
        assertEquals(ValidationResult.Ok, HevyCsvValidator.validate(validHeaders))
    }

    @Test fun caseAndWhitespaceInsensitive() {
        val messy = validHeaders.map { " ${it.uppercase()} " }
        assertEquals(ValidationResult.Ok, HevyCsvValidator.validate(messy))
    }

    @Test fun missingRequiredColumn_reportsIt() {
        val missingWeight = validHeaders.filterNot { it == "weight_kg" }
        val result = HevyCsvValidator.validate(missingWeight)
        assertTrue(result is ValidationResult.MissingColumns)
        assertEquals(listOf("weight_kg"), (result as ValidationResult.MissingColumns).names)
    }

    @Test fun missingOptionalColumns_stillOk() {
        val withoutOptional = validHeaders.filterNot { it in setOf("description", "superset_id", "exercise_notes", "rpe") }
        assertEquals(ValidationResult.Ok, HevyCsvValidator.validate(withoutOptional))
    }

    @Test fun aDaybookJsonBackup_failsValidation() {
        // The header set a Daybook JSON export's CSV-shaped header never has any of.
        val result = HevyCsvValidator.validate(listOf("meta", "definitions", "days"))
        assertTrue(result is ValidationResult.MissingColumns)
    }

    @Test fun extraUnknownColumns_areIgnored() {
        assertEquals(ValidationResult.Ok, HevyCsvValidator.validate(validHeaders + "some_future_column"))
    }
}
