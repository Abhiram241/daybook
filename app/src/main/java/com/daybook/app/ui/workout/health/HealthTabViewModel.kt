package com.daybook.app.ui.workout.health

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.HealthRepository
import com.daybook.app.data.health.HealthAggregate
import com.daybook.app.data.health.HealthConnectAvailability
import com.daybook.app.data.health.HealthConnectSdkState
import com.daybook.app.data.health.HealthPermissions
import com.daybook.app.data.health.aggregateHealthDays
import com.daybook.app.data.health.toHiddenCardsCsv
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.HealthDay
import com.daybook.app.data.model.HealthSession
import com.daybook.app.data.model.HealthWeightReading
import com.daybook.app.util.recordUnhandledException
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** See `refreshNow()`'s `finally` — the shortest amount of time the pull-to-refresh indicator is
 *  guaranteed to stay visible, so a very fast "nothing changed" sync doesn't finish invisibly fast. */
private const val MIN_REFRESH_INDICATOR_MILLIS = 600L

enum class HealthTabMode { DAY, AGGREGATE }

enum class HealthRangePreset { LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, LAST_3_MONTHS, CUSTOM }

data class HealthRangeSelection(
    val preset: HealthRangePreset,
    val start: LocalDate,
    val end: LocalDate
)

/** §6.2's degradation ladder, relocated to the `Health` tab per §7.4's superseding decision. */
sealed class HealthLadderState {
    object Unavailable : HealthLadderState()
    object UpdateRequired : HealthLadderState()
    object NotConnected : HealthLadderState()
    data class Connected(val missingCount: Int) : HealthLadderState()
}

data class HealthTabUiState(
    val mode: HealthTabMode = HealthTabMode.DAY,
    val ladder: HealthLadderState = HealthLadderState.NotConnected,
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val calendarExpanded: Boolean = false,
    val weekStart: String = "MONDAY",
    val day: HealthDay? = null,
    val daySessions: List<HealthSession> = emptyList(),
    // HEALTH_VITALS_RICHNESS_PLAN.md §6 — only used by the Weight detail sheet's "N readings
    // today" expandable list; Day mode only, `Range` mode's weight figure stays the aggregate mean.
    val dayWeightReadings: List<HealthWeightReading> = emptyList(),
    val range: HealthRangeSelection = defaultRange(),
    val rangeDays: List<HealthDay> = emptyList(),
    val rangeSessions: List<HealthSession> = emptyList(),
    val statusLine: String? = null,
    val statusIsFailure: Boolean = false,
    val isRefreshing: Boolean = false,
    val isImportingPast: Boolean = false,
    val actionResult: String? = null,
    // L4 fix — this was a computed `get()` (`aggregateHealthDays(rangeDays)`, 17 `mapNotNull` +
    // `average` passes over the list), so it re-ran on EVERY read — at least six times per
    // `HealthTabScreen` composition plus once per `HealthDetailSheet` row; with `LAST_3_MONTHS`
    // (92 days) that's ~1,500 list traversals per frame. Now a regular constructor property,
    // computed once per state object (in the `combine` below) rather than once per property read.
    val rangeAggregate: HealthAggregate = aggregateHealthDays(rangeDays),
    // AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §3.2 — Weight's unit follows the user's
    // setting instead of a hardcoded "kg".
    val weightUnit: String = "KG",
    // User request — the Health tab's "hide cards" picker. CSV-persisted in AppSettings
    // (device-only, same treatment as every other app_settings column since v16).
    val hiddenCards: Set<com.daybook.app.data.health.HealthCardKind> = emptySet(),
    val showCardVisibilitySheet: Boolean = false,
    // User request — no more persistent "Up to date — nothing new from your band." caption; a
    // transient toast on a SUCCESSFUL manual refresh only, which then goes away on its own
    // (mirrors HomeViewModel's `UndoFeedback` — token bumped only when a toast should actually
    // show, never on every recomposition, so the toast doesn't re-fire on an unrelated state change).
    val refreshToastToken: Int = 0,
    val refreshToastMessage: String = ""
) {
}

