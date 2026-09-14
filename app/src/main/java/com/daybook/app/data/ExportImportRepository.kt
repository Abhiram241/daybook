package com.daybook.app.data

import android.util.Log
import androidx.room.withTransaction
import com.daybook.app.BuildConfig
import com.daybook.app.data.backup.BackupMeta
import com.daybook.app.data.backup.BackupStatus
import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.DaybookBackup
import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.HabitDef
import com.daybook.app.data.backup.HabitLog
import com.daybook.app.data.backup.IntakeLog
import com.daybook.app.data.backup.IntakeReminderDef
import com.daybook.app.data.backup.ExerciseDef
import com.daybook.app.data.backup.RoutineDef
import com.daybook.app.data.backup.RoutineExerciseDef
import com.daybook.app.data.backup.WorkoutLog
import com.daybook.app.data.backup.WorkoutExerciseLog
import com.daybook.app.data.backup.WorkoutSetLog
import com.daybook.app.data.backup.HealthDayLog
import com.daybook.app.data.backup.HealthSessionLog
import com.daybook.app.data.backup.HealthWeightReadingLog
import com.daybook.app.data.backup.DailyReportAiSummaryLog
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.ColorTag
import com.daybook.app.data.model.CustomCategory
import com.daybook.app.data.model.CustomPrompt
import com.daybook.app.data.model.DayOfWeek
import com.daybook.app.data.model.Exercise
import com.daybook.app.data.model.FoodMedOccurrence
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.HabitType
import com.daybook.app.data.model.Occurrence
import com.daybook.app.data.model.RedFlag
import com.daybook.app.data.model.TaskType
import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutRoutine
import com.daybook.app.data.model.WorkoutRoutineExercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.model.HealthDay
import com.daybook.app.data.model.HealthSession
import com.daybook.app.data.model.HealthWeightReading
import com.daybook.app.data.model.DailyReportAiSummary
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.util.JsonUtils
import com.daybook.app.util.notification.NotificationIdSequence
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup export / restore in the v2 day-partitioned format (see `data/backup/BackupModel.kt`).
 *
 * Clean break from v1: a v1 file no longer parses into this shape and is refused outright, and a
 * restore is a **replace**, not a merge — the previous format's per-row upsert produced silently
 * blended histories that no stats view could interpret.
 */
