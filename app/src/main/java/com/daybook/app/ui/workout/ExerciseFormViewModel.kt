package com.daybook.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.workout.Equipment
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** A6a — `new_exercise` (exerciseId == null) and `edit_exercise/{id}`. */
@HiltViewModel
class ExerciseFormViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class State(
        val isEdit: Boolean = false,
        val name: String = "",
        val primaryMuscle: MuscleGroup = MuscleGroup.OTHER,
        val equipment: Equipment = Equipment.NONE,
        val trackingMode: String = "WEIGHT_REPS",
        val busy: Boolean = false,
        val done: Boolean = false,
        val rejectedMessage: String? = null
    )

    private val exerciseId: String? = savedStateHandle["exerciseId"]
    private val _state = MutableStateFlow(State(isEdit = exerciseId != null))
    val state = _state.asStateFlow()

    init {
        val id = exerciseId
        if (id != null) {
            safeLaunch {
                val existing = repo.resolveExercise(id)
                if (existing != null) {
                    _state.update {
                        it.copy(
                            name = existing.name,
                            primaryMuscle = existing.primaryMuscle,
                            equipment = existing.equipment,
                            trackingMode = existing.trackingMode
                        )
                    }
                }
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setPrimaryMuscle(v: MuscleGroup) = _state.update { it.copy(primaryMuscle = v) }
    fun setEquipment(v: Equipment) = _state.update { it.copy(equipment = v) }
    fun setTrackingMode(v: String) = _state.update { it.copy(trackingMode = v) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank() || s.busy) return
        _state.update { it.copy(busy = true, rejectedMessage = null) }
        safeLaunch {
            val result = runCatching {
                if (exerciseId != null) {
                    val existing = repo.resolveExercise(exerciseId)
                    if (existing != null) {
                        repo.updateExercise(
                            com.daybook.app.data.model.Exercise(
                                id = exerciseId, name = s.name.trim(),
                                primaryMuscle = s.primaryMuscle.name, equipment = s.equipment.name,
                                trackingMode = s.trackingMode, isArchived = false,
                                source = existing.source, createdAt = System.currentTimeMillis(),
                                notes = existing.notes
                            )
                        )
                    }
                } else {
                    repo.createCustomExercise(s.name, s.primaryMuscle, s.equipment, s.trackingMode)
                }
            }
            if (result.isSuccess) {
                _state.update { it.copy(busy = false, done = true) }
            } else {
                com.daybook.app.util.recordUnhandledException(result.exceptionOrNull() ?: Exception("save failed"))
                _state.update { it.copy(busy = false, rejectedMessage = "Couldn't save this exercise. Try again.") }
            }
        }
    }
}
