package com.daybook.app.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.formatWeight
import com.daybook.app.data.workout.isPersonalRecord
import com.daybook.app.data.workout.parseWeightUnit
import com.daybook.app.data.workout.trimTrailingZero
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    appSettingsRepository: AppSettingsRepository
) : androidx.lifecycle.ViewModel() {

    /** §2.7 fix — this sheet used to hardcode "kg" regardless of `app_settings.weight_unit`. */
    val weightUnit = appSettingsRepository.observeSettings()
        .map { parseWeightUnit(it.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    private val _rows = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val rows = _rows.asStateFlow()

    /** §3 fix — this used to be a bare `suspend fun` invoked directly inside the screen's
     *  `LaunchedEffect(exerciseId)`: a DB throw there crashed the composition coroutine instead of
     *  going through `safeLaunch`, and the screen's own un-keyed local `rows` state briefly kept
     *  showing the PREVIOUS exercise's history while the new one loaded. Resetting to empty before
     *  the reload, and driving the screen off this StateFlow instead of local state, fixes both. */
    fun load(exerciseId: String) = safeLaunch {
        _rows.value = emptyList()
        runCatching { repo.exerciseHistory(exerciseId) }.onSuccess { _rows.value = it }
    }
}

/**
 * A6a (§3.7.3) — what the trend button opens: every past set of this exercise, newest first,
 * PRs marked. A list, not a chart (the chart is Round C, §C.3.9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistorySheet(
    exerciseId: String,
    exerciseName: String,
    trackingMode: String,
    onDismiss: () -> Unit,
    viewModel: ExerciseHistoryViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    LaunchedEffect(exerciseId) { viewModel.load(exerciseId) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.sheet
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(exerciseName, style = DaybookText.SectionTitle, color = DaybookColors.TextPrimary)
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            if (rows.isEmpty()) {
                EmptyState(icon = DaybookIcons.Clock, title = "No history yet", body = "Log this exercise in a workout to see it here.")
            } else {
                // The "best so far" walk runs oldest -> newest so isPersonalRecord sees only what
                // came before each set, then the rendered list stays newest-first. §2.17 fix — the
                // query orders `ws.started_at DESC, s.set_number ASC`; a plain `rows.asReversed()`
                // of that two-key ordering walks each session's OWN sets backwards (`set_number
                // DESC`), which could award a PR badge to more than one set in the same session.
                // Grouping by `sessionId` (rows for one session are always contiguous, since the
                // query orders by session first) and reversing only the GROUP order keeps each
                // session's own set_number-ascending order intact.
                val chronological = rows.groupBy { it.sessionId }.entries.reversed().flatMap { it.value }
                val prIds = HashSet<String>()
                var best: WorkoutSet? = null
                for (s in chronological) {
                    if (isPersonalRecord(s, best, trackingMode)) {
                        prIds += s.id
                        best = s
                    } else if (best == null) {
                        best = s
                    }
                }
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(rows, key = { it.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                        ) {
                            Text(formatSetLine(s, weightUnit), style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary)
                            if (s.id in prIds) {
                                Text("PR", style = DaybookText.Caption, color = DaybookColors.Warning)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSetLine(s: WorkoutSet, weightUnit: WeightUnit): String {
    val parts = mutableListOf<String>()
    if (s.weightKg != null) parts += formatWeight(s.weightKg, weightUnit)
    if (s.reps != null) parts += "${s.reps} reps"
    if (s.durationSeconds != null) parts += "${s.durationSeconds}s"
    if (s.distanceMeters != null) parts += "${trimTrailingZero(s.distanceMeters / 1000f)} km"
    return if (parts.isEmpty()) "Set ${s.setNumber}" else "Set ${s.setNumber}: " + parts.joinToString(" × ")
}