@Singleton
class ExportImportRepository @Inject constructor(
    private val database: AppDatabase,
    private val jsonUtils: JsonUtils,
    private val notificationIds: NotificationIdSequence
) {
    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        const val UNSUPPORTED =
            "This backup was made by an older version of Daybook and can't be restored."
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 6 (S-3): SQLite's bound-variable ceiling is 999 on
        // the versions shipped with Android 8-11 (minSdk 26). 900 leaves headroom for the query's
        // other bound params.
        const val SQLITE_MAX_VARS = 900
        const val TAG = "ExportImportRepository"
    }

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-7, Low): maps a caught import/export exception
     * to a friendly, non-technical message instead of surfacing [Throwable.message] raw (a raw
     * `SQLiteException`/`IOException`/`SerializationException` message is developer-facing, not
     * something a user reading "Import failed: ..." can act on). The raw message is still logged
     * via [Log.e] for anyone who does need it (e.g. via the Settings "Copy crash log" row if it
     * escalates to an actual crash) — never simply discarded. [fallback] is the call site's own
     * existing generic text, used for an exception type not specifically mapped below.
     */
    private fun friendlyImportError(e: Exception, fallback: String): String {
        Log.e(TAG, "import/export failed", e)
        return when (e) {
            is android.database.sqlite.SQLiteException ->
                "Couldn't save the imported data — try again, or restart the app if it keeps happening."
            is java.io.IOException ->
                "Couldn't read the file. Try picking it again."
            is kotlinx.serialization.SerializationException ->
                "That file doesn't look like a Daybook backup."
            else -> e.message ?: fallback
        }
    }

    // ------------------------------------------------------------------ export

    /**
     * The file-export path: the v2 backup as a pretty-printed JSON string.
     *
     * The cloud-sync path must NOT go through here — it needs the [DaybookBackup] object, and
     * `jsonUtils.decode(exportAllData())` was serialising the entire history to a pretty-printed
     * String and parsing it straight back on every debounced push (§1 D1). Call [exportBackup].
     */
    suspend fun exportAllData(): String = jsonUtils.encode(exportBackup())

    /**
     * v0.5.3 Phase 6 (D2): the same [DaybookBackup] builder as [exportBackup], but with `days`
     * clipped to `[start, end]` **inclusive** and `meta.rangeStart` / `meta.rangeEnd` stamped.
     *
     * The clip keys off each `DayEntry.date` — which [exportBackup] fills from the stored
     * `local_date` column (Phase 2 / S17), never a re-computed epoch — so the range is
     * timezone-stable: a day the user lived on the 1st stays in a range that starts on the 1st,
     * whatever zone the export runs in.
     *
     * Definitions are **not** filtered: a range file still carries the full habit / reminder set so
     * it restores standalone. Days come back sorted ascending.
     */
    suspend fun exportRange(start: LocalDate, end: LocalDate): DaybookBackup {
        val lo = if (start.isAfter(end)) end else start
        val hi = if (start.isAfter(end)) start else end
        val full = exportBackup()
        return full.copy(
            meta = full.meta.copy(rangeStart = lo.toString(), rangeEnd = hi.toString()),
            days = daysInRange(full.days, lo, hi)
        )
    }

    /** v0.5.3 Phase 6 (D2): [exportRange] as a pretty-printed JSON string for the file path. */
    suspend fun exportRangeJson(start: LocalDate, end: LocalDate): String =
        jsonUtils.encode(exportRange(start, end))

    // ------------------------------------------------------------- B5a: the split export/import

    /**
     * B5a (§7.5.1) — the "Beast Mode" file: `meta.kind = KIND_BEAST_MODE`; `definitions.habits`,
     * `.intakeReminders`, `.customCategories`, `.customPrompts` all zeroed (kept:
     * `.customExercises`, `.routines`); `days` filtered to only entries with non-empty `workouts`
     * and/or non-null `health` — every such day keeps `habitLogs`/`intakeLogs` empty, and a day
     * with neither is dropped entirely.
     */
    suspend fun exportBeastModeBackup(): DaybookBackup {
        val full = exportBackup()
        return full.copy(
            meta = full.meta.copy(kind = BackupMeta.KIND_BEAST_MODE),
            definitions = full.definitions.copy(
                habits = emptyList(), intakeReminders = emptyList(),
                customCategories = emptyList(), customPrompts = emptyList()
            ),
            days = full.days
                .filter { it.workouts.isNotEmpty() || it.health != null }
                // DAILY_REPORT_PLAN.md §3.6 — the AI summary is a main-app Daybook artifact, not a
                // workout/health one; it never travels in the Beast Mode file.
                .map { it.copy(habitLogs = emptyList(), intakeLogs = emptyList(), aiSummary = null, hydrationMl = null) }
        )
    }

    /**
     * B5a — the "Daybook" file: `meta.kind = KIND_DAYBOOK`; `definitions.customExercises`,
     * `.routines` zeroed; every `DayEntry.workouts` zeroed and `.health` nulled. Because all four
     * fields are already `@EncodeDefault(NEVER)`-empty/absent, zeroing them here produces exactly
     * the bytes today's export already produces for a user with no Beast Mode data (§7.5.1).
     */
    suspend fun exportDaybookBackup(): DaybookBackup {
        val full = exportBackup()
        return full.copy(
            meta = full.meta.copy(kind = BackupMeta.KIND_DAYBOOK),
            definitions = full.definitions.copy(customExercises = emptyList(), routines = emptyList()),
            days = full.days.map { it.copy(workouts = emptyList(), health = null) }
        )
    }

    /** B5a — [exportBeastModeBackup] clipped to `[start, end]`, mirroring [exportRange]. */
    suspend fun exportBeastModeRange(start: LocalDate, end: LocalDate): DaybookBackup {
        val lo = if (start.isAfter(end)) end else start
        val hi = if (start.isAfter(end)) start else end
        val full = exportBeastModeBackup()
        return full.copy(
            meta = full.meta.copy(rangeStart = lo.toString(), rangeEnd = hi.toString()),
            days = daysInRange(full.days, lo, hi)
        )
    }

    /** B5a — [exportDaybookBackup] clipped to `[start, end]`, mirroring [exportRange]. */
    suspend fun exportDaybookRange(start: LocalDate, end: LocalDate): DaybookBackup {
        val lo = if (start.isAfter(end)) end else start
        val hi = if (start.isAfter(end)) start else end
        val full = exportDaybookBackup()
        return full.copy(
            meta = full.meta.copy(rangeStart = lo.toString(), rangeEnd = hi.toString()),
            days = daysInRange(full.days, lo, hi)
        )
    }

    suspend fun exportBeastModeRangeJson(start: LocalDate, end: LocalDate): String =
        jsonUtils.encode(exportBeastModeRange(start, end))

    suspend fun exportDaybookRangeJson(start: LocalDate, end: LocalDate): String =
        jsonUtils.encode(exportDaybookRange(start, end))

    /**
     * B5a (§7.5.1/R30) — reached from Beast Mode Settings' own `Backup & data` sub-group, never
     * from Daybook's main "Import JSON". Requires `meta.kind == KIND_BEAST_MODE` exactly; rejects
     * an absent or [BackupMeta.KIND_DAYBOOK] value with a specific, named-destination message.
     * Only `workout_*` and `health_*` tables are ever touched — this never calls into
     * [importAllData]'s full-replace path, so a malformed file can never clobber the user's
     * journal (habits/intake/custom categories/prompts).
     */
    suspend fun importBeastModeBackup(json: String): ImportResult {
        val backup = runCatching { jsonUtils.decode(json) }.getOrNull()
            ?: return ImportResult(success = false, message = UNSUPPORTED)
        if (backup.meta.formatVersion != BackupMeta.FORMAT_VERSION) {
            return ImportResult(success = false, message = UNSUPPORTED)
        }
        if (backup.meta.kind != BackupMeta.KIND_BEAST_MODE) {
            return ImportResult(
                success = false,
                message = "This looks like a Daybook backup, not a Beast Mode one. Import it from " +
                    "Settings → Backup & data instead — your Beast Mode data will be " +
                    "restored from it too if the file has any."
            )
        }
        return try {
            val customExercises = backup.definitions.customExercises.map { it.toEntity() }
            val routines = backup.definitions.routines.map { it.toRoutineEntity() }
            val routineExercises = backup.definitions.routines.flatMap { it.toChildEntities() }
            val (workoutSessions, workoutExercises, workoutSets) = mapDaysToWorkouts(backup.days)
            val (healthDays, healthSessions, healthWeightReadings) = mapDaysToHealth(backup.days)

            database.withTransaction {
                database.workoutDao().deleteAllSets()
                database.workoutDao().deleteAllExercises()
                database.workoutDao().deleteAllSessions()
                database.routineDao().deleteAllRoutineExercises()
                database.routineDao().deleteAllRoutines()
                database.exerciseDao().deleteAll()
                database.healthDao().deleteAllSessions()
                database.healthDao().deleteAllDays()
                database.healthDao().deleteAllWeightReadings()

                if (customExercises.isNotEmpty()) database.exerciseDao().insertAll(customExercises)
                if (routines.isNotEmpty()) database.routineDao().upsertRoutines(routines)
                if (routineExercises.isNotEmpty()) database.routineDao().upsertRoutineExercises(routineExercises)
                if (workoutSessions.isNotEmpty()) database.workoutDao().insertSessions(workoutSessions)
                if (workoutExercises.isNotEmpty()) database.workoutDao().insertExercises(workoutExercises)
                if (workoutSets.isNotEmpty()) database.workoutDao().insertSets(workoutSets)
                if (healthDays.isNotEmpty()) database.healthDao().upsertDays(healthDays)
                if (healthSessions.isNotEmpty()) database.healthDao().upsertSessions(healthSessions)
                if (healthWeightReadings.isNotEmpty()) database.healthDao().upsertWeightReadings(healthWeightReadings)
            }
            ImportResult(
                success = true,
                message = "${customExercises.size} exercises, ${routines.size} routines, " +
                    "${backup.days.size} days of Beast Mode history",
                coveredMonths = coveredMonths(backup.days)
            )
        } catch (e: Exception) {
            ImportResult(success = false, message = friendlyImportError(e, "Invalid Beast Mode backup file"))
        }
    }

    /**
     * Builds the [DaybookBackup] object straight from Room — no serialise/parse round trip.
     * [ContentHash] and [MonthPartitioner] consume this object directly.
     *
     * v0.5.3 Phase 7 (audit S13 — export/import round trip is not byte-identity, informational):
     *  - A past occurrence still `PENDING` at export time is written as `MISSED`; on import it
     *    comes back `PENDING`, and the next `syncAll()` flips it to `SKIPPED`. So a file
     *    round-trip is not hash-stable on the first push after import — it self-corrects in
     *    exactly one push and never affects streaks or stats (a past PENDING/MISSED/SKIPPED slot
     *    counts the same in all three).
     *  - `respondedAt` round-trips at **second** precision: sub-second millis are dropped on the
     *    first import, after which the hash is stable.
     * Neither is a bug; documented so the "why did one month re-push after an import" question
     * has an answer.
     */
    suspend fun exportBackup(): DaybookBackup {
        val habits = database.habitDao().observeAllHabits().first()
        val tasks = database.foodMedTaskDao().observeAllTasks().first()
        val habitOccurrences = database.habitOccurrenceDao().getAllOccurrences().first()
        val foodMedOccurrences = database.foodMedOccurrenceDao().getAllOccurrences().first()
        val customCategories = database.customCategoryDao().getNames()
        val customPrompts = database.customPromptDao().getNames()
        // A4 (§4.4 item 1): custom exercises + routines (definitions), and every workout session
        // grouped by local_date (history, nested inside DayEntry.workouts).
        val customExercises = database.exerciseDao().getAll()
        val routines = database.routineDao().getAllRoutinesIncludingArchived()
        val routineExerciseRows = routines.map { it.id }.chunked(SQLITE_MAX_VARS)
            .flatMap { database.routineDao().getRoutineExercisesForRoutines(it) }
            .groupBy { it.routineId }
        val workoutByDate = exportWorkoutsByLocalDate()
        // B5 (§7.5.0) — health has no definitions; per-day aggregates + that day's sessions only.
        val healthByDate = exportHealthByLocalDate()
        // DAILY_REPORT_PLAN.md §3.6 — at most one cached AI summary per day, no definitions either.
        val aiSummaryByDate = exportAiSummaryByLocalDate()
        // Hydration habit (DB v31) — one amount per day.
        val hydrationByDate = database.hydrationDao().getAll().associate { it.localDate to it.amountMl }

        val habitIds = habits.mapTo(HashSet()) { it.id }
        val taskIds = tasks.mapTo(HashSet()) { it.id }
        val now = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()

        // Bucket by LOCAL date, so a day in the file is the day the user actually lived.
        // v0.5.3 Phase 2 (S17): key off the STORED `local_date` (timezone-stable), falling back to a
        // recompute for pre-migration rows. `MonthPartitioner` still consumes the `DayEntry.date`
        // strings unchanged.
        val habitByDate = HashMap<String, MutableList<HabitLog>>()
        habitOccurrences.forEach { occ ->
            if (occ.habitId !in habitIds) return@forEach
            // A still-PENDING future slot is not history — it is regenerated from the definitions
            // on restore. Emitting it would read as "missed" and poison every rollup.
            if (occ.status == Occurrence.Status.PENDING && occ.scheduledFor > now) return@forEach
            val date = exportDateFor(occ.localDate, occ.scheduledFor, zoneId)
            habitByDate.getOrPut(date) { mutableListOf() } += HabitLog(
                habitId = occ.habitId,
                scheduledTime = DateTimeUtils.timestampToLocalTime(occ.scheduledFor).format(HHMM),
                status = when (occ.status) {
                    Occurrence.Status.COMPLETED -> BackupStatus.DONE
                    Occurrence.Status.SKIPPED -> BackupStatus.SKIPPED
                    Occurrence.Status.LOGGED -> BackupStatus.DONE
                    Occurrence.Status.PENDING -> BackupStatus.MISSED
                },
                resolvedAt = occ.respondedAt?.let { jsonUtils.toIso(it) },
                qaJson = occ.qaJson?.takeIf { it.isNotBlank() }   // Journal-as-habit round
            )
        }

        val intakeByDate = HashMap<String, MutableList<IntakeLog>>()
        foodMedOccurrences.forEach { occ ->
            if (occ.taskId !in taskIds) return@forEach
            if (occ.status == Occurrence.Status.PENDING && occ.scheduledFor > now) return@forEach
            val date = exportDateFor(occ.localDate, occ.scheduledFor, zoneId)
            intakeByDate.getOrPut(date) { mutableListOf() } += IntakeLog(
                reminderId = occ.taskId,
                scheduledTime = DateTimeUtils.timestampToLocalTime(occ.scheduledFor).format(HHMM),
                status = when (occ.status) {
                    Occurrence.Status.LOGGED, Occurrence.Status.COMPLETED -> BackupStatus.LOGGED
                    Occurrence.Status.SKIPPED -> BackupStatus.SKIPPED
                    Occurrence.Status.PENDING -> BackupStatus.MISSED
                },
                answer = occ.responseText.takeIf { it.isNotBlank() },
                resolvedAt = occ.respondedAt?.let { jsonUtils.toIso(it) },
                description = occ.description?.takeIf { it.isNotBlank() },
                qaJson = occ.qaJson?.takeIf { it.isNotBlank() },   // v0.5.4 Phase 2 (D3)
                redFlag = occ.redFlag?.takeIf { it != RedFlag.NONE }?.name,
                suspectedFood = occ.suspectedFood?.takeIf { it.isNotBlank() },
                outsideFood = occ.outsideFood?.takeIf { it }
            )
        }

        // ISO "yyyy-MM-dd" strings sort chronologically as plain text. A4/B5: a day with ONLY a
        // workout or ONLY health data must still appear, so both keys join the union.
        val days = (habitByDate.keys + intakeByDate.keys + workoutByDate.keys + healthByDate.keys + aiSummaryByDate.keys + hydrationByDate.keys)
            .sorted().map { date ->
            DayEntry(
                date = date,
                habitLogs = habitByDate[date].orEmpty().sortedBy { it.scheduledTime },
                intakeLogs = intakeByDate[date].orEmpty().sortedBy { it.scheduledTime },
                workouts = workoutByDate[date].orEmpty(),
                health = healthByDate[date],
                aiSummary = aiSummaryByDate[date],
                hydrationMl = hydrationByDate[date]
            )
        }

        return DaybookBackup(
            meta = BackupMeta(
                exportedAt = jsonUtils.nowIso(),
                appVersionName = BuildConfig.VERSION_NAME
            ),
            definitions = Definitions(
                    habits = habits.map { h ->
                        HabitDef(
                            id = h.id,
                            name = h.title,
                            iconKey = h.iconKey,
                            colorTag = h.colorTag.name,
                            times = timesOf(h.timesJson),
                            activeDays = daysOf(h.activeDaysJson),
                            snoozeMinutes = h.snoozeIntervalMinutes,
                            createdAt = jsonUtils.toIso(h.createdAt),
                            archived = h.isArchived,
                            type = h.type.name,
                            streakStartedAt = h.streakStartedAt,
                            streakLongest = h.streakLongest,
                            promptMessage = h.promptMessage,
                            motivation = h.motivation,
                            journalQuestions = DateTimeUtils.jsonToJournalQuestions(h.journalQuestionsJson)
                        )
                    },
                    intakeReminders = tasks.map { t ->
                        IntakeReminderDef(
                            id = t.id,
                            name = t.label,
                            type = t.type.name,
                            iconKey = t.iconKey,
                            colorTag = t.colorTag.name,
                            times = timesOf(t.timesJson),
                            activeDays = daysOf(t.activeDaysJson),
                            snoozeMinutes = t.snoozeIntervalMinutes,
                            createdAt = jsonUtils.toIso(t.createdAt),
                            archived = t.isArchived,
                            customCategory = t.customCategory,
                            promptMessage = t.promptMessage,
                            motivation = t.motivation,
                            defaultRedFlag = t.defaultRedFlag?.takeIf { it != RedFlag.NONE }?.name,
                            defaultSuspectedFood = t.defaultSuspectedFood?.takeIf { it.isNotBlank() },
                            defaultOutsideFood = t.defaultOutsideFood?.takeIf { it }
                        )
                    },
                customCategories = customCategories,
                customPrompts = customPrompts,
                customExercises = customExercises.map { e ->
                    ExerciseDef(
                        id = e.id, name = e.name, primaryMuscle = e.primaryMuscle,
                        equipment = e.equipment, trackingMode = e.trackingMode,
                        createdAt = jsonUtils.toIso(e.createdAt), archived = e.isArchived,
                        source = e.source, notes = e.notes?.takeIf { it.isNotBlank() }
                    )
                },
                routines = routines.map { r ->
                    RoutineDef(
                        id = r.id, name = r.name, orderIndex = r.orderIndex,
                        createdAt = jsonUtils.toIso(r.createdAt), updatedAt = jsonUtils.toIso(r.updatedAt),
                        archived = r.isArchived, source = r.source,
                        notes = r.notes?.takeIf { it.isNotBlank() },
                        exercises = routineExerciseRows[r.id].orEmpty().sortedBy { it.orderIndex }.map { k ->
                            RoutineExerciseDef(
                                id = k.id, exerciseId = k.exerciseId, orderIndex = k.orderIndex,
                                targetSets = k.targetSets, targetReps = k.targetReps,
                                targetWeightKg = k.targetWeightKg,
                                targetDurationSeconds = k.targetDurationSeconds,
                                targetDistanceMeters = k.targetDistanceMeters,
                                restSeconds = k.restSeconds, notes = k.notes?.takeIf { it.isNotBlank() }
                            )
                        }
                    )
                }
            ),
            days = days
        )
    }

    /**
     * A4 (§4.4 item 1) — every workout session, its blocks and its sets, grouped by the session's
     * `local_date`. Both the block fetch and the set fetch go through the chunked
     * `getExercisesForSessions` / `getSetsForSessions` (900-var cap) rather than one query per
     * session (an N+1 that would scale with the user's whole workout history).
     */
    private suspend fun exportWorkoutsByLocalDate(): Map<String, List<WorkoutLog>> {
        val sessions = database.workoutDao().getAllSessions()
        if (sessions.isEmpty()) return emptyMap()
        val sessionIds = sessions.map { it.id }
        val exercisesBySession = sessionIds.chunked(SQLITE_MAX_VARS)
            .flatMap { database.workoutDao().getExercisesForSessions(it) }
            .groupBy { it.sessionId }
        val setsByBlock = sessionIds.chunked(SQLITE_MAX_VARS)
            .flatMap { database.workoutDao().getSetsForSessions(it) }
            .groupBy { it.workoutExerciseId }

        val byDate = HashMap<String, MutableList<WorkoutLog>>()
        for (session in sessions) {
            val blocks = exercisesBySession[session.id].orEmpty().sortedBy { it.orderIndex }
            val exerciseLogs = blocks.map { block ->
                val sets = setsByBlock[block.id].orEmpty().sortedBy { it.setNumber }
                WorkoutExerciseLog(
                    id = block.id, exerciseId = block.exerciseId, orderIndex = block.orderIndex,
                    notes = block.notes?.takeIf { it.isNotBlank() }, supersetId = block.supersetId,
                    restSeconds = block.restSeconds,
                    sets = sets.map { s ->
                        WorkoutSetLog(
                            id = s.id, setNumber = s.setNumber, reps = s.reps, weightKg = s.weightKg,
                            durationSeconds = s.durationSeconds, distanceMeters = s.distanceMeters,
                            rpe = s.rpe, setType = s.setType,
                            notes = s.notes?.takeIf { it.isNotBlank() },
                            completedAt = s.completedAt?.let { jsonUtils.toIso(it) }
                        )
                    }
                )
            }
            byDate.getOrPut(session.localDate) { mutableListOf() } += WorkoutLog(
                id = session.id,
                startedAt = jsonUtils.toIso(session.startedAt),
                endedAt = session.endedAt?.let { jsonUtils.toIso(it) },
                title = session.title, notes = session.notes, status = session.status,
                source = session.source, routineId = session.routineId, exercises = exerciseLogs
            )
        }
        return byDate
    }

    /**
     * B5 (§7.5.0) — every `health_days` row plus that day's `health_sessions`, keyed by local
     * date. One `HealthDayLog` per date (health is a per-day aggregate, not a list like workouts).
     */
    private suspend fun exportHealthByLocalDate(): Map<String, HealthDayLog> {
        val days = database.healthDao().getAllDays()
        if (days.isEmpty()) return emptyMap()
        val sessionsByDate = database.healthDao().getAllSessions().groupBy { it.localDate }
        // HEALTH_VITALS_RICHNESS_PLAN.md §5 — keyed alongside sessions, same per-day grouping.
        val weightReadingsByDate = database.healthDao().getAllWeightReadings().groupBy { it.localDate }
        return days.associate { d ->
            d.localDate to HealthDayLog(
                steps = d.steps, distanceMeters = d.distanceMeters,
                activeCalories = d.activeCalories, totalCalories = d.totalCalories,
                restingHeartRate = d.restingHeartRate, avgHeartRate = d.avgHeartRate,
                minHeartRate = d.minHeartRate, maxHeartRate = d.maxHeartRate,
                sleepMinutes = d.sleepMinutes, sleepDeepMinutes = d.sleepDeepMinutes,
                sleepLightMinutes = d.sleepLightMinutes, sleepRemMinutes = d.sleepRemMinutes,
                sleepAwakeMinutes = d.sleepAwakeMinutes,
                sleepStartMillis = d.sleepStartMillis, sleepEndMillis = d.sleepEndMillis,
                spo2Percent = d.spo2Percent,
                spo2MinPercent = d.spo2MinPercent, spo2MaxPercent = d.spo2MaxPercent,
                weightKg = d.weightKg, hydrationMl = d.hydrationMl,
                nutritionCalories = d.nutritionCalories, nutritionProteinGrams = d.nutritionProteinGrams,
                nutritionCarbsGrams = d.nutritionCarbsGrams, nutritionFatGrams = d.nutritionFatGrams,
                nutritionSourceApp = d.nutritionSourceApp,
                sessions = sessionsByDate[d.localDate].orEmpty().sortedBy { it.startMillis }.map { s ->
                    HealthSessionLog(
                        id = s.id, exerciseType = s.exerciseType, title = s.title,
                        startMillis = s.startMillis, endMillis = s.endMillis,
                        durationMinutes = s.durationMinutes, activeCalories = s.activeCalories,
                        distanceMeters = s.distanceMeters, avgHeartRate = s.avgHeartRate,
                        sourceApp = s.sourceApp
                    )
                },
                weightReadings = weightReadingsByDate[d.localDate].orEmpty().sortedBy { it.atMillis }.map { w ->
                    HealthWeightReadingLog(atMillis = w.atMillis, weightKg = w.weightKg, sourceApp = w.sourceApp)
                }
            )
        }
    }

    /** DAILY_REPORT_PLAN.md §3.6 — every `daily_report_ai_summaries` row, keyed by local date. At
     *  most one per date, mirroring [exportHealthByLocalDate]'s per-day-aggregate shape. */
    private suspend fun exportAiSummaryByLocalDate(): Map<String, DailyReportAiSummaryLog> {
        val rows = database.dailyReportAiSummaryDao().getAll()
        if (rows.isEmpty()) return emptyMap()
        return rows.associate { r ->
            r.localDate to DailyReportAiSummaryLog(
                provider = r.provider, model = r.model,
                summaryText = r.summaryText, generatedAt = r.generatedAt,
                settingsFingerprint = r.settingsFingerprint
            )
        }
    }

    /** B5 — the inverse of [exportHealthByLocalDate]: `DayEntry.health` -> flat entity lists. */
    private fun mapDaysToHealth(
        days: List<DayEntry>
    ): Triple<List<HealthDay>, List<HealthSession>, List<HealthWeightReading>> {
        val now = System.currentTimeMillis()
        val healthDays = ArrayList<HealthDay>()
        val healthSessions = ArrayList<HealthSession>()
        val weightReadings = ArrayList<HealthWeightReading>()
        for (day in days) {
            val h = day.health ?: continue
            healthDays += HealthDay(
                localDate = day.date, steps = h.steps, distanceMeters = h.distanceMeters,
                activeCalories = h.activeCalories, totalCalories = h.totalCalories,
                restingHeartRate = h.restingHeartRate, avgHeartRate = h.avgHeartRate,
                minHeartRate = h.minHeartRate, maxHeartRate = h.maxHeartRate,
                sleepMinutes = h.sleepMinutes, sleepDeepMinutes = h.sleepDeepMinutes,
                sleepLightMinutes = h.sleepLightMinutes, sleepRemMinutes = h.sleepRemMinutes,
                sleepAwakeMinutes = h.sleepAwakeMinutes,
                sleepStartMillis = h.sleepStartMillis, sleepEndMillis = h.sleepEndMillis,
                spo2Percent = h.spo2Percent,
                spo2MinPercent = h.spo2MinPercent, spo2MaxPercent = h.spo2MaxPercent,
                weightKg = h.weightKg, hydrationMl = h.hydrationMl,
                nutritionCalories = h.nutritionCalories, nutritionProteinGrams = h.nutritionProteinGrams,
                nutritionCarbsGrams = h.nutritionCarbsGrams, nutritionFatGrams = h.nutritionFatGrams,
                nutritionSourceApp = h.nutritionSourceApp, updatedAt = now
            )
            for (s in h.sessions) {
                healthSessions += HealthSession(
                    id = s.id, localDate = day.date, exerciseType = s.exerciseType, title = s.title,
                    startMillis = s.startMillis, endMillis = s.endMillis,
                    durationMinutes = s.durationMinutes, activeCalories = s.activeCalories,
                    distanceMeters = s.distanceMeters, avgHeartRate = s.avgHeartRate,
                    sourceApp = s.sourceApp, updatedAt = now
                )
            }
            // HealthWeightReadingLog carries no id (§5 — it's not a Health Connect record once it
            // has round-tripped through a JSON backup); date+millis is stable across re-imports of
            // the same file, so the synthesized id still upserts cleanly rather than duplicating.
            for (w in h.weightReadings) {
                weightReadings += HealthWeightReading(
                    id = "${day.date}:${w.atMillis}", localDate = day.date,
                    atMillis = w.atMillis, weightKg = w.weightKg, sourceApp = w.sourceApp
                )
            }
        }
        return Triple(healthDays, healthSessions, weightReadings)
    }

    /** DAILY_REPORT_PLAN.md §3.6 — the inverse of [exportAiSummaryByLocalDate]: `DayEntry.aiSummary`
     *  -> flat entity list, ready to upsert. */
    private fun mapDaysToHydration(days: List<DayEntry>): List<com.daybook.app.data.model.HydrationDay> {
        val now = System.currentTimeMillis()
        return days.mapNotNull { day ->
            day.hydrationMl?.takeIf { it > 0 }?.let {
                com.daybook.app.data.model.HydrationDay(localDate = day.date, amountMl = it, updatedAt = now)
            }
        }
    }

    private fun mapDaysToAiSummary(days: List<DayEntry>): List<DailyReportAiSummary> {
        val out = ArrayList<DailyReportAiSummary>()
        for (day in days) {
            val a = day.aiSummary ?: continue
            out += DailyReportAiSummary(
                localDate = day.date, provider = a.provider, model = a.model,
                summaryText = a.summaryText, generatedAt = a.generatedAt,
                settingsFingerprint = a.settingsFingerprint
            )
        }
        return out
    }

    /**
     * A4 — the inverse of [exportWorkoutsByLocalDate]: `DayEntry.workouts` -> flat entity lists,
     * ready to insert. Each set/block's `sessionId`/`exerciseId` is re-derived from its enclosing
     * `WorkoutLog`/`WorkoutExerciseLog` (§4.2 — neither rides the wire denormalised).
     */
    private fun mapDaysToWorkouts(days: List<DayEntry>): Triple<List<WorkoutSession>, List<WorkoutExercise>, List<WorkoutSet>> {
        val sessions = ArrayList<WorkoutSession>()
        val exercises = ArrayList<WorkoutExercise>()
        val sets = ArrayList<WorkoutSet>()
        for (day in days) {
            for (log in day.workouts) {
                val startedAt = jsonUtils.fromIso(log.startedAt) ?: continue
                sessions += WorkoutSession(
                    id = log.id, localDate = day.date, startedAt = startedAt,
                    endedAt = log.endedAt?.let { jsonUtils.fromIso(it) }, title = log.title,
                    notes = log.notes, status = log.status, source = log.source,
                    routineId = log.routineId, createdAt = startedAt
                )
                for (ex in log.exercises) {
                    exercises += WorkoutExercise(
                        id = ex.id, sessionId = log.id, exerciseId = ex.exerciseId,
                        orderIndex = ex.orderIndex, notes = ex.notes, supersetId = ex.supersetId,
                        restSeconds = ex.restSeconds, createdAt = startedAt
                    )
                    for (s in ex.sets) {
                        sets += WorkoutSet(
                            id = s.id, workoutExerciseId = ex.id, sessionId = log.id,
                            exerciseId = ex.exerciseId, setNumber = s.setNumber, reps = s.reps,
                            weightKg = s.weightKg, durationSeconds = s.durationSeconds,
                            distanceMeters = s.distanceMeters, rpe = s.rpe, setType = s.setType,
                            notes = s.notes, completedAt = s.completedAt?.let { jsonUtils.fromIso(it) }
                        )
                    }
                }
            }
        }
        return Triple(sessions, exercises, sets)
    }

    private fun Exercise.toDef() = ExerciseDef(
        id = id, name = name, primaryMuscle = primaryMuscle, equipment = equipment,
        trackingMode = trackingMode, createdAt = jsonUtils.toIso(createdAt), archived = isArchived,
        source = source, notes = notes?.takeIf { it.isNotBlank() }
    )

    private fun ExerciseDef.toEntity() = Exercise(
        id = id, name = name, primaryMuscle = primaryMuscle, equipment = equipment,
        trackingMode = trackingMode, isArchived = archived, source = source,
        createdAt = jsonUtils.fromIso(createdAt) ?: System.currentTimeMillis(),
        notes = notes?.takeIf { it.isNotBlank() }
    )

    private fun RoutineDef.toRoutineEntity() = WorkoutRoutine(
        id = id, name = name, notes = notes?.takeIf { it.isNotBlank() }, orderIndex = orderIndex,
        isArchived = archived, source = source,
        createdAt = jsonUtils.fromIso(createdAt) ?: System.currentTimeMillis(),
        updatedAt = jsonUtils.fromIso(updatedAt) ?: System.currentTimeMillis()
    )

    private fun RoutineDef.toChildEntities(): List<WorkoutRoutineExercise> {
        val createdAtMillis = jsonUtils.fromIso(createdAt) ?: System.currentTimeMillis()
        return exercises.map { e ->
            WorkoutRoutineExercise(
                id = e.id, routineId = id, exerciseId = e.exerciseId, orderIndex = e.orderIndex,
                targetSets = e.targetSets, targetReps = e.targetReps, targetWeightKg = e.targetWeightKg,
                targetDurationSeconds = e.targetDurationSeconds, targetDistanceMeters = e.targetDistanceMeters,
                restSeconds = e.restSeconds, notes = e.notes?.takeIf { it.isNotBlank() },
                createdAt = createdAtMillis
            )
        }
    }

    private fun timesOf(timesJson: String): List<String> =
        DateTimeUtils.jsonToTimes(timesJson).sorted().map { it.format(HHMM) }

    /** Internal weekday enum → 1=Mon … 7=Sun, sorted. */
    private fun daysOf(activeDaysJson: String): List<Int> =
        DateTimeUtils.jsonToDays(activeDaysJson).map { it.ordinal + 1 }.distinct().sorted()

    // ------------------------------------------------------------------ import

    /**
     * Wipes the user tables and rebuilds them from [json]. Everything happens in one transaction,
     * so a malformed file leaves the database exactly as it was. Callers re-arm alarms afterwards
     * (`OccurrenceScheduler.syncAll()`), since AlarmManager is not part of the transaction.
     */
    suspend fun importAllData(json: String): ImportResult {
        val backup = runCatching { jsonUtils.decode(json) }.getOrNull()
            ?: return ImportResult(success = false, message = UNSUPPORTED)
        if (backup.meta.formatVersion != BackupMeta.FORMAT_VERSION) {
            return ImportResult(success = false, message = UNSUPPORTED)
        }
        // B5a (§7.5.1/R30) — this is the Daybook import button; a Beast Mode file must be rejected
        // here with a specific, named-destination message, not silently no-op'd or half-imported.
        if (backup.meta.kind == BackupMeta.KIND_BEAST_MODE) {
            return ImportResult(
                success = false,
                message = "This looks like a Beast Mode backup. Import it from Beast Mode Settings instead."
            )
        }
        // Definitions are what everything else hangs off; a file with none is not a Daybook backup.
        if (backup.definitions.habits.isEmpty() && backup.definitions.intakeReminders.isEmpty()) {
            return ImportResult(success = false, message = UNSUPPORTED)
        }

        // v0.5.3 Phase 6 (D2/S5): a range-scoped file (meta.rangeStart set) merges only the months
        // it names and NEVER `deleteAll()`s — so re-importing a Jan–Mar export does not wipe
        // Apr–Dec, locally or (via the next push's diff) in the cloud. A full-range file (null
        // range) keeps the historical whole-replace behaviour below.
        if (isRangeScoped(backup.meta)) {
            return importRange(backup)
        }

        return try {
            val habits = backup.definitions.habits.map { d ->
                Habit(
                    id = d.id,
                    title = d.name,
                    colorTag = ColorTag.fromNameOrAuto(d.colorTag),
                    iconKey = d.iconKey,
                    createdAt = jsonUtils.fromIso(d.createdAt) ?: System.currentTimeMillis(),
                    timesJson = joinTimes(d.times),
                    activeDaysJson = joinDays(d.activeDays),
                    isArchived = d.archived,
                    snoozeIntervalMinutes = d.snoozeMinutes,
                    notificationId = notificationIds.next(),
                    type = runCatching { HabitType.valueOf(d.type) }.getOrDefault(HabitType.INDIVIDUAL),
                    streakStartedAt = d.streakStartedAt,
                    streakLongest = d.streakLongest,
                    promptMessage = d.promptMessage?.trim()?.takeIf { it.isNotBlank() },
                    motivation = d.motivation?.trim()?.takeIf { it.isNotBlank() },
                    journalQuestionsJson = DateTimeUtils.journalQuestionsToJson(d.journalQuestions)
                )
            }
            val tasks = backup.definitions.intakeReminders.map { d ->
                FoodMedTask(
                    id = d.id,
                    label = d.name,
                    // Journal-as-habit round (B3): a genuinely old backup can still carry
                    // `"type":"JOURNAL"` — TaskType.JOURNAL decodes losslessly (kept dead-but-present
                    // specifically for this), but nothing can render/schedule/edit a JOURNAL-typed
                    // FoodMedTask correctly anymore, so remap it to CUSTOM on import rather than
                    // silently reintroducing the retired UI.
                    type = runCatching { TaskType.valueOf(d.type) }.getOrDefault(TaskType.FOOD)
                        .let { remapLegacyJournalTaskType(it) },
                    colorTag = ColorTag.fromNameOrAuto(d.colorTag),
                    iconKey = d.iconKey,
                    createdAt = jsonUtils.fromIso(d.createdAt) ?: System.currentTimeMillis(),
                    timesJson = joinTimes(d.times),
                    activeDaysJson = joinDays(d.activeDays),
                    isArchived = d.archived,
                    snoozeIntervalMinutes = d.snoozeMinutes,
                    notificationId = notificationIds.next(),
                    customCategory = d.customCategory?.trim()?.takeIf { it.isNotBlank() },
                    promptMessage = d.promptMessage?.trim()?.takeIf { it.isNotBlank() },
                    motivation = d.motivation?.trim()?.takeIf { it.isNotBlank() },
                    defaultRedFlag = RedFlag.fromNameOrNull(d.defaultRedFlag),
                    defaultSuspectedFood = d.defaultSuspectedFood?.trim()?.takeIf { it.isNotBlank() },
                    defaultOutsideFood = d.defaultOutsideFood?.takeIf { it }
                )
            }
            val habitIds = habits.mapTo(HashSet()) { it.id }
            val taskIds = tasks.mapTo(HashSet()) { it.id }
            val categories = backup.definitions.customCategories.map { it.trim() }
                .filter { it.isNotBlank() }.distinct().map { CustomCategory(name = it) }
            val prompts = backup.definitions.customPrompts.map { it.trim() }
                .filter { it.isNotBlank() }.distinct().map { CustomPrompt(name = it) }

            val (habitOccurrences, foodMedOccurrences) =
                mapDaysToOccurrences(backup.days, habitIds, taskIds)

            // A4 (§4.4 item 3): full-replace also covers the six workout tables.
            val customExercises = backup.definitions.customExercises.map { it.toEntity() }
            val routines = backup.definitions.routines.map { it.toRoutineEntity() }
            val routineExercises = backup.definitions.routines.flatMap { it.toChildEntities() }
            val (workoutSessions, workoutExercises, workoutSets) = mapDaysToWorkouts(backup.days)
            // B5 (§7.5.0) — full-replace also covers health, exactly like the six workout tables:
            // a pre-split legacy file that happens to carry embedded health data (impossible before
            // this round, but the code path is the same one workouts already use) imports it too.
            val (healthDays, healthSessions, healthWeightReadings) = mapDaysToHealth(backup.days)
            // DAILY_REPORT_PLAN.md §3.6 — full-replace also covers the cached AI summaries.
            val aiSummaries = mapDaysToAiSummary(backup.days)
            val hydrationDays = mapDaysToHydration(backup.days)

            database.withTransaction {
                // Order matters only for readability — there are no FK constraints.
                database.habitEventDao().deleteAll()
                database.foodMedEventDao().deleteAll()
                database.habitOccurrenceDao().deleteAll()
                database.foodMedOccurrenceDao().deleteAll()
                database.habitDao().deleteAll()
                database.foodMedTaskDao().deleteAll()
                database.customCategoryDao().deleteAll()
                database.customPromptDao().deleteAll()
                // A4: wipe the six workout tables — sets/blocks before sessions, routine rows
                // before routines, purely for readability (no FK to satisfy either way).
                database.workoutDao().deleteAllSets()
                database.workoutDao().deleteAllExercises()
                database.workoutDao().deleteAllSessions()
                database.routineDao().deleteAllRoutineExercises()
                database.routineDao().deleteAllRoutines()
                database.exerciseDao().deleteAll()
                // B5: wipe the health tables too, same full-replace treatment.
                database.healthDao().deleteAllSessions()
                database.healthDao().deleteAllDays()
                database.healthDao().deleteAllWeightReadings()
                // DAILY_REPORT_PLAN.md §3.6 — full-replace wipe also covers the AI summary cache.
                database.dailyReportAiSummaryDao().deleteAll()
                database.hydrationDao().deleteAll()

                if (habits.isNotEmpty()) database.habitDao().insertAll(*habits.toTypedArray())
                if (tasks.isNotEmpty()) database.foodMedTaskDao().insertAll(*tasks.toTypedArray())
                if (categories.isNotEmpty()) database.customCategoryDao().insertAll(*categories.toTypedArray())
                if (prompts.isNotEmpty()) database.customPromptDao().insertAll(*prompts.toTypedArray())
                if (habitOccurrences.isNotEmpty()) {
                    database.habitOccurrenceDao().insertAll(*habitOccurrences.toTypedArray())
                }
                if (foodMedOccurrences.isNotEmpty()) {
                    database.foodMedOccurrenceDao().insertAll(*foodMedOccurrences.toTypedArray())
                }
                if (customExercises.isNotEmpty()) database.exerciseDao().insertAll(customExercises)
                if (routines.isNotEmpty()) database.routineDao().upsertRoutines(routines)
                if (routineExercises.isNotEmpty()) database.routineDao().upsertRoutineExercises(routineExercises)
                if (workoutSessions.isNotEmpty()) database.workoutDao().insertSessions(workoutSessions)
                if (workoutExercises.isNotEmpty()) database.workoutDao().insertExercises(workoutExercises)
                if (workoutSets.isNotEmpty()) database.workoutDao().insertSets(workoutSets)
                if (healthDays.isNotEmpty()) database.healthDao().upsertDays(healthDays)
                if (healthSessions.isNotEmpty()) database.healthDao().upsertSessions(healthSessions)
                if (healthWeightReadings.isNotEmpty()) database.healthDao().upsertWeightReadings(healthWeightReadings)
                if (aiSummaries.isNotEmpty()) database.dailyReportAiSummaryDao().upsertAll(aiSummaries)
                if (hydrationDays.isNotEmpty()) database.hydrationDao().upsertAll(hydrationDays)
            }

            ImportResult(
                success = true,
                message = "${habits.size} habits, ${tasks.size} intake, " +
                    "${backup.days.size} days of history",
                // v0.5.3 Phase 6 (S5): the caller resets hydratedMonths / monthHashes to exactly
                // this set + recentMonths(), so the next push's changedMonths diff cannot phantom-
                // delete a cloud month that the imported file simply did not carry.
                coveredMonths = coveredMonths(backup.days)
            )
        } catch (e: Exception) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-7).
            ImportResult(success = false, message = friendlyImportError(e, "Invalid backup file"))
        }
    }

    /**
     * v0.5.3 Phase 6 (D2/S5): non-destructive restore of a **range-scoped** file.
     *
     * Unlike [importAllData] this NEVER `deleteAll()`s. It:
     *  - upserts the definitions through [applyRemoteDefinitions] (targeted upsert, Phase 1 / S6 —
     *    a range file carries the full definition set, so nothing is pruned);
     *  - groups `backup.days` by local month and runs each through [importMonth] — the Phase 3 /
     *    S15 per-log merge, which clears only that month's stale PENDING slots and upserts the
     *    file's rows. A month **not** in the file is never touched.
     *
     * `coveredMonths` on the result drives `CloudSyncRepository.onLocalDataReplaced`, which pins
     * `hydratedMonths` to this set so a later `doPush` cannot mark an out-of-range cloud month
     * deleted (audit S5).
     */
    suspend fun importRange(backup: DaybookBackup): ImportResult {
        if (backup.definitions.habits.isEmpty() && backup.definitions.intakeReminders.isEmpty()) {
            return ImportResult(success = false, message = UNSUPPORTED)
        }
        return try {
            val defsResult = applyRemoteDefinitions(backup.definitions)
            if (!defsResult.success) return defsResult
            val byMonth = com.daybook.app.data.sync.MonthPartitioner.partition(backup.days)
            for ((monthKey, days) in byMonth) {
                val r = importMonth(monthKey, days)
                if (!r.success) {
                    return ImportResult(success = false, message = r.message ?: "Could not merge $monthKey")
                }
            }
            ImportResult(
                success = true,
                message = "${byMonth.size} month(s), ${backup.days.size} days " +
                    "(${backup.meta.rangeStart}–${backup.meta.rangeEnd})",
                coveredMonths = byMonth.keys
            )
        } catch (e: Exception) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-7).
            ImportResult(success = false, message = friendlyImportError(e, "Could not import range"))
        }
    }

    /**
     * `DayEntry` -> occurrence rows. Each day's logs enumerate exactly the slots that existed on
     * that date, so regenerating the covered span *is* walking `days` — no schedule replay needed,
     * and no phantom rows for days whose schedule has since changed.
     *
     * Factored out of [importAllData] in v0.5.1 §N so [importMonth] uses the byte-identical
     * mapping (`id = "$itemId:$millis"`, the same status vocabulary, a fresh notification id).
     * The two paths must not be allowed to drift.
     */
    private fun mapDaysToOccurrences(
        days: List<DayEntry>,
        habitIds: Set<String>,
        taskIds: Set<String>,
        // v0.5.3 Phase 1 (S14): reuse a live row's notification_id rather than minting a fresh one
        // (a fresh id orphans any posted notification + its refire chain).
        existingHabitNotif: Map<String, Int> = emptyMap(),
        existingTaskNotif: Map<String, Int> = emptyMap(),
        // v0.5.3 Phase 1 (S14): ids of still-PENDING rows that already have a SHOWN event — do NOT
        // re-insert them, leave the live row (and its armed alarm) exactly as it is.
        keepIfShownPending: Set<String> = emptySet()
    ): Pair<List<HabitOccurrence>, List<FoodMedOccurrence>> {
        val habitOccurrences = ArrayList<HabitOccurrence>()
        val foodMedOccurrences = ArrayList<FoodMedOccurrence>()
        for (day in days) {
            val date = parseDate(day.date) ?: continue
            for (log in day.habitLogs) {
                if (log.habitId !in habitIds) continue
                val millis = epochOf(date, log.scheduledTime) ?: continue
                val id = "${log.habitId}:$millis"
                if (id in keepIfShownPending) continue
                habitOccurrences += HabitOccurrence(
                    id = id,
                    habitId = log.habitId,
                    scheduledFor = millis,
                    status = when {
                        // Journal-as-habit round: a "done" habit-log carrying a non-blank qa_json
                        // round-trips back to LOGGED (not COMPLETED) — mirrors the export side's
                        // `Occurrence.Status.LOGGED -> BackupStatus.DONE` and the FoodMed-side
                        // reverse decode, so a re-import doesn't erode the "answered a chat" signal.
                        log.status.lowercase() == BackupStatus.DONE && !log.qaJson.isNullOrBlank() ->
                            Occurrence.Status.LOGGED
                        log.status.lowercase() == BackupStatus.DONE -> Occurrence.Status.COMPLETED
                        log.status.lowercase() == BackupStatus.SKIPPED -> Occurrence.Status.SKIPPED
                        else -> Occurrence.Status.PENDING
                    },
                    respondedAt = jsonUtils.fromIso(log.resolvedAt),
                    notificationId = resolveNotifId(id, existingHabitNotif) { notificationIds.next() },
                    // v0.5.3 Phase 2 (S17): carry the exporter's stable local date, not a recompute.
                    localDate = day.date,
                    qaJson = log.qaJson?.takeIf { it.isNotBlank() }   // Journal-as-habit round
                )
            }
            for (log in day.intakeLogs) {
                if (log.reminderId !in taskIds) continue
                val millis = epochOf(date, log.scheduledTime) ?: continue
                val id = "${log.reminderId}:$millis"
                if (id in keepIfShownPending) continue
                foodMedOccurrences += FoodMedOccurrence(
                    id = id,
                    taskId = log.reminderId,
                    scheduledFor = millis,
                    status = when (log.status.lowercase()) {
                        BackupStatus.LOGGED -> Occurrence.Status.LOGGED
                        BackupStatus.SKIPPED -> Occurrence.Status.SKIPPED
                        else -> Occurrence.Status.PENDING
                    },
                    responseText = log.answer.orEmpty(),
                    respondedAt = jsonUtils.fromIso(log.resolvedAt),
                    notificationId = resolveNotifId(id, existingTaskNotif) { notificationIds.next() },
                    description = log.description,
                    qaJson = log.qaJson,   // v0.5.4 Phase 2 (D3) — shared by full import + per-month merge
                    redFlag = RedFlag.fromNameOrNull(log.redFlag),
                    suspectedFood = log.suspectedFood?.takeIf { it.isNotBlank() },
                    outsideFood = log.outsideFood?.takeIf { it },
                    // v0.5.3 Phase 2 (S17): carry the exporter's stable local date, not a recompute.
                    localDate = day.date
                )
            }
        }
        return habitOccurrences to foodMedOccurrences
    }

    /**
     * v0.5.1 §N / v0.5.3 Phase 3 (S15 + S17): **per-log merge** of one month's day-logs into Room.
     * The counterpart to [importAllData], which cannot be reused — that is a full replace.
     *
     * v0.5.3 Phase 3 (S15). The old form cleared `[startOfMonth, now)` wholesale and re-inserted
     * from [days]. That was *worse* than field-level last-writer-wins: the loser of an offline race
     * lost **every** edit they made that month ("two devices, same month, different reminders").
     * The merge, at `(itemId, scheduledTime)` occurrence-id granularity, is:
     *  - **upsert** every incoming row (REPLACE on the `itemId:millis` PK — the cloud is the source
     *    for everything it names);
     *  - **delete** a local *past* PENDING row the cloud no longer names (a stale window slot);
     *  - **keep** a local *past* resolved row the cloud no longer names — that is this device's
     *    offline edit the other device has not seen yet. A re-export/re-push then reconciles the
     *    cloud with the merged result in one round (Phase 1 §1.3 S4 re-arm).
     *
     * Future PENDING slots (`scheduled_for >= now`) are never in scope: the exporter never emits
     * one, so the cloud month cannot contain one, and the merge only ever looks at rows before
     * `now` — a currently-armed alarm is untouched.
     *
     * v0.5.3 Phase 3 (S17). In addition to the merge's PENDING clear, a
     * `deletePendingByLocalMonthBefore` keyed off the STORED `local_date` catches a past PENDING
     * row whose `scheduled_for` (recomputed in a changed zone) would fall outside this month's
     * epoch range — without touching a cloud-named id or a currently-armed future slot.
     *
     * `habits` / `food_med_tasks` are left **untouched** (definitions come from the parent doc);
     * `habit_events` / `food_med_events` are device-local history the v2 format does not carry.
     */
    suspend fun importMonth(monthKey: String, days: List<DayEntry>): ImportResult {
        val range = com.daybook.app.data.sync.MonthPartitioner.epochRangeOf(monthKey)
            ?: return ImportResult(success = false, message = "Bad month key: $monthKey")
        return try {
            val habitIds = database.habitDao().observeAllHabits().first().mapTo(HashSet()) { it.id }
            val taskIds = database.foodMedTaskDao().observeAllTasks().first().mapTo(HashSet()) { it.id }
            val (start, end) = range
            val clearUntil = minOf(end, System.currentTimeMillis())

            // v0.5.3 Phase 1 (S14): read the month's live occurrence rows ONCE, before the
            // transaction — the merge needs the resolved rows too (to decide keep-vs-delete), and
            // S14 needs them to (a) carry each row's notification_id through the re-insert and
            // (b) spare a still-PENDING row that already has a SHOWN event.
            val liveHabitOccs =
                database.habitOccurrenceDao().getAllOccurrencesInTimeRange(start, end).first()
            val liveTaskOccs =
                database.foodMedOccurrenceDao().getAllOccurrencesInTimeRange(start, end).first()
            val existingHabitNotif = liveHabitOccs.associate { it.id to it.notificationId }
            val existingTaskNotif = liveTaskOccs.associate { it.id to it.notificationId }
            val keepIfShownPending = buildSet {
                liveHabitOccs.forEach {
                    if (shouldSkipReinsert(it.status, database.habitEventDao().hasShownEvent(it.id))) {
                        add(it.id)
                    }
                }
                liveTaskOccs.forEach {
                    if (shouldSkipReinsert(it.status, database.foodMedEventDao().hasShownEvent(it.id))) {
                        add(it.id)
                    }
                }
            }

            val (habitOccurrences, foodMedOccurrences) = mapDaysToOccurrences(
                days, habitIds, taskIds, existingHabitNotif, existingTaskNotif, keepIfShownPending
            )
            val incomingHabitIds = habitOccurrences.mapTo(HashSet()) { it.id }
            val incomingTaskIds = foodMedOccurrences.mapTo(HashSet()) { it.id }

            // v0.5.3 Phase 3 (S15): the merge decision, over PAST rows only (a future PENDING slot
            // is never deleted or resurrected). [mergeMonth] is pure — see [MonthMergeTest].
            fun key(id: String, status: Occurrence.Status) = OccKey(id, status != Occurrence.Status.PENDING)
            val habitMerge = mergeMonth(
                liveHabitOccs.filter { it.scheduledFor < clearUntil }.map { key(it.id, it.status) },
                habitOccurrences.map { key(it.id, it.status) }
            )
            val taskMerge = mergeMonth(
                liveTaskOccs.filter { it.scheduledFor < clearUntil }.map { key(it.id, it.status) },
                foodMedOccurrences.map { key(it.id, it.status) }
            )
            val habitDelete = (habitMerge.deletePending - keepIfShownPending).toList()
            val taskDelete = (taskMerge.deletePending - keepIfShownPending).toList()
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 6 (S-3): kept as Sets now — they're used for a
            // Kotlin-side `NOT IN` filter below (see deletePendingByLocalMonthBefore's KDoc for why
            // this can't just be a chunked NOT-IN query), not passed to SQL as a bound-variable list.
            val habitKeepSet = incomingHabitIds + keepIfShownPending
            val taskKeepSet = incomingTaskIds + keepIfShownPending

            database.withTransaction {
                // S-3: a power user's heavy month can produce a delete/keep list past SQLite's
                // 999-bound-variable limit (minSdk 26 devices can ship SQLite < 3.32, which enforces
                // it). `deleteByIds`' `IN (:ids)` chunks safely — deleting id-in-chunk1 OR
                // id-in-chunk2 OR ... is exactly deleting id-in-(chunk1+chunk2+...).
                habitDelete.chunked(SQLITE_MAX_VARS).forEach {
                    if (it.isNotEmpty()) database.habitOccurrenceDao().deleteByIds(it)
                }
                taskDelete.chunked(SQLITE_MAX_VARS).forEach {
                    if (it.isNotEmpty()) database.foodMedOccurrenceDao().deleteByIds(it)
                }
                // v0.5.3 Phase 3 (S17): local_date-keyed clear for zone-drifted boundary rows.
                // S-3: fetch candidates with no `keep` bound at all, filter against the full keep
                // Set in Kotlin (no SQL variable-count limit on a Set lookup), then chunk-delete.
                if (clearUntil > start) {
                    val habitCandidates = database.habitOccurrenceDao()
                        .pendingIdsByLocalMonthBefore(monthKey, clearUntil)
                    (habitCandidates - habitKeepSet).chunked(SQLITE_MAX_VARS).forEach {
                        if (it.isNotEmpty()) database.habitOccurrenceDao().deleteByIds(it)
                    }
                    val taskCandidates = database.foodMedOccurrenceDao()
                        .pendingIdsByLocalMonthBefore(monthKey, clearUntil)
                    (taskCandidates - taskKeepSet).chunked(SQLITE_MAX_VARS).forEach {
                        if (it.isNotEmpty()) database.foodMedOccurrenceDao().deleteByIds(it)
                    }
                }
                if (habitOccurrences.isNotEmpty()) {
                    database.habitOccurrenceDao().insertAll(*habitOccurrences.toTypedArray())
                }
                if (foodMedOccurrences.isNotEmpty()) {
                    database.foodMedOccurrenceDao().insertAll(*foodMedOccurrences.toTypedArray())
                }

                // BUG_AUDIT_REPORT.md §1.1 fix: this used to be full delete-then-insert over the
                // month's local_date range, which silently destroyed any local (possibly unpushed)
                // workout session the incoming payload didn't name — e.g. a session finished on
                // this device seconds before a remote month from another device lands. Give
                // sessions the same keep-local-if-not-named rule occurrences already have: only
                // clear (and replace) a session id the incoming payload itself carries.
                // insertSessions uses OnConflictStrategy.REPLACE, so a session named by BOTH sides
                // is overwritten by the incoming row without needing a delete first; the pre-clear
                // here only exists to drop that session's now-stale blocks/sets. A session that
                // exists solely on this device — including one still ACTIVE — is never touched.
                val (monthStartYmd, monthEndYmd) = monthLocalDateRange(monthKey)
                val (incomingSessions, incomingExercises, incomingSets) = mapDaysToWorkouts(days)
                val incomingSessionIds = incomingSessions.mapTo(HashSet()) { it.id }
                val staleSessionIds = database.workoutDao()
                    .getSessionsInLocalDateRange(monthStartYmd, monthEndYmd)
                    .map { it.id }
                    .filter { it in incomingSessionIds }
                staleSessionIds.chunked(SQLITE_MAX_VARS).forEach {
                    if (it.isNotEmpty()) {
                        database.workoutDao().deleteSetsForSessions(it)
                        database.workoutDao().deleteExercisesForSessions(it)
                    }
                }
                if (incomingSessions.isNotEmpty()) database.workoutDao().insertSessions(incomingSessions)
                if (incomingExercises.isNotEmpty()) database.workoutDao().insertExercises(incomingExercises)
                if (incomingSets.isNotEmpty()) database.workoutDao().insertSets(incomingSets)

                // B5 (§7.5.0) — health merges the same delete-then-insert-over-this-month's-range
                // way workouts do: a day's aggregate is simply present or not, nothing to diff.
                val (incomingHealthDays, incomingHealthSessions, incomingHealthWeightReadings) = mapDaysToHealth(days)
                database.healthDao().deleteSessionsInLocalDateRange(monthStartYmd, monthEndYmd)
                database.healthDao().deleteDaysInLocalDateRange(monthStartYmd, monthEndYmd)
                database.healthDao().deleteWeightReadingsBetween(monthStartYmd, monthEndYmd)
                if (incomingHealthDays.isNotEmpty()) database.healthDao().upsertDays(incomingHealthDays)
                if (incomingHealthSessions.isNotEmpty()) database.healthDao().upsertSessions(incomingHealthSessions)
                if (incomingHealthWeightReadings.isNotEmpty()) database.healthDao().upsertWeightReadings(incomingHealthWeightReadings)

                // DAILY_REPORT_PLAN.md §3.6 — the AI summary merges the same delete-then-insert-
                // over-this-month's-range way health/workouts do: at most one row per day, nothing
                // to diff.
                val incomingAiSummaries = mapDaysToAiSummary(days)
                database.dailyReportAiSummaryDao().deleteInLocalDateRange(monthStartYmd, monthEndYmd)
                if (incomingAiSummaries.isNotEmpty()) database.dailyReportAiSummaryDao().upsertAll(incomingAiSummaries)

                // Hydration habit — same delete-then-insert over this month's range.
                val incomingHydration = mapDaysToHydration(days)
                database.hydrationDao().deleteInLocalDateRange(monthStartYmd, monthEndYmd)
                if (incomingHydration.isNotEmpty()) database.hydrationDao().upsertAll(incomingHydration)
            }
            ImportResult(success = true, message = "$monthKey: ${days.size} days")
        } catch (e: Exception) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-7).
            ImportResult(success = false, message = friendlyImportError(e, "Could not merge $monthKey"))
        }
    }

    /**
     * v0.5.3 Phase 1 (S6): apply the parent doc's definitions **without** going through
     * [importAllData]. `importAllData` is a full replace — it `deleteAll()`s both event tables and
     * every future PENDING occurrence, and re-issues every `notification_id`. A cross-device rename
     * must not cost the user their history or churn every armed alarm.
     *
     * In ONE transaction:
     *  - upsert habits / tasks (REPLACE), preserving each existing row's `notification_id`;
     *  - replace the small `custom_categories` / `custom_prompts` tables;
     *  - delete only the habit / task rows whose id is absent from [defs];
     *  - **never touch** `habit_events`, `food_med_events`, `habit_occurrences`,
     *    `food_med_occurrences`.
     */
    suspend fun applyRemoteDefinitions(defs: Definitions): ImportResult {
        if (defs.habits.isEmpty() && defs.intakeReminders.isEmpty()) {
            return ImportResult(success = false, message = UNSUPPORTED)
        }
        return try {
            val existingHabitNotif = database.habitDao().observeAllHabits().first()
                .associate { it.id to it.notificationId }
            val existingTaskNotif = database.foodMedTaskDao().observeAllTasks().first()
                .associate { it.id to it.notificationId }

            val habits = defs.habits.map { d ->
                Habit(
                    id = d.id,
                    title = d.name,
                    colorTag = ColorTag.fromNameOrAuto(d.colorTag),
                    iconKey = d.iconKey,
                    createdAt = jsonUtils.fromIso(d.createdAt) ?: System.currentTimeMillis(),
                    timesJson = joinTimes(d.times),
                    activeDaysJson = joinDays(d.activeDays),
                    isArchived = d.archived,
                    snoozeIntervalMinutes = d.snoozeMinutes,
                    notificationId = existingHabitNotif[d.id] ?: notificationIds.next(),
                    type = runCatching { HabitType.valueOf(d.type) }.getOrDefault(HabitType.INDIVIDUAL),
                    streakStartedAt = d.streakStartedAt,
                    streakLongest = d.streakLongest,
                    promptMessage = d.promptMessage?.trim()?.takeIf { it.isNotBlank() },
                    motivation = d.motivation?.trim()?.takeIf { it.isNotBlank() },
                    journalQuestionsJson = DateTimeUtils.journalQuestionsToJson(d.journalQuestions)
                )
            }
            val tasks = defs.intakeReminders.map { d ->
                FoodMedTask(
                    id = d.id,
                    label = d.name,
                    // Journal-as-habit round (B3): see identical remap in importAllData above.
                    type = runCatching { TaskType.valueOf(d.type) }.getOrDefault(TaskType.FOOD)
                        .let { remapLegacyJournalTaskType(it) },
                    colorTag = ColorTag.fromNameOrAuto(d.colorTag),
                    iconKey = d.iconKey,
                    createdAt = jsonUtils.fromIso(d.createdAt) ?: System.currentTimeMillis(),
                    timesJson = joinTimes(d.times),
                    activeDaysJson = joinDays(d.activeDays),
                    isArchived = d.archived,
                    snoozeIntervalMinutes = d.snoozeMinutes,
                    notificationId = existingTaskNotif[d.id] ?: notificationIds.next(),
                    customCategory = d.customCategory?.trim()?.takeIf { it.isNotBlank() },
                    promptMessage = d.promptMessage?.trim()?.takeIf { it.isNotBlank() },
                    motivation = d.motivation?.trim()?.takeIf { it.isNotBlank() },
                    defaultRedFlag = RedFlag.fromNameOrNull(d.defaultRedFlag),
                    defaultSuspectedFood = d.defaultSuspectedFood?.trim()?.takeIf { it.isNotBlank() },
                    defaultOutsideFood = d.defaultOutsideFood?.takeIf { it }
                )
            }
            val remoteHabitIds = habits.mapTo(HashSet()) { it.id }
            val remoteTaskIds = tasks.mapTo(HashSet()) { it.id }
            val categories = defs.customCategories.map { it.trim() }
                .filter { it.isNotBlank() }.distinct().map { CustomCategory(name = it) }
            val prompts = defs.customPrompts.map { it.trim() }
                .filter { it.isNotBlank() }.distinct().map { CustomPrompt(name = it) }
            // A4 (§4.4 item 5): custom exercises + routines are definitions too.
            val remoteExercises = defs.customExercises.map { it.toEntity() }
            val remoteExerciseIds = remoteExercises.mapTo(HashSet()) { it.id }
            val remoteRoutines = defs.routines.map { it.toRoutineEntity() }
            val remoteRoutineIds = remoteRoutines.mapTo(HashSet()) { it.id }
            val remoteRoutineExercises = defs.routines.flatMap { it.toChildEntities() }

            database.withTransaction {
                if (habits.isNotEmpty()) database.habitDao().insertAll(*habits.toTypedArray())
                if (tasks.isNotEmpty()) database.foodMedTaskDao().insertAll(*tasks.toTypedArray())
                database.customCategoryDao().deleteAll()
                if (categories.isNotEmpty()) {
                    database.customCategoryDao().insertAll(*categories.toTypedArray())
                }
                database.customPromptDao().deleteAll()
                if (prompts.isNotEmpty()) {
                    database.customPromptDao().insertAll(*prompts.toTypedArray())
                }
                val (_, staleHabits) = defsDelta(database.habitDao().allIds().toSet(), remoteHabitIds)
                if (staleHabits.isNotEmpty()) database.habitDao().deleteByIds(staleHabits.toList())
                val (_, staleTasks) = defsDelta(database.foodMedTaskDao().allIds().toSet(), remoteTaskIds)
                if (staleTasks.isNotEmpty()) database.foodMedTaskDao().deleteByIds(staleTasks.toList())

                // A4: upsert incoming custom exercises, delete local ones absent from the remote set.
                if (remoteExercises.isNotEmpty()) database.exerciseDao().insertAll(remoteExercises)
                val (_, staleExercises) = defsDelta(database.exerciseDao().allIds().toSet(), remoteExerciseIds)
                staleExercises.chunked(SQLITE_MAX_VARS).forEach {
                    if (it.isNotEmpty()) database.exerciseDao().deleteByIds(it)
                }

                // A4: a routine's child rows are replaced wholesale with its parent — delete by
                // routine_id, then insert — the same rule `updateRoutine` uses (§4.4 item 5).
                if (remoteRoutines.isNotEmpty()) database.routineDao().upsertRoutines(remoteRoutines)
                remoteRoutines.forEach { database.routineDao().deleteRoutineExercises(it.id) }
                if (remoteRoutineExercises.isNotEmpty()) {
                    database.routineDao().upsertRoutineExercises(remoteRoutineExercises)
                }
                val (_, staleRoutines) = defsDelta(database.routineDao().allIds().toSet(), remoteRoutineIds)
                if (staleRoutines.isNotEmpty()) {
                    staleRoutines.forEach { database.routineDao().deleteRoutineExercises(it) }
                    database.routineDao().deleteByIds(staleRoutines.toList())
                }
            }
            ImportResult(
                success = true,
                message = "${habits.size} habits, ${tasks.size} intake (definitions)"
            )
        } catch (e: Exception) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-7).
            ImportResult(success = false, message = friendlyImportError(e, "Invalid definitions"))
        }
    }

    /**
     * v0.5.1 §N eviction: drop one month's occurrence rows without touching definitions. Only ever
     * called for a month whose per-month hash already matches the cloud's — never for unpushed
     * local data. See `CloudSyncRepository.evictStaleMonths`.
     */
    suspend fun evictMonth(monthKey: String): Boolean {
        val (start, end) = com.daybook.app.data.sync.MonthPartitioner.epochRangeOf(monthKey)
            ?: return false
        return runCatching {
            database.withTransaction {
                // v0.5.3 Phase 3 (A3): prune the month's events BEFORE its occurrences — the
                // subquery in [deleteForLocalMonth] resolves against the occurrence rows. Events
                // otherwise outlive their evicted month forever and grow unbounded.
                database.habitEventDao().deleteForLocalMonth(monthKey)
                database.foodMedEventDao().deleteForLocalMonth(monthKey)
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 14 (S-12, Low): the local_date-keyed
                // deletes above can never catch a pre-MIGRATION_12_13 occurrence with a NULL
                // local_date — this scheduled_for-range fallback does, for the same [start, end)
                // this call is evicting. Must also run before the occurrence deletes below.
                database.habitEventDao().deleteForNullLocalDateInRange(start, end)
                database.foodMedEventDao().deleteForNullLocalDateInRange(start, end)
                database.habitOccurrenceDao().deleteInRange(start, end)
                database.foodMedOccurrenceDao().deleteInRange(start, end)

                // A4 (§4.4 item 6 — "the easiest one to get wrong"): drop this month's workout
                // rows too, sets -> blocks -> sessions, chunked, by local_date. Must NOT touch
                // workout_routines / workout_routine_exercises — those are definitions, not month
                // data; evicting them would delete the user's templates every time an old month
                // was tidied up.
                val (monthStartYmd, monthEndYmd) = monthLocalDateRange(monthKey)
                val staleSessionIds = database.workoutDao()
                    .getSessionsInLocalDateRange(monthStartYmd, monthEndYmd).map { it.id }
                staleSessionIds.chunked(SQLITE_MAX_VARS).forEach {
                    if (it.isNotEmpty()) {
                        database.workoutDao().deleteSetsForSessions(it)
                        database.workoutDao().deleteExercisesForSessions(it)
                    }
                }
                database.workoutDao().deleteSessionsInLocalDateRange(monthStartYmd, monthEndYmd)

                // B5 (§7.5.0/R2) — the same trap §4.4 item 6 already names for workouts: forgetting
                // this here means an evicted month's health rows re-push forever.
                database.healthDao().deleteSessionsInLocalDateRange(monthStartYmd, monthEndYmd)
                database.healthDao().deleteDaysInLocalDateRange(monthStartYmd, monthEndYmd)
                // HEALTH_VITALS_RICHNESS_PLAN.md §5 — the exact same trap, named again on purpose.
                database.healthDao().deleteWeightReadingsBetween(monthStartYmd, monthEndYmd)

                // DAILY_REPORT_PLAN.md §3.6/R2 — the exact same trap, named again: forgetting this
                // here means an evicted month's cached AI summary re-pushes forever.
                database.dailyReportAiSummaryDao().deleteInLocalDateRange(monthStartYmd, monthEndYmd)
                // Hydration habit — the same trap again.
                database.hydrationDao().deleteInLocalDateRange(monthStartYmd, monthEndYmd)
            }
        }.isSuccess
    }

    private fun joinTimes(times: List<String>): String =
        times.mapNotNull { runCatching { LocalTime.parse(it.trim(), HHMM) }.getOrNull() }
            .sorted()
            .joinToString(",") { it.format(HHMM) }

    /** 1=Mon … 7=Sun back to the internal weekday enum names. */
    private fun joinDays(days: List<Int>): String =
        days.filter { it in 1..7 }.distinct().sorted()
            .joinToString(",") { DayOfWeek.entries[it - 1].name }

    private fun parseDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim(), YMD) }.getOrNull()

    /**
     * v0.5.3 Phase 3 (S17) — residual, documented. The reconstructed occurrence PK is still
     * `"$itemId:$millis"` where `millis` is recomputed here in the **current** zone. Restore a
     * backup made in zone A onto a device in zone B and a boundary row's PK shifts by the offset.
     * The **sync** path masks this — the Phase 3 [importMonth] merge and `deletePendingByLocalMonth*`
     * match / clear by the stored `local_date`, not the PK. The **file** import path
     * ([importAllData]) still recomputes; making the PK zone-stable needs a v2-wire-model
     * `scheduledForMillis` field — deferred to Phase 6 (low likelihood, merge covers the common case).
     */
    private fun epochOf(date: LocalDate, hhmm: String): Long? =
        epochOfLocal(date, hhmm, ZoneId.systemDefault())

    data class ImportResult(
        val success: Boolean,
        val message: String? = null,
        /**
         * v0.5.3 Phase 6 (S5): the `"yyyy-MM"` months this import actually covers. The caller
         * (`SettingsViewModel` → `CloudSyncRepository.onLocalDataReplaced`) uses it to pin the sync
         * bookkeeping so the next push cannot delete a cloud month that was outside the file.
         */
        val coveredMonths: Set<String> = emptySet()
    )
}

