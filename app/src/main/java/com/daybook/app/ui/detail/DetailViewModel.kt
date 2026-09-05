package com.daybook.app.ui.detail

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.model.Event
import com.daybook.app.data.model.Occurrence
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.util.streak.StreakMode
import com.daybook.app.util.streak.parseRestDays
import com.daybook.app.util.streak.streaksFromScheduledStatuses
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class TimelineEvent(
    /** v0.5.2 §2: stable unique key. Terminal rows key on "o:<occurrenceId>", activity rows on
     *  "e:<eventRowId>". */
    val id: String,
    val timestamp: String,
    /** Raw epoch millis of the event — sort by this, not the formatted string (v0.5.2 / 5B.6). */
    val rawTimestamp: Long,
    /** The occurrence's scheduled instant. Differs from [rawTimestamp]'s date for a backfill. */
    val scheduledFor: Long = 0L,
    val action: Event.Action,
    val responseText: String? = null,
    /** v0.5.2 §3: the JOURNAL long-form entry, when this REPLIED event has one. */
    val description: String? = null,
    /** v0.5.4 Phase 5: the ordered [{"q":…,"a":…}] snapshot for a JOURNAL REPLIED row — the Detail
     *  history row renders stacked Q&A from this, not from the [responseText] chip. Null for
     *  FOOD/MED/CUSTOM and for a non-REPLIED row. */
    val qaJson: String? = null,
    /** v0.5.2: the occurrence this event belongs to — lets a JOURNAL REPLIED row reopen the page. */
    val occurrenceId: String? = null,
    /** v0.5.4: the red-flag marker on this FOOD log, when set. */
    val redFlag: com.daybook.app.data.model.RedFlag? = null,
    /** v0.5.4: the suspected trigger food recorded with this FOOD log, when any. */
    val suspectedFood: String? = null,
    /** v0.5.2 build 8: the "outside food" marker on this FOOD log, when set. */
    val outsideFood: Boolean? = null
) {
    /** v0.5.3 Phase 5 (§5.7) — the human label for [action]; was prettified in the composable. */
    val displayLabel: String
        get() = action.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * v0.5.2 build 8 item 3 — the Stats-tab holder. Computed off [Dispatchers.Default] from the
 * already-loaded occurrence list; no extra Room query. Folds in the old streak flow.
 */
@androidx.compose.runtime.Immutable
data class DetailStats(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val completionRatePct: Int = 0,   // 0..100, all-time, scheduled_for <= now
    val thisMonthCount: Int = 0       // COMPLETED/LOGGED, scheduled_for in current local month
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val foodMedRepository: FoodMedRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    private val settingsRepository: AppSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val itemType: String = savedStateHandle.get<String>("itemType") ?: "habit"
    private val itemId: String = savedStateHandle.get<String>("itemId") ?: ""

    private val _itemTitle = MutableStateFlow("")
    val itemTitle: StateFlow<String> = _itemTitle.asStateFlow()

    private val _itemSubtitle = MutableStateFlow<String?>(null)
    val itemSubtitle: StateFlow<String?> = _itemSubtitle.asStateFlow()

    /** Customization round (rec 8 / SD-6): the "why this matters" note, or null. */
    private val _itemMotivation = MutableStateFlow<String?>(null)
    val itemMotivation: StateFlow<String?> = _itemMotivation.asStateFlow()

    /** v0.5.3 item 3: the saved category for a CUSTOM/JOURNAL intake; null for habits/plain intake. */
    private val _itemCategory = MutableStateFlow<String?>(null)
    val itemCategory: StateFlow<String?> = _itemCategory.asStateFlow()

    private val _itemIconKey = MutableStateFlow("water")
    val itemIconKey: StateFlow<String> = _itemIconKey.asStateFlow()

    private val _itemColorTag = MutableStateFlow("AUTO")
    val itemColorTag: StateFlow<String> = _itemColorTag.asStateFlow()

    private val _timelineEvents = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timelineEvents: StateFlow<List<TimelineEvent>> = _timelineEvents.asStateFlow()

    /** v0.5.2 §3: true for a JOURNAL intake — makes its REPLIED timeline rows reopen the page. */
    private val _isJournal = MutableStateFlow(false)
    val isJournal: StateFlow<Boolean> = _isJournal.asStateFlow()

    /** Journal-as-habit round: true for a JOURNAL-type habit — its REPLIED timeline rows reopen
     *  the plain edit-form (never the chat, never the generic toggle-to-undo path). Habit-side
     *  counterpart of [_isJournal], which stays FoodMed-only. */
    private val _isHabitJournal = MutableStateFlow(false)
    val isHabitJournal: StateFlow<Boolean> = _isHabitJournal.asStateFlow()

    /** v0.5.5: true for an "Ongoing" (STREAK) habit — History shows an empty state and Stats hides
     *  the completion-rate / this-month row. `computeStats` / `StreakCalculator` are never called. */
    private val _isOngoing = MutableStateFlow(false)
    val isOngoing: StateFlow<Boolean> = _isOngoing.asStateFlow()

    /** v0.5.2 build 8 item 3: single stats holder (was [_streakInfo]). */
    private val _stats = MutableStateFlow(DetailStats())
    val stats: StateFlow<DetailStats> = _stats.asStateFlow()

    /** rec 6 (S5) — reactive gate for the streak figure on the Stats tab. Ongoing (STREAK) habits'
     *  own day-count is NOT gated by this — see [isOngoing] / the DetailScreen render. */
    val showStreaks: StateFlow<Boolean> = settingsRepository.observeSettings()
        .map { it.showStreaks }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** v0.5.3 Phase 3 (A4): true while there are older terminal rows still unpaged. */
    private val _canLoadMoreTerminal = MutableStateFlow(false)
    val canLoadMoreTerminal: StateFlow<Boolean> = _canLoadMoreTerminal.asStateFlow()

    /** v0.5.3 Phase 3 (A4): how many [TERMINAL_PAGE]-sized pages of terminal rows to load. Bumped
     *  by [loadMoreTerminal]; preserved across a [refresh] so a resume keeps the scroll depth. */
    private var terminalPagesLoaded = 1

    // --- inline search (v0.5.2 build 8 item 3) -------------------------------------------------
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun onQueryChange(v: String) { _query.value = v }

    /** Non-empty query hides rows with no text match (non-REPLIED rows have null text). */
    val filteredTimeline: StateFlow<List<TimelineEvent>> =
        combine(_timelineEvents, _query) { events, q ->
            val needle = q.trim().lowercase()
            if (needle.isEmpty()) events
            else events.filter { e ->
                (e.responseText?.lowercase()?.contains(needle) == true) ||
                    (e.description?.lowercase()?.contains(needle) == true)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private companion object {
        /** Cap on activity (SHOWN/USER_SNOOZED) rows loaded per item — bounds that query (REV-24). */
        const val TIMELINE_LIMIT = 200
        /** v0.5.3 Phase 3 (A4): terminal (occurrence-derived) rows are paged this many at a time,
         *  newest first, instead of a single uncapped snapshot. */
        const val TERMINAL_PAGE = 100
    }

    init {
        loadItemDetails(itemType, itemId)
    }

    /** Re-run the (snapshot) load — used after a habit-occurrence toggle. Keeps the paged depth. */
    fun refresh() = loadItemDetails(itemType, itemId)

    /** v0.5.3 Phase 3 (A4): append the next page of older terminal rows. */
    fun loadMoreTerminal() {
        if (!_canLoadMoreTerminal.value) return
        terminalPagesLoaded += 1
        loadItemDetails(itemType, itemId)
    }

    fun loadItemDetails(itemType: String, itemId: String) {
        safeLaunch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                when (itemType) {
                    "habit" -> loadHabitDetails(itemId)
                    "food_med" -> loadFoodMedDetails(itemId)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load item details: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadHabitDetails(habitId: String) {
        val habit = habitRepository.getHabitById(habitId) ?: return

        // v0.5.5: an "Ongoing" (STREAK) habit has zero occurrences. Never touch the occurrence DAOs,
        // `computeStats` or `StreakCalculator` — derive the two stats straight from the columns.
        if (habit.type == com.daybook.app.data.model.HabitType.STREAK) {
            _isJournal.value = false
            _isHabitJournal.value = false
            _isOngoing.value = true
            _itemTitle.value = habit.title
            _itemSubtitle.value = habit.description.takeIf { it.isNotBlank() }
            _itemMotivation.value = habit.motivation?.takeIf { it.isNotBlank() }
            _itemCategory.value = null
            _itemIconKey.value = habit.iconKey
            _itemColorTag.value = habit.colorTag.name
            _timelineEvents.value = emptyList()
            _canLoadMoreTerminal.value = false
            val run = habit.streakStartedAt?.let {
                com.daybook.app.util.streak.daysSince(it, System.currentTimeMillis())
            } ?: 0
            _stats.value = DetailStats(
                currentStreak = run,
                longestStreak = habit.streakLongest,
                completionRatePct = 0,
                thisMonthCount = 0
            )
            return
        }
        _isOngoing.value = false

        val dao = habitRepository.database.habitOccurrenceDao()
        // v0.5.3 Phase 3 (A4): the newest N terminal rows only — paged, not one uncapped snapshot.
        val limit = terminalPagesLoaded * TERMINAL_PAGE
        val terminalOccs = dao.getTerminalPageForHabit(habitId, limit, 0)
        _canLoadMoreTerminal.value = terminalOccs.size == limit
        // The stats fold + streak maths still need the WHOLE history — a two-column projection,
        // no TimelineEvent allocation.
        val schedStatuses = dao.getScheduledStatusesForHabit(habitId).map { it.scheduledFor to it.status }
        val activity = habitRepository.database.habitEventDao()
            .getRecentActivityEventsForHabit(habitId, TIMELINE_LIMIT)

        _itemTitle.value = habit.title
        _itemSubtitle.value = habit.description.takeIf { it.isNotBlank() }
        _itemMotivation.value = habit.motivation?.takeIf { it.isNotBlank() }
        _itemCategory.value = null
        _itemIconKey.value = habit.iconKey
        _itemColorTag.value = habit.colorTag.name
        _isJournal.value = false
        val isJournalHabit = habit.type == com.daybook.app.data.model.HabitType.JOURNAL
        _isHabitJournal.value = isJournalHabit

        // v0.5.2 §2/§3: terminal rows come from the occurrence list; SHOWN/USER_SNOOZED activity is
        // merged in separately (capped). v0.5.3 Phase 3 (A4): the occurrence list is now a page.
        _timelineEvents.value = withContext(Dispatchers.Default) {
            val occById = terminalOccs.associateBy { it.id }
            val rows = ArrayList<TimelineEvent>(terminalOccs.size + activity.size)
            terminalOccs.forEach { occ ->
                val action = when (occ.status) {
                    Occurrence.Status.COMPLETED -> Event.Action.COMPLETED
                    Occurrence.Status.SKIPPED -> Event.Action.SKIPPED
                    Occurrence.Status.LOGGED -> Event.Action.REPLIED
                    Occurrence.Status.PENDING -> return@forEach
                }
                val raw = occ.respondedAt ?: occ.scheduledFor
                rows += TimelineEvent(
                    id = "o:${occ.id}",
                    timestamp = fmtTs(raw),
                    rawTimestamp = raw,
                    scheduledFor = occ.scheduledFor,
                    action = action,
                    responseText = null,
                    // Journal-as-habit round: a JOURNAL habit's REPLIED row carries its qa_json
                    // snapshot, exactly like the FoodMed-side journal row (below). Null for
                    // INDIVIDUAL/BATCH — their occurrences never write qa_json.
                    qaJson = if (isJournalHabit && action == Event.Action.REPLIED) occ.qaJson else null,
                    occurrenceId = occ.id
                )
            }
            activity.forEach { e ->
                rows += TimelineEvent(
                    id = "e:${e.id}",
                    timestamp = fmtTs(e.timestamp),
                    rawTimestamp = e.timestamp,
                    scheduledFor = occById[e.occurrenceId]?.scheduledFor ?: 0L,
                    action = e.action,
                    responseText = null,
                    occurrenceId = e.occurrenceId
                )
            }
            rows.apply { sortByDescending { it.rawTimestamp } }
        }

        val (mode, restDays) = streakParams()
        // Journal-as-habit round (B5/B6): a JOURNAL habit's answered slots resolve to LOGGED, not
        // COMPLETED — use the type-correct doneStatus so completion-rate / this-month aren't stuck
        // at 0 (the streak fold itself is additionally hardened in `daySatisfies`, see StreakCalculator.kt).
        val doneStatus = if (isJournalHabit) Occurrence.Status.LOGGED else Occurrence.Status.COMPLETED
        _stats.value = withContext(Dispatchers.Default) {
            computeStats(
                schedStatuses,
                streaksFromScheduledStatuses(schedStatuses, doneStatus, mode = mode, restDays = restDays),
                doneStatus
            )
        }
    }

    /** rec 6 — the device-local streak-mode + rest-day set, read once per detail load. */
    private suspend fun streakParams(): Pair<StreakMode, Set<java.time.DayOfWeek>> {
        val s = settingsRepository.getSettings()
        val mode = if (s.streakMode == "LENIENT") StreakMode.LENIENT else StreakMode.STRICT
        return mode to parseRestDays(s.streakRestDays)
    }

    private fun fmtTs(ts: Long): String = DateTimeUtils.formatDateTime(
        DateTimeUtils.timestampToLocalDate(ts),
        DateTimeUtils.timestampToLocalTime(ts)
    )

    private suspend fun loadFoodMedDetails(taskId: String) {
        val task = foodMedRepository.getTaskById(taskId) ?: return
        val dao = foodMedRepository.database.foodMedOccurrenceDao()
        // v0.5.3 Phase 3 (A4): the newest N terminal rows only — paged, not one uncapped snapshot.
        val limit = terminalPagesLoaded * TERMINAL_PAGE
        val terminalOccs = dao.getTerminalPageForTask(taskId, limit, 0)
        _canLoadMoreTerminal.value = terminalOccs.size == limit
        // The stats fold + streak maths still need the WHOLE history — a two-column projection,
        // no TimelineEvent allocation.
        val schedStatuses = dao.getScheduledStatusesForTask(taskId).map { it.scheduledFor to it.status }
        val activity = foodMedRepository.database.foodMedEventDao()
            .getRecentActivityEventsForTask(taskId, TIMELINE_LIMIT)

        _itemTitle.value = task.label
        _itemSubtitle.value = null
        _itemMotivation.value = task.motivation?.takeIf { it.isNotBlank() }
        _itemCategory.value = task.customCategory
        _itemIconKey.value = task.iconKey
        _itemColorTag.value = task.colorTag.name
        _isJournal.value = task.type == com.daybook.app.data.model.TaskType.JOURNAL

        // v0.5.2 §2/§3 (Problem 2): terminal rows come from the occurrence list; SHOWN/USER_SNOOZED
        // activity is merged in separately (capped). v0.5.3 Phase 3 (A4): the occurrence list is
        // now a page (newest terminal rows first), not one uncapped snapshot — mirrors
        // [loadHabitDetails]. Field sources for a terminal REPLIED row are unchanged, so the
        // rendered row is byte-identical.
        _timelineEvents.value = withContext(Dispatchers.Default) {
            val occById = terminalOccs.associateBy { it.id }
            val rows = ArrayList<TimelineEvent>(terminalOccs.size + activity.size)
            terminalOccs.forEach { occ ->
                val action = when (occ.status) {
                    Occurrence.Status.LOGGED, Occurrence.Status.COMPLETED -> Event.Action.REPLIED
                    Occurrence.Status.SKIPPED -> Event.Action.SKIPPED
                    Occurrence.Status.PENDING -> return@forEach
                }
                val replied = action == Event.Action.REPLIED
                val raw = occ.respondedAt ?: occ.scheduledFor
                rows += TimelineEvent(
                    id = "o:${occ.id}",
                    timestamp = fmtTs(raw),
                    rawTimestamp = raw,
                    scheduledFor = occ.scheduledFor,
                    action = action,
                    responseText = if (replied) occ.responseText else null,
                    description = if (replied) occ.description else null,
                    // v0.5.4 Phase 5: carry the Q&A snapshot for a JOURNAL REPLIED row. Null for
                    // FOOD/MED/CUSTOM (they never write `qa_json`), so their row stays byte-identical.
                    qaJson = if (replied) occ.qaJson else null,
                    occurrenceId = occ.id,
                    redFlag = if (replied) occ.redFlag else null,
                    suspectedFood = if (replied) occ.suspectedFood else null,
                    outsideFood = if (replied) occ.outsideFood else null
                )
            }
            activity.forEach { e ->
                rows += TimelineEvent(
                    id = "e:${e.id}",
                    timestamp = fmtTs(e.timestamp),
                    rawTimestamp = e.timestamp,
                    scheduledFor = occById[e.occurrenceId]?.scheduledFor ?: 0L,
                    action = e.action,
                    occurrenceId = e.occurrenceId
                )
            }
            rows.apply { sortByDescending { it.rawTimestamp } }
        }

        // v0.5.4 / v0.5.3 Phase 3 (A4): the streak fold consumes the whole-history
        // `(scheduled_for, status)` projection — `streaksFromScheduledStatuses` keeps the v0.5.4
        // rule that EVERY occurrence on a day must be LOGGED for that day to count.
        val (mode, restDays) = streakParams()
        _stats.value = withContext(Dispatchers.Default) {
            computeStats(
                schedStatuses,
                streaksFromScheduledStatuses(schedStatuses, Occurrence.Status.LOGGED, mode = mode, restDays = restDays),
                Occurrence.Status.LOGGED
            )
        }
    }

    private fun computeStats(
        scheduledStatuses: List<Pair<Long, Occurrence.Status>>,
        streak: com.daybook.app.util.streak.StreakResult,
        doneStatus: Occurrence.Status
    ): DetailStats {
        val now = System.currentTimeMillis()
        val due = scheduledStatuses.filter { it.first <= now }
        val done = due.count { it.second == doneStatus }
        val pct = if (due.isEmpty()) 0 else (done * 100) / due.size
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.now(zone)
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val month = scheduledStatuses.count { it.first in start until end && it.second == doneStatus }
        return DetailStats(streak.currentStreak, streak.longestStreak, pct, month)
    }

    /** v0.5.2 build 8 item 3: History-tab tap on a habit row flips COMPLETED <-> SKIPPED. */
    fun toggleHabitOccurrence(occId: String) {
        safeLaunch {
            val occ = habitRepository.database.habitOccurrenceDao().getOccurrenceById(occId) ?: return@safeLaunch
            // Journal-as-habit round: a LOGGED row is a Journal habit's answered entry — DetailScreen
            // routes those to the edit-form instead of calling this, but guard here too so a stray
            // call can never clobber an answered entry into a plain COMPLETED/SKIPPED toggle.
            if (occ.status == Occurrence.Status.LOGGED) return@safeLaunch
            if (occ.status == Occurrence.Status.COMPLETED) occurrenceScheduler.skipHabit(occId)
            else occurrenceScheduler.completeHabit(occId)
            refresh()
        }
    }
}
