package com.daybook.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.data.workout.Equipment
import com.daybook.app.data.workout.EquipmentLabels
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.MuscleGroupLabels
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SortOption
import com.daybook.app.ui.components.SortSheet
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

private val TRACKING_MODE_LABELS = linkedMapOf(
    "WEIGHT_REPS" to "Weight + reps",
    "REPS_ONLY" to "Reps only",
    "DURATION" to "Time",
    "DISTANCE_DURATION" to "Distance + time"
)

/** A6a (§3.7) — `new_exercise` / `edit_exercise/{id}`. Mirrors AddHabitScreen/HabitForm's shape. */
@Composable
fun ExerciseFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExerciseFormViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showMuscleSheet by remember { mutableStateOf(false) }
    var showEquipmentSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }

    if (state.done) onNavigateBack()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackHeader(title = if (state.isEdit) "Edit exercise" else "New exercise", onBack = onNavigateBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                item {
                    DaybookTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        label = "Name",
                        placeholder = "e.g. Cable curl (rope)"
                    )
                    Spacer(Modifier.height(16.dp))
                    SectionHeader("Details")
                    PickerRow("Primary muscle", MuscleGroupLabels[state.primaryMuscle].orEmpty()) { showMuscleSheet = true }
                    PickerRow("Equipment", EquipmentLabels[state.equipment].orEmpty()) { showEquipmentSheet = true }
                    PickerRow("Tracking", TRACKING_MODE_LABELS[state.trackingMode].orEmpty()) { showModeSheet = true }
                }
            }
        }
        StickySaveBar(modifier = Modifier.align(Alignment.BottomCenter).imePadding()) {
            if (state.rejectedMessage != null) {
                Text(state.rejectedMessage!!, style = DaybookText.Caption, color = DaybookColors.Danger)
                Spacer(Modifier.height(8.dp))
            }
            PrimaryButton(
                text = "Save",
                onClick = viewModel::save,
                enabled = state.name.isNotBlank() && !state.busy
            )
        }
    }

    SortSheet(
        visible = showMuscleSheet,
        onDismiss = { showMuscleSheet = false },
        title = "Primary muscle",
        sortOptions = MuscleGroup.entries.map { SortOption(it.name, MuscleGroupLabels[it].orEmpty()) },
        selectedSortKey = state.primaryMuscle.name,
        onSelectSort = { viewModel.setPrimaryMuscle(MuscleGroup.valueOf(it)) },
        dismissOnSelect = true,
        neutralHeader = true
    )
    SortSheet(
        visible = showEquipmentSheet,
        onDismiss = { showEquipmentSheet = false },
        title = "Equipment",
        sortOptions = Equipment.entries.map { SortOption(it.name, EquipmentLabels[it].orEmpty()) },
        selectedSortKey = state.equipment.name,
        onSelectSort = { viewModel.setEquipment(Equipment.valueOf(it)) },
        dismissOnSelect = true,
        neutralHeader = true
    )
    SortSheet(
        visible = showModeSheet,
        onDismiss = { showModeSheet = false },
        title = "Tracking",
        sortOptions = TRACKING_MODE_LABELS.map { (k, v) -> SortOption(k, v) },
        selectedSortKey = state.trackingMode,
        onSelectSort = { viewModel.setTrackingMode(it) },
        dismissOnSelect = true,
        neutralHeader = true
    )
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
        TextButton(onClick = onClick) {
            Text(value, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
        }
    }
}
