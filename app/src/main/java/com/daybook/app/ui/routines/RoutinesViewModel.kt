package com.daybook.app.ui.routines

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.HabitType
import com.daybook.app.ui.components.ProfileUi
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.util.minuteTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Habits list ordering, chosen from the top-right filter sheet (v0.5.1 §B). */
enum class HabitSort(val label: String) { ADDED("Added"), NAME("Name"), NEXT_REMINDER("Next reminder") }

/**
 * Task C (C1/C2) — pure defense-in-depth: a backdated "Start" is structurally capped at today by
 * the date picker's own `maxDate`, but this guards the same rule at the call site in case a picked
 * instant's time-of-day component ever pushed it fractionally past "now".
 */
internal fun clampStreakStart(picked: Long, now: Long): Long = minOf(picked, now)

/**
 * Pure ordering for the Habits list (v0.5.1 §B) — mirrors [com.daybook.app.ui.foodmed.sortIntake].
 * Extracted so `SortComparatorTest` can exercise it. Stable for equal keys.
 */
fun sortHabits(rows: List<Pair<Habit, Long?>>, sort: HabitSort): List<Pair<Habit, Long?>> =
    when (sort) {
        HabitSort.ADDED -> rows.sortedBy { it.first.createdAt }
        HabitSort.NAME -> rows.sortedBy { it.first.title.lowercase() }
        HabitSort.NEXT_REMINDER -> rows.sortedWith(compareBy(nullsLast()) { it.second })
    }

