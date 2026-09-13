package com.daybook.app.data.workout

import android.util.Log
import androidx.room.withTransaction
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.Exercise
import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.sync.CloudSyncRepository
import com.daybook.app.data.sync.HydrateResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** §3.9.9 — the pure summary inputs. */
data class HevyImportResult(
    val sessions: Int,
    val sets: Int,
    val newExercises: Int,
    val skippedDuplicates: Int,
    val skippedRows: Int,
    /** The exact names behind [newExercises] — not in the catalog under any known name or alias,
     *  so a new custom `Exercise` row was auto-created for each. Surfaced to the user as a
     *  warning by [summarise] rather than silently folded into a bare count. */
    val newExerciseNames: List<String> = emptyList()
)

sealed interface HevyImportOutcome {
    data class Success(val message: String, val isNeutral: Boolean = false) : HevyImportOutcome
    data class Failure(val message: String) : HevyImportOutcome
}

private const val MAX_IMPORT_BYTES = 10 * 1024 * 1024
private const val TAG = "HevyImporter"

/**
 * A8 (§3.9.10) — the only impure piece of the Hevy import: the dedupe probe, the hydration
 * guard, one transaction, and the summary string. Called from `WorkoutRepository`.
 */
@Singleton
class HevyImporter @Inject constructor(
    private val database: AppDatabase,
    private val exerciseCatalog: ExerciseCatalog,
    private val cloudSyncRepository: CloudSyncRepository
) {
    /** [csvBytes] pre-read so V1's size gate never has to fully decode an oversized file. */
    suspend fun import(csvText: String, csvBytes: Int): HevyImportOutcome {
        // V1 — size.
        if (csvBytes > MAX_IMPORT_BYTES) {
            return HevyImportOutcome.Failure("Couldn't import: that file is too large (the limit is 10 MB).")
        }
        // V2 — non-empty.
        val trimmed = csvText.trim()
        if (trimmed.isEmpty() || trimmed.lines().count { it.isNotBlank() } < 2) {
            return HevyImportOutcome.Failure("Couldn't import: that file is empty.")
        }
        // V3 — parses as CSV.
        val header = runCatching { CsvReader.parseHeader(csvText) }.getOrNull()
        if (header.isNullOrEmpty()) {
            return HevyImportOutcome.Failure("Couldn't import: that file isn't a CSV. Export your data from Hevy as CSV and try again.")
        }
        // V4 — is a Hevy export.
        when (val v = HevyCsvValidator.validate(header)) {
            is ValidationResult.MissingColumns ->
                return HevyImportOutcome.Failure("Couldn't import: that doesn't look like a Hevy export. It's missing: ${v.names.joinToString(", ")}.")
            ValidationResult.Ok -> Unit
        }

        val rows = runCatching { CsvReader.parse(csvText) }.getOrElse {
            return HevyImportOutcome.Failure("Couldn't import: that file isn't a CSV. Export your data from Hevy as CSV and try again.")
        }
        val parsed = HevyCsvParser.parse(rows)
        // V5 — has >= 1 usable row.
        if (parsed.sessions.isEmpty() && rows.isNotEmpty() && parsed.skippedRows == rows.size) {
            return HevyImportOutcome.Failure("Couldn't import: none of the rows in that file could be read.")
        }
        if (rows.isEmpty()) {
            return HevyImportOutcome.Failure("Couldn't import: none of the rows in that file could be read.")
        }

        return try {
            doImport(parsed)
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "Hevy import failed", e)
            recordException(e)
            HevyImportOutcome.Failure("Couldn't save the imported workouts — try again, or restart the app if it keeps happening. Nothing was imported.")
        } catch (e: Exception) {
            Log.e(TAG, "Hevy import failed", e)
            recordException(e)
            HevyImportOutcome.Failure("Couldn't import: something went wrong and nothing was changed.")
        }
    }

    private fun recordException(t: Throwable) = com.daybook.app.util.recordUnhandledException(t)

    private suspend fun doImport(parsed: ParseResult): HevyImportOutcome {
        // §3 fix — both branches of this used to return the identical string (dead/copy-paste
        // branch); collapsed to one.
        if (parsed.sessions.isEmpty()) {
            return HevyImportOutcome.Failure("Couldn't import: no workouts were found in that file.")
        }

        // §3.9.7 — hydrate every touched month first; abort with nothing written if any can't be
        // reached. Signed-out users have nothing to hydrate (NoAccount) and proceed purely local.
        val ymd = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val months = parsed.sessions.map {
            Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString().substring(0, 7)
        }.toSortedSet()
        if (months.isNotEmpty()) {
            val hydrate = cloudSyncRepository.hydrateRange(months.first(), months.last())
            try {
                if (hydrate is HydrateResult.Offline) {
                    val monthLabel = runCatching {
                        java.time.YearMonth.parse(hydrate.month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                    }.getOrDefault(hydrate.month)
                    return HevyImportOutcome.Failure("Couldn't fetch $monthLabel from your account. Check your connection and try again — nothing was imported.")
                }
            } finally {
                if (hydrate !is HydrateResult.NoAccount) cloudSyncRepository.endRangeExport()
            }
        }

        // Dedupe probe — exact (startedAt, endedAt), against every existing session regardless
        // of source (§3.9.5). Grouped BEFORE dedupe, per session, not per row. One range query
        // covering every session in the file, then the pure partition below (HevyDedupeTest).
        val minStart = parsed.sessions.minOf { it.startedAt }
        val maxStart = parsed.sessions.maxOf { it.startedAt }
        val existing = database.workoutDao().sessionsStartingBetween(minStart, maxStart)
            .mapTo(HashSet()) { it.startedAt to it.endedAt }
        val (toImport, skippedDuplicates) = partitionDuplicates(parsed.sessions, existing)

        if (toImport.isEmpty()) {
            return if (skippedDuplicates > 0) {
                HevyImportOutcome.Success(
                    "Nothing new to import — all $skippedDuplicates ${if (skippedDuplicates == 1) "workout" else "workouts"} in that file ${if (skippedDuplicates == 1) "is" else "are"} already in Daybook.",
                    isNeutral = true
                )
            } else {
                HevyImportOutcome.Failure("Couldn't import: no workouts were found in that file.")
            }
        }

        // Resolve exercise names -> ids ONCE per distinct name across the whole file, so the
        // same custom exercise isn't created twice within one import (§3.9.4).
        val existingCustoms = database.exerciseDao().getAll()
        val builtins = exerciseCatalog.builtins()
        val candidates = builtins.map { MatchCandidate(it.id, it.name, it.equipment.name) } +
            existingCustoms.map { MatchCandidate(it.id, it.name, it.equipment) }
        val distinctNames = toImport.flatMap { s -> s.exercises.map { it.exerciseTitle } }.distinct()
        val nameToId = HashMap<String, String>()
        val newExercisesToInsert = ArrayList<Exercise>()
        for (name in distinctNames) {
            when (val result = HevyExerciseMatcher.resolve(name, candidates)) {
                is HevyMatchResult.Existing -> nameToId[name] = result.exerciseId
                is HevyMatchResult.CreateCustom -> {
                    val allSetsForName = toImport.flatMap { s -> s.exercises.filter { it.exerciseTitle == name }.flatMap { it.sets } }
                    val id = UUID.randomUUID().toString()
                    nameToId[name] = id
                    // The parenthetical hint ("(Cable)", "(Barbell)") resolves equipment directly
                    // for most known-custom names, but not all of them (no parenthetical at all —
                    // "Cycling", "Single Arm Cable Row" — or a hint that isn't itself an Equipment
                    // name — "Smith Machine"). Fall back to HEVY_KNOWN_CUSTOMS' own equipment for
                    // exactly the names it covers, only when the hint-based parse didn't resolve.
                    val equipmentFromHint = result.equipmentHint?.let { hint ->
                        runCatching { Equipment.valueOf(hint) }.getOrNull()
                    }
                    val knownCustomEquipment = HEVY_KNOWN_CUSTOMS[HevyExerciseMatcher.canonicaliseHevyRawName(name)]?.second
                    newExercisesToInsert += Exercise(
                        id = id, name = name,
                        primaryMuscle = result.muscleHint?.name ?: "OTHER",
                        equipment = (equipmentFromHint ?: knownCustomEquipment)?.name ?: "OTHER",
                        trackingMode = HevyExerciseMatcher.inferTrackingMode(allSetsForName),
                        source = "IMPORTED_HEVY",
                        createdAt = System.currentTimeMillis()
                    )
                }
            }
        }

        var sessionCount = 0
        var setCount = 0
        // §3 fix — a block whose name never resolved to an exercise id used to just vanish via
        // `return@forEachIndexed`, with no count of what was dropped; now folded into the reported
        // skipped-rows total, one per CSV row (set) the unresolved block would have contributed.
        var unresolvedRows = 0
        database.withTransaction {
            if (newExercisesToInsert.isNotEmpty()) database.exerciseDao().insertAll(newExercisesToInsert)
            for (session in toImport) {
                val sessionId = UUID.randomUUID().toString()
                val localDate = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(ymd)
                database.workoutDao().insertSession(
                    WorkoutSession(
                        id = sessionId, localDate = localDate, startedAt = session.startedAt,
                        endedAt = session.endedAt, title = session.title, notes = session.notes,
                        status = "COMPLETED", source = "IMPORTED_HEVY", routineId = null,
                        createdAt = session.startedAt
                    )
                )
                session.exercises.forEachIndexed { index, block ->
                    val blockId = UUID.randomUUID().toString()
                    val exerciseId = nameToId[block.exerciseTitle] ?: run {
                        unresolvedRows += block.sets.size
                        return@forEachIndexed
                    }
                    database.workoutDao().insertExercise(
                        WorkoutExercise(
                            id = blockId, sessionId = sessionId, exerciseId = exerciseId, orderIndex = index,
                            notes = block.exerciseNotes, supersetId = block.supersetId, restSeconds = null,
                            createdAt = session.startedAt
                        )
                    )
                    val setRows = block.sets.map { s ->
                        setCount++
                        WorkoutSet(
                            id = UUID.randomUUID().toString(), workoutExerciseId = blockId, sessionId = sessionId,
                            exerciseId = exerciseId, setNumber = s.setNumber, reps = s.reps, weightKg = s.weightKg,
                            durationSeconds = s.durationSeconds, distanceMeters = s.distanceMeters, rpe = s.rpe,
                            setType = s.setType, notes = null,
                            // §3.9.3 — every row in an export IS a set that happened.
                            completedAt = session.endedAt ?: session.startedAt
                        )
                    }
                    if (setRows.isNotEmpty()) database.workoutDao().insertSets(setRows)
                }
                sessionCount++
            }
        }

        val result = HevyImportResult(
            sessions = sessionCount, sets = setCount, newExercises = newExercisesToInsert.size,
            skippedDuplicates = skippedDuplicates, skippedRows = parsed.skippedRows + unresolvedRows,
            newExerciseNames = newExercisesToInsert.map { it.name }
        )
        return HevyImportOutcome.Success(summarise(result))
    }
}

