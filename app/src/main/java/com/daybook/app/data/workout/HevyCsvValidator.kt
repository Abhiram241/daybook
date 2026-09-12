package com.daybook.app.data.workout

/** A8 (§3.9.9, V4) — the required Hevy header set, compared case-insensitively after trimming. */
private val REQUIRED_HEADERS = listOf(
    "title", "start_time", "end_time", "exercise_title", "set_index", "set_type",
    "weight_kg", "reps", "distance_km", "duration_seconds"
)

sealed interface ValidationResult {
    data object Ok : ValidationResult
    data class MissingColumns(val names: List<String>) : ValidationResult
}

/**
 * A8 (§3.9.9, V4) — "is this a Hevy export". Deliberately does NOT require `description`,
 * `superset_id`, `exercise_notes` or `rpe`: those are mapped when present, but rejecting an
 * otherwise-importable file over a missing `rpe` column would be a self-inflicted failure.
 */
object HevyCsvValidator {
    fun validate(headers: List<String>): ValidationResult {
        val normalised = headers.map { it.trim().lowercase() }.toSet()
        val missing = REQUIRED_HEADERS.filter { it !in normalised }
        return if (missing.isEmpty()) ValidationResult.Ok else ValidationResult.MissingColumns(missing)
    }
}