// ------------------------------------------------------------------ v0.5.3 Phase 1 pure helpers

/**
 * v0.5.3 Phase 1 (S6): given the definition ids resident locally and the ids present in the remote
 * definitions, return `(toUpsert, toDelete)`. A rename (same id, changed name) is in `toUpsert`
 * and never in `toDelete`; a brand-new remote id is upsert-only; a removed remote id is
 * delete-only. Pure — [DefinitionsUpsertTest] needs no Room.
 */
internal fun defsDelta(
    localIds: Set<String>,
    remoteIds: Set<String>
): Pair<Set<String>, Set<String>> = remoteIds to (localIds - remoteIds)

// ------------------------------------------------------------------ Journal-as-habit round (B3)

/**
 * B3 guard: a `TaskType` decoded from an old (pre-this-round) backup or cloud parent doc. The enum
 * value `JOURNAL` decodes losslessly (kept dead-but-present specifically for this), but nothing can
 * render/schedule/edit a JOURNAL-typed `FoodMedTask` correctly anymore after this round — so a
 * freshly-imported legacy JOURNAL task is remapped to CUSTOM rather than silently reintroducing the
 * retired UI. Every other type passes through unchanged. Pure — [RemapLegacyJournalTaskTypeTest].
 */
internal fun remapLegacyJournalTaskType(type: TaskType): TaskType =
    if (type == TaskType.JOURNAL) TaskType.CUSTOM else type

