package com.daybook.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.model.Exercise
import com.daybook.app.data.workout.Equipment
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Item 1 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — `exercise_review?ids={ids}`. Walks a queue of
 * exercise ids one at a time, reusing [ExerciseDetailsForm] (the extracted body of
 * [ExerciseFormScreen]) and writing through [WorkoutRepository.updateExercise] — the identical
 * call [ExerciseFormViewModel.save] already makes for an edit.
 *
 * The queue and cursor live in [SavedStateHandle] (not just in-memory `State`), so a rotation or a
 * process death resumes on the same card instead of restarting the whole review from card 1.
 */
@HiltViewModel
class ExerciseReviewViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class State(
        val total: Int = 0,
        val index: Int = 0,
        val name: String = "",
        val primaryMuscle: MuscleGroup = MuscleGroup.OTHER,
        val equipment: Equipment = Equipment.OTHER,
        val trackingMode: String = "WEIGHT_REPS",
        val loading: Boolean = true,
        val busy: Boolean = false,
        // True once the queue is exhausted (every card Skipped/Next'd through) or was empty to
        // start with — the screen navigates back on this flipping true, same shape as
        // ExerciseFormViewModel's `done`.
        val done: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    /** The full queue for this review session, persisted so it survives a process death — the
     *  predicate query itself is re-derived fresh only if nothing was persisted yet (first run). */
    private var ids: List<String>
        get() = savedStateHandle.get<ArrayList<String>>(KEY_QUEUE_IDS) ?: emptyList()
        set(v) { savedStateHandle[KEY_QUEUE_IDS] = ArrayList(v) }

    private var index: Int
        get() = savedStateHandle.get<Int>(KEY_QUEUE_INDEX) ?: 0
        set(v) { savedStateHandle[KEY_QUEUE_INDEX] = v }

    init {
        safeLaunch {
            if (savedStateHandle.get<ArrayList<String>>(KEY_QUEUE_IDS) == null) {
                // First launch of this instance (not a rotation/process-death restore) — build the
                // queue. A comma-joined `ids` nav arg (item 1: "review these N from the import you
                // just did") scopes it; absent means the whole predicate (item 2: "Fix them").
                val scoped = savedStateHandle.get<String>("ids")
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                ids = if (!scoped.isNullOrEmpty()) scoped else repo.exercisesNeedingDetailsIds()
                index = 0
            }
            loadCurrentOrFinish()
        }
    }

    private suspend fun loadCurrentOrFinish() {
        val queue = ids
        var i = index
        // Skip any id no longer present (deleted, or fixed elsewhere since the queue was built)
        // rather than showing a blank/broken card for it.
        var row: Exercise? = null
        while (i < queue.size) {
            row = repo.getExerciseRow(queue[i])
            if (row != null) break
            i++
        }
        index = i
        if (row == null) {
            _state.update { it.copy(done = true, loading = false) }
            return
        }
        _state.update {
            it.copy(
                total = queue.size,
                index = i,
                name = row.name,
                primaryMuscle = runCatching { MuscleGroup.valueOf(row.primaryMuscle) }.getOrDefault(MuscleGroup.OTHER),
                equipment = runCatching { Equipment.valueOf(row.equipment) }.getOrDefault(Equipment.OTHER),
                trackingMode = row.trackingMode,
                loading = false,
                busy = false
            )
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setPrimaryMuscle(v: MuscleGroup) = _state.update { it.copy(primaryMuscle = v) }
    fun setEquipment(v: Equipment) = _state.update { it.copy(equipment = v) }
    fun setTrackingMode(v: String) = _state.update { it.copy(trackingMode = v) }

    /** "Next" — saves the current card's edits (if the name is still non-blank; an emptied name
     *  is refused the same way ExerciseFormViewModel.save() refuses one) then advances. */
    fun next() = safeLaunch {
        val s = _state.value
        if (s.busy) return@safeLaunch
        _state.update { it.copy(busy = true) }
        val queue = ids
        val currentId = queue.getOrNull(index)
        if (currentId != null && s.name.isNotBlank()) {
            val base = repo.getExerciseRow(currentId)
            if (base != null) {
                repo.updateExercise(
                    base.copy(
                        name = s.name.trim(),
                        primaryMuscle = s.primaryMuscle.name,
                        equipment = s.equipment.name,
                        trackingMode = s.trackingMode
                    )
                )
            }
        }
        advance()
    }

    /** "Skip" — leaves this exercise untouched (it still matches the predicate, so it will simply
     *  reappear next time the review queue is opened) and moves on. */
    fun skip() = safeLaunch {
        if (_state.value.busy) return@safeLaunch
        _state.update { it.copy(busy = true) }
        advance()
    }

    private suspend fun advance() {
        index += 1
        loadCurrentOrFinish()
    }

    private companion object {
        const val KEY_QUEUE_IDS = "exercise_review_queue_ids"
        const val KEY_QUEUE_INDEX = "exercise_review_queue_index"
    }
}
