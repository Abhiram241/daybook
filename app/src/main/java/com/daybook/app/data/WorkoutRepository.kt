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
import com.daybook.app.data.workout.BUILTIN_ID_PREFIX
import com.daybook.app.data.workout.EXERCISE_ADDITIONS
import com.daybook.app.data.workout.EXERCISE_RENAME_MAP
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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

    /** `builtins + customRows`, merged and sorted — the picker sees one flat list (§3.3.3).
     *  §2.3 fix — a builtin's id can also carry a Room "override" row (created the first time it's
     *  edited or archived, keyed by the SAME `builtin:<slug>` id — see [updateExercise] /
     *  [archiveExercise]): an override replaces that builtin's displayed name/muscle/etc, and an
     *  archived override hides it from this list entirely, instead of ever showing both. */
    fun observeExerciseCatalog(): Flow<List<CatalogExercise>> =
        database.exerciseDao().observeAll()
            .onStart { ensureExerciseCatalogRefreshed() }
            .map { rows ->
            val overridesByBuiltinId = rows.filter { exerciseCatalog.byId(it.id) != null }.associateBy { it.id }
            val customRows = rows.filter { exerciseCatalog.byId(it.id) == null && !it.isArchived }
            val builtinCatalog = exerciseCatalog.builtins().mapNotNull { b ->
                when (val override = overridesByBuiltinId[b.id]) {
                    null -> b.toCatalogExercise()
                    else -> if (override.isArchived) null else override.toCatalogExercise()
                }
            }
            (builtinCatalog + customRows.map { it.toCatalogExercise() }).sortedBy { it.name.lowercase() }
        }

    /** Defensive read: an id that resolves to neither a builtin nor a custom row (an old custom
     *  exercise deleted on another device before this one synced) returns null; the UI renders
     *  "Unknown exercise" rather than crashing (§3.3.3). Room is checked FIRST so a builtin
     *  override (§2.3) wins over the static catalog definition it shadows. */
    suspend fun resolveExercise(exerciseId: String): CatalogExercise? =
        database.exerciseDao().getById(exerciseId)?.toCatalogExercise()
            ?: exerciseCatalog.byId(exerciseId)?.toCatalogExercise()

    /** The raw Room row only — never resolves a builtin. Used by the exercise form to preserve a
     *  custom row's own `createdAt`/`isArchived` on edit (§2.8) instead of rebuilding them. */
    suspend fun getExerciseRow(id: String): Exercise? = database.exerciseDao().getById(id)

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

    /** §2.3 fix — `insert` (REPLACE) rather than `@Update`: a genuine custom exercise's row
     *  already exists so this simply overwrites it, but a builtin (`id` starting `"builtin:"`)
     *  has NO existing row, and `@Update` silently affects 0 rows for those — this is also how a
     *  builtin's first edit creates its override row, keyed by the same id (see
     *  [observeExerciseCatalog]). */
    suspend fun updateExercise(exercise: Exercise) = database.exerciseDao().insert(exercise)

    /** §2.3 fix — archiving a builtin with no override row yet forks one in place (same id,
     *  fields copied from the builtin, `isArchived` set as requested) instead of running an
     *  `UPDATE ... WHERE id = :id` that matches zero rows. */
    suspend fun archiveExercise(id: String, archived: Boolean) {
        if (database.exerciseDao().getById(id) != null) {
            database.exerciseDao().archive(id, archived)
            return
        }
        val builtin = exerciseCatalog.byId(id) ?: return
        database.exerciseDao().insert(
            Exercise(
                id = builtin.id, name = builtin.name, primaryMuscle = builtin.primaryMuscle.name,
                equipment = builtin.equipment.name, trackingMode = builtin.trackingMode,
                isArchived = archived, source = "USER", createdAt = System.currentTimeMillis(), notes = null
            )
        )
    }

    private val exerciseCatalogRefreshDone = AtomicBoolean(false)

    /** User request — see `data/workout/ExerciseCatalogRefresh.kt`'s KDoc for the full "why".
     *  Runs once per process, kicked off by [observeExerciseCatalog]'s first collection; safe to
     *  call any number of times since every step below is itself a no-op once already applied. */
    private suspend fun ensureExerciseCatalogRefreshed() {
        if (!exerciseCatalogRefreshDone.compareAndSet(false, true)) return
        runCatching { refreshExerciseCatalog() }
            .onFailure { com.daybook.app.util.recordUnhandledException(it) }
    }

    private suspend fun refreshExerciseCatalog() {
        // 1) Renames — matched against the STATIC catalog's original name (never the possibly
        // already-overridden one), so re-running this after a previous run already applied a
        // rename still finds the same builtin and simply sees "already applied" below.
        for ((oldName, newName) in EXERCISE_RENAME_MAP) {
            val builtin = exerciseCatalog.builtins().firstOrNull { it.name == oldName } ?: continue
            val current = database.exerciseDao().getById(builtin.id)
            if (current?.name == newName) continue
            database.exerciseDao().insert(
                Exercise(
                    id = builtin.id,
                    name = newName,
                    primaryMuscle = current?.primaryMuscle ?: builtin.primaryMuscle.name,
                    equipment = current?.equipment ?: builtin.equipment.name,
                    trackingMode = current?.trackingMode ?: builtin.trackingMode,
                    isArchived = current?.isArchived ?: false,
                    source = "USER",
                    createdAt = current?.createdAt ?: System.currentTimeMillis(),
                    notes = current?.notes
                )
            )
        }

        // 2) Additions — resolved against CURRENT display names (post-rename), so this never
        // double-adds one that a rename above already produced or that a previous run already
        // added.
        val builtinNames = exerciseCatalog.builtins()
            .map { b -> database.exerciseDao().getById(b.id)?.name ?: b.name }
        val existingNames = (builtinNames + database.exerciseDao().getAll().map { it.name }).toMutableSet()
        for (draft in EXERCISE_ADDITIONS) {
            if (draft.name in existingNames) continue
            database.exerciseDao().insert(
                Exercise(
                    id = newId(), name = draft.name,
                    primaryMuscle = draft.muscle.name, equipment = draft.equipment.name,
                    trackingMode = draft.trackingMode, source = "USER",
                    createdAt = System.currentTimeMillis()
                )
            )
            existingNames += draft.name
        }

        // 3) Dedupe — a name can now be shared by a builtin (renamed or not) and a Room row that
        // an earlier Hevy import auto-created for that same title back when it didn't resolve via
        // HEVY_ALIASES (§3.9.4), or by two Room rows created independently. Every reference to the
        // non-canonical row(s) is reassigned to the canonical one FIRST — inside the same
        // transaction as the row's own deletion — so a merge can never orphan real logged history.
        // A builtin override row (its id starts with `builtin:`) is never itself deleted here, only
        // ever chosen as the canonical target.
        val builtinIdByName = exerciseCatalog.builtins()
            .associate { b -> (database.exerciseDao().getById(b.id)?.name ?: b.name) to b.id }
        val groupsByName = database.exerciseDao().getAll().groupBy { it.name.trim().lowercase() }
        for (rows in groupsByName.values) {
            if (rows.size < 2) continue
            val name = rows.first().name
            val canonicalId = builtinIdByName[name] ?: rows.minByOrNull { it.createdAt }?.id ?: continue
            val duplicates = rows.filter { it.id != canonicalId && !it.id.startsWith(BUILTIN_ID_PREFIX) }
            for (dup in duplicates) {
                database.withTransaction {
                    database.workoutDao().reassignExerciseIdInBlocks(dup.id, canonicalId)
                    database.workoutDao().reassignExerciseIdInSets(dup.id, canonicalId)
                    database.routineDao().reassignExerciseIdInRoutineExercises(dup.id, canonicalId)
                    database.exerciseDao().deleteByIds(listOf(dup.id))
                }
            }
        }
    }

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

    /** §2.5 — a cheap re-emit signal (not a value anyone reads) so History's per-row aggregates
     *  can refresh when a set changes without `workout_sessions` itself changing. */
    fun observeSetsRevision(): Flow<Int> = database.workoutDao().observeSetsRevision()

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

    /** Returns false (silent no-op) if the session row is already gone — e.g. a concurrent
     *  discard — so the caller can tell "finished" from "there was nothing left to finish". */
    suspend fun finishSession(id: String): Boolean {
        val session = database.workoutDao().getSession(id) ?: return false
        database.workoutDao().updateSession(session.copy(status = "COMPLETED", endedAt = System.currentTimeMillis()))
        return true
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
     *  session is untouched. Blank/whitespace-only clears the title back to the date fallback.
     *  §1.5 fix — returns false (silent no-op) if the session row is already gone, mirroring
     *  [finishSession]'s contract, instead of no-oping with no signal to the caller. */
    suspend fun renameSession(id: String, title: String): Boolean {
        val session = database.workoutDao().getSession(id) ?: return false
        database.workoutDao().updateSession(session.copy(title = title.trim().takeIf { it.isNotBlank() }))
        return true
    }

    /** Feature addition — lets the live session screen correct "I forgot to end this on time":
     *  re-anchors `startedAt` so the session's elapsed time (measured from now) equals
     *  [newElapsedSeconds], without touching anything else about the session. The ticker keeps
     *  running normally afterwards — this is a one-off correction, not a frozen override.
     *  §1.5 fix — returns false (silent no-op) if the session row is already gone. */
    suspend fun setSessionElapsedSeconds(id: String, newElapsedSeconds: Long): Boolean {
        val session = database.workoutDao().getSession(id) ?: return false
        val newStartedAt = System.currentTimeMillis() - newElapsedSeconds * 1000
        database.workoutDao().updateSession(session.copy(startedAt = newStartedAt))
        return true
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

    /**
     * Feature addition — History card's "muscle groups worked" bar chart. One batched sets query
     * for the whole visible page (§2.4's own pattern — never a per-row query), grouped by session
     * and then by each set's exercise's primary muscle. Volume is Σ(weightKg × reps) over
     * COMPLETED sets, the exact same definition [sessionStats]/[sessionAggregates] use, so a
     * card's bars always sum to the same number as its own headline volume figure. A muscle group
     * with zero logged weight (bodyweight/reps-only work) is dropped rather than shown as a
     * zero-length bar; sorted heaviest-first so the chart reads like a ranking.
     */
    suspend fun muscleGroupVolumeForSessions(
        sessionIds: List<String>
    ): Map<String, List<Pair<MuscleGroup, Float>>> {
        if (sessionIds.isEmpty()) return emptyMap()
        val sets = database.workoutDao().getSetsForSessions(sessionIds).filter { it.completedAt != null }
        val exerciseIds = sets.map { it.exerciseId }.distinct()
        val muscleById = exerciseIds.associateWith { resolveExercise(it)?.primaryMuscle ?: MuscleGroup.OTHER }
        return sets.groupBy { it.sessionId }.mapValues { (_, sessionSets) ->
            sessionSets
                .groupBy { muscleById[it.exerciseId] ?: MuscleGroup.OTHER }
                .mapValues { (_, list) -> list.fold(0f) { acc, s -> acc + (s.weightKg ?: 0f) * (s.reps ?: 0) } }
                .filterValues { it > 0f }
                .toList()
                .sortedByDescending { it.second }
        }
    }

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
        val lastRest = database.workoutDao().lastRestSecondsForExercise(exerciseId)
        val appDefault = appSettingsRepository.getSettings().restTimerDefaultSeconds
        val restSeconds = com.daybook.app.data.workout.resolveRestSeconds(null, lastRest, appDefault)
        val previous = previousSetsForExercise(exerciseId, sessionId)
        // §2.1 fix — the order-index read-then-insert used to run outside any transaction, so N
        // concurrent calls (multi-select "Add (n)") could all read the same max and collide on the
        // same orderIndex. One transaction per call still isn't enough on its own if callers race
        // each other (see WorkoutSessionViewModel.addExercises, which now serialises the whole
        // list into one coroutine instead) — this transaction is the second half of that fix.
        val id = newId()
        database.withTransaction {
            val orderIndex = database.workoutDao().maxExerciseOrderIndex(sessionId) + 1
            database.workoutDao().insertExercise(
                WorkoutExercise(
                    id = id, sessionId = sessionId, exerciseId = exerciseId, orderIndex = orderIndex,
                    restSeconds = restSeconds, createdAt = System.currentTimeMillis()
                )
            )
        }
        // Feature addition — a freshly-added exercise starts with one set row instead of an empty
        // table with nothing to fill in; prefilled from last time's set 1, same as "+ Add Set"
        // prefills every later row.
        addSet(workoutExerciseId = id, sessionId = sessionId, exerciseId = exerciseId, previousBySetNumber = previous)
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
     *  supplies one (the ViewModel reads `previousSetsForExercise` and passes the map). The set
     *  number the prefill is keyed by is looked up AFTER `maxSetNumber` is resolved here, not
     *  derived by the caller from `sets.size` — set numbers aren't renumbered after a mid-session
     *  delete, so `sets.size` can undercount the real next number and pull the wrong PREVIOUS
     *  value (bug fix, see BEAST_MODE_BUG_REPORT.md §1.4). */
    suspend fun addSet(
        workoutExerciseId: String,
        sessionId: String,
        exerciseId: String,
        previousBySetNumber: Map<Int, WorkoutSet> = emptyMap()
    ): String {
        val id = newId()
        // §2.6 fix — same shape as §2.1: read-then-insert inside one transaction so two rapid
        // "+ Add Set" taps can't both read the same maxSetNumber and collide.
        database.withTransaction {
            val setNumber = database.workoutDao().maxSetNumber(workoutExerciseId) + 1
            val prefillFrom = previousBySetNumber[setNumber]
            database.workoutDao().insertSet(
                WorkoutSet(
                    id = id, workoutExerciseId = workoutExerciseId, sessionId = sessionId,
                    exerciseId = exerciseId, setNumber = setNumber,
                    reps = prefillFrom?.reps, weightKg = prefillFrom?.weightKg,
                    durationSeconds = prefillFrom?.durationSeconds, distanceMeters = prefillFrom?.distanceMeters,
                    setType = "NORMAL", completedAt = null
                )
            )
        }
        return id
    }

    // §2.13 fix — column-scoped writes for the set-cell edit path (replacing a whole-row
    // `@Update` built from a stale composition-time `WorkoutSet` snapshot), matching how
    // `toggleSetComplete`/`setCompletedAt` already do a single-column write. A whole-row write
    // could otherwise clobber a `completed_at` toggle that lands between the cell reading its
    // snapshot and the commit.
    suspend fun setSetWeight(id: String, weightKg: Float?) = database.workoutDao().setWeightKg(id, weightKg)
    suspend fun setSetReps(id: String, reps: Int?) = database.workoutDao().setReps(id, reps)
    suspend fun setSetDuration(id: String, durationSeconds: Int?) = database.workoutDao().setDurationSeconds(id, durationSeconds)
    suspend fun setSetDistance(id: String, distanceMeters: Float?) = database.workoutDao().setDistanceMeters(id, distanceMeters)

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

    /** Add-Exercise picker's "Recent" section (§2.9 — wires up the previously-dead
     *  `WorkoutDao.recentExerciseIds`) — the exercises most recently used in any session, resolved
     *  and kept in that recency order. */
    suspend fun recentExercises(): List<CatalogExercise> =
        database.workoutDao().recentExerciseIds().mapNotNull { resolveExercise(it) }

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
        val newRoutineId = newId()
        val now = System.currentTimeMillis()
        val finalName = uniqueRoutineName(original.name, existingRoutineNames())
        val orderIndex = (database.routineDao().maxOrderIndex() ?: -1) + 1
        database.withTransaction {
            database.routineDao().upsertRoutine(
                WorkoutRoutine(
                    id = newRoutineId, name = finalName, notes = original.notes, orderIndex = orderIndex,
                    isArchived = false, source = "USER", createdAt = now, updatedAt = now
                )
            )
            if (originalExercises.isNotEmpty()) {
                database.routineDao().upsertRoutineExercises(
                    originalExercises.map { it.copy(id = newId(), routineId = newRoutineId, createdAt = now) }
                )
            }
        }
        return newRoutineId
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
