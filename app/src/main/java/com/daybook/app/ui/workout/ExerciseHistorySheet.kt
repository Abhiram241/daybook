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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.workout.isPersonalRecord
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    private val repo: WorkoutRepository
) : androidx.lifecycle.ViewModel() {
    suspend fun load(exerciseId: String): List<WorkoutSet> = repo.exerciseHistory(exerciseId)
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
    var rows by remember { mutableStateOf<List<WorkoutSet>>(emptyList()) }
    LaunchedEffect(exerciseId) {
        rows = viewModel.load(exerciseId)
    }
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
                // The "best so far" walk runs oldest -> newest so isPersonalRecord sees only
                // what came before each set, then the rendered list stays newest-first.
                val chronological = rows.asReversed()
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
                            Text(formatSetLine(s), style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary)
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

private fun formatSetLine(s: WorkoutSet): String {
    val parts = mutableListOf<String>()
    if (s.weightKg != null) parts += "${trimZero(s.weightKg)} kg"
    if (s.reps != null) parts += "${s.reps} reps"
    if (s.durationSeconds != null) parts += "${s.durationSeconds}s"
    if (s.distanceMeters != null) parts += "${trimZero(s.distanceMeters / 1000f)} km"
    return if (parts.isEmpty()) "Set ${s.setNumber}" else "Set ${s.setNumber}: " + parts.joinToString(" × ")
}

private fun trimZero(v: Float): String = if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()
