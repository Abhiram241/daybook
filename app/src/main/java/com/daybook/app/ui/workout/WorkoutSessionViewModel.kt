package com.daybook.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.SessionStats
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.parseWeightUnit
import com.daybook.app.data.workout.sessionStats
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One exercise block, everything the set table needs, immutable primitives only (P5). */
data class BlockUi(
    val block: WorkoutExercise,
    val exerciseName: String,
    val trackingMode: String,
    // Bug fix (post-A6) — carried through so the block header can show the real RepDB
    // illustration (`ExerciseThumbnail`) instead of the generic taxonomy icon.
    val imageId: String? = null,
    val hasStartPeak: Boolean = false,
    // BEAST_MODE_REDESIGN_PLAN.md §3.7 — carried through so the block card can tint itself by
    // muscle group instead of always CardTints.Neutral.
    val primaryMuscle: MuscleGroup = MuscleGroup.OTHER,
    val sets: List<WorkoutSet>,
    val previous: Map<Int, WorkoutSet>,
    val best: WorkoutSet?
)

data class SessionUiState(
    val session: WorkoutSession? = null,
    val blocks: List<BlockUi> = emptyList(),
    val stats: SessionStats = SessionStats(0f, 0)
)

/** A6b (§3.7.1/§3.7.2) — the live log. Owns the elapsed-time ticker and the rest-timer ticker,
 *  the per-block PREVIOUS map and pre-session bests, sessionStats, and consumes
 *  "picked_exercise_id" from the picker's SavedStateHandle result. */
