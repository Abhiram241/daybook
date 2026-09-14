package com.daybook.app.data

import com.daybook.app.data.health.HealthCardKind
import com.daybook.app.data.health.visibleHealthCards
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.DailyReportAiSummary
import com.daybook.app.data.model.FoodMedOccurrence
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.HealthDay
import com.daybook.app.data.model.HealthSession
import com.daybook.app.data.model.Occurrence
import com.daybook.app.data.model.RedFlag
import com.daybook.app.data.model.TaskType
import com.daybook.app.data.model.HabitType
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.formatWeight
import com.daybook.app.data.workout.parseWeightUnit
import com.daybook.app.data.workout.sessionStats
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.util.streak.StreakMode
import com.daybook.app.util.streak.parseRestDays
import com.daybook.app.util.streak.streaksFromScheduledStatuses
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import com.daybook.app.util.recordUnhandledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * DAILY_REPORT_PLAN.md §4 — a thin, READ-ONLY, cross-cutting repository for the Daily Report
 * screen. It does not add a table of its own beyond §3.6's AI summary cache (owned here the same
 * way `HealthRepository` owns `health_days`); it reads the other four repositories'/DAOs' existing
 * queries and `combine`s them into one Flow, the same pattern `HealthTabViewModel.kt:139-167`
 * already established for combining several flows into one `uiState` — kept here instead of in a
 * ViewModel so the combine logic is in one tested place, not duplicated.
 */

/** R-DR1 (§1.5) — each section renders only if it has data; the screen shows one `EmptyState`
 *  only when every section is empty. */
data class DailyReportData(
    val date: LocalDate,
    val workout: WorkoutSectionData?,
    val health: HealthSectionData?,
    val intake: List<IntakeEntryRow>,
    val todo: List<TodoEntryRow>,
    val aiSummary: DailyReportAiSummary?,
    val weightUnit: WeightUnit
) {
    val isEmpty: Boolean
        get() = workout == null && health == null && intake.isEmpty() && todo.isEmpty()
}

data class WorkoutSectionData(val sessions: List<WorkoutSessionSummary>)

data class WorkoutSessionSummary(
    val title: String,
    val durationMinutes: Int?,
    val totalVolumeKg: Float,
    val setCount: Int,
    val exercises: List<WorkoutExerciseSummary>
)

data class WorkoutExerciseSummary(val name: String, val setCount: Int, val bestSetLabel: String?)

data class HealthSectionData(
    val day: HealthDay?,
    val visibleCards: Set<HealthCardKind>,
    val sessions: List<HealthSession>,
    /** User request — every sleep touching this date ("13–14 Sep", "14–15 Sep"), morning first. */
    val sleepEntries: List<com.daybook.app.data.health.SleepEntry> = emptyList()
)

/**
 * Round 2 (Feature 2) — a collapsed one-line row by default (time + label + flag badge); tapping
 * it expands in place to the full logged content carried in the extra fields below (responseText,
 * description, qaJson pairs, redFlag, suspectedFood, outsideFood) — same shape for FOOD and MED.
 */
data class IntakeEntryRow(
    val id: String,
    /** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.4 — the owning reminder's id, so
     *  [AiExclusionSet.hidesIntake] can hide by whole-task as well as by entry. */
    val taskId: String,
    val timeLabel: String,
    val label: String,
    val statusLabel: String,
    val flagLabel: String?,
    val outsideFood: Boolean,
    val scheduledFor: Long,
    val taskType: TaskType,
    val responseText: String,
    val description: String?,
    val suspectedFood: String?,
    val qaPairs: List<Pair<String, String>>
)

/**
 * Round 2 (Feature 3) — one compact row per habit occurrence. [habitType] drives the glyph:
 * INDIVIDUAL/BATCH/JOURNAL show [done] as a check/x; STREAK shows [streakDays] instead (the
 * current streak AS OF the report's selected date, not live-today). [qaPairs] lets a JOURNAL row
 * expand-on-tap the same way Feature 2's Intake rows do.
 */
data class TodoEntryRow(
    val id: String,
    /** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.4 — the owning habit's id, so
     *  [AiExclusionSet.hidesTodo] can hide by whole-habit as well as by entry. */
    val habitId: String,
    val timeLabel: String,
    val label: String,
    val statusLabel: String,
    val scheduledFor: Long,
    val habitType: HabitType,
    val done: Boolean,
    val qaPairs: List<Pair<String, String>>,
    /** Non-null only for [HabitType.STREAK] rows — the streak length in days as of the selected
     *  report date. */
    val streakDays: Int? = null
)

