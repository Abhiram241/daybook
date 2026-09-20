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
import androidx.compose.runtime.LaunchedEffect
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

val TRACKING_MODE_LABELS = linkedMapOf(
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

    // §2.11 fix — used to run directly in the composable body, which re-runs on every
    // recomposition while `state.done` stays true; an effect only fires once per flip.
    LaunchedEffect(state.done) { if (state.done) onNavigateBack() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackHeader(title = if (state.isEdit) "Edit exercise" else "New exercise", onBack = onNavigateBack)
            ExerciseDetailsForm(
                name = state.name,
                primaryMuscle = state.primaryMuscle,
                equipment = state.equipment,
                trackingMode = state.trackingMode,
                onNameChange = viewModel::setName,
                onMuscleChange = viewModel::setPrimaryMuscle,
                onEquipmentChange = viewModel::setEquipment,
                onTrackingModeChange = viewModel::setTrackingMode,
                modifier = Modifier.fillMaxSize()
            )
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
}

/**
 * Item 1 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — [ExerciseFormScreen]'s body (name field + three
 * [PickerRow]s + their [SortSheet]s), extracted VERBATIM so `ExerciseReviewScreen` can reuse the
 * exact same form rather than re-deriving it — the plan's own risk note: "the one real hazard is
 * the extracted form accidentally changing ExerciseFormScreen's behaviour". Deliberately generic
 * over plain (name, primaryMuscle, equipment, trackingMode) + callbacks rather than
 * `ExerciseFormViewModel.State`, so it carries no assumption about which ViewModel owns the data.
 */
@Composable
fun ExerciseDetailsForm(
    name: String,
    primaryMuscle: MuscleGroup,
    equipment: Equipment,
    trackingMode: String,
    onNameChange: (String) -> Unit,
    onMuscleChange: (MuscleGroup) -> Unit,
    onEquipmentChange: (Equipment) -> Unit,
    onTrackingModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Item 1 — the review flow's sticky bar (Skip/Next) already reserves the bottom clearance a
    // save bar would; the plain form screen still needs the 140dp room its own `StickySaveBar`
    // wants. Same clearance either way in practice, but named so each caller states its own intent.
    bottomContentPadding: androidx.compose.ui.unit.Dp = 140.dp
) {
    var showMuscleSheet by remember { mutableStateOf(false) }
    var showEquipmentSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = Spacing.screenH),
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
        item {
            DaybookTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Name",
                placeholder = "e.g. Cable curl (rope)"
            )
            Spacer(Modifier.height(16.dp))
            SectionHeader("Details")
            PickerRow("Primary muscle", MuscleGroupLabels[primaryMuscle].orEmpty()) { showMuscleSheet = true }
            PickerRow("Equipment", EquipmentLabels[equipment].orEmpty()) { showEquipmentSheet = true }
            PickerRow("Tracking", TRACKING_MODE_LABELS[trackingMode].orEmpty()) { showModeSheet = true }
        }
    }

    SortSheet(
        visible = showMuscleSheet,
        onDismiss = { showMuscleSheet = false },
        title = "Primary muscle",
        sortOptions = MuscleGroup.entries.map { SortOption(it.name, MuscleGroupLabels[it].orEmpty()) },
        selectedSortKey = primaryMuscle.name,
        onSelectSort = { onMuscleChange(MuscleGroup.valueOf(it)) },
        dismissOnSelect = true,
        neutralHeader = true
    )
    SortSheet(
        visible = showEquipmentSheet,
        onDismiss = { showEquipmentSheet = false },
        title = "Equipment",
        sortOptions = Equipment.entries.map { SortOption(it.name, EquipmentLabels[it].orEmpty()) },
        selectedSortKey = equipment.name,
        onSelectSort = { onEquipmentChange(Equipment.valueOf(it)) },
        dismissOnSelect = true,
        neutralHeader = true
    )
    SortSheet(
        visible = showModeSheet,
        onDismiss = { showModeSheet = false },
        title = "Tracking",
        sortOptions = TRACKING_MODE_LABELS.map { (k, v) -> SortOption(k, v) },
        selectedSortKey = trackingMode,
        onSelectSort = { onTrackingModeChange(it) },
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
