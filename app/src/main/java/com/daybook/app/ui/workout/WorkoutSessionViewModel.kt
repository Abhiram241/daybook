package com.daybook.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.SessionStats
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

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
    private val _restEndsAt = MutableStateFlow<Long?>(null)
    val restEndsAt = _restEndsAt.asStateFlow()
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
    fun removeExercise(blockId: String) = safeLaunch { repo.removeExerciseFromSession(blockId) }
    fun setExerciseNotes(blockId: String, notes: String) = safeLaunch { repo.setExerciseNotes(blockId, notes) }
    fun setExerciseRest(blockId: String, restSeconds: Int?) = safeLaunch { repo.setExerciseRest(blockId, restSeconds) }

    /** Feature addition — "I forgot to end this on time": lets the duration ring be corrected
     *  before Finish. Re-anchors the session's `startedAt`; the ticker keeps running afterwards. */
    fun setElapsedSeconds(newElapsedSeconds: Long) = safeLaunch {
        repo.setSessionElapsedSeconds(sessionId, newElapsedSeconds)
        _elapsedSeconds.value = newElapsedSeconds
    }

    fun addSet(blockId: String, exerciseId: String) = safeLaunch {
        val prevMatch = state.value.blocks.firstOrNull { it.block.id == blockId }
            ?.let { it.previous[it.sets.size + 1] }
        repo.addSet(blockId, sessionId, exerciseId, prevMatch)
    }

    fun updateSet(set: WorkoutSet) = safeLaunch { repo.updateSet(set) }
    fun deleteSet(id: String) = safeLaunch { repo.deleteSet(id) }
    fun toggleSetComplete(id: String) = safeLaunch { repo.toggleSetComplete(id) }

    private val _finished = MutableStateFlow(false)
    val finished = _finished.asStateFlow()

    fun finish() = safeLaunch {
        repo.finishSession(sessionId)
        _finished.value = true
    }

    fun discard() = safeLaunch {
        repo.discardSession(sessionId)
        _finished.value = true
    }
}
