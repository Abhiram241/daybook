package com.daybook.app.data.workout

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ParsedSet(
    val setNumber: Int,          // 1-based (Hevy's 0-based set_index + 1)
    val reps: Int? = null,
    val weightKg: Float? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Float? = null,
    val rpe: Int? = null,
    val setType: String = "NORMAL"
)

data class ParsedExerciseBlock(
    val exerciseTitle: String,   // Hevy's raw name — the matcher's input
    val supersetId: String? = null,
    val exerciseNotes: String? = null,
    val sets: List<ParsedSet>
)

data class ParsedSession(
    val title: String? = null,
    val startedAt: Long,         // epoch millis
    val endedAt: Long? = null,
    val notes: String? = null,   // Hevy's `description`
    val exercises: List<ParsedExerciseBlock>
)

data class ParseResult(val sessions: List<ParsedSession>, val skippedRows: Int)

/**
 * A8 (§3.9.3/§3.9.0) — rows -> sessions. Pure, Room-free.
 *
 * Grouping: one row per set; a session is the rows sharing `title` + `start_time` + `end_time`;
 * an exercise block is a RUN of consecutive rows within it sharing `exercise_title` (matching
 * Hevy's own export order — the same exercise appearing again non-contiguously becomes a
 * separate block, not merged back into the first).
 */
object HevyCsvParser {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    // §3.9.3 — Locale.ENGLISH explicitly: Hevy writes English month abbreviations regardless of
    // device locale.
    private val HEVY_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

    /** Tries ISO-8601 first (Hevy's format has varied across app versions), then the pattern
     *  above. A string that parses as neither returns null. */
    internal fun parseHevyDate(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val iso = runCatching { LocalDateTime.parse(trimmed, ISO) }.getOrNull()
        if (iso != null) return iso.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val hevy = runCatching { LocalDateTime.parse(trimmed, HEVY_PATTERN) }.getOrNull()
        if (hevy != null) return hevy.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return null
    }

    internal fun mapSetType(raw: String): String = when (raw.trim().lowercase()) {
        "normal" -> "NORMAL"
        "warmup" -> "WARMUP"
        "dropset" -> "DROPSET"
        "failure" -> "FAILURE"
        else -> "NORMAL"
    }

    private fun blankToNull(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }
    private fun parseIntOrNull(s: String?): Int? = blankToNull(s)?.toDoubleOrNull()?.toInt()
    private fun parseFloatOrNull(s: String?): Float? = blankToNull(s)?.toFloatOrNull()

    fun parse(rows: List<Map<String, String>>): ParseResult {
        data class SessionKey(val title: String?, val start: String, val end: String)

        val sessions = ArrayList<ParsedSession>()
        var skipped = 0

        var currentKey: SessionKey? = null
        var currentRows = ArrayList<Map<String, String>>()

        fun flushSession(rowsForSession: List<Map<String, String>>) {
            if (rowsForSession.isEmpty()) return
            val first = rowsForSession.first()
            val startedAt = parseHevyDate(first["start_time"].orEmpty())
            if (startedAt == null) {
                skipped += rowsForSession.size
                return
            }
            val endedAt = parseHevyDate(first["end_time"].orEmpty())

            // Group into blocks by a contiguous run of the same exercise_title.
            val blocks = ArrayList<ParsedExerciseBlock>()
            var blockTitle: String? = null
            var blockSuperset: String? = null
            var blockNotes: String? = null
            var blockSets = ArrayList<ParsedSet>()

            fun flushBlock() {
                if (blockTitle != null) {
                    blocks += ParsedExerciseBlock(blockTitle!!, blockSuperset, blockNotes, blockSets)
                }
                blockSets = ArrayList()
            }

            for (row in rowsForSession) {
                // §3 fix — this used to `continue` without counting the row, so the import
                // summary under-reported what it ignored.
                val exerciseTitle = blankToNull(row["exercise_title"])
                if (exerciseTitle == null) {
                    skipped++
                    continue
                }
                if (exerciseTitle != blockTitle) {
                    flushBlock()
                    blockTitle = exerciseTitle
                    blockSuperset = blankToNull(row["superset_id"])
                    blockNotes = blankToNull(row["exercise_notes"])
                }
                val setIndex = parseIntOrNull(row["set_index"]) ?: blockSets.size
                blockSets += ParsedSet(
                    setNumber = setIndex + 1,
                    reps = parseIntOrNull(row["reps"]),
                    weightKg = parseFloatOrNull(row["weight_kg"]),
                    durationSeconds = parseIntOrNull(row["duration_seconds"]),
                    distanceMeters = parseFloatOrNull(row["distance_km"])?.let { it * 1000f },
                    rpe = parseIntOrNull(row["rpe"]),
                    setType = mapSetType(row["set_type"].orEmpty())
                )
            }
            flushBlock()

            if (blocks.isNotEmpty()) {
                sessions += ParsedSession(
                    title = blankToNull(first["title"]),
                    startedAt = startedAt,
                    endedAt = endedAt,
                    notes = blankToNull(first["description"]),
                    exercises = blocks
                )
            }
        }

        for (row in rows) {
            val key = SessionKey(blankToNull(row["title"]), row["start_time"].orEmpty(), row["end_time"].orEmpty())
            if (currentKey == null) {
                currentKey = key
            } else if (key != currentKey) {
                flushSession(currentRows)
                currentRows = ArrayList()
                currentKey = key
            }
            currentRows.add(row)
        }
        flushSession(currentRows)

        return ParseResult(sessions, skipped)
    }
}