@Singleton
class DailyReportRepository @Inject constructor(
    private val database: AppDatabase,
    private val workoutRepository: WorkoutRepository,
    private val appSettingsRepository: AppSettingsRepository
) {

    private val ymd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** §3.5 step 3 — upserts the cached summary for one day. Regenerating replaces the row for
     *  that date (one summary per day, never a history of regenerations). */
    suspend fun saveAiSummary(summary: DailyReportAiSummary) {
        database.dailyReportAiSummaryDao().upsert(summary)
    }

    /** DAILY_REPORT_REDESIGN_PLAN.md §7.2 — chat's configurable multi-day context. [startDate]..
     *  [endDate] inclusive. Passing the same date for both (the default/fallback case) returns
     *  exactly the one-day list the current single-day openChat() already builds from. Read-only,
     *  additive — does not touch [observeReport]. A one-shot suspend read per day (not a live
     *  multi-day Flow): chat context is captured once at "open chat" / "send message" time, same
     *  as today's single-day openChat() already does with a snapshot of `state.report`. */
    /** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.4 — reads one scope's exclusion rows and
     *  turns them into the pure, in-memory [AiExclusionSet] filter. */
    suspend fun getAiExclusions(scope: AiScope): AiExclusionSet =
        database.aiExclusionDao().get(scope.name).toSet()

    /** Reactive counterpart of [getAiExclusions] — for the settings screen's live "N items
     *  hidden" subtitles and this screen's fingerprint/caption, both of which must update the
     *  moment an exclusion is toggled without needing the screen reopened. */
    fun observeAiExclusions(scope: AiScope): Flow<AiExclusionSet> =
        database.aiExclusionDao().observe(scope.name).map { it.toSet() }

    suspend fun buildChatContext(startDate: LocalDate, endDate: LocalDate): List<DailyReportData> {
        val days = generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .toList()
        return days.map { d -> observeReport(d).first() }
    }

    fun observeReport(date: LocalDate): Flow<DailyReportData> {
        val dateStr = date.format(ymd)
        val start = DateTimeUtils.startOfDay(date)
        val end = DateTimeUtils.endOfDay(date)

        // C1 — each section is wrapped in its own `.catch{}` (HomeViewModel's established
        // pattern) so one section's failure degrades to that section being absent (R-DR1)
        // instead of crashing the whole combine chain / the entire Daily Report tab.
        val workoutFlow: Flow<WorkoutSectionData?> = database.workoutDao()
            .observeSessionsForLocalDate(dateStr)
            .flatMapLatest { sessions -> flow { emit(buildWorkoutSection(sessions)) } }
            .flowOn(Dispatchers.Default)
            .catch { recordUnhandledException(it); emit(null) }

        val healthDayFlow = database.healthDao().observeDay(dateStr)
            .catch { recordUnhandledException(it); emit(null) }
        val healthSessionsFlow = database.healthDao().observeSessionsForDay(dateStr)
            .catch { recordUnhandledException(it); emit(emptyList()) }
        // The next day's row — its sleep may have started on this date's evening.
        val nextHealthDayFlow = database.healthDao().observeDay(date.plusDays(1).toString())
            .catch { recordUnhandledException(it); emit(null) }

        val intakeFlow = combine(
            database.foodMedOccurrenceDao().getAllOccurrencesInTimeRange(start, end),
            database.foodMedTaskDao().observeAllTasks()
        ) { occs, tasks -> buildIntakeRows(occs, tasks) }
            .catch { recordUnhandledException(it); emit(emptyList()) }

        val streakParamsFlow = appSettingsRepository.observeSettings().map { s ->
            val mode = if (s.streakMode == "LENIENT") StreakMode.LENIENT else StreakMode.STRICT
            mode to parseRestDays(s.streakRestDays)
        }.distinctUntilChanged()

        val todoFlow = combine(
            database.habitOccurrenceDao().getAllOccurrencesInTimeRange(start, end),
            database.habitDao().observeAllHabits(),
            streakParamsFlow
        ) { occs, habits, streakParams -> Triple(occs, habits, streakParams) }
            .flatMapLatest { (occs, habits, streakParams) ->
                flow { emit(buildTodoRows(occs, habits, date, streakParams.first, streakParams.second)) }
            }
            .flowOn(Dispatchers.Default)
            .catch { recordUnhandledException(it); emit(emptyList()) }

        val aiSummaryFlow = database.dailyReportAiSummaryDao().observe(dateStr)
            .catch { recordUnhandledException(it); emit(null) }
        val weightUnitFlow = appSettingsRepository.observeSettings()
            .map { parseWeightUnit(it.weightUnit) }
            .distinctUntilChanged()
            .catch { recordUnhandledException(it); emit(WeightUnit.KG) }

        val slice1 = combine(workoutFlow, healthDayFlow, healthSessionsFlow, nextHealthDayFlow) { w, hd, hs, next ->
            Triple(w, hd, hs to com.daybook.app.data.health.sleepEntriesForDay(date, hd, next))
        }
        val slice2 = combine(intakeFlow, todoFlow, aiSummaryFlow, weightUnitFlow) { i, t, ai, wu ->
            RestSlice(i, t, ai, wu)
        }

        return combine(slice1, slice2) { (workout, healthDay, sessionsAndSleep), rest ->
            val (healthSessions, sleepEntries) = sessionsAndSleep
            val health = if (healthDay != null || healthSessions.isNotEmpty() || sleepEntries.isNotEmpty()) {
                HealthSectionData(
                    day = healthDay,
                    visibleCards = visibleHealthCards(healthDay, healthSessions.isNotEmpty()).let {
                        if (sleepEntries.isNotEmpty()) it + HealthCardKind.SLEEP else it - HealthCardKind.SLEEP
                    },
                    sessions = healthSessions,
                    sleepEntries = sleepEntries
                )
            } else null
            DailyReportData(
                date = date,
                workout = workout,
                health = health,
                intake = rest.intake,
                todo = rest.todo,
                aiSummary = rest.aiSummary,
                weightUnit = rest.weightUnit
            )
        }.flowOn(Dispatchers.Default)
            // C1 — outer last-resort net, matching HomeViewModel's pattern: per-section
            // `.catch{}` above should already absorb section-local failures, but this covers
            // anything from the `combine` operators themselves.
            // M1 fix — this used to swallow the exception WITHOUT emitting, so the Flow simply
            // completed: `buildChatContext`'s `observeReport(d).first()` then threw
            // `NoSuchElementException` on an empty Flow (silently killing `openChat()`'s
            // `safeLaunch`, so tapping "Chat" did nothing at all), and on the Report tab itself
            // `uiState`'s `combine` never emits until every source has emitted once, so a
            // completed-without-emitting `reportFlow` left the whole tab stuck on a blank initial
            // state with no error shown. Emitting a neutral, empty-but-valid `DailyReportData`
            // instead means every downstream `first()`/`combine` consumer always gets a value —
            // "degrade to an absent section" (R-DR1), not "hang forever."
            .catch { t ->
                recordUnhandledException(t)
                emit(DailyReportData(date, null, null, emptyList(), emptyList(), null, WeightUnit.KG))
            }
    }

    private data class RestSlice(
        val intake: List<IntakeEntryRow>,
        val todo: List<TodoEntryRow>,
        val aiSummary: DailyReportAiSummary?,
        val weightUnit: WeightUnit
    )

    /** §1.1 — session title (or "Ad-hoc workout"), duration, total volume + set count (the same
     *  pure [sessionStats] the live session header already computes — reused, not reimplemented),
     *  exercise list with per-exercise best set. */
    private suspend fun buildWorkoutSection(sessions: List<com.daybook.app.data.model.WorkoutSession>): WorkoutSectionData? {
        if (sessions.isEmpty()) return null
        val summaries = sessions.map { session ->
            val sets = database.workoutDao().getSetsForSession(session.id)
            val exercises = database.workoutDao().getExercisesForSession(session.id)
            val stats = sessionStats(sets)
            val exerciseSummaries = exercises.sortedBy { it.orderIndex }.map { ex ->
                val exCompletedSets = sets.filter { it.workoutExerciseId == ex.id && it.completedAt != null }
                val name = workoutRepository.resolveExercise(ex.exerciseId)?.name ?: "Exercise"
                val best = exCompletedSets.maxByOrNull { (it.weightKg ?: 0f) * (it.reps ?: 0) }
                val bestLabel = best?.let { s ->
                    val parts = buildList {
                        s.reps?.let { add("$it reps") }
                        s.weightKg?.takeIf { it > 0f }?.let { add(formatWeight(it, WeightUnit.KG)) }
                        s.durationSeconds?.takeIf { it > 0 }?.let { add("${it}s") }
                        s.distanceMeters?.takeIf { it > 0f }?.let { add("${it}m") }
                    }
                    parts.joinToString(" @ ").ifBlank { null }
                }
                WorkoutExerciseSummary(name = name, setCount = exCompletedSets.size, bestSetLabel = bestLabel)
            }
            WorkoutSessionSummary(
                title = session.title?.trim()?.takeIf { it.isNotBlank() } ?: "Ad-hoc workout",
                durationMinutes = session.endedAt?.let { ((it - session.startedAt) / 60_000L).toInt() },
                totalVolumeKg = stats.totalVolumeKg,
                setCount = stats.setCount,
                exercises = exerciseSummaries
            )
        }
        return WorkoutSectionData(summaries)
    }

    /** §1.3 — reuses the existing [RedFlag] label meanings rather than inventing new copy; only
     *  entries that were actually LOGGED show (a still-PENDING slot hasn't been eaten/taken yet,
     *  a SKIPPED one was never logged). */
    private fun buildIntakeRows(occs: List<FoodMedOccurrence>, tasks: List<FoodMedTask>): List<IntakeEntryRow> {
        val taskById = tasks.associateBy { it.id }
        return occs
            .filter { it.status == Occurrence.Status.LOGGED || it.status == Occurrence.Status.COMPLETED }
            .sortedBy { it.scheduledFor }
            .map { occ ->
                val task = taskById[occ.taskId]
                IntakeEntryRow(
                    id = occ.id,
                    taskId = occ.taskId,
                    timeLabel = DateTimeUtils.timestampToLocalTime(occ.scheduledFor)
                        .format(DateTimeFormatter.ofPattern("h:mm a")),
                    label = task?.label ?: "Intake",
                    statusLabel = "Logged",
                    flagLabel = when (occ.redFlag) {
                        RedFlag.RED -> "Trigger flagged"
                        RedFlag.MAYBE -> "Possible trigger"
                        else -> null
                    },
                    outsideFood = occ.outsideFood == true,
                    scheduledFor = occ.scheduledFor,
                    taskType = task?.type ?: TaskType.CUSTOM,
                    responseText = occ.responseText,
                    description = occ.description?.takeIf { it.isNotBlank() },
                    suspectedFood = occ.suspectedFood?.takeIf { it.isNotBlank() },
                    qaPairs = JournalQa.decode(occ.qaJson).filter { it.second.isNotBlank() }
                )
            }
    }

    /** §1.4 — reuses `RoutinesScreen`'s existing status vocabulary (Done / Skipped / Missed /
     *  Pending); every occurrence for the day shows, not just resolved ones, so a still-open
     *  today reads as "Pending" rather than being silently dropped.
     *
     *  Round 2 (Feature 3): a STREAK-type habit's row instead carries [TodoEntryRow.streakDays] —
     *  the streak length AS OF [date], computed from that habit's whole history via
     *  [StreakCalculator]'s existing date-bounded `asOf` parameter (no reimplementation of streak
     *  maths), not live-today's streak. */
    private suspend fun buildTodoRows(
        occs: List<HabitOccurrence>,
        habits: List<Habit>,
        date: LocalDate,
        streakMode: StreakMode,
        restDays: Set<java.time.DayOfWeek>
    ): List<TodoEntryRow> {
        val habitById = habits.associateBy { it.id }
        val streakCache = mutableMapOf<String, Int>()
        return occs.sortedBy { it.scheduledFor }.map { occ ->
            val habit = habitById[occ.habitId]
            val habitType = habit?.type ?: HabitType.INDIVIDUAL
            val done = occ.status == Occurrence.Status.COMPLETED || occ.status == Occurrence.Status.LOGGED
            val streakDays = if (habitType == HabitType.STREAK) {
                streakCache.getOrPut(occ.habitId) {
                    val schedStatuses = database.habitOccurrenceDao().getScheduledStatusesForHabit(occ.habitId)
                        .map { Triple(it.localDate, it.scheduledFor, it.status) }
                    streaksFromScheduledStatuses(
                        schedStatuses,
                        Occurrence.Status.COMPLETED,
                        asOf = date,
                        mode = streakMode,
                        restDays = restDays
                    ).currentStreak
                }
            } else null
            TodoEntryRow(
                id = occ.id,
                habitId = occ.habitId,
                timeLabel = DateTimeUtils.timestampToLocalTime(occ.scheduledFor)
                    .format(DateTimeFormatter.ofPattern("h:mm a")),
                label = habit?.title ?: "Habit",
                statusLabel = when (occ.status) {
                    Occurrence.Status.COMPLETED, Occurrence.Status.LOGGED -> "Done"
                    Occurrence.Status.SKIPPED -> "Skipped"
                    Occurrence.Status.PENDING ->
                        if (occ.scheduledFor < System.currentTimeMillis()) "Missed" else "Pending"
                },
                scheduledFor = occ.scheduledFor,
                habitType = habitType,
                done = done,
                qaPairs = JournalQa.decode(occ.qaJson).filter { it.second.isNotBlank() },
                streakDays = streakDays
            )
        }
    }
}
