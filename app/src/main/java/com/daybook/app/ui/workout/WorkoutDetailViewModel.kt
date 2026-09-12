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
import javax.inject.Inject

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    appSettingsRepository: AppSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Bug fix (open question 2, resolved) — this used to be hardcoded to kg regardless of
     *  `app_settings.weight_unit`; the Volume stat now respects the real setting like every other
     *  weight display in the app. */
    val weightUnit = appSettingsRepository.observeSettings()
        .map { parseWeightUnit(it.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    data class DetailState(
        val session: WorkoutSession? = null,
        val exercises: List<WorkoutExercise> = emptyList(),
        val sets: List<WorkoutSet> = emptyList(),
        val stats: SessionStats = SessionStats(0f, 0),
        // Bug fix (post-A6, deviation #4 resolved) — exerciseId -> the real resolved catalog row
        // (name, trackingMode, imageId, hasStartPeak), replacing the old id-shape-guessing
        // fallback so custom exercises show their real name and every exercise can show its
        // real RepDB thumbnail.
        val exerciseInfo: Map<String, CatalogExercise> = emptyMap(),
        val routineStillExists: Boolean = false,
        val newSessionId: String? = null
    )

    private val base = combine(
        repo.observeSession(sessionId),
        repo.observeExercisesForSession(sessionId),
        repo.observeSetsForSession(sessionId)
    ) { session, exercises, sets -> Triple(session, exercises, sets) }

    private val _exerciseInfo = MutableStateFlow<Map<String, CatalogExercise>>(emptyMap())

    val state = combine(base, _exerciseInfo) { (session, exercises, sets), info ->
        DetailState(session, exercises, sets, sessionStats(sets), info)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    init {
        base.onEach { (_, exercises, _) ->
            val ids = exercises.map { it.exerciseId }.distinct().filterNot { it in _exerciseInfo.value }
            if (ids.isNotEmpty()) {
                val resolved = ids.mapNotNull { id -> repo.resolveExercise(id)?.let { id to it } }.toMap()
                _exerciseInfo.update { it + resolved }
            }
        }.launchIn(viewModelScope)
    }

    private val _newSessionId = MutableStateFlow<String?>(null)
    val newSessionId = _newSessionId.asStateFlow()

    fun startRoutineAgain(routineId: String) = safeLaunch {
        val newId = runCatching { repo.startSessionFromRoutine(routineId) }.getOrNull()
        if (newId != null) _newSessionId.value = newId
    }
}