private fun defaultRange(): HealthRangeSelection {
    val today = LocalDate.now()
    return HealthRangeSelection(HealthRangePreset.LAST_7_DAYS, today.minusDays(6), today)
}

@HiltViewModel
class HealthTabViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val healthRepository: HealthRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val ymd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _selectedDate = MutableStateFlow(LocalDate.now(zoneId))
    private val _calendarExpanded = MutableStateFlow(false)
    private val _mode = MutableStateFlow(HealthTabMode.DAY)
    private val _range = MutableStateFlow(defaultRange())
    private val _actionResult = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isImportingPast = MutableStateFlow(false)
    private val _ladderRefresh = MutableStateFlow(0)
    private val _showCardVisibilitySheet = MutableStateFlow(false)
    private val _refreshToastToken = MutableStateFlow(0)
    private val _refreshToastMessage = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val saved = appSettingsRepository.getSettings().healthTabLastMode
            _mode.value = if (saved == 1) HealthTabMode.AGGREGATE else HealthTabMode.DAY
        }
    }

    private val ladder = _ladderRefresh.flatMapLatest {
        kotlinx.coroutines.flow.flow {
            val state = HealthConnectAvailability.state(context)
            emit(
                when (state) {
                    HealthConnectSdkState.UNAVAILABLE -> HealthLadderState.Unavailable
                    HealthConnectSdkState.UPDATE_REQUIRED -> HealthLadderState.UpdateRequired
                    HealthConnectSdkState.AVAILABLE -> {
                        val granted = healthRepository.grantedPermissions()
                        if (granted.isEmpty()) HealthLadderState.NotConnected
                        else HealthLadderState.Connected(healthRepository.missingPermissions(granted).size)
                    }
                }
            )
        }
    }

    // C2 fix — every one of these cold Flows now carries the same `.catch{}` guard
    // `DailyReportRepository.kt` copied from this file's own established pattern (per its own
    // KDoc) and then had added to itself as BUG_AUDIT.md's C1 fix; this is the original template
    // finally getting the same treatment, so a DB/IO failure on any single source degrades that
    // one field rather than crashing the whole combine chain / the Health tab.
    private val dayFlow = _selectedDate.flatMapLatest { date ->
        database.healthDao().observeDay(date.format(ymd))
    }.flowOn(Dispatchers.Default).catch { recordUnhandledException(it); emit(null) }

    private val daySessionsFlow = _selectedDate.flatMapLatest { date ->
        database.healthDao().observeSessionsForDay(date.format(ymd))
    }.flowOn(Dispatchers.Default).catch { recordUnhandledException(it); emit(emptyList()) }

    private val dayWeightReadingsFlow = _selectedDate.flatMapLatest { date ->
        database.healthDao().observeWeightReadingsForDay(date.format(ymd))
    }.flowOn(Dispatchers.Default).catch { recordUnhandledException(it); emit(emptyList()) }

    private val rangeDaysFlow = _range.flatMapLatest { r ->
        database.healthDao().observeDaysInRange(r.start.format(ymd), r.end.format(ymd))
    }.flowOn(Dispatchers.Default).catch { recordUnhandledException(it); emit(emptyList()) }

    private val rangeSessionsFlow = _range.flatMapLatest { r ->
        database.healthDao().observeSessionsInRange(r.start.format(ymd), r.end.format(ymd))
    }.flowOn(Dispatchers.Default).catch { recordUnhandledException(it); emit(emptyList()) }

    private val weekStartFlow = appSettingsRepository.observeSettings().map { it.weekStart }.distinctUntilChanged()
        .catch { recordUnhandledException(it); emit("MONDAY") }

    private val weightUnitFlow = appSettingsRepository.observeSettings().map { it.weightUnit }.distinctUntilChanged()
        .catch { recordUnhandledException(it); emit("KG") }

    private val hiddenCardsFlow = appSettingsRepository.observeSettings()
        .map { com.daybook.app.data.health.parseHiddenHealthCards(it.healthHiddenCards) }
        .distinctUntilChanged()
        .catch { recordUnhandledException(it); emit(emptySet()) }

    // L6 fix — `LocalDate.now(zoneId)` used to be evaluated straight inside the final combine
    // lambda below, so it only re-ran when some OTHER source in this combine emitted; a screen
    // left open across midnight kept `today` (and so the `WeekStrip` highlight) on yesterday
    // until something unrelated changed. Folded in as its own combine source instead.
    private val todayFlow = com.daybook.app.util.currentDateFlow(zoneId)

    val uiState = combine(
        combine(_mode, ladder, _selectedDate, _calendarExpanded, combine(weekStartFlow, todayFlow, weightUnitFlow) { ws, today, wu -> Triple(ws, today, wu) }) { mode, ladder, sel, exp, wsToday ->
            arrayOf(mode, ladder, sel, exp, wsToday)
        },
        combine(dayFlow, daySessionsFlow, _range, rangeDaysFlow, dayWeightReadingsFlow) { d, ds, range, rd, dwr -> arrayOf(d, ds, range, rd, dwr) },
        combine(
            combine(rangeSessionsFlow, _actionResult, _isRefreshing, _isImportingPast) { rs, action, refreshing, importing ->
                arrayOf(rs, action, refreshing, importing)
            },
            hiddenCardsFlow,
            _showCardVisibilitySheet,
            combine(_refreshToastToken, _refreshToastMessage) { token, msg -> token to msg }
        ) { quad, hidden, sheetOpen, toast -> arrayOf(quad[0], quad[1], quad[2], quad[3], hidden, sheetOpen, toast) }
    ) { a, b, c ->
        @Suppress("UNCHECKED_CAST")
        val wsToday = a[4] as Triple<String, LocalDate, String>
        @Suppress("UNCHECKED_CAST")
        val toast = c[6] as Pair<Int, String>
        HealthTabUiState(
            mode = a[0] as HealthTabMode,
            ladder = a[1] as HealthLadderState,
            selectedDate = a[2] as LocalDate,
            today = wsToday.second,
            calendarExpanded = a[3] as Boolean,
            weekStart = wsToday.first,
            weightUnit = wsToday.third,
            day = b[0] as HealthDay?,
            daySessions = b[1] as List<HealthSession>,
            range = b[2] as HealthRangeSelection,
            rangeDays = b[3] as List<HealthDay>,
            dayWeightReadings = b[4] as List<HealthWeightReading>,
            rangeSessions = c[0] as List<HealthSession>,
            statusLine = healthRepository.statusLine(),
            statusIsFailure = healthRepository.statusIsFailure(),
            actionResult = c[1] as String?,
            isRefreshing = c[2] as Boolean,
            isImportingPast = c[3] as Boolean,
            hiddenCards = c[4] as Set<com.daybook.app.data.health.HealthCardKind>,
            showCardVisibilitySheet = c[5] as Boolean,
            refreshToastToken = toast.first,
            refreshToastMessage = toast.second
        )
    }.catch { recordUnhandledException(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthTabUiState())

    fun selectDate(date: LocalDate) { _selectedDate.value = date }
    fun toggleCalendarExpanded() { _calendarExpanded.value = !_calendarExpanded.value }

    fun setMode(mode: HealthTabMode) {
        _mode.value = mode
        safeLaunch { appSettingsRepository.setHealthTabLastMode(if (mode == HealthTabMode.AGGREGATE) 1 else 0) }
    }

    fun setRangePreset(preset: HealthRangePreset) {
        val today = LocalDate.now(zoneId)
        val (start, end) = when (preset) {
            HealthRangePreset.LAST_7_DAYS -> today.minusDays(6) to today
            HealthRangePreset.LAST_30_DAYS -> today.minusDays(29) to today
            HealthRangePreset.THIS_MONTH -> today.withDayOfMonth(1) to today
            HealthRangePreset.LAST_3_MONTHS -> today.minusMonths(3).plusDays(1) to today
            HealthRangePreset.CUSTOM -> _range.value.start to _range.value.end
        }
        _range.value = HealthRangeSelection(preset, start, end)
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        val lo = if (start.isAfter(end)) end else start
        val hi = if (start.isAfter(end)) start else end
        _range.value = HealthRangeSelection(HealthRangePreset.CUSTOM, lo, hi)
    }

    /** Called after the OS consent sheet returns, regardless of outcome — re-checks the ladder. */
    fun onPermissionFlowFinished() {
        _ladderRefresh.value += 1
        refreshNow()
    }

    fun refreshNow() {
        safeLaunch {
            _isRefreshing.value = true
            _actionResult.value = null
            val startedAt = System.currentTimeMillis()
            try {
                // User bug report — "spinner is stuck forever": whatever the underlying cause
                // (a slow/hung Health Connect binder call, a device-specific stall), the pull-to-
                // refresh spinner must NEVER be able to spin indefinitely. A hard timeout guarantees
                // `_isRefreshing` always resolves within a bounded window no matter what happens
                // below — `finally` already reset it on a thrown exception, this covers a hang that
                // never throws at all.
                val r = kotlinx.coroutines.withTimeoutOrNull(20_000) { healthRepository.refreshNow() }
                when (r) {
                    is HealthRepository.PullOutcome.Success -> {
                        // User request — no more persistent "Up to date…" caption; a toast that
                        // shows only on a successful refresh and then goes away on its own (the
                        // Composable side keys `UndoSnack` off `refreshToastToken`, bumped here).
                        _refreshToastMessage.value = if (r.hasNewData) "Last updated just now." else "Up to date — nothing new from your band."
                        _refreshToastToken.value += 1
                    }
                    is HealthRepository.PullOutcome.Failure -> _actionResult.value = r.message
                    // H5 fix — see WorkoutSettingsViewModel.refreshHealthNow's identical fix.
                    HealthRepository.PullOutcome.PermissionsMissing ->
                        _actionResult.value = "Daybook no longer has access to your health data. Tap Connect to share it again."
                    null -> {
                        recordUnhandledException(java.util.concurrent.TimeoutException("HealthTabViewModel.refreshNow timed out after 20s"))
                        _actionResult.value = "Refresh took too long. Try again."
                    }
                }
                _ladderRefresh.value += 1
            } finally {
                // User report (live, on-device): after the concurrency fix, a routine incremental
                // sync (the common "no changes" case) can complete in well under 100ms — far
                // faster than the indicator's own fade-in animation, so `_isRefreshing` flipped
                // back to false before the spinner ever became visible ("totally missing", not
                // "stuck"). Enforce a floor on how long it stays true so the user actually gets to
                // see the refresh happen, regardless of how fast the real work finished.
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < MIN_REFRESH_INDICATOR_MILLIS) {
                    kotlinx.coroutines.delay(MIN_REFRESH_INDICATOR_MILLIS - elapsed)
                }
                _isRefreshing.value = false
            }
        }
    }

    fun importPastData() {
        safeLaunch {
            _isImportingPast.value = true
            _actionResult.value = null
            try {
                when (val r = healthRepository.importPastData()) {
                    is HealthRepository.PullOutcome.Success -> _actionResult.value = "Import complete."
                    is HealthRepository.PullOutcome.Failure -> _actionResult.value = r.message
                    HealthRepository.PullOutcome.PermissionsMissing ->
                        _actionResult.value = "Daybook can only see the last 30 days without access to your history."
                }
            } finally {
                _isImportingPast.value = false
            }
        }
    }

    fun requestPermissionsContract() = HealthPermissions.requestPermissionsContract()
    fun initialPermissionSet() = HealthPermissions.initialRequestSet()

    // User request — "hide parameters I don't want to see in health section".
    fun openCardVisibilitySheet() { _showCardVisibilitySheet.value = true }
    fun dismissCardVisibilitySheet() { _showCardVisibilitySheet.value = false }

    fun toggleCardHidden(kind: com.daybook.app.data.health.HealthCardKind) {
        val current = uiState.value.hiddenCards
        val next = if (kind in current) current - kind else current + kind
        safeLaunch { appSettingsRepository.setHealthHiddenCards(next.toHiddenCardsCsv()) }
    }
}
