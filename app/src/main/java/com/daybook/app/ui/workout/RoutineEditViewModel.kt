package com.daybook.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.RoutineExerciseDraft
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.parseWeightUnit
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** A6c (§3.7.5) — create/edit a routine. `routineId == null` -> create. */
@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    appSettingsRepository: AppSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** §2.7 fix — the target summary line used to hardcode `WeightUnit.KG`. */
    val weightUnit = appSettingsRepository.observeSettings()
        .map { parseWeightUnit(it.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    data class State(
        val isEdit: Boolean = false,
        val name: String = "",
        val notes: String = "",
        val exercises: List<RoutineExerciseDraft> = emptyList(),
        /** exerciseId -> (display name, trackingMode, primary muscle). */
        val exerciseNames: Map<String, Triple<String, String, MuscleGroup>> = emptyMap(),
        val busy: Boolean = false,
        val done: Boolean = false,
        val rejectedMessage: String? = null
    )

    private val routineId: String? = savedStateHandle["routineId"]
    private val _state = MutableStateFlow(State(isEdit = routineId != null))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        // Bug fix (post-A6) — the exercise picker's result used to be consumed here via this
        // screen's own injected SavedStateHandle (the PREVIOUS back-stack entry's handle, per
        // §3.6.6). On-device testing found that path unreliable; MainActivity's
        // `composable(ROUTINE_EDIT)` now calls [addExercise] directly off a plain shared
        // MutableStateFlow instead (mirrors the existing notification-deep-link mechanism). See
        // `MainActivity.pickedExerciseId`'s KDoc for the full story.

        val id = routineId
        if (id != null) {
            safeLaunch {
                // One-shot reads — the editor loads once, then edits are purely local (draft
                // rows) until Save.
                val r = repo.observeRoutine(id).first()
                val rows = repo.observeRoutineExercises(id).first()
                if (r != null) {
                    val drafts = rows.sortedBy { it.orderIndex }.map {
                        RoutineExerciseDraft(
                            id = it.id, exerciseId = it.exerciseId, targetSets = it.targetSets,
                            targetReps = it.targetReps, targetWeightKg = it.targetWeightKg,
                            targetDurationSeconds = it.targetDurationSeconds,
                            targetDistanceMeters = it.targetDistanceMeters,
                            restSeconds = it.restSeconds, notes = it.notes
                        )
                    }
                    _state.update { it.copy(name = r.name, notes = r.notes.orEmpty(), exercises = drafts) }
                    refreshNames(drafts)
                }
            }
        }
    }

    private fun refreshNames(drafts: List<RoutineExerciseDraft>) = safeLaunch {
        val names = drafts.associate { d ->
            val resolved = repo.resolveExercise(d.exerciseId)
            d.exerciseId to Triple(
                resolved?.name ?: "Unknown exercise",
                resolved?.trackingMode ?: "WEIGHT_REPS",
                resolved?.primaryMuscle ?: MuscleGroup.OTHER
            )
        }
        _state.update { it.copy(exerciseNames = it.exerciseNames + names) }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }

    fun addExercise(exerciseId: String) {
        val draft = RoutineExerciseDraft(id = UUID.randomUUID().toString(), exerciseId = exerciseId)
        _state.update { it.copy(exercises = it.exercises + draft) }
        refreshNames(listOf(draft))
    }

    fun removeExercise(draftId: String) = _state.update { it.copy(exercises = it.exercises.filterNot { d -> d.id == draftId }) }

    fun moveExercise(from: Int, to: Int) = _state.update {
        val list = it.exercises.toMutableList()
        if (from !in list.indices || to !in list.indices) return@update it
        val item = list.removeAt(from)
        list.add(to, item)
        it.copy(exercises = list)
    }

    fun setTargets(draftId: String, targets: RoutineExerciseDraft) = _state.update {
        it.copy(exercises = it.exercises.map { d -> if (d.id == draftId) targets else d })
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank() || s.busy) return
        _state.update { it.copy(busy = true, rejectedMessage = null) }
        safeLaunch {
            val result = runCatching {
                if (routineId != null) repo.updateRoutine(routineId, s.name, s.notes, s.exercises)
                else repo.createRoutine(s.name, s.notes, s.exercises)
            }
            if (result.isSuccess) {
                _state.update { it.copy(busy = false, done = true) }
            } else {
                com.daybook.app.util.recordUnhandledException(result.exceptionOrNull()!!)
                _state.update { it.copy(busy = false, rejectedMessage = "Couldn't save this routine. Try again.") }
            }
        }
    }
}