@HiltViewModel
class WorkoutSessionViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    appSettingsRepository: AppSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    /** §2.7 fix — the Volume tile/set-table used to hardcode "kg" regardless of
     *  `app_settings.weight_unit`; now the same read-through `WorkoutDetailViewModel` already
     *  used. */
    val weightUnit = appSettingsRepository.observeSettings()
        .map { parseWeightUnit(it.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    // P1: no `List<WorkoutSet>` of the VM's own — everything below is a read-through of Room.
    private val base = combine(
        repo.observeSession(sessionId),
        repo.observeExercisesForSession(sessionId),
        repo.observeSetsForSession(sessionId)
    ) { session, exercises, sets -> Triple(session, exercises, sets) }

    private val _derived = MutableStateFlow<Map<String, Pair<Map<Int, WorkoutSet>, WorkoutSet?>>>(emptyMap())
    // Bug fix (post-A6) — the full resolved CatalogExercise (name, trackingMode, imageId,
    // hasStartPeak), not just a name/mode pair, so the block header can render the real RepDB
    // thumbnail via ExerciseThumbnail.
    private val _names = MutableStateFlow<Map<String, CatalogExercise>>(emptyMap())

    val state = combine(base, _derived, _names) { (session, exercises, sets), derived, names ->
        val blocks = exercises.sortedBy { it.orderIndex }.map { block ->
            val resolved = names[block.exerciseId]
            val (prev, best) = derived[block.exerciseId] ?: (emptyMap<Int, WorkoutSet>() to null)
            BlockUi(
                block = block,
                exerciseName = resolved?.name ?: "Exercise",
                trackingMode = resolved?.trackingMode ?: "WEIGHT_REPS",
                imageId = resolved?.imageId,
                hasStartPeak = resolved?.hasStartPeak ?: false,
                primaryMuscle = resolved?.primaryMuscle ?: MuscleGroup.OTHER,
                sets = sets.filter { it.workoutExerciseId == block.id }.sortedBy { it.setNumber },
                previous = prev, best = best
            )
        }
        SessionUiState(session, blocks, sessionStats(sets))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        // Refresh the derived per-exercise data (names, PREVIOUS, best) whenever the block set
        // changes — P3: read once per exercise block opened, not per keystroke.
        base.onEach { (_, exercises, _) ->
            val ids = exercises.map { it.exerciseId }.distinct()
            val missingNames = ids.filterNot { it in _names.value }
            if (missingNames.isNotEmpty()) {
                val resolved = missingNames.mapNotNull { id -> repo.resolveExercise(id)?.let { id to it } }.toMap()
                _names.update { current -> current + resolved }
            }
            val missingDerived = ids.filterNot { it in _derived.value }
            if (missingDerived.isNotEmpty()) {
                val computed = missingDerived.associateWith { id ->
                    repo.previousSetsForExercise(id, sessionId) to repo.bestSetForExercise(id, sessionId)
                }
                _derived.update { it + computed }
            }
        }.launchIn(viewModelScope)
    }

    // ------------------------------------------------------------------------ ticker (§3.7.2)
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds = _elapsedSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val startedAt = state.value.session?.startedAt
                if (startedAt != null) {
                    _elapsedSeconds.value = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // ------------------------------------------------------------------------ rest timer (§3.7.2)
    // §2.9/§3 fix — `restEndsAt` used to also be exposed publicly, but nothing ever collected it
    // (only the derived `restRemainingSeconds` below is); `restRemainingSeconds` makes it fully
    // redundant as a public surface, so only the private backing field remains.
    private val _restEndsAt = MutableStateFlow<Long?>(null)
    private val _restRemainingSeconds = MutableStateFlow<Long?>(null)
    val restRemainingSeconds = _restRemainingSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val ends = _restEndsAt.value
                _restRemainingSeconds.value = ends?.let { ((it - System.currentTimeMillis()) / 1000).coerceAtLeast(0) }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun startRest(seconds: Int) { _restEndsAt.value = System.currentTimeMillis() + seconds * 1000L }
    fun cancelRest() { _restEndsAt.value = null }

    // Bug fix (post-A6) — the exercise picker's result used to be consumed here via this
    // screen's own injected SavedStateHandle (per §3.6.6). On-device testing found that path
    // unreliable (tapping a row popped back to the session with nothing added); MainActivity's
    // `composable(SESSION)` now calls [addExercise] directly off a plain shared MutableStateFlow
    // instead (mirrors the existing notification-deep-link mechanism). See
    // `MainActivity.pickedExerciseId`'s KDoc for the full story.

    // ------------------------------------------------------------------------ actions (P1)
    fun addExercise(exerciseId: String) = safeLaunch { repo.addExerciseToSession(sessionId, exerciseId) }

    /** §2.1 fix — the multi-select "Add (n)" call site used to fire one `safeLaunch` per exercise
     *  id, so N coroutines raced each other's read of `maxExerciseOrderIndex` before any of them
     *  had inserted. One coroutine, looping sequentially, guarantees each add sees the previous
     *  one's write (on top of `WorkoutRepository.addExerciseToSession`'s own transaction, which
     *  handles the case of two truly-concurrent callers). */
    fun addExercises(exerciseIds: List<String>) = safeLaunch {
        for (id in exerciseIds) repo.addExerciseToSession(sessionId, id)
    }
    fun removeExercise(blockId: String) = safeLaunch { repo.removeExerciseFromSession(blockId) }
    fun setExerciseNotes(blockId: String, notes: String) = safeLaunch { repo.setExerciseNotes(blockId, notes) }
    fun setExerciseRest(blockId: String, restSeconds: Int?) = safeLaunch { repo.setExerciseRest(blockId, restSeconds) }

    /** Feature addition — "I forgot to end this on time": lets the duration ring be corrected
     *  before Finish. Re-anchors the session's `startedAt`; the ticker keeps running afterwards.
     *  §1.5 fix — only updates the local ticker state on a true result, matching
     *  `WorkoutRepository.setSessionElapsedSeconds`'s new "false if the session is already gone"
     *  contract, instead of always assuming the write landed. */
    fun setElapsedSeconds(newElapsedSeconds: Long) = safeLaunch {
        if (repo.setSessionElapsedSeconds(sessionId, newElapsedSeconds)) {
            _elapsedSeconds.value = newElapsedSeconds
        } else {
            _errorMessage.value = "This workout is no longer available."
            _errorToken.value++
        }
    }

    fun addSet(blockId: String, exerciseId: String) = safeLaunch {
        val previous = state.value.blocks.firstOrNull { it.block.id == blockId }?.previous ?: emptyMap()
        repo.addSet(blockId, sessionId, exerciseId, previous)
    }

    /** §2.9 — wires up the previously-dead `WorkoutRepository.reorderExercises`: the session
     *  screen's per-block up/down move buttons pass the whole block list, reordered. */
    fun reorderExercises(orderedBlockIds: List<String>) = safeLaunch { repo.reorderExercises(orderedBlockIds) }

    // §2.13 fix — column-scoped commits instead of a whole-row `updateSet(set.copy(...))` built
    // from a stale composition-time snapshot, so a set-cell commit can't clobber a concurrent
    // `toggleSetComplete`.
    fun updateSetWeight(id: String, weightKg: Float?) = safeLaunch { repo.setSetWeight(id, weightKg) }
    fun updateSetReps(id: String, reps: Int?) = safeLaunch { repo.setSetReps(id, reps) }
    fun updateSetDuration(id: String, durationSeconds: Int?) = safeLaunch { repo.setSetDuration(id, durationSeconds) }
    fun updateSetDistance(id: String, distanceMeters: Float?) = safeLaunch { repo.setSetDistance(id, distanceMeters) }
    fun deleteSet(id: String) = safeLaunch { repo.deleteSet(id) }
    fun toggleSetComplete(id: String) = safeLaunch { repo.toggleSetComplete(id) }

    private val _finished = MutableStateFlow(false)
    val finished = _finished.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _errorToken = MutableStateFlow(0)
    val errorToken = _errorToken.asStateFlow()

    fun finish() = safeLaunch {
        // Bug fix (BEAST_MODE_BUG_REPORT.md §1.5) — `finishSession` silently no-ops if the
        // session row is already gone (a race with a concurrent discard/sync eviction); only
        // navigate away when it actually saved, instead of always reporting success.
        if (repo.finishSession(sessionId)) {
            _finished.value = true
        } else {
            _errorMessage.value = "This workout is no longer available."
            _errorToken.value++
        }
    }

    fun discard() = safeLaunch {
        repo.discardSession(sessionId)
        _finished.value = true
    }
}
