package com.daybook.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.workout.EquipmentLabels
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.MuscleGroupLabels
import com.daybook.app.data.workout.exerciseSearchRank
import com.daybook.app.data.workout.matchesGroupFilter
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** A6a (§3.7.3) — powers both `add_exercise` (PICK) and `workout_library` (BROWSE). */
@HiltViewModel
class AddExerciseViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    /**
     * Feature addition (post-A6) — the active filter chip: `null` means "All". Pre-selected from
     * `app_settings.default_exercise_group` (set on Beast Mode's own settings screen), read once
     * when the picker opens; changing the chip here is session-local and does not write back to
     * the setting (the setting only controls what's pre-selected NEXT time).
     */
    private val _selectedGroup = MutableStateFlow<MuscleGroup?>(null)
    val selectedGroup = _selectedGroup.asStateFlow()

    init {
        safeLaunch {
            val stored = appSettingsRepository.getSettings().defaultExerciseGroup
            _selectedGroup.value = stored?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }
        }
    }

    fun setGroup(group: MuscleGroup?) { _selectedGroup.value = group }

    /** §2.9 — wires up `WorkoutRepository.recentExercises`/`frequentlyLoggedExercises`, both
     *  previously dead: the KDoc on each described a picker "Recent"/"Frequently logged" default
     *  view that the picker never actually showed (a flat alphabetical list always, regardless of
     *  query/group state). Shown only in the idle state (no search, no group filter) — see
     *  `AddExerciseScreen`. */
    private val _recentExercises = MutableStateFlow<List<CatalogExercise>>(emptyList())
    val recentExercises = _recentExercises.asStateFlow()
    private val _frequentlyLogged = MutableStateFlow<List<CatalogExercise>>(emptyList())
    val frequentlyLogged = _frequentlyLogged.asStateFlow()

    init {
        safeLaunch {
            runCatching { repo.recentExercises() }.onSuccess { _recentExercises.value = it }
            runCatching { repo.frequentlyLoggedExercises() }.onSuccess { _frequentlyLogged.value = it }
        }
    }

    /** Search (`exerciseSearchRank`, Round 3 — see WorkoutLogic.kt) also matches the muscle-group
     *  and equipment labels and tolerates small typos, then the whole filtered list is sorted by
     *  match quality (exact/prefix name first, then token, then muscle/equipment, then fuzzy) —
     *  `sortedBy` is stable, so same-rank rows keep the catalog's alphabetical order. The group
     *  filter still matches `primaryMuscle` exactly. */
    val catalog = combine(repo.observeExerciseCatalog(), _query, _selectedGroup) { rows, q, group ->
        rows.filter { matchesGroupFilter(it.primaryMuscle, group) }
            .mapNotNull { ex ->
                val rank = exerciseSearchRank(
                    q, ex.name, MuscleGroupLabels[ex.primaryMuscle], EquipmentLabels[ex.equipment]
                )
                if (rank == null) null else ex to rank
            }
            .sortedBy { it.second }
            .map { it.first }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(v: String) { _query.value = v }

    /** Multi-select picker (PICK mode only) — the set of checked exercise ids, confirmed via one
     *  "Add (n)" tap rather than adding immediately on row-tap. */
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    fun toggleSelected(exerciseId: String) {
        _selectedIds.update { if (exerciseId in it) it - exerciseId else it + exerciseId }
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _errorToken = MutableStateFlow(0)
    val errorToken = _errorToken.asStateFlow()

    /** §2.3 — this used to be a bare `suspend fun` invoked from a plain `scope.launch` at the call
     *  site, so a throw here (or a silent builtin no-op, before the §2.3 fix) never surfaced to the
     *  user. Now `safeLaunch` + the established `reportError` shape. */
    fun archive(exerciseId: String, archived: Boolean) = safeLaunch {
        runCatching { repo.archiveExercise(exerciseId, archived) }
            .onFailure {
                com.daybook.app.util.recordUnhandledException(it)
                _errorMessage.value = "Couldn't archive that exercise. Try again."
                _errorToken.value++
            }
    }
}
