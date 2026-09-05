package com.daybook.app.ui.home

import com.daybook.app.util.safeLaunch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.canBackfill
import com.daybook.app.data.model.Event
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.Occurrence
import com.daybook.app.data.sync.CloudSyncRepository
import com.daybook.app.ui.components.ProfileUi
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.util.notification.NotificationUtils
import com.daybook.app.util.streak.StreakMode
import com.daybook.app.util.streak.calculateFoodMedStreaks
import com.daybook.app.util.streak.calculateHabitStreaks
import com.daybook.app.util.streak.parseRestDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

/** Trailing-slot label for a past-day occurrence that was never answered. */
internal const val MISSED_LABEL = "Missed"

/** Journal Mode: trailing-slot label for a resolved intake / journal text reply. The Today card
 *  routes this (unlike a habit "Done" or a "Skipped") to the entry editor, not to undo-to-pending. */
internal const val LOGGED_LABEL = "Logged"

@Immutable
data class HomeItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val iconKey: String,
    val colorTag: String,
    val scheduledTime: String,
    val scheduledEpoch: Long,
    val isHabit: Boolean,
    val detailId: String,
    val occurrenceId: String?,
    val canComplete: Boolean,
    val canSkip: Boolean,
    val canSnooze: Boolean,
    val canReply: Boolean,
    val responseText: String?,
    val statusLabel: String?,
    val isPast: Boolean,
    val isFuture: Boolean,
    /** v0.5.2 §3: a JOURNAL intake — the card taps through to the journal page instead of an
     *  inline reply, and the trailing slot shows a "write entry" affordance. */
    val isJournal: Boolean = false,
    /** Journal-as-habit round: a JOURNAL-type habit — the same "write entry" trailing affordance
     *  as [isJournal], but routes to the habit-side chat/edit-form, never the generic habit
     *  Complete/Skip actions and never the FoodMed respond/journal screens. Kept as a distinct flag
     *  (not folded into [isJournal]) since the two are backed by different tables/routes. */
    val isHabitJournal: Boolean = false,
    /** v0.5.2 §9: this row is a past-day slot (real or synthetic) that can be resolved
     *  retroactively through the no-alarm backfill path. */
    val isBackfill: Boolean = false,
    /** v0.5.3 item 8: the intake task's custom prompt message, if any. Habits leave this null. */
    val promptMessage: String? = null,
    /** v0.5.4: this row is a FOOD intake — unlocks the Crohn's trigger-flag capture on the
     *  inline reply. Habits / MED / CUSTOM / JOURNAL are all false. */
    val isFood: Boolean = false,
    /** v0.5.4: the red-flag recorded on this log (resolved rows only); null when unflagged. */
    val loggedRedFlag: com.daybook.app.data.model.RedFlag? = null,
    /** v0.5.4: the suspected trigger food recorded on this log; null when none. */
    val loggedSuspectedFood: String? = null,
    /** v0.5.4: the FOOD reminder's default flag, used to pre-fill the inline reply. */
    val defaultRedFlag: com.daybook.app.data.model.RedFlag? = null,
    /** v0.5.4: the FOOD reminder's default suspected trigger food, used to pre-fill. */
    val defaultSuspectedFood: String? = null,
    /** v0.5.2 build 8: the "outside food" marker recorded on this log (resolved rows only). */
    val loggedOutsideFood: Boolean? = null,
    /** v0.5.2 build 8: the FOOD reminder's default "outside food" marker, used to pre-fill. */
    val defaultOutsideFood: Boolean? = null
)

/** v0.5.3 item 7: Home "Reminders" filter buckets. UI-only, never persisted — keep order stable. */
enum class ReminderFilter { HABITS, INTAKE, JOURNAL }

/**
 * v0.5.3 item 7 — pure predicate for the Home "Reminders" filter. Empty [types] = all buckets.
 * [showResolved]=false hides COMPLETED/SKIPPED/LOGGED and past-day "Missed" (statusLabel != null)
 * BUT keeps future "Upcoming" rows (statusLabel == null).
 */
