package com.daybook.app.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.daybook.app.data.RoutineExerciseDraft
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

/**
 * A6c (§3.7.5) — per-exercise targets inside the routine editor. Fields shown follow
 * `trackingMode`, mirroring the live set table's `columnsFor` (Ri2: this is the routine-side
 * analogue named explicitly by the plan as a permitted second reader of trackingMode). Every
 * field optional; an empty field stores `NULL`, never `0` (Ri3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineTargetSheet(
    visible: Boolean,
    draft: RoutineExerciseDraft,
    trackingMode: String,
    onDone: (RoutineExerciseDraft) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    var sets by remember(draft.id) { mutableStateOf(draft.targetSets?.toString().orEmpty()) }
    var reps by remember(draft.id) { mutableStateOf(draft.targetReps?.toString().orEmpty()) }
    var weight by remember(draft.id) { mutableStateOf(draft.targetWeightKg?.toString().orEmpty()) }
    var duration by remember(draft.id) { mutableStateOf(draft.targetDurationSeconds?.toString().orEmpty()) }
    var distance by remember(draft.id) { mutableStateOf(draft.targetDistanceMeters?.let { it / 1000f }?.toString().orEmpty()) }
    var note by remember(draft.id) { mutableStateOf(draft.notes.orEmpty()) }

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = DaybookColors.Surface, shape = AppShapes.sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Targets", style = DaybookText.SectionTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            // §2.10 fix — Sets/Reps/Time are integer targets parsed with `toIntOrNull()`; letting
            // the field accept "." meant a stray decimal point (e.g. a fat-fingered "12.") silently
            // discarded the whole value on save instead of being rejected at entry.
            NumField("Sets", sets, allowDecimal = false) { sets = it }
            if (trackingMode == "WEIGHT_REPS" || trackingMode == "REPS_ONLY") {
                NumField("Reps", reps, allowDecimal = false) { reps = it }
            }
            if (trackingMode == "WEIGHT_REPS") {
                NumField("Weight (kg)", weight, allowDecimal = true) { weight = it }
            }
            if (trackingMode == "DURATION" || trackingMode == "DISTANCE_DURATION") {
                NumField("Time (s)", duration, allowDecimal = false) { duration = it }
            }
            if (trackingMode == "DISTANCE_DURATION") {
                NumField("Distance (km)", distance, allowDecimal = true) { distance = it }
            }
            DaybookTextField(value = note, onValueChange = { note = it }, label = "Note", placeholder = "Optional")
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Done",
                onClick = {
                    onDone(
                        draft.copy(
                            targetSets = sets.toIntOrNull(),
                            targetReps = reps.toIntOrNull(),
                            targetWeightKg = weight.toFloatOrNull(),
                            targetDurationSeconds = duration.toIntOrNull(),
                            targetDistanceMeters = distance.toFloatOrNull()?.let { it * 1000f },
                            notes = note.trim().takeIf { it.isNotEmpty() }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NumField(label: String, value: String, allowDecimal: Boolean, onChange: (String) -> Unit) {
    DaybookTextField(
        value = value,
        onValueChange = { v ->
            val valid = if (allowDecimal) v.all { it.isDigit() || it == '.' } else v.all { it.isDigit() }
            if (valid) onChange(v)
        },
        label = label,
        placeholder = "—"
    )
    Spacer(Modifier.height(8.dp))
}
