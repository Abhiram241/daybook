package com.daybook.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.workout.formatVolume
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.ui.workout.beast.BeastPalette
import com.daybook.app.ui.workout.beast.BeastText
import com.daybook.app.ui.workout.beast.RingStat
import com.daybook.app.ui.workout.beast.StatGridTile
import com.daybook.app.ui.workout.beast.muscleTint

/** A6a (§3.7) — a read view of a finished session with an Edit affordance. */
@Composable
fun WorkoutDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onSessionStarted: (String) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val newSessionId by viewModel.newSessionId.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()

    LaunchedEffect(newSessionId) { newSessionId?.let(onSessionStarted) }

    Column(Modifier.fillMaxSize().background(BeastPalette.groundBrush())) {
        BackHeader(title = state.session?.title ?: state.session?.localDate ?: "Workout", onBack = onNavigateBack) {
            GhostButton(text = "Edit", onClick = onEdit)
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH)) {
            item {
                // §3.2/§4.5 — the same RingStat + StatGridTile hero as the live session screen,
                // but static (no animation, no live ticker — this is a finished workout).
                SoftCard(tint = CardTints.Neutral, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RingStat(
                            progress = durationRingProgress(state.session),
                            ringColor = BeastPalette.hotAccent(),
                            size = 108.dp,
                            strokeWidth = 9.dp,
                            animate = false
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    durationLabel(state.session), style = BeastText.BigNumber.copy(fontSize = 18.sp),
                                    color = DaybookColors.TextPrimary, maxLines = 1
                                )
                                Text("DURATION", style = BeastText.MicroLabel, color = DaybookColors.TextMuted)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatGridTile(
                                icon = com.daybook.app.ui.icons.DaybookIcons.BarChart,
                                value = formatVolume(state.stats.totalVolumeKg, weightUnit),
                                label = "Volume",
                                tint = CardTints.Mint
                            )
                            StatGridTile(
                                icon = com.daybook.app.ui.icons.DaybookIcons.Task,
                                value = "${state.stats.setCount}",
                                label = "Sets",
                                tint = CardTints.SlateBlue
                            )
                        }
                    }
                }
                if (state.session?.notes?.isNotBlank() == true) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.session!!.notes!!, style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
                }
                val routineId = state.session?.routineId
                if (routineId != null) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton(
                        text = "Start this routine again",
                        onClick = { viewModel.startRoutineAgain(routineId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader("Exercises")
            }
            items(state.exercises.sortedBy { it.orderIndex }, key = { it.id }) { block ->
                val info = state.exerciseInfo[block.exerciseId]
                val tint = muscleTint(info?.primaryMuscle ?: com.daybook.app.data.workout.MuscleGroup.OTHER)
                SoftCard(tint = tint, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        ExerciseThumbnail(
                            imageId = info?.imageId,
                            hasStartPeak = info?.hasStartPeak ?: false,
                            fallbackIcon = com.daybook.app.ui.icons.DaybookIcons.Category,
                            tint = tint,
                            size = 40.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(info?.name ?: "Exercise", style = DaybookText.CardTitle, color = tint.onFill)
                    }
                    if (!block.notes.isNullOrBlank()) {
                        Text(block.notes!!, style = DaybookText.Caption, color = DaybookColors.TextMuted)
                    }
                    val blockSets = state.sets.filter { it.workoutExerciseId == block.id }.sortedBy { it.setNumber }
                    blockSets.forEach { s ->
                        Text(formatDetailSetLine(s), style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary)
                    }
                }
            }
        }
    }
}

private fun durationLabel(session: com.daybook.app.data.model.WorkoutSession?): String {
    if (session?.endedAt == null) return "–"
    val minutes = ((session.endedAt!! - session.startedAt) / 60000).coerceAtLeast(0)
    return "${minutes}m"
}

/** §3.2 — the same 60-minute "typical session" baseline the live session ring uses, so a finished
 *  workout's ring reads consistently with the one that produced it. */
private fun durationRingProgress(session: com.daybook.app.data.model.WorkoutSession?): Float {
    if (session?.endedAt == null) return 0f
    val seconds = ((session.endedAt!! - session.startedAt) / 1000).coerceAtLeast(0)
    return seconds / 3600f
}

// Bug fix (post-A6): the exercise name/thumbnail here used to be guessed from the id's own shape
// (a deviation, now resolved) — `WorkoutDetailViewModel.state.exerciseInfo` now resolves every
// block's real `CatalogExercise` via `WorkoutRepository.resolveExercise`, the same source of
// truth the picker and live session use.

private fun formatDetailSetLine(s: com.daybook.app.data.model.WorkoutSet): String {
    val parts = mutableListOf<String>()
    if (s.weightKg != null) parts += "${s.weightKg} kg"
    if (s.reps != null) parts += "${s.reps} reps"
    if (s.durationSeconds != null) parts += "${s.durationSeconds}s"
    if (s.distanceMeters != null) parts += "${s.distanceMeters / 1000f} km"
    val prefix = "Set ${s.setNumber}: "
    return if (parts.isEmpty()) "${prefix}—" else prefix + parts.joinToString(" × ")
}