internal fun homeItemVisible(item: HomeItem, types: Set<ReminderFilter>, showResolved: Boolean): Boolean {
    val bucket = when {
        item.isJournal || item.isHabitJournal -> ReminderFilter.JOURNAL
        item.isHabit -> ReminderFilter.HABITS
        else -> ReminderFilter.INTAKE
    }
    if (types.isNotEmpty() && bucket !in types) return false
    if (!showResolved && item.statusLabel != null) return false
    return true
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val foodMedRepository: FoodMedRepository,
    private val settingsRepository: AppSettingsRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    // v0.5.1 §N: a @Singleton already in the graph, so this dependency is free. Used only for the
    // lazy month hydration below.
    private val cloudSync: CloudSyncRepository,
    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-2) — surfaces notificationBlockReason() for the
    // Today-screen banner below. Also a @Singleton already in the graph.
    private val notificationUtils: NotificationUtils
) : ViewModel() {

    /** Null when notifications can actually be posted; otherwise why they can't (Phase 7, N-2). */
    fun notificationBlockReason(): String? = notificationUtils.notificationBlockReason()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * Wall-clock tick. Advanced at each time-of-day / midnight boundary by [init] so the header,
     * the week strip's "today" marker, past/future classification and the greeting don't go stale
     * when the app is left open across midnight or a greeting boundary (REV-22).
     */
    private val _now = MutableStateFlow(LocalDateTime.now())

    // v0.5.2 / 5B.8: bumped after each ensureMonthHydrated attempt so `monthReady` re-evaluates
    // once a fetch lands (a bare _selectedDate/_now combine would keep showing "Loading…").
    private val _hydrationTick = MutableStateFlow(0)

    val today: StateFlow<LocalDate> =
        _now.map { it.toLocalDate() }
            .distinctUntilChanged()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 4 (S-1) — a wedged sync (Paused from an unresolved
    // conflict, or a persistent Error) was previously invisible outside Settings/Account. Surfaced
    // here so the Today screen can show a small banner instead of burying it.
    val syncStatus: StateFlow<com.daybook.app.data.sync.SyncStatus> = cloudSync.status

    init {
        // v0.5.1 §N: only the current and previous month are hydrated into Room (SD-3a). When the
        // selected date moves into any other month, fetch that month's cloud doc and merge it in.
        //
        // Every history surface ultimately moves `_selectedDate` — the week strip's onSelect, the
        // calendar, any future stats view — so keying off it here covers all of them with one
        // collector. `ensureMonthHydrated` is failure-inert and must not block rendering: the day
        // renders empty and fills in when the fetch lands, through the same reactive Room path a
        // remote change already uses.
        safeLaunch {
            var previousMonth: String? = null
            _selectedDate
                .map { YearMonth.from(it).toString() }
                .distinctUntilChanged()
                .collect { month ->
                    // v0.5.3 Phase 1 (S3): pin the month being viewed so a background push/worker
                    // never evicts it out from under the user; unpin the one just left.
                    previousMonth?.let { if (it != month) cloudSync.unpinMonth(it) }
                    previousMonth = month
                    runCatching { cloudSync.ensureMonthHydrated(month) }
                    cloudSync.pinMonth(month)
                    _hydrationTick.value++
                }
        }
        safeLaunch {
            while (isActive) {
                delay(millisUntilNextBoundary())
                val prevToday = _now.value.toLocalDate()
                val fresh = LocalDateTime.now()
                _now.value = fresh
                // If the user was parked on "today", roll the selection over with the date.
                if (fresh.toLocalDate() != prevToday && _selectedDate.value == prevToday) {
                    _selectedDate.value = fresh.toLocalDate()
                }
            }
        }
    }

    private companion object {
        /**
         * D2 streak look-back window. Longer than two years: a *current* streak cannot realistically
         * reach this, so windowing the streak query to it is not a user-visible behaviour change —
         * but it replaces an unbounded full-history table scan on every occurrence write.
         */
        const val STREAK_WINDOW_DAYS = 800L
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    // Hoisted: cold Room flows re-subscribed on every date change inside flatMapLatest made Room
    // re-run both queries on each day tap. Sharing them keeps one live subscription (REV-22).
    private val allHabits: StateFlow<List<Habit>> =
        habitRepository.observeAllHabits()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val allTasks: StateFlow<List<FoodMedTask>> =
        foodMedRepository.observeAllTasks()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // v0.5.2 Phase 4a: ONE upstream `app_settings` subscription, shared. `checkinTime`, `greeting`
    // and `profile` were each subscribing `observeSettings()` separately, so one settings write
    // re-ran the query three times in this ViewModel alone.
    private val settings: StateFlow<com.daybook.app.data.model.AppSettings> =
        settingsRepository.observeSettings()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                com.daybook.app.data.model.AppSettings()
            )

    // v0.5.2 §9: the app-wide BATCH check-in time supplies a BATCH habit's slot on a past day.
    private val checkinTime: StateFlow<String> =
        settings.map { it.habitCheckinTime }
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "21:00")

    // rec 1 — device-local display prefs surfaced to HomeScreen / WeekStrip.
    val weekStart: StateFlow<String> =
        settings.map { it.weekStart }.distinctUntilChanged()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "MONDAY")
    val calendarDefaultExpanded: StateFlow<Boolean> =
        settings.map { it.calendarDefaultExpanded }.distinctUntilChanged()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // rec 1 (C2) — the two inputs buildItems needs off settings, folded so an unrelated settings
    // write (accent, font, …) doesn't re-run the item builder.
    private data class HomeCfg(val checkin: String, val clock24h: Boolean)
    private val homeCfg: StateFlow<HomeCfg> =
        settings.map { HomeCfg(it.habitCheckinTime, it.clock24h) }.distinctUntilChanged()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeCfg("21:00", false))

    // rec 6 — streak interpretation, folded off settings.
    private data class StreakCfg(
        val mode: StreakMode,
        val restDays: Set<java.time.DayOfWeek>,
        val show: Boolean
    )
    private val streakCfg: StateFlow<StreakCfg> =
        settings.map {
            StreakCfg(
                if (it.streakMode == "LENIENT") StreakMode.LENIENT else StreakMode.STRICT,
                parseRestDays(it.streakRestDays),
                it.showStreaks
            )
        }.distinctUntilChanged()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                StreakCfg(StreakMode.STRICT, emptySet(), true)
            )

    /** rec 6 (S5) — gate for the flame pill on Today's "Your progress" cards. */
    val showStreaks: StateFlow<Boolean> =
        streakCfg.map { it.show }
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val homeItems: StateFlow<List<HomeItem>> =
        combine(_selectedDate, _now.map { it.toLocalDate() }.distinctUntilChanged()) { date, today ->
            date to today
        }.flatMapLatest { (date, today) ->
            val start = DateTimeUtils.startOfDay(date)
            val end = DateTimeUtils.endOfDay(date)
            combine(
                habitRepository.database.habitOccurrenceDao().getAllOccurrencesInTimeRange(start, end),
                foodMedRepository.database.foodMedOccurrenceDao().getAllOccurrencesInTimeRange(start, end),
                allHabits,
                allTasks,
                homeCfg
            ) { hOccs, fOccs, habits, tasks, cfg ->
                buildItems(date, today, hOccs, fOccs, habits, tasks, cfg.checkin, cfg.clock24h)
            }
        }
            .flowOn(Dispatchers.Default)
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // v0.5.3 item 7: Home "Reminders" filter — session-scoped, resets on process death.
    private val _typeFilter = MutableStateFlow<Set<ReminderFilter>>(emptySet())   // empty = All
    val typeFilter: StateFlow<Set<ReminderFilter>> = _typeFilter.asStateFlow()

    private val _showResolved = MutableStateFlow(false)
    val showResolved: StateFlow<Boolean> = _showResolved.asStateFlow()

    init {
        // rec 3 (H8) — seed the Today "hide resolved" filter from `home_hide_resolved`
        // (sense: home_hide_resolved == 1 → start with resolved hidden, i.e. showResolved = false).
        safeLaunch {
            _showResolved.value = !settingsRepository.getSettings().homeHideResolved
        }
    }

    fun toggleFilter(f: ReminderFilter) {
        _typeFilter.value = _typeFilter.value.let { if (f in it) it - f else it + f }
    }
    fun setShowResolved(v: Boolean) {
        _showResolved.value = v
        safeLaunch { settingsRepository.setHomeHideResolved(!v) }
    }
    fun resetReminderFilter() {
        _typeFilter.value = emptySet()
        safeLaunch { _showResolved.value = !settingsRepository.getSettings().homeHideResolved }
    }

    /** The filtered view of [homeItems] the "Reminders" list renders. The unfiltered [homeItems]
     *  still drives the "$N left" headline and the progress-bar ratios. */
    val visibleItems: StateFlow<List<HomeItem>> =
        combine(homeItems, _typeFilter, _showResolved) { items, types, showResolved ->
            items.filter { homeItemVisible(it, types, showResolved) }
        }.catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * v0.5.2 / 5B.8: true when the selected month's day-logs are resident in Room, so a §9
     * backfill into it is safe to push. Signed-out users are always ready (no cloud to lose).
     * Re-checked whenever the selected date's month changes AND whenever hydration might have
     * completed (the wall-clock tick is a cheap re-poll trigger).
     */
    val monthReady: StateFlow<Boolean> =
        combine(_selectedDate, _now, _hydrationTick) { date, _, _ ->
            YearMonth.from(date).toString()
        }
            .map { month ->
                val resident = cloudSync.isMonthResident(month)
                // v0.5.3 Phase 1 (S3): the month we're on is no longer resident (an unrelated push
                // evicted it, or residency went stale). Re-trigger hydration. Only bump the tick
                // when it actually became resident, so a failed fetch can't spin this loop; the
                // repo's own hydrationAttempted set caps the network churn either way.
                if (!resident && month == YearMonth.from(_selectedDate.value).toString()) {
                    safeLaunch {
                        runCatching { cloudSync.ensureMonthHydrated(month) }
                        if (cloudSync.isMonthResident(month)) _hydrationTick.value++
                    }
                }
                resident
            }
            .flowOn(Dispatchers.Default)   // Phase 4b: isMonthResident off the collector's dispatcher
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // v0.5.1 §I (SD-2 option B): keyed to _selectedDate, not a single global number. Scrolling
    // back to an empty day now yields 0 (so the pill's `if (streak > 0)` hides it, which is what
    // the bug report asked for), while a past day inside a real run reports that run's length.
    // Names and types are unchanged, so HomeScreen's collect sites need no edit.
    //
    // v0.5.2 perf (D2): was `combine(getAllOccurrences(), _selectedDate)` — a `SELECT *` over the
    // ENTIRE occurrence history, re-folded on every occurrence write while the app is open. The
    // current streak can only count consecutive days backward from the selected date, so a window
    // of [selectedDate − STREAK_WINDOW_DAYS, selectedDate + 1d) holds every row the maths can
    // reach. The window follows `_selectedDate` through `flatMapLatest`, so a day tap re-subscribes
    // ONE bounded query instead of keeping a whole-history flow hot; `distinctUntilChanged()`
    // drops identical re-emissions. Only `.currentStreak` is read here, and it is unaffected by
    // rows older than the window. Home does not use `longestStreak` (Detail loads the full list).
    val habitStreak: StateFlow<Int> =
        combine(_selectedDate, streakCfg) { date, cfg -> date to cfg }
            .flatMapLatest { (date, cfg) ->
                val start = DateTimeUtils.startOfDay(date.minusDays(STREAK_WINDOW_DAYS))
                val end = DateTimeUtils.startOfDay(date.plusDays(1))
                habitRepository.database.habitOccurrenceDao()
                    .getAllOccurrencesInTimeRange(start, end)
                    .map { occs -> calculateHabitStreaks(occs, date, cfg.mode, cfg.restDays).currentStreak }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val foodMedStreak: StateFlow<Int> =
        combine(_selectedDate, streakCfg) { date, cfg -> date to cfg }
            .flatMapLatest { (date, cfg) ->
                val start = DateTimeUtils.startOfDay(date.minusDays(STREAK_WINDOW_DAYS))
                val end = DateTimeUtils.startOfDay(date.plusDays(1))
                foodMedRepository.database.foodMedOccurrenceDao()
                    .getAllOccurrencesInTimeRange(start, end)
                    .map { occs -> calculateFoodMedStreaks(occs, date, cfg.mode, cfg.restDays).currentStreak }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Greeting: deterministic per calendar day (testable, and stable across process death), with
    // the time-of-day word recomputed whenever the wall-clock tick crosses a boundary (REV-22).
    val greeting: StateFlow<String> =
        combine(settings, _now) { s, now ->
            renderGreeting(
                greetingIndexFor(now.toLocalDate()), s.userName.trim(), todWordFor(now.toLocalTime()),
                s.greetingTone, s.greetingTimeWord
            )
        }
            .flowOn(Dispatchers.Default)
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                renderGreeting(greetingIndexFor(LocalDate.now()), "", todWordFor(LocalTime.now()))
            )

    /** rec 2 (H4) — the "$n left today" hero-line phrasing style. */
    val heroStyle: StateFlow<String> =
        settings.map { it.heroStyle }.distinctUntilChanged()
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "COUNT_LEFT")

    /** Name + photo for the Today-screen avatar (taps through to Settings). */
    val profile: StateFlow<ProfileUi> =
        settings
            .map { ProfileUi(it.userName.trim(), it.profilePhotoPath) }
            .catch { com.daybook.app.util.recordUnhandledException(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi("", null))

    private fun greetingIndexFor(date: LocalDate): Int =
        Math.floorMod(date.toEpochDay(), GREETINGS_ANON.size.toLong()).toInt()

    private fun todWordFor(t: LocalTime): String = when (t.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

    /** Millis until the next of {00:00, 05:00, 12:00, 17:00, 22:00} local — the date and greeting boundaries. */
    private fun millisUntilNextBoundary(): Long {
        val now = LocalDateTime.now()
        val next = intArrayOf(0, 5, 12, 17, 22)
            .map { h ->
                if (h == 0) now.toLocalDate().plusDays(1).atStartOfDay()
                else now.toLocalDate().atTime(h, 0)
            }
            .filter { it.isAfter(now) }
            .minOrNull() ?: now.toLocalDate().plusDays(1).atStartOfDay()
        return Duration.between(now, next).toMillis().coerceAtLeast(1_000L)
    }

    /**
     * v0.5.3 Phase 7 (audit A9 — synthetic backfill-slot synthesis cost, informational):
     * for a PAST day, the loop below synthesises one [HomeItem] per (item × scheduled time-of-day)
     * that has no occurrence row yet — a day the 7-day rolling window never covered, or an evicted
     * month. That is O(items × times/day) per past-day render, done only when the user scrubs the
     * week strip onto a past date (not on the hot today path). At 100× (~200 synthetic items for a
     * heavy day) it is a few ms of list construction — acceptable, no batching needed.
     */
    private fun buildItems(
        date: LocalDate,
        today: LocalDate,
        habitOccurrences: List<com.daybook.app.data.model.HabitOccurrence>,
        foodMedOccurrences: List<com.daybook.app.data.model.FoodMedOccurrence>,
        habits: List<Habit>,
        tasks: List<FoodMedTask>,
        checkin: String,
        clock24h: Boolean
    ): List<HomeItem> {
        val isPastDay = date.isBefore(today)
        val isFutureDay = date.isAfter(today)
        val habitsById = habits.associateBy { it.id }
        val tasksById = tasks.associateBy { it.id }
        val items = mutableListOf<HomeItem>()
        val zone = java.time.ZoneId.systemDefault()

        val habitOccIds = habitOccurrences.mapTo(HashSet()) { it.id }
        val foodMedOccIds = foodMedOccurrences.mapTo(HashSet()) { it.id }

        fun habitEffectiveTimes(h: Habit): List<java.time.LocalTime> {
            val json = if (h.type == com.daybook.app.data.model.HabitType.BATCH) checkin else h.timesJson
            return DateTimeUtils.jsonToTimes(json)
        }

        habitOccurrences.forEach { occurrence ->
            val h = habitsById[occurrence.habitId] ?: return@forEach
            val pending = occurrence.status == Occurrence.Status.PENDING
            val backfillable = isPastDay && canBackfill(date, today, h.createdAt, h.activeDaysJson, h.isArchived)
            val actionable = !isFutureDay && ((pending && !isPastDay) || (pending && backfillable))
            // Journal-as-habit round: a JOURNAL habit resolves through the chat/edit-form, never
            // the plain Complete/Skip buttons — mirrors how a FoodMed JOURNAL task sets canComplete
            // = false / canReply = actionable below.
            val isJournalHabit = h.type == com.daybook.app.data.model.HabitType.JOURNAL
            items.add(
                HomeItem(
                    // F3-1: identity, not table-of-origin. A synthetic past-day slot and the real
                    // occurrence row that later replaces it now produce the SAME key (occId is
                    // "$itemId:$millis" and scheduledFor == that millis), so a backfill stops
                    // re-keying the card and discarding its reply draft.
                    id = "h:${h.id}:${occurrence.scheduledFor}",
                    title = h.title,
                    subtitle = h.description.takeIf { it.isNotBlank() },
                    iconKey = h.iconKey,
                    colorTag = h.colorTag.name,
                    scheduledTime = DateTimeUtils.formatTime(
                        DateTimeUtils.timestampToLocalTime(occurrence.scheduledFor), clock24h
                    ),
                    scheduledEpoch = occurrence.scheduledFor,
                    isHabit = true,
                    detailId = h.id,
                    occurrenceId = occurrence.id,
                    canComplete = actionable && !isJournalHabit,
                    canSkip = actionable,
                    canSnooze = actionable && !isPastDay,
                    canReply = actionable && isJournalHabit,
                    responseText = null,
                    statusLabel = statusLabelFor(occurrence.status, isPastDay, backfillable),
                    isPast = isPastDay,
                    isFuture = isFutureDay,
                    isHabitJournal = isJournalHabit,
                    isBackfill = pending && backfillable
                )
            )
        }

        foodMedOccurrences.forEach { occurrence ->
            val t = tasksById[occurrence.taskId] ?: return@forEach
            val pending = occurrence.status == Occurrence.Status.PENDING
            val backfillable = isPastDay && canBackfill(date, today, t.createdAt, t.activeDaysJson, t.isArchived)
            val actionable = !isFutureDay && ((pending && !isPastDay) || (pending && backfillable))
            items.add(
                HomeItem(
                    id = "f:${t.id}:${occurrence.scheduledFor}",   // F3-1: see the habit branch
                    title = t.label,
                    subtitle = occurrence.responseText.takeIf { it.isNotBlank() },
                    iconKey = t.iconKey,
                    colorTag = t.colorTag.name,
                    scheduledTime = DateTimeUtils.formatTime(
                        DateTimeUtils.timestampToLocalTime(occurrence.scheduledFor), clock24h
                    ),
                    scheduledEpoch = occurrence.scheduledFor,
                    isHabit = false,
                    detailId = t.id,
                    occurrenceId = occurrence.id,
                    canComplete = false,
                    canSkip = actionable,
                    canSnooze = actionable && !isPastDay,
                    canReply = actionable,
                    responseText = occurrence.responseText.takeIf { it.isNotBlank() },
                    statusLabel = statusLabelFor(occurrence.status, isPastDay, backfillable),
                    isPast = isPastDay,
                    isFuture = isFutureDay,
                    isJournal = t.type == com.daybook.app.data.model.TaskType.JOURNAL,
                    isBackfill = pending && backfillable,
                    promptMessage = t.promptMessage,
                    isFood = t.type == com.daybook.app.data.model.TaskType.FOOD,
                    loggedRedFlag = occurrence.redFlag,
                    loggedSuspectedFood = occurrence.suspectedFood?.takeIf { it.isNotBlank() },
                    defaultRedFlag = t.defaultRedFlag,
                    defaultSuspectedFood = t.defaultSuspectedFood?.takeIf { it.isNotBlank() },
                    loggedOutsideFood = occurrence.outsideFood,
                    defaultOutsideFood = t.defaultOutsideFood
                )
            )
        }

        // v0.5.2 §9: on a PAST day, synthesise the scheduled slots that have no occurrence row yet
        // (a day the 7-day rolling window never covered, or an evicted month). The id scheme is
        // identical to OccurrenceScheduler.occId / mapDaysToOccurrences ("$itemId:$millis"), so a
        // row created on demand is idempotent with anything the cloud later hydrates.
        if (isPastDay) {
            habits.forEach { h ->
                // v0.5.5: "Ongoing" (STREAK) habits have no times and never appear on Today —
                // explicit guard so a future default-time change can't synthesise slots for them.
                if (h.type == com.daybook.app.data.model.HabitType.STREAK) return@forEach
                if (!canBackfill(date, today, h.createdAt, h.activeDaysJson, h.isArchived)) return@forEach
                val isJournalHabit = h.type == com.daybook.app.data.model.HabitType.JOURNAL
                habitEffectiveTimes(h).forEach { t ->
                    val millis = date.atTime(t).atZone(zone).toInstant().toEpochMilli()
                    if ("${h.id}:$millis" in habitOccIds) return@forEach
                    items.add(
                        HomeItem(
                            id = "h:${h.id}:$millis",   // F3-1: same key the real row will carry
                            title = h.title,
                            subtitle = h.description.takeIf { it.isNotBlank() },
                            iconKey = h.iconKey,
                            colorTag = h.colorTag.name,
                            scheduledTime = DateTimeUtils.formatTime(t, clock24h),
                            scheduledEpoch = millis,
                            isHabit = true,
                            detailId = h.id,
                            occurrenceId = null,
                            canComplete = !isJournalHabit,
                            canSkip = true,
                            canSnooze = false,
                            canReply = isJournalHabit,
                            responseText = null,
                            statusLabel = null,
                            isPast = true,
                            isFuture = false,
                            isHabitJournal = isJournalHabit,
                            isBackfill = true
                        )
                    )
                }
            }
            tasks.forEach { t0 ->
                if (!canBackfill(date, today, t0.createdAt, t0.activeDaysJson, t0.isArchived)) return@forEach
                DateTimeUtils.jsonToTimes(t0.timesJson).forEach { t ->
                    val millis = date.atTime(t).atZone(zone).toInstant().toEpochMilli()
                    if ("${t0.id}:$millis" in foodMedOccIds) return@forEach
                    items.add(
                        HomeItem(
                            id = "f:${t0.id}:$millis",   // F3-1: same key the real row will carry
                            title = t0.label,
                            subtitle = null,
                            iconKey = t0.iconKey,
                            colorTag = t0.colorTag.name,
                            scheduledTime = DateTimeUtils.formatTime(t, clock24h),
                            scheduledEpoch = millis,
                            isHabit = false,
                            detailId = t0.id,
                            occurrenceId = null,
                            canComplete = false,
                            canSkip = true,
                            canSnooze = false,
                            canReply = true,
                            responseText = null,
                            statusLabel = null,
                            isPast = true,
                            isFuture = false,
                            isJournal = t0.type == com.daybook.app.data.model.TaskType.JOURNAL,
                            isBackfill = true,
                            promptMessage = t0.promptMessage,
                            isFood = t0.type == com.daybook.app.data.model.TaskType.FOOD,
                            defaultRedFlag = t0.defaultRedFlag,
                            defaultSuspectedFood = t0.defaultSuspectedFood?.takeIf { it.isNotBlank() },
                            defaultOutsideFood = t0.defaultOutsideFood
                        )
                    )
                }
            }
        }

        return items.sortedBy { it.scheduledEpoch }
    }

    private fun statusLabelFor(status: Occurrence.Status, isPastDay: Boolean, backfillable: Boolean): String? = when (status) {
        Occurrence.Status.COMPLETED -> "Done"
        Occurrence.Status.SKIPPED -> "Skipped"
        Occurrence.Status.LOGGED -> LOGGED_LABEL
        // A past-day PENDING item is "Missed" ONLY when it can't be backfilled — otherwise the card
        // would show "Missed" and an action button at the same time (v0.5.2 / 5B.3).
        Occurrence.Status.PENDING -> if (isPastDay && !backfillable) MISSED_LABEL else null
    }

    fun completeItem(item: HomeItem) = safeLaunch {
        when {
            !item.isHabit -> Unit
            item.isBackfill -> occurrenceScheduler.backfillHabit(
                item.detailId, item.scheduledEpoch, Occurrence.Status.COMPLETED, Event.Action.COMPLETED
            )
            item.occurrenceId != null -> occurrenceScheduler.completeHabit(item.occurrenceId)
        }
    }

    fun skipItem(item: HomeItem) = safeLaunch {
        when {
            item.isBackfill && item.isHabit -> occurrenceScheduler.backfillHabit(
                item.detailId, item.scheduledEpoch, Occurrence.Status.SKIPPED, Event.Action.SKIPPED
            )
            item.isBackfill -> occurrenceScheduler.backfillFoodMed(
                item.detailId, item.scheduledEpoch, Occurrence.Status.SKIPPED, "", null, Event.Action.SKIPPED
            )
            item.occurrenceId == null -> Unit
            item.isHabit -> occurrenceScheduler.skipHabit(item.occurrenceId)
            else -> occurrenceScheduler.skipFoodMed(item.occurrenceId)
        }
    }

    fun snoozeItem(item: HomeItem) {
        val occ = item.occurrenceId ?: return
        safeLaunch {
            if (item.isHabit) occurrenceScheduler.snoozeHabit(occ) else occurrenceScheduler.snoozeFoodMed(occ)
        }
    }

    /** v0.5.3 item 1: one-tap undo of a resolved reminder back to a blank PENDING slot. */
    fun revertItem(item: HomeItem) {
        val occ = item.occurrenceId ?: return
        safeLaunch {
            if (item.isHabit) occurrenceScheduler.revertHabit(occ)
            else occurrenceScheduler.revertFoodMed(occ)
        }
    }

    fun replyToItem(
        item: HomeItem,
        responseText: String,
        redFlag: com.daybook.app.data.model.RedFlag? = null,
        suspectedFood: String? = null,
        outsideFood: Boolean? = null
    ) = safeLaunch {
        val flag = redFlag?.takeIf { item.isFood }
        val suspected = suspectedFood?.takeIf { item.isFood }
        val outside = outsideFood?.takeIf { item.isFood }
        when {
            item.isHabit -> Unit
            item.isBackfill -> occurrenceScheduler.backfillFoodMed(
                item.detailId, item.scheduledEpoch, Occurrence.Status.LOGGED, responseText.trim(), null,
                Event.Action.REPLIED, redFlag = flag, suspectedFood = suspected, outsideFood = outside
            )
            item.occurrenceId != null -> occurrenceScheduler.logFoodMed(
                item.occurrenceId, responseText, redFlag = flag, suspectedFood = suspected, outsideFood = outside
            )
        }
    }
}

private val GREETINGS_ANON = listOf(
    "Hi there",
    "Welcome back",
    "What's the plan?",
    "Ready when you are",
    "Let's go",
    "TOD_PLACEHOLDER",
    "Good to see you",
    "One day at a time",
)

private fun namedGreeting(i: Int, name: String, tod: String?): String = when (i) {
    0 -> "Hi, $name"
    1 -> "Welcome back, $name"
    2 -> "What's the plan, $name?"
    3 -> "Hey $name, ready?"
    4 -> "Let's go, $name"
    // rec 2 (H2): when the time-of-day word is off this template drops back to a neutral line.
    5 -> if (tod != null) "$tod, $name" else "Hi, $name"
    6 -> "Good to see you, $name"
    else -> "One day at a time, $name"
}

/**
 * rec 2 (H1/H2) — the greeting line for the given [tone] and [timeWord] flag.
 *  - WARM (default): today's rotating pool (unchanged when [timeWord] is on).
 *  - PLAIN: one fixed line — "Hi, {name}" / "Hi there".
 *  - MINIMAL: empty string — [HomeHeader] skips the greeting `Text`.
 * [timeWord] = false replaces the one "Good morning/…" template with a neutral phrase.
 * Pure — see `GreetingRenderTest`.
 */
internal fun renderGreeting(
    i: Int,
    name: String,
    tod: String,
    tone: String = "WARM",
    timeWord: Boolean = true
): String = when (tone) {
    "MINIMAL" -> ""
    "PLAIN" -> if (name.isBlank()) "Hi there" else "Hi, $name"
    else -> {
        val effectiveTod = if (timeWord) tod else null
        if (name.isBlank()) {
            GREETINGS_ANON[i].let {
                if (it == "TOD_PLACEHOLDER") (effectiveTod ?: "Hi there") else it
            }
        } else {
            namedGreeting(i, name, effectiveTod)
        }
    }
}