// ------------------------------------------------------------------ v0.5.4 Phase 2 pure helpers (D3)

/**
 * v0.5.4 Phase 2 (D3): the `qa_json` blob codec — a JSON array `[{"q":"…","a":"…"}, …]` in ask
 * order. Tolerant decode: null / blank / garbage -> empty list; unknown keys ignored; a pair with
 * an empty answer survives the round trip. Pure — [JournalQaCodecTest].
 */
internal object JournalQa {

    @kotlinx.serialization.Serializable
    private data class QaPairDto(val q: String = "", val a: String = "")

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val listSerializer =
        kotlinx.serialization.builtins.ListSerializer(QaPairDto.serializer())

    fun encode(pairs: List<Pair<String, String>>): String =
        json.encodeToString(listSerializer, pairs.map { QaPairDto(it.first, it.second) })

    fun decode(jsonStr: String?): List<Pair<String, String>> {
        val raw = jsonStr?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            json.decodeFromString(listSerializer, raw).map { it.q to it.a }
        }.getOrDefault(emptyList())
    }
}

/**
 * v0.5.3 Phase 1 (S14): reuse an occurrence's existing `notification_id` when the row is still
 * live, otherwise mint a fresh one. Pure — [ImportMonthNotifPreserveTest].
 */
internal fun resolveNotifId(id: String, existing: Map<String, Int>, next: () -> Int): Int =
    existing[id] ?: next()