@androidx.compose.runtime.Immutable
data class RoutineItem(
    val id: String,
    val title: String,
    val description: String?,
    val iconKey: String,
    val colorTag: String,
    val times: List<String>,
    val activeDays: List<String>,
    val snoozeInterval: Int,
    val isArchived: Boolean,
    val nextText: String?,
    val isBatch: Boolean,
    // v0.5.5: "Ongoing" (STREAK) habit fields. `streakDays` is null when not started, else the
    // inclusive running day-count as of the last minute tick.
    val isStreak: Boolean,
    val streakStartedAt: Long?,
    val streakLongest: Int,
    val streakDays: Int?
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    /** Corner-avatar profile (name + photo) for the tab header. */
    val profile: StateFlow<ProfileUi> =
        appSettingsRepository.observeSettings()
            .map { ProfileUi(it.userName.trim(), it.profilePhotoPath) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi("", null))

    /**
     * habit id -> next pending reminder millis, one grouped query instead of one per row (Section 9.1).
     *
     * v0.5.3 Phase 3 (A8): `now` is re-derived from a minute ticker rather than bound once at
     * construction — otherwise "Next: …" drifted into the past and a fired slot still showed as
     * upcoming the longer the screen stayed alive.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val nextMillisByHabit: StateFlow<Map<String, Long>> =
        minuteTicker()
            .flatMapLatest { now ->
                habitRepository.observeNextPendingMillis(now)
                    .map { rows -> rows.associate { it.habitId to it.nextMillis } }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---- Filter / sort state (top-right sheet on the Habits screen, §B) — mirrors FoodMedViewModel ----
    private val _sort = MutableStateFlow(HabitSort.ADDED)
    val sort: StateFlow<HabitSort> = _sort.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    init {
        // rec 3 — the filter sheet just *remembers* its last sort + show-archived (no Settings row).
        safeLaunch {
            val s = appSettingsRepository.getSettings()
            _sort.value = runCatching { HabitSort.valueOf(s.habitSort) }.getOrDefault(HabitSort.ADDED)
            _showArchived.value = s.habitShowArchived
        }
    }

    /** Empty = show every type. Otherwise only the selected [HabitType]s (v0.5.2). */
    private val _typeFilter = MutableStateFlow<Set<HabitType>>(emptySet())
    val typeFilter: StateFlow<Set<HabitType>> = _typeFilter.asStateFlow()

    /** True when the list is anything other than the default (Added order, active only, all types). */
    val filterActive: StateFlow<Boolean> =
        combine(_sort, _showArchived, _typeFilter) { sort, archived, types ->
            sort != HabitSort.ADDED || archived || types.isNotEmpty()
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** v0.5.5: a minute tick so an Ongoing habit's running day-count rolls over at local midnight
     *  while the screen stays open (mirrors [nextMillisByHabit]'s ticker). */
    private val nowTick: StateFlow<Long> =
        minuteTicker()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    /** Hoisted once so the query isn't re-run on every filter change (REV-19). */
    private val activeHabits: StateFlow<List<Habit>> =
        habitRepository.observeActiveHabits()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All habits incl. archived — only *used* when "Show archived" is on (cheap and idle otherwise). */
    private val allHabits: StateFlow<List<Habit>> =
        habitRepository.observeAllHabits()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Individual/Batch counts for the filter sheet, over whatever the archived toggle shows. */
    val typeCounts: StateFlow<Map<String, Int>> =
        combine(activeHabits, allHabits, _showArchived) { active, all, arch ->
            val src = if (arch) all else active
            HabitType.entries.associate { t -> t.name to src.count { it.type == t } }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private data class Filter(val sort: HabitSort, val archived: Boolean, val types: Set<HabitType>)

    /**
     * Reactive list — re-emits on any write to the habits table (an edit, an import, a
     * notification action) or a filter/sort change. Filtering and sorting are applied in memory
     * via `combine`, so changing the sheet does not hit the DB (REV-19).
     */
    val habits: StateFlow<List<RoutineItem>> =
        combine(
            activeHabits,
            allHabits,
            nextMillisByHabit,
            nowTick,
            combine(_sort, _showArchived, _typeFilter) { s, a, t -> Filter(s, a, t) }
        ) { active, all, nextMap, now, f ->
            val source = (if (f.archived) all else active)
                .let { list -> if (f.types.isEmpty()) list else list.filter { it.type in f.types } }
            val rows: List<Pair<Habit, Long?>> = source.map { habit ->
                habit to (if (habit.isArchived) null else nextMap[habit.id])
            }
            sortHabits(rows, f.sort).map { (habit, nextMillis) ->
                RoutineItem(
                    id = habit.id,
                    title = habit.title,
                    description = habit.description.takeIf { it.isNotBlank() },
                    iconKey = habit.iconKey,
                    colorTag = habit.colorTag.name,
                    times = DateTimeUtils.jsonToTimes(habit.timesJson).map { DateTimeUtils.formatTime(it) },
                    activeDays = DateTimeUtils.jsonToDays(habit.activeDaysJson).map { DateTimeUtils.getDayName(it) },
                    snoozeInterval = habit.snoozeIntervalMinutes,
                    isArchived = habit.isArchived,
                    nextText = nextMillis?.let { "Next: ${DateTimeUtils.formatWhen(it)}" },
                    isBatch = habit.type == HabitType.BATCH,
                    isStreak = habit.type == HabitType.STREAK,
                    streakStartedAt = habit.streakStartedAt,
                    streakLongest = habit.streakLongest,
                    streakDays = habit.streakStartedAt?.let { com.daybook.app.util.streak.daysSince(it, now) }
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSort(newSort: HabitSort) {
        _sort.value = newSort
        safeLaunch { appSettingsRepository.setHabitSort(newSort.name) }
    }

    fun toggleArchived() {
        val v = !_showArchived.value
        _showArchived.value = v
        safeLaunch { appSettingsRepository.setHabitShowArchived(v) }
    }

    fun toggleType(type: HabitType) {
        _typeFilter.value = _typeFilter.value.let { if (type in it) it - type else it + type }
    }

    fun resetFilter() {
        setSort(HabitSort.ADDED)
        if (_showArchived.value) toggleArchived()
        _typeFilter.value = emptySet()
    }

    fun addHabit(habit: Habit) {
        safeLaunch {
            habitRepository.save(habit)
            occurrenceScheduler.syncHabit(habit.id)
        }
    }

    fun updateHabit(habit: Habit) {
        safeLaunch {
            habitRepository.save(habit)
            occurrenceScheduler.syncHabit(habit.id)
        }
    }

    fun deleteHabit(habitId: String) {
        safeLaunch {
            val habit = habitRepository.getHabitById(habitId)
            habit?.let { h ->
                occurrenceScheduler.cancelHabit(h.id)
                habitRepository.delete(h)
            }
        }
    }

    fun archiveHabit(habitId: String) {
        safeLaunch {
            habitRepository.archiveHabit(habitId)
            occurrenceScheduler.cancelHabit(habitId)
        }
    }

    fun unarchiveHabit(habitId: String) {
        safeLaunch {
            habitRepository.unarchiveHabit(habitId)
            occurrenceScheduler.syncHabit(habitId)
        }
    }

    fun getHabitById(habitId: String, onResult: (Habit?) -> Unit) {
        safeLaunch {
            onResult(habitRepository.getHabitById(habitId))
        }
    }

    /**
     * v0.5.5: begin the running day-count for an "Ongoing" (STREAK) habit. No scheduling.
     * Task C (C1/C2): [atMillis] defaults to now, but the card's date picker can pass a backdated
     * start-of-day instant (capped at today by the picker's own `maxDate`, defended again here as
     * belt-and-braces) so a forgotten start date counts correctly via `daysSince`'s inclusive math.
     */
    fun startStreak(id: String, atMillis: Long = System.currentTimeMillis()) =
        safeLaunch { habitRepository.startStreak(id, clampStreakStart(atMillis, System.currentTimeMillis())) }

    /** v0.5.5: "Mark as broken" — record longest = max(longest, run), then clear the run. */
    fun markStreakBroken(id: String) = safeLaunch { habitRepository.markStreakBroken(id) }
}
