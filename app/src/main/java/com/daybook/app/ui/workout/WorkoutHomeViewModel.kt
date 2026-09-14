package com.daybook.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.local.RoutineSummary
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.workout.WeeklyStats
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.currentStreakDays
import com.daybook.app.data.workout.isPersonalRecord
import com.daybook.app.data.workout.parseWeightUnit
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutHomeViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    /** §2.7 fix — the "This week · Volume" tile used to hardcode `parseWeightUnit("KG")`. */
    val weightUnit = appSettingsRepository.observeSettings()
        .map { parseWeightUnit(it.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    val routines = repo.observeRoutineSummaries()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<RoutineSummary>())

    val activeSession = repo.observeActiveSession()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as WorkoutSession?)

    // Bug fix — the home card's "up for" label was a one-shot `remember` snapshot computed only
    // when the card first appeared (frozen while you sat on this screen), which read as a stuck
    // clock: the real session duration kept moving in the background the whole time, so reopening
    // the session showed it had visibly "jumped". Ticks every second instead, same pattern as
    // `WorkoutSessionViewModel._elapsedSeconds`, so the card is a live clock like the rest of the
    // app expects.
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds = _elapsedSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val startedAt = activeSession.value?.startedAt
                _elapsedSeconds.value = if (startedAt != null) {
                    ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
                } else 0L
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // BEAST_MODE_REDESIGN_PLAN.md §3.3 item 3 — the "This week" stat grid. Fixed Monday-start ISO
    // week (doesn't read `app_settings.week_start`, unlike the Habits week-strip — a deliberate
    // simplification for this new stat panel, not a bug). Recomputed each time the screen calls
    // [refreshWeeklyStats] (its own LaunchedEffect(Unit)) rather than kept live/reactive — this is
    // a "how's my week going" snapshot, not a ticking value like [elapsedSeconds].
    private val _weeklyStats = MutableStateFlow(WeeklyStats(0, 0f, 0, 0))
    val weeklyStats = _weeklyStats.asStateFlow()

    fun refreshWeeklyStats() = safeLaunch {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.with(java.time.DayOfWeek.MONDAY)
        val weekStartMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()

        // Streak looks back further than the week grid itself — up to 60 days.
        val streakRangeStart = today.minusDays(60)
        val recentSessions = repo.getSessionsInLocalDateRange(streakRangeStart.toString(), today.toString())
        val completedDates = recentSessions.filter { it.status == "COMPLETED" }.map { it.localDate }.toSet()
        val streak = currentStreakDays(completedDates, today)

        val thisWeekSessions = recentSessions.filter {
            it.status == "COMPLETED" && it.startedAt >= weekStartMillis
        }
        val sessionIds = thisWeekSessions.map { it.id }
        val aggregates = repo.sessionAggregates(sessionIds)
        val volume = aggregates.values.sumOf { it.totalVolumeKg }.toFloat()

        val weekSets = repo.getSetsForSessions(sessionIds).filter { it.completedAt != null }
        var prs = 0
        weekSets.groupBy { it.exerciseId }.forEach { (exerciseId, sets) ->
            val trackingMode = repo.resolveExercise(exerciseId)?.trackingMode ?: return@forEach
            var runningBest = repo.bestSetForExerciseBefore(exerciseId, weekStartMillis)
            sets.sortedBy { it.completedAt }.forEach { s ->
                if (isPersonalRecord(s, runningBest, trackingMode)) {
                    prs++
                    runningBest = s
                }
            }
        }

        _weeklyStats.value = WeeklyStats(
            workouts = thisWeekSessions.size, volumeKg = volume, streakDays = streak, prs = prs
        )
    }

    private val _newSessionId = MutableStateFlow<String?>(null)
    val newSessionId = _newSessionId.asStateFlow()
    fun clearNewSessionId() { _newSessionId.value = null }

    private val _deletedToken = MutableStateFlow(0)
    val deletedToken = _deletedToken.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _errorToken = MutableStateFlow(0)
    val errorToken = _errorToken.asStateFlow()

    private fun reportError(t: Throwable, message: String) {
        com.daybook.app.util.recordUnhandledException(t)
        _errorMessage.value = message
        _errorToken.value++
    }

    // Bug fix (BEAST_MODE_BUG_REPORT.md §1.2) — this used to be a bare `safeLaunch` with no
    // `runCatching`: a failure fell through to safeLaunch's Crashlytics-only default handler,
    // `_newSessionId` never got set, and the button just did nothing with zero on-screen
    // feedback. Now mirrors [startRoutine]'s error path.
    fun startEmptyWorkout() = safeLaunch {
        runCatching { repo.startEmptySession() }
            .onSuccess { _newSessionId.value = it }
            .onFailure { reportError(it, "Couldn't start a workout. Try again.") }
    }

    fun startRoutine(routineId: String) = safeLaunch {
        val result = runCatching { repo.startSessionFromRoutine(routineId) }
        result.onSuccess { _newSessionId.value = it }
            .onFailure { reportError(it, "Couldn't start that routine. Try again.") }
    }

    /** Discard-and-start confirmation — permanently deletes the current active session's logged
     *  sets, then runs the start action ([startEmptyWorkout] or [startRoutine]) that was stashed
     *  while the "already running" dialog was up. */
    fun discardActiveSessionAndThen(startAction: () -> Unit) = safeLaunch {
        // L5 fix — the only start-path action left without a `runCatching`/error message; a throw
        // from `repo.discardSession` used to mean `startAction()` never ran and "Discard and
        // start" did nothing visible, unlike every sibling action here (`startEmptyWorkout`,
        // `startRoutine`, `duplicateRoutine`, `deleteRoutine`), which all cite
        // BEAST_MODE_BUG_REPORT.md §1.2/§1.3 for exactly this reason.
        runCatching { activeSession.value?.let { repo.discardSession(it.id) } }
            .onSuccess { startAction() }
            .onFailure { reportError(it, "Couldn't discard that workout. Try again.") }
    }

    fun duplicateRoutine(routineId: String) = safeLaunch {
        // Bug fix (BEAST_MODE_BUG_REPORT.md §1.3) — this used to only report to Crashlytics on
        // failure, with no `_errorMessage`/toast, so "Duplicate" from the overflow menu could
        // silently fail with the user never knowing it didn't work.
        runCatching { repo.duplicateRoutine(routineId) }
            .onFailure { reportError(it, "Couldn't duplicate that routine. Try again.") }
    }

    fun deleteRoutine(routineId: String) = safeLaunch {
        val result = runCatching { repo.deleteRoutine(routineId) }
        if (result.isSuccess) _deletedToken.value++
        else reportError(result.exceptionOrNull()!!, "Couldn't delete that routine. Try again.")
    }
}