/**
 * v0.5.3 Phase 1 (S14): a month re-import must skip re-inserting an occurrence exactly when it is
 * still `PENDING` locally AND already has a `SHOWN` event — its posted notification / refire chain
 * are keyed to the live row. Pure — [ImportMonthNotifPreserveTest].
 */
internal fun shouldSkipReinsert(status: Occurrence.Status, hasShown: Boolean): Boolean =
    status == Occurrence.Status.PENDING && hasShown

/**
 * v0.5.3 Phase 2 (S17): the local calendar date ("yyyy-MM-dd") a stored occurrence belongs to.
 * Prefers the persisted [localDate] (written at insert time — timezone-stable, so history does not
 * re-bucket when the device zone changes); falls back to recomputing from [scheduledFor] in [zone]
 * for rows created before MIGRATION_12_13 or not-yet-migrated in-memory rows. Pure —
 * [ExportLocalDateTest].
 */
internal fun exportDateFor(localDate: String?, scheduledFor: Long, zone: java.time.ZoneId): String =
    localDate?.takeIf { it.isNotBlank() }
        ?: java.time.Instant.ofEpochMilli(scheduledFor).atZone(zone).toLocalDate().toString()

// ------------------------------------------------------------------ v0.5.3 Phase 3 pure helpers

/**
 * v0.5.3 Phase 3 (S17): epoch millis for a local date + "HH:mm" in an explicit [zone]. Extracted
 * from the private `epochOf` so [EpochRoundTripTest] can show that a boundary time (00:30) maps to
 * *different* millis in zone A vs zone B — i.e. a backup restored across zones shifts a row's PK by
 * the offset, and the `local_date`-keyed clears (not the PK) are the sync-path mitigation.
 */
