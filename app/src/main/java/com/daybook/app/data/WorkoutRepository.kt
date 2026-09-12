package com.daybook.app.data

import androidx.room.withTransaction
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.local.RoutineSummary
import com.daybook.app.data.local.SessionAggregate
import com.daybook.app.data.model.Exercise
import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutRoutine
import com.daybook.app.data.model.WorkoutRoutineExercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.workout.BuiltinExercise
import com.daybook.app.data.workout.Equipment
import com.daybook.app.data.workout.ExerciseCatalog
import com.daybook.app.data.workout.HevyImportOutcome
import com.daybook.app.data.workout.HevyImporter
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.instantSessionTitle
import com.daybook.app.data.workout.RoutineStartResult
import com.daybook.app.data.workout.buildSessionFromRoutine
import com.daybook.app.data.workout.normaliseExerciseName
import com.daybook.app.data.workout.previousBySetNumber
import com.daybook.app.data.workout.uniqueRoutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The picker's one flat, merged view of `builtins + customRows` (§3.3.3). */
data class CatalogExercise(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val trackingMode: String,
    val imageId: String?,
    val hasStartPeak: Boolean,
    val isCustom: Boolean,
    val source: String,
    val notes: String?
)

/** The routine editor's in-memory row (§3.4). Immutable, primitives only — no entity, no Flow. */
data class RoutineExerciseDraft(
    val id: String,
    val exerciseId: String,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetWeightKg: Float? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Float? = null,
    val restSeconds: Int? = null,
    val notes: String? = null
)

