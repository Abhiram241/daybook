package com.daybook.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.local.SessionAggregate
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSessionPhoto
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.bucketByHistorySection
import com.daybook.app.data.workout.bucketByMuscleGroup
import com.daybook.app.data.workout.dominantMuscleGroup
import com.daybook.app.data.workout.parseWeightUnit
import com.daybook.app.data.workout.pickThumbnailExerciseId
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

private const val HISTORY_LIMIT = 200

/** Item 13 (§13b) — "Date ▾ / Muscle ▾", the third filter-bar chip. Session-local (not persisted),
 *  same treatment `AddExerciseViewModel`'s own group chip already gets. */
enum class HistoryGroupingMode { DATE, MUSCLE }

/**
 * History tab redesign — grouped, richer rows (§2.1/§2.2), a single-exercise filter (open
 * question 3) and Edit/Duplicate-as-routine overflow actions (open question 4), on top of A6a's
 * original flat list.
 */
@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    /** One row's worth of pre-resolved display data — the aggregate and thumbnail are looked up
     *  via page-wide batch queries (§2.4), never per row. */
    data class HistoryRow(
        val session: WorkoutSession,
        val aggregate: SessionAggregate?,
        val thumbnail: CatalogExercise?,
        // Feature addition — History card's muscle-group bar chart; heaviest-first, kg volume per
        // group. Empty for an active/no-sets session, or one built entirely from bodyweight work.
        val muscleVolume: List<Pair<MuscleGroup, Float>> = emptyList(),
        // Item 12 — up to 3, ordered by `orderIndex`. Empty for the vast majority of sessions
        // (photos are optional); item 13 renders this as a thumbnail strip below the chart.
        val photos: List<WorkoutSessionPhoto> = emptyList()
    )

    private val rawSessions = repo.observeRecentSessions(HISTORY_LIMIT)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<WorkoutSession>())

    /** Unfiltered count, for the screen header's "N workouts" subtitle — matches §2.5: the
     *  exercise filter narrows the visible rows, it doesn't change what "History" as a whole is. */
    val sessions = rawSessions

    private val _aggregatesBySession = MutableStateFlow<Map<String, SessionAggregate>>(emptyMap())
    /** Item 11 — the exercise each session shows a picture of. Was the session's FIRST exercise;
     *  now whichever one [pickThumbnailExerciseId] settles on (stably, seeded off the session id). */
    private val _thumbnailExerciseBySession = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _thumbnailByExerciseId = MutableStateFlow<Map<String, CatalogExercise>>(emptyMap())
    private val _muscleVolumeBySession = MutableStateFlow<Map<String, List<Pair<MuscleGroup, Float>>>>(emptyMap())
    // Item 12 — same batched-per-page shape as the other `_xxxBySession` maps above.
    private val _photosBySession = MutableStateFlow<Map<String, List<WorkoutSessionPhoto>>>(emptyMap())

    init {
        // §2.4 — one aggregate query + one exercises query per change to the visible page of
        // sessions, mirroring WorkoutDetailViewModel's exerciseInfo pattern (never a query per row).
        // §2.5 fix — also re-runs on `observeSetsRevision()`'s emissions, so editing a completed
        // session's sets (without re-finishing, so `workout_sessions` itself never changes) still
        // refreshes this row's volume/set-count instead of going stale until the process restarts.
        combine(rawSessions, repo.observeSetsRevision()) { list, _ -> list }.onEach { list ->
            val ids = list.map { it.id }
            if (ids.isEmpty()) return@onEach
            runCatching { repo.sessionAggregates(ids) }.onSuccess { agg -> _aggregatesBySession.update { it + agg } }
            // Item 11 — this used to ask for each session's FIRST exercise only, and whatever came
            // back was the thumbnail; if that one exercise had no RepDB photo the card fell
            // through to the generic icon even when the rest of the session had artwork. Now every
            // block of the page comes back in one query (still never per-row) and
            // `pickThumbnailExerciseId` chooses stably per session.
            runCatching { repo.exerciseIdsForSessions(ids) }.onSuccess { bySession ->
                val newIds = bySession.values.flatten().distinct()
                    .filterNot { it in _thumbnailByExerciseId.value }
                if (newIds.isNotEmpty()) {
                    val resolved = newIds.mapNotNull { id -> repo.resolveExercise(id)?.let { id to it } }.toMap()
                    _thumbnailByExerciseId.update { it + resolved }
                }
                // Resolved AFTER the map above is filled, so `hasImage` can see this page's rows.
                val resolvedById = _thumbnailByExerciseId.value
                val picked = bySession.mapNotNull { (sessionId, exerciseIds) ->
                    pickThumbnailExerciseId(sessionId, exerciseIds) { id ->
                        resolvedById[id]?.imageId != null
                    }?.let { sessionId to it }
                }.toMap()
                _thumbnailExerciseBySession.update { it + picked }
            }
            // Same batched-per-page shape as the two queries above; re-runs on the same triggers
            // so an edited set's muscle-group bars stay in sync with its (also re-run) volume.
            runCatching { repo.muscleGroupVolumeForSessions(ids) }
                .onSuccess { breakdown -> _muscleVolumeBySession.update { it + breakdown } }
            // Item 12 — one batched query per page, same "never a query per row" rule.
            runCatching { repo.photosForSessions(ids) }
                .onSuccess { photos -> _photosBySession.update { it + photos.groupBy { p -> p.sessionId } } }
        }.launchIn(viewModelScope)
    }

    val weightUnit = appSettingsRepository.observeSettings()
        .map { parseWeightUnit(it.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    // §2.4 fix — this used to be its own `stateIn(WhileSubscribed)` flow with zero collectors
    // (nothing ever subscribed to it, so `observeSettings()` was never actually collected and it
    // stayed frozen at the "MONDAY" default), and was read via `.value` inside `sections`'
    // `combine` transform rather than being one of its inputs — so even a live value would never
    // have re-triggered a re-bucket. Now a plain flow, combined directly below.
    private val weekStart = appSettingsRepository.observeSettings().map { it.weekStart }

    // ------------------------------------------------------------ exercise filter (open question 3)

    private val _availableExercises = MutableStateFlow<List<CatalogExercise>>(emptyList())
    val availableExercises = _availableExercises.asStateFlow()

    init {
        safeLaunch {
            runCatching { repo.loggedExercisesForFilter() }.onSuccess { _availableExercises.value = it }
        }
    }

    private val _selectedExercise = MutableStateFlow<CatalogExercise?>(null)
    val selectedExercise = _selectedExercise.asStateFlow()

    /** null == no filter active (show everything); non-null == only these session ids match. */
    private val _filterSessionIds = MutableStateFlow<Set<String>?>(null)

    fun selectExerciseFilter(exercise: CatalogExercise?) {
        _selectedExercise.value = exercise
        if (exercise == null) {
            _filterSessionIds.value = null
            return
        }
        safeLaunch {
            runCatching { repo.sessionIdsForExercise(exercise.id) }
                .onSuccess { _filterSessionIds.value = it }
        }
    }

    // ------------------------------------------------------------------ muscle-group filter + grouping mode (item 13, §13b)

    /** null == no filter active. Intersects with the exercise filter above (both, when set, must
     *  match) — this one needs no repo round trip: `dominantMuscleGroup` is derived from data
     *  already loaded for the chart, so it's applied in [sections]' own transform. */
    private val _selectedMuscleGroupFilter = MutableStateFlow<MuscleGroup?>(null)
    val selectedMuscleGroupFilter = _selectedMuscleGroupFilter.asStateFlow()
    fun selectMuscleGroupFilter(group: MuscleGroup?) { _selectedMuscleGroupFilter.value = group }

    /** Session-local, not persisted — same reasoning `AddExerciseViewModel`'s group chip already
     *  documents for an identical "which chip is active" choice. */
    private val _groupingMode = MutableStateFlow(HistoryGroupingMode.DATE)
    val groupingMode = _groupingMode.asStateFlow()
    fun setGroupingMode(mode: HistoryGroupingMode) { _groupingMode.value = mode }

    // ------------------------------------------------------------------------- combined UI state

    // Kotlin's typed `combine` tops out at 5 flows per call, but nothing stops chaining separate
    // 5-flow calls — this one folds the filter/grouping inputs (5 of them) into one bundle so
    // `sections`' own combine below stays at its own 5-flow ceiling.
    private data class FilterBundle(
        val exerciseFilterIds: Set<String>?,
        val weekStart: String,
        val photosBySession: Map<String, List<WorkoutSessionPhoto>>,
        val muscleGroupFilter: MuscleGroup?,
        val groupingMode: HistoryGroupingMode
    )
    private val filterAndWeekStart = combine(
        _filterSessionIds, weekStart, _photosBySession, _selectedMuscleGroupFilter, _groupingMode
    ) { filterIds, ws, photos, muscleFilter, mode -> FilterBundle(filterIds, ws, photos, muscleFilter, mode) }

    // Kotlin's typed `combine` tops out at 5 flows too — folding the thumbnails + muscle-volume
    // maps into one pair keeps this `combine` at 5, same trick `filterAndWeekStart` uses above.
    private val thumbnailsAndMuscleVolume = combine(_thumbnailByExerciseId, _muscleVolumeBySession) { t, m -> t to m }

    val sections = combine(
        rawSessions, _aggregatesBySession, _thumbnailExerciseBySession, thumbnailsAndMuscleVolume, filterAndWeekStart
    ) { list, aggregates, thumbnailExercise, (thumbnails, muscleVolume), bundle ->
        val visible = if (bundle.exerciseFilterIds == null) list else list.filter { it.id in bundle.exerciseFilterIds }
        val rows = visible.map { s ->
            HistoryRow(
                session = s,
                aggregate = aggregates[s.id],
                thumbnail = thumbnailExercise[s.id]?.let { thumbnails[it] },
                muscleVolume = muscleVolume[s.id].orEmpty(),
                photos = bundle.photosBySession[s.id].orEmpty()
            )
        }
        // Item 13 (§13b) — the muscle-group chip intersects with the exercise filter above: a row
        // must pass both when both are set. `dominantMuscleGroup` is the same derivation the "group
        // by muscle" mode below uses, so a session shown under a chip's filter is always the same
        // one that mode would file it under.
        val muscleFiltered = if (bundle.muscleGroupFilter == null) rows
        else rows.filter { dominantMuscleGroup(it.muscleVolume) == bundle.muscleGroupFilter }
        when (bundle.groupingMode) {
            HistoryGroupingMode.DATE -> bucketByHistorySection(muscleFiltered, { it.session.localDate }, LocalDate.now(), bundle.weekStart)
            HistoryGroupingMode.MUSCLE -> bucketByMuscleGroup(muscleFiltered) { dominantMuscleGroup(it.muscleVolume) }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------------------------ actions

    private val _deletedToken = MutableStateFlow(0)
    val deletedToken = _deletedToken.asStateFlow()

    /** A second, differently-worded toast for the new overflow actions — kept separate from
     *  [deletedToken] so its message can vary ("Renamed", "Saved as routine") without disturbing
     *  the existing delete-undo-style toast's fixed text. */
    private val _actionToken = MutableStateFlow(0)
    val actionToken = _actionToken.asStateFlow()
    private val _actionText = MutableStateFlow("")
    val actionText = _actionText.asStateFlow()

    private fun announce(text: String) {
        _actionText.value = text
        _actionToken.value++
    }

    fun deleteSession(id: String) = safeLaunch {
        runCatching { repo.deleteSession(id) }
            .onSuccess { _deletedToken.value++ }
            .onFailure { com.daybook.app.util.recordUnhandledException(it) }
    }

    /** Overflow "Edit" (open question 4) — rename only. §1.5 fix — `renameSession` now returns
     *  false (rather than silently no-oping) if the session row is already gone; only announce
     *  "Renamed" when it actually wrote something, instead of always claiming success. */
    fun renameSession(id: String, title: String) = safeLaunch {
        runCatching { repo.renameSession(id, title) }
            .onSuccess { renamed -> if (renamed) announce("Renamed") else announce("Couldn't rename — that workout is no longer available.") }
            .onFailure { com.daybook.app.util.recordUnhandledException(it) }
    }

    /** Overflow "Duplicate as routine" (open question 4) — reuses the routine-creation path
     *  ([WorkoutRepository.createRoutineFromSession] -> [WorkoutRepository.createRoutine]). */
    fun duplicateAsRoutine(session: WorkoutSession) = safeLaunch {
        val name = session.title?.takeIf { it.isNotBlank() } ?: session.localDate
        runCatching { repo.createRoutineFromSession(session.id, name) }
            .onSuccess { announce("Saved as routine") }
            .onFailure { com.daybook.app.util.recordUnhandledException(it) }
    }

    private val _newSessionId = MutableStateFlow<String?>(null)
    val newSessionId = _newSessionId.asStateFlow()
    fun clearNewSessionId() { _newSessionId.value = null }

    /** §2.14 fix — the empty state's "Start an empty workout" CTA used to be a no-op
     *  (`onAction = { /* landing owns starting a session */ }`); this mirrors
     *  `WorkoutHomeViewModel.startEmptyWorkout` so it actually starts one from History too. */
    fun startEmptyWorkout() = safeLaunch {
        runCatching { repo.startEmptySession() }
            .onSuccess { _newSessionId.value = it }
            .onFailure { com.daybook.app.util.recordUnhandledException(it) }
    }
}