internal fun epochOfLocal(date: java.time.LocalDate, hhmm: String, zone: java.time.ZoneId): Long? =
    runCatching {
        date.atTime(java.time.LocalTime.parse(hhmm.trim()))
            .atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

/** v0.5.3 Phase 3 (S15): one occurrence's identity for the per-log month merge — the PK and
 *  whether it has reached a terminal (non-PENDING) state. */
internal data class OccKey(val id: String, val resolved: Boolean)

/**
 * v0.5.3 Phase 3 (S15): the outcome of merging one month.
 *  - [upsert]       — every incoming id (REPLACE on PK; the cloud is the source for what it names);
 *  - [deletePending] — local ids that are still PENDING and absent from incoming (stale window slots);
 *  - [keepLocal]    — local ids that are resolved and absent from incoming (this device's unseen
 *                     offline edits — must survive the merge).
 */
internal data class MergeResult(
    val upsert: List<String>,
    val deletePending: List<String>,
    val keepLocal: List<String>
)

/**
 * v0.5.3 Phase 3 (S15): pure per-log month merge. Union by PK; a local resolved row the cloud does
 * not name is kept; a local PENDING row the cloud does not name is dropped; a local PENDING row the
 * cloud *does* name is upserted (not dropped). Covers "A logs breakfast, B logs lunch": A's
 * breakfast (local, resolved, not in B's incoming) lands in [MergeResult.keepLocal] and survives.
 * Pure — [MonthMergeTest].
 */
internal fun mergeMonth(local: List<OccKey>, incoming: List<OccKey>): MergeResult {
    val incomingIds = incoming.mapTo(HashSet()) { it.id }
    return MergeResult(
        upsert = incoming.map { it.id },
        deletePending = local.filter { !it.resolved && it.id !in incomingIds }.map { it.id },
        keepLocal = local.filter { it.resolved && it.id !in incomingIds }.map { it.id }
    )
}

// ------------------------------------------------------------------ v0.5.3 Phase 6 pure helpers (D2/S5)

/**
 * v0.5.3 Phase 6 (D2): the `days` that fall inside `[start, end]` **inclusive**, sorted ascending.
 * A day whose `date` is unparseable is dropped. Pure — [com.daybook.app.data.ExportRangeTest].
 */
internal fun daysInRange(
    days: List<DayEntry>,
    start: java.time.LocalDate,
    end: java.time.LocalDate
): List<DayEntry> = days.filter { d ->
    val parsed = runCatching { java.time.LocalDate.parse(d.date.trim()) }.getOrNull()
    parsed != null && !parsed.isBefore(start) && !parsed.isAfter(end)
}.sortedBy { it.date }

/**
 * v0.5.3 Phase 6 (D2): true when a file is range-scoped and must take the non-destructive
 * [ExportImportRepository.importRange] path rather than the whole-replace [ExportImportRepository.importAllData].
 * A full export leaves both range fields null. Pure — [com.daybook.app.data.RangeImportNonDestructiveTest].
 */
internal fun isRangeScoped(meta: BackupMeta): Boolean =
    meta.rangeStart != null || meta.rangeEnd != null

/**
 * v0.5.3 Phase 6 (S5): the `"yyyy-MM"` month keys a set of `days` covers. Pure —
 * [com.daybook.app.data.RangeImportNonDestructiveTest].
 */
internal fun coveredMonths(days: List<DayEntry>): Set<String> =
    days.mapNotNull { com.daybook.app.data.sync.MonthPartitioner.monthKeyOf(it.date) }.toSet()

/**
 * v0.5.3 Phase 6 (S5): the month set that stays "resident" after an import — exactly the covered
 * months plus the always-hydrated recent months. `CloudSyncRepository.onLocalDataReplaced` pins
 * `hydratedMonths` to this, so `MonthPartitioner.changedMonths` can never see an out-of-range cloud
 * month as "emptied locally". Pure — [com.daybook.app.data.RangeImportNonDestructiveTest].
 */
internal fun residentAfterImport(covered: Set<String>, recent: Set<String>): Set<String> =
    covered + recent

/**
 * v0.5.3 Phase 6 (S5c): a diff-driven push may only carry a month deletion (a `null` value — the
 * month emptied locally) when the write is explicitly user-initiated. Otherwise an evicted or
 * range-trimmed month looks identical to an emptied one and the cloud doc must be kept. Pure —
 * [com.daybook.app.data.RangeImportNonDestructiveTest].
 */
internal fun pushDeletesAllowed(
    changed: Map<String, List<DayEntry>?>,
    userInitiated: Boolean
): Boolean = userInitiated || changed.values.none { it == null }

// ------------------------------------------------------------------ A4 pure helpers

/**
 * A4 (§4.4 items 4 and 6) — the `local_date` bounds of a `"yyyy-MM"` month key, as plain strings.
 * `-31` as the upper bound is safe (not just for 31-day months): `local_date` values are always
 * real calendar dates, and fixed-width ISO date strings compare lexicographically exactly like
 * they compare chronologically, so `BETWEEN "$monthKey-01" AND "$monthKey-31"` catches every real
 * day of the month regardless of how many days it actually has. Pure —
 * [com.daybook.app.data.RangeImportNonDestructiveTest].
 */
internal fun monthLocalDateRange(monthKey: String): Pair<String, String> = "$monthKey-01" to "$monthKey-31"