/**
 * A3 (§3.4) — mirrors `HabitRepository`'s shape. Owns sessions, blocks, sets, custom exercises
 * and routines. No sync-queue, no `operation_id` (§2.1) — Room is the only source of truth; the
 * sync layer (A4) reads it the same way every other repository's tables are read.
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val database: AppDatabase,
    private val exerciseCatalog: ExerciseCatalog,
    private val appSettingsRepository: AppSettingsRepository,
    private val hevyImporter: HevyImporter
) {
    /** A8 (§3.9) — the only entry point into the Hevy CSV importer. */
    suspend fun importHevyCsv(csvText: String, csvBytes: Int): HevyImportOutcome =
        hevyImporter.import(csvText, csvBytes)

    private companion object {
        val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private fun newId(): String = UUID.randomUUID().toString()
    private fun todayLocalDate(): String = LocalDate.now().format(YMD)

    // ------------------------------------------------------------------------ exercise catalog

    private fun BuiltinExercise.toCatalogExercise() = CatalogExercise(
        id = id, name = name, primaryMuscle = primaryMuscle, equipment = equipment,
        trackingMode = trackingMode, imageId = imageId, hasStartPeak = hasStartPeak,
        isCustom = false, source = "BUILTIN", notes = null
    )

    private fun Exercise.toCatalogExercise() = CatalogExercise(
        id = id, name = name,
        primaryMuscle = runCatching { MuscleGroup.valueOf(primaryMuscle) }.getOrDefault(MuscleGroup.OTHER),
        equipment = runCatching { Equipment.valueOf(equipment) }.getOrDefault(Equipment.OTHER),
        trackingMode = trackingMode, imageId = null, hasStartPeak = false,
        isCustom = true, source = source, notes = notes
    )

    /** `builtins + customRows`, merged and sorted — the picker sees one flat list (§3.3.3). */
    fun observeExerciseCatalog(): Flow<List<CatalogExercise>> =
        database.exerciseDao().observeCustom().map { customs ->
            (exerciseCatalog.builtins().map { it.toCatalogExercise() } + customs.map { it.toCatalogExercise() })
                .sortedBy { it.name.lowercase() }
        }

    /** Defensive read: an id that resolves to neither a builtin nor a custom row (an old custom
     *  exercise deleted on another device before this one synced) returns null; the UI renders
     *  "Unknown exercise" rather than crashing (§3.3.3). */
    suspend fun resolveExercise(exerciseId: String): CatalogExercise? =
        exerciseCatalog.byId(exerciseId)?.toCatalogExercise()
            ?: database.exerciseDao().getById(exerciseId)?.toCatalogExercise()

    suspend fun createCustomExercise(
        name: String,
        primaryMuscle: MuscleGroup,
        equipment: Equipment,
        trackingMode: String
    ): String? {
        val cleanName = normaliseExerciseName(name) ?: return null
        val id = newId()
        database.exerciseDao().insert(
            Exercise(
                id = id, name = cleanName, primaryMuscle = primaryMuscle.name,
                equipment = equipment.name, trackingMode = trackingMode,
                createdAt = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun updateExercise(exercise: Exercise) = database.exerciseDao().update(exercise)
    suspend fun archiveExercise(id: String, archived: Boolean) = database.exerciseDao().archive(id, archived)

    // ------------------------------------------------------------------------ sessions

    fun observeActiveSession(): Flow<WorkoutSession?> = database.workoutDao().observeActiveSession()
    fun observeRecentSessions(limit: Int): Flow<List<WorkoutSession>> = database.workoutDao().observeRecentSessions(limit)
    fun observeSessionsBetween(startMillis: Long, endMillis: Long): Flow<List<WorkoutSession>> =
        database.workoutDao().observeSessionsBetween(startMillis, endMillis)
    fun observeSession(id: String): Flow<WorkoutSession?> = database.workoutDao().observeSession(id)
    fun observeExercisesForSession(sessionId: String): Flow<List<WorkoutExercise>> =
        database.workoutDao().observeExercisesForSession(sessionId)
    fun observeSetsForSession(sessionId: String): Flow<List<WorkoutSet>> =
        database.workoutDao().observeSetsForSession(sessionId)

    suspend fun startEmptySession(): String {
        val id = newId()
        val now = System.currentTimeMillis()
        database.workoutDao().insertSession(
            WorkoutSession(
                id = id, localDate = todayLocalDate(), startedAt = now, status = "ACTIVE",
                // Item 6 (Workout UI fixes plan, LOCKED) — default time-of-day title for a
                // no-routine "instant" session; stays user-renameable via the existing rename
                // feature. Routine-started sessions already get the routine's own name
                // (buildSessionFromRoutine), so this is specific to the empty-session path.
                title = instantSessionTitle(LocalTime.now().hour),
                source = "MANUAL", routineId = null, createdAt = now
            )
        )
        return id
    }

    suspend fun finishSession(id: String) {
        val session = database.workoutDao().getSession(id) ?: return
        database.workoutDao().updateSession(session.copy(status = "COMPLETED", endedAt = System.currentTimeMillis()))
    }

    /** Deletes a session and everything under it — sets, then blocks, then the session — inside
     *  one transaction (§3.2: no FK, so this ordering is the whole of "cascade"). */
    suspend fun deleteSession(id: String) {
        database.withTransaction {
            database.workoutDao().deleteSetsForSession(id)
            database.workoutDao().deleteExercisesForSession(id)
            database.workoutDao().deleteSession(id)
        }
    }

    /** "Discard Workout" on the live session screen — same operation as [deleteSession], named
     *  for the call site that means it. */
    suspend fun discardSession(id: String) = deleteSession(id)

    /** History overflow "Edit" (open question 4) — rename only; everything else about the
     *  session is untouched. Blank/whitespace-only clears the title back to the date fallback. */
    suspend fun renameSession(id: String, title: String) {
        val session = database.workoutDao().getSession(id) ?: return
        database.workoutDao().updateSession(session.copy(title = title.trim().takeIf { it.isNotBlank() }))
    }

    /** Feature addition — lets the live session screen correct "I forgot to end this on time":
     *  re-anchors `startedAt` so the session's elapsed time (measured from now) equals
     *  [newElapsedSeconds], without touching anything else about the session. The ticker keeps
     *  running normally afterwards — this is a one-off correction, not a frozen override. */
    suspend fun setSessionElapsedSeconds(id: String, newElapsedSeconds: Long) {
        val session = database.workoutDao().getSession(id) ?: return
        val newStartedAt = System.currentTimeMillis() - newElapsedSeconds * 1000
        database.workoutDao().updateSession(session.copy(startedAt = newStartedAt))
    }

    /** Beast Mode home "This week" stat grid — sessions in the given local-date range, for the
     *  workouts-count/volume/streak tiles (§3.3 item 3). */
    suspend fun getSessionsInLocalDateRange(startYmd: String, endYmd: String) =
        database.workoutDao().getSessionsInLocalDateRange(startYmd, endYmd)

    suspend fun getSetsForSessions(sessionIds: List<String>) =
        if (sessionIds.isEmpty()) emptyList() else database.workoutDao().getSetsForSessions(sessionIds)

    suspend fun bestSetForExerciseBefore(exerciseId: String, beforeMillis: Long) =
        database.workoutDao().bestSetForExerciseBefore(exerciseId, beforeMillis)

    // ------------------------------------------------------------------------ History tab (§2)

    /** §2.4 — one `GROUP BY` query for the whole visible page of session ids, not per-row. */
    suspend fun sessionAggregates(sessionIds: List<String>): Map<String, SessionAggregate> =
        if (sessionIds.isEmpty()) emptyMap()
        else database.workoutDao().sessionAggregates(sessionIds).associateBy { it.sessionId }

    /** §2.1/§5 — each session's first exercise id, one query for the whole visible page. */
    suspend fun firstExerciseIdsForSessions(sessionIds: List<String>): Map<String, String> =
        if (sessionIds.isEmpty()) emptyMap()
        else database.workoutDao().firstExercisePerSession(sessionIds).associate { it.sessionId to it.exerciseId }

    /** History's exercise filter chip (open question 3) — every distinct exercise ever logged,
     *  resolved and sorted by name (the same resolve-then-sort shape [frequentlyLoggedExercises]
     *  already uses). */
    suspend fun loggedExercisesForFilter(): List<CatalogExercise> =
        database.workoutDao().allLoggedExerciseIds()
            .mapNotNull { resolveExercise(it) }
            .sortedBy { it.name.lowercase() }

    /** History's exercise filter (open question 3) — every session id that logged [exerciseId]. */
    suspend fun sessionIdsForExercise(exerciseId: String): Set<String> =
        database.workoutDao().sessionIdsForExercise(exerciseId).toSet()

    /**
     * History overflow "Duplicate as routine" (open question 4) — reuses [createRoutine] verbatim
     * (the same path the routine editor's Save button calls): the past session's blocks become
     * plain exercise-only draft rows (no targets carried over), in their original order.
     */
    suspend fun createRoutineFromSession(sessionId: String, routineName: String): String {
        val blocks = database.workoutDao().getExercisesForSession(sessionId)
        val drafts = blocks.sortedBy { it.orderIndex }.map { RoutineExerciseDraft(id = newId(), exerciseId = it.exerciseId) }
        return createRoutine(routineName, notes = null, exercises = drafts)
    }

    // ------------------------------------------------------------------------ blocks

    suspend fun addExerciseToSession(sessionId: String, exerciseId: String): String {
        val id = newId()
        val orderIndex = database.workoutDao().maxExerciseOrderIndex(sessionId) + 1
        val lastRest = database.workoutDao().lastRestSecondsForExercise(exerciseId)
        val appDefault = appSettingsRepository.getSettings().restTimerDefaultSeconds
        val restSeconds = com.daybook.app.data.workout.resolveRestSeconds(null, lastRest, appDefault)
        database.workoutDao().insertExercise(
            WorkoutExercise(
                id = id, sessionId = sessionId, exerciseId = exerciseId, orderIndex = orderIndex,
                restSeconds = restSeconds, createdAt = System.currentTimeMillis()
            )
        )
        // Feature addition — a freshly-added exercise starts with one set row instead of an empty
        // table with nothing to fill in; prefilled from last time's set 1, same as "+ Add Set"
        // prefills every later row.
        val prefill = previousSetsForExercise(exerciseId, sessionId)[1]
        addSet(workoutExerciseId = id, sessionId = sessionId, exerciseId = exerciseId, prefillFrom = prefill)
        return id
    }

    suspend fun removeExerciseFromSession(blockId: String) {
        database.withTransaction {
            database.workoutDao().getExercise(blockId)?.let { block ->
                // No FK — delete this block's own sets first via a session-scoped fetch filtered
                // in Kotlin (a single block's sets are a small subset of the session's).
                database.workoutDao().getSetsForSession(block.sessionId)
                    .filter { it.workoutExerciseId == blockId }
                    .forEach { database.workoutDao().deleteSet(it.id) }
            }
            database.workoutDao().deleteExercise(blockId)
        }
    }

    suspend fun reorderExercises(orderedBlockIds: List<String>) {
        database.withTransaction {
            orderedBlockIds.forEachIndexed { index, blockId ->
                database.workoutDao().getExercise(blockId)?.let {
                    database.workoutDao().updateExercise(it.copy(orderIndex = index))
                }
            }
        }
    }

    suspend fun setExerciseNotes(blockId: String, notes: String?) =
        database.workoutDao().updateExerciseNotes(blockId, notes?.takeIf { it.isNotBlank() })

    suspend fun setExerciseRest(blockId: String, restSeconds: Int?) =
        database.workoutDao().updateExerciseRest(blockId, restSeconds)

    // ------------------------------------------------------------------------ sets

    /** A blank new row, pre-filled from the previous set of the same set-number when the caller
     *  supplies one (the ViewModel reads `previousSetsForExercise` and passes the match). */
    suspend fun addSet(
        workoutExerciseId: String,
        sessionId: String,
        exerciseId: String,
        prefillFrom: WorkoutSet? = null
    ): String {
        val id = newId()
        val setNumber = database.workoutDao().maxSetNumber(workoutExerciseId) + 1
        database.workoutDao().insertSet(
            WorkoutSet(
                id = id, workoutExerciseId = workoutExerciseId, sessionId = sessionId,
                exerciseId = exerciseId, setNumber = setNumber,
                reps = prefillFrom?.reps, weightKg = prefillFrom?.weightKg,
                durationSeconds = prefillFrom?.durationSeconds, distanceMeters = prefillFrom?.distanceMeters,
                setType = "NORMAL", completedAt = null
            )
        )
        return id
    }

    suspend fun updateSet(set: WorkoutSet) = database.workoutDao().updateSet(set)
    suspend fun deleteSet(id: String) = database.workoutDao().deleteSet(id)

    /** P1: a single, immediate Room write — the live session never accumulates sets in memory. */
    suspend fun toggleSetComplete(id: String) {
        val set = database.workoutDao().getSet(id) ?: return
        database.workoutDao().setCompletedAt(id, if (set.completedAt == null) System.currentTimeMillis() else null)
    }

    // ------------------------------------------------------------------ derived-value reads (§3.4)

    suspend fun previousSetsForExercise(exerciseId: String, excludeSessionId: String): Map<Int, WorkoutSet> =
        previousBySetNumber(database.workoutDao().previousSetsForExercise(exerciseId, excludeSessionId))

    suspend fun bestSetForExercise(exerciseId: String, excludeSessionId: String): WorkoutSet? =
        database.workoutDao().bestSetForExercise(exerciseId, excludeSessionId)

    suspend fun lastRestSecondsForExercise(exerciseId: String): Int? =
        database.workoutDao().lastRestSecondsForExercise(exerciseId)

    /** ExerciseHistorySheet (§3.7.3) — a list, not a chart (§C.3.9 defers the chart to Round C). */
    suspend fun exerciseHistory(exerciseId: String): List<WorkoutSet> =
        database.workoutDao().setsHistoryForExercise(exerciseId)

    /**
     * Feature addition — Add-Exercise's default "Frequently logged" view: the top [limit]
     * exercises by total completed-set count, all-time, ties broken alphabetically. The SQL side
     * is one aggregate query (`WorkoutDao.mostLoggedExercises`, over-fetched by 3x for headroom);
     * resolving each id to its real `CatalogExercise` (for the name-based tie-break and for
     * display) is a handful of indexed/in-memory reads, not a per-row DB round trip against the
     * whole history.
     */
    suspend fun frequentlyLoggedExercises(limit: Int = 10): List<CatalogExercise> {
        val counts = database.workoutDao().mostLoggedExercises(limit * 3)
        return counts
            .mapNotNull { c -> resolveExercise(c.exerciseId)?.let { it to c.setCount } }
            .sortedWith(compareByDescending<Pair<CatalogExercise, Int>> { it.second }.thenBy { it.first.name.lowercase() })
            .take(limit)
            .map { it.first }
    }

    // ------------------------------------------------------------------------ routines

    fun observeRoutineSummaries(): Flow<List<RoutineSummary>> = database.routineDao().observeRoutineSummaries()
    fun observeRoutineExercises(routineId: String): Flow<List<WorkoutRoutineExercise>> =
        database.routineDao().observeRoutineExercises(routineId)
    fun observeRoutine(routineId: String): Flow<WorkoutRoutine?> = database.routineDao().observeRoutine(routineId)

    private suspend fun existingRoutineNames(excludingId: String? = null): List<String> =
        database.routineDao().getAllRoutines().filter { it.id != excludingId }.map { it.name }

    suspend fun createRoutine(name: String, notes: String?, exercises: List<RoutineExerciseDraft>): String {
        val id = newId()
        val now = System.currentTimeMillis()
        val finalName = uniqueRoutineName(name, existingRoutineNames())
        val orderIndex = (database.routineDao().maxOrderIndex() ?: -1) + 1
        database.withTransaction {
            database.routineDao().upsertRoutine(
                WorkoutRoutine(
                    id = id, name = finalName, notes = notes?.takeIf { it.isNotBlank() },
                    orderIndex = orderIndex, createdAt = now, updatedAt = now
                )
            )
            if (exercises.isNotEmpty()) {
                database.routineDao().upsertRoutineExercises(exercises.mapIndexed { i, d -> d.toEntity(id, i, now) })
            }
        }
        return id
    }

    suspend fun updateRoutine(routineId: String, name: String, notes: String?, exercises: List<RoutineExerciseDraft>) {
        val existing = database.routineDao().getRoutine(routineId) ?: return
        val now = System.currentTimeMillis()
        val finalName = uniqueRoutineName(name, existingRoutineNames(excludingId = routineId))
        database.withTransaction {
            database.routineDao().updateRoutine(
                existing.copy(name = finalName, notes = notes?.takeIf { it.isNotBlank() }, updatedAt = now)
            )
            // delete-all-then-reinsert (§3.4) — a routine is a handful of rows and orderIndex
            // changes on every reorder; a diff here is more code with more ways to leave the
            // ordering inconsistent.
            database.routineDao().deleteRoutineExercises(routineId)
            if (exercises.isNotEmpty()) {
                database.routineDao().upsertRoutineExercises(exercises.mapIndexed { i, d -> d.toEntity(routineId, i, now) })
            }
        }
    }

    suspend fun duplicateRoutine(routineId: String): String {
        val original = database.routineDao().getRoutine(routineId) ?: error("Routine $routineId not found")
        val originalExercises = database.routineDao().getRoutineExercises(routineId)
        val newId = newId()
        val now = System.currentTimeMillis()
        val finalName = uniqueRoutineName(original.name, existingRoutineNames())
        val orderIndex = (database.routineDao().maxOrderIndex() ?: -1) + 1
        database.withTransaction {
            database.routineDao().upsertRoutine(
                WorkoutRoutine(
                    id = newId, name = finalName, notes = original.notes, orderIndex = orderIndex,
                    isArchived = false, source = "USER", createdAt = now, updatedAt = now
                )
            )
            if (originalExercises.isNotEmpty()) {
                database.routineDao().upsertRoutineExercises(
                    originalExercises.map { it.copy(id = newId(), routineId = newId, createdAt = now) }
                )
            }
        }
        return newId
    }

    suspend fun deleteRoutine(routineId: String) {
        database.withTransaction {
            database.routineDao().deleteRoutineExercises(routineId)
            database.routineDao().deleteRoutine(routineId)
        }
    }

    /** §3.4 — the whole of "pre-populate", built by the pure [buildSessionFromRoutine] and
     *  persisted in one transaction. */
    suspend fun startSessionFromRoutine(routineId: String): String {
        val routine = database.routineDao().getRoutine(routineId) ?: error("Routine $routineId not found")
        val routineExercises = database.routineDao().getRoutineExercises(routineId)
        val lastRestByExercise = routineExercises.associate {
            it.exerciseId to database.workoutDao().lastRestSecondsForExercise(it.exerciseId)
        }
        val appDefault = appSettingsRepository.getSettings().restTimerDefaultSeconds
        val sessionId = newId()
        val blockIds = routineExercises.map { newId() }
        val result: RoutineStartResult = buildSessionFromRoutine(
            routine = routine,
            routineExercises = routineExercises,
            lastRestSecondsByExercise = lastRestByExercise,
            appDefaultRestSeconds = appDefault,
            now = System.currentTimeMillis(),
            today = todayLocalDate(),
            sessionId = sessionId,
            blockIdAt = { index -> blockIds[index] },
            setIdAt = { _, _ -> newId() }
        )
        database.withTransaction {
            database.workoutDao().insertSession(result.session)
            if (result.exercises.isNotEmpty()) database.workoutDao().insertExercises(result.exercises)
            if (result.sets.isNotEmpty()) database.workoutDao().insertSets(result.sets)
        }
        return sessionId
    }

    private fun RoutineExerciseDraft.toEntity(routineId: String, orderIndex: Int, now: Long) =
        WorkoutRoutineExercise(
            id = id, routineId = routineId, exerciseId = exerciseId, orderIndex = orderIndex,
            targetSets = targetSets, targetReps = targetReps, targetWeightKg = targetWeightKg,
            targetDurationSeconds = targetDurationSeconds, targetDistanceMeters = targetDistanceMeters,
            restSeconds = restSeconds, notes = notes?.takeIf { it.isNotBlank() }, createdAt = now
        )
}