/**
 * §3.9.5 — pure dedupe partition: exact `(startedAt, endedAt)` match against every existing
 * session's pair (regardless of source — a hand-logged session blocks its Hevy twin). No fuzzy
 * time window: a missed duplicate is recoverable (delete it); a wrongly-skipped session is
 * silently absent forever.
 */
internal fun partitionDuplicates(
    sessions: List<ParsedSession>,
    existingStartEnd: Set<Pair<Long, Long?>>
): Pair<List<ParsedSession>, Int> {
    val toImport = ArrayList<ParsedSession>()
    var duplicates = 0
    for (s in sessions) {
        if ((s.startedAt to s.endedAt) in existingStartEnd) duplicates++ else toImport += s
    }
    return toImport to duplicates
}

/** §3.9.9 — pure, exact-string summary. Composition rules: correct singular/plural; drop any
 *  clause whose count is 0; skipped clauses appended after a single space, duplicates-then-rows. */
fun summarise(result: HevyImportResult): String {
    if (result.sessions == 0) {
        return if (result.skippedDuplicates > 0) {
            "Nothing new to import — all ${result.skippedDuplicates} ${plural(result.skippedDuplicates, "workout", "workouts")} in that file ${if (result.skippedDuplicates == 1) "is" else "are"} already in Daybook."
        } else {
            "Couldn't import: no workouts were found in that file."
        }
    }
    // §3.9.9 — an Oxford-free list of the non-zero count clauses: "A and B." (two clauses) or
    // "A, B and C." (three), never a comma before the final "and".
    val clauses = buildList {
        add("${result.sessions} ${plural(result.sessions, "workout", "workouts")}")
        add("${result.sets} ${plural(result.sets, "set", "sets")}")
        if (result.newExercises > 0) add("${result.newExercises} new ${plural(result.newExercises, "exercise", "exercises")}")
    }
    val joined = when (clauses.size) {
        1 -> clauses[0]
        else -> clauses.dropLast(1).joinToString(", ") + " and " + clauses.last()
    }
    val base = StringBuilder("Imported $joined.")
    if (result.skippedDuplicates > 0) {
        base.append(" Skipped ${result.skippedDuplicates} already in Daybook.")
    }
    if (result.skippedRows > 0) {
        base.append(" Ignored ${result.skippedRows} ${plural(result.skippedRows, "row", "rows")} it couldn't read.")
    }
    if (result.newExerciseNames.isNotEmpty()) {
        val maxNamesShown = 5
        val shown = result.newExerciseNames.take(maxNamesShown).joinToString(", ")
        val extra = result.newExerciseNames.size - maxNamesShown
        val names = if (extra > 0) "$shown and $extra more" else shown
        base.append(
            " ⚠ ${plural(result.newExerciseNames.size, "This wasn't", "These weren't")} in your " +
                "exercise library, so ${plural(result.newExerciseNames.size, "it was", "they were")} " +
                "added as custom: $names."
        )
    }
    return base.toString()
}

private fun plural(count: Int, singular: String, plural: String): String = if (count == 1) singular else plural
