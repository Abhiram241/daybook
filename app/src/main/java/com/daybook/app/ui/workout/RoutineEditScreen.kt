package com.daybook.app.ui.workout

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.RoutineExerciseDraft
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.targetSummary
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.BottomSheetMenu
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SheetAction
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.ui.workout.beast.muscleTint

/**
 * A6c (§3.7.5) — create/edit a routine. The exercise picker (§3.6.6) sets
 * `"picked_exercise_id"` on the PREVIOUS back-stack entry's `SavedStateHandle` and pops;
 * [RoutineEditViewModel] observes its OWN injected `SavedStateHandle` for that key (Compose
 * Navigation scopes a `hiltViewModel()` to its `NavBackStackEntry`, so the two are the same
 * handle) — this composable needs no `NavController` reference at all.
 */
@Composable
fun RoutineEditScreen(
    onNavigateBack: () -> Unit,
    onPickExercise: () -> Unit,
    viewModel: RoutineEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var overflowFor by remember { mutableStateOf<RoutineExerciseDraft?>(null) }
    var targetsFor by remember { mutableStateOf<RoutineExerciseDraft?>(null) }

    if (state.done) onNavigateBack()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackHeader(title = if (state.isEdit) "Edit routine" else "New routine", onBack = onNavigateBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                item {
                    DaybookTextField(value = state.name, onValueChange = viewModel::setName, label = "Name", placeholder = "e.g. Push day")
                    Spacer(Modifier.height(12.dp))
                    DaybookTextField(
                        value = state.notes, onValueChange = viewModel::setNotes, label = "Notes",
                        placeholder = "Anything to remember about this routine", singleLine = false, minLines = 2
                    )
                    Spacer(Modifier.height(16.dp))
                    SectionHeader("Exercises")
                }
                if (state.exercises.isEmpty()) {
                    item {
                        EmptyState(
                            icon = DaybookIcons.Category,
                            title = "No exercises yet",
                            body = "Add the exercises you do in this routine, in the order you do them."
                        )
                    }
                } else {
                    items(state.exercises, key = { it.id }) { draft ->
                        val (name, mode, muscle) = state.exerciseNames[draft.exerciseId]
                            ?: Triple("Exercise", "WEIGHT_REPS", MuscleGroup.OTHER)
                        RoutineExerciseRow(
                            draft = draft,
                            name = name,
                            trackingMode = mode,
                            muscle = muscle,
                            onClick = { targetsFor = draft },
                            onOverflow = { overflowFor = draft }
                        )
                    }
                }
                item {
                    GhostButton(text = "+ Add exercise", onClick = onPickExercise, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        StickySaveBar(modifier = Modifier.align(Alignment.BottomCenter).imePadding()) {
            if (state.rejectedMessage != null) {
                Text(state.rejectedMessage!!, style = DaybookText.Caption, color = DaybookColors.Danger)
                Spacer(Modifier.height(8.dp))
            }
            com.daybook.app.ui.components.PrimaryButton(
                text = "Save routine",
                onClick = viewModel::save,
                enabled = state.name.isNotBlank() && !state.busy
            )
        }
    }

    val overflowTarget = overflowFor
    BottomSheetMenu(
        visible = overflowTarget != null,
        onDismiss = { overflowFor = null },
        actions = listOfNotNull(
            overflowTarget?.let { SheetAction(Icons.Filled.MoreVert, "Set targets") { targetsFor = it; overflowFor = null } },
            overflowTarget?.let { SheetAction(Icons.Filled.MoreVert, "Remove", destructive = true) { viewModel.removeExercise(it.id); overflowFor = null } }
        )
    )

    val target = targetsFor
    if (target != null) {
        val trackingMode = state.exerciseNames[target.exerciseId]?.second ?: "WEIGHT_REPS"
        RoutineTargetSheet(
            visible = true,
            draft = target,
            trackingMode = trackingMode,
            onDone = { updated -> viewModel.setTargets(target.id, updated); targetsFor = null },
            onDismiss = { targetsFor = null }
        )
    }
}

@Composable
private fun RoutineExerciseRow(
    draft: RoutineExerciseDraft,
    name: String,
    trackingMode: String,
    muscle: MuscleGroup,
    onClick: () -> Unit,
    onOverflow: () -> Unit
) {
    val tint = muscleTint(muscle)
    SoftCard(tint = tint, onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(name, style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1)
                val summary = targetSummary(
                    draft.targetSets, draft.targetReps, draft.targetWeightKg,
                    draft.targetDurationSeconds, draft.targetDistanceMeters, trackingMode, WeightUnit.KG
                )
                Text(summary, style = DaybookText.Caption, color = tint.onFillMuted)
            }
            CircleIconButton(icon = Icons.Filled.MoreVert, contentDescription = "More", onClick = onOverflow)
        }
    }
}
