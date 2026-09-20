package com.daybook.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

/**
 * Item 1 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — `exercise_review?ids={ids}`. NOT a new form: it
 * re-uses [ExerciseDetailsForm] (the extracted body of [ExerciseFormScreen]) one card at a time,
 * with a Skip/Next sticky bar instead of a single Save. Optional by design — back/system-back
 * exits the flow entirely at any point; nothing is lost, since the review predicate
 * (`ExerciseDao.exercisesNeedingDetails`) still matches a skipped exercise next time.
 */
@Composable
fun ExerciseReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExerciseReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) { if (state.done) onNavigateBack() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackHeader(title = "New exercises", onBack = onNavigateBack)
            if (!state.loading && state.total > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${state.index + 1} of ${state.total}",
                        style = DaybookText.Caption,
                        color = DaybookColors.TextMuted
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
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
        }
        if (!state.loading) {
            StickySaveBar(modifier = Modifier.align(Alignment.BottomCenter).imePadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    GhostButton(
                        text = "Skip",
                        onClick = viewModel::skip,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        // The last card's "Next" reads "Done" — matches the plan's own copy.
                        text = if (state.index >= state.total - 1) "Done" else "Next",
                        onClick = viewModel::next,
                        enabled = state.name.isNotBlank() && !state.busy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
