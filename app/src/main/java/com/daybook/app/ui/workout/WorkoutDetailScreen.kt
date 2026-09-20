package com.daybook.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.MuscleGroupLabels
import com.daybook.app.data.workout.formatVolume
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.ChipSize
import com.daybook.app.ui.components.DaybookChip
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.UndoSnack
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTint
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
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val errorToken by viewModel.errorToken.collectAsStateWithLifecycle()
    val muscleGroupFilter by viewModel.muscleGroupFilter.collectAsStateWithLifecycle()

    // §2.2 fix — used to never clear `newSessionId`, so returning to this (retained) screen after
    // "Start this routine again" re-ran this effect and bounced straight back into the session.
    LaunchedEffect(newSessionId) { newSessionId?.let { onSessionStarted(it); viewModel.clearNewSessionId() } }

    Box(Modifier.fillMaxSize()) {
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
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                    Spacer(Modifier.height(Spacing.md))
                    Text(state.session!!.notes!!, style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
                }
                // Fix 3 (owner report, 2026-09-20: "redesign this aswell with same specs") — the
                // same muscle-group volume breakdown History's session rows show, in its own card
                // rather than squeezed under the stats hero above (that card's already a fixed
                // ring+tiles layout; a variable-height chart doesn't belong inside it).
                if (state.muscleVolume.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    SoftCard(tint = CardTints.Neutral, modifier = Modifier.fillMaxWidth()) {
                        MuscleVolumeChart(
                            breakdown = state.muscleVolume,
                            weightUnit = weightUnit,
                            tint = CardTints.Neutral
                        )
                    }
                }
                if (state.photos.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    SessionPhotoStrip(photos = state.photos)
                }
                val routineId = state.session?.routineId
                if (routineId != null) {
                    Spacer(Modifier.height(Spacing.md))
                    GhostButton(
                        text = "Start this routine again",
                        onClick = { viewModel.startRoutineAgain(routineId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader("Exercises")
                // [owner feedback, 2026-09-20] — WorkoutDetailScreen's Exercises list had no
                // filter at all (AddExerciseScreen's picker filter is a separate, pre-existing
                // thing). Chips scroll with the list, consistent with this screen's all-`item`
                // layout — no docked/floating bar here since Detail is a static page, not a
                // picker.
                val presentGroups = remember(state.exercises, state.exerciseInfo) {
                    state.exercises.mapNotNull { state.exerciseInfo[it.exerciseId]?.primaryMuscle }.distinct()
                }
                if (presentGroups.size > 1) {
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)
                    ) {
                        DaybookChip(
                            label = "All",
                            selected = muscleGroupFilter == null,
                            onClick = { viewModel.setMuscleGroupFilter(null) },
                            size = ChipSize.Small
                        )
                        presentGroups.forEach { g ->
                            DaybookChip(
                                label = MuscleGroupLabels[g].orEmpty(),
                                selected = muscleGroupFilter == g,
                                onClick = { viewModel.setMuscleGroupFilter(if (muscleGroupFilter == g) null else g) },
                                size = ChipSize.Small
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }
            val filteredExercises = state.exercises.sortedBy { it.orderIndex }
                .filter { muscleGroupFilter == null || state.exerciseInfo[it.exerciseId]?.primaryMuscle == muscleGroupFilter }
            if (filteredExercises.isEmpty()) {
                item {
                    EmptyState(
                        icon = com.daybook.app.ui.icons.DaybookIcons.Category,
                        title = "No exercises in that group",
                        body = "Try a different filter."
                    )
                }
            } else {
                items(filteredExercises, key = { it.id }) { block ->
                    val info = state.exerciseInfo[block.exerciseId]
                    val tint = muscleTint(info?.primaryMuscle ?: MuscleGroup.OTHER)
                    val blockSets = state.sets.filter { it.workoutExerciseId == block.id }.sortedBy { it.setNumber }
                    SoftCard(tint = tint, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            ExerciseThumbnail(
                                imageId = info?.imageId,
                                hasStartPeak = info?.hasStartPeak ?: false,
                                fallbackIcon = com.daybook.app.ui.icons.DaybookIcons.Category,
                                tint = tint,
                                size = 44.dp,
                                // Item 3 — equipment/muscle fallback art for a fully-described
                                // exercise with no RepDB photo.
                                primaryMuscle = info?.primaryMuscle ?: MuscleGroup.OTHER,
                                equipment = info?.equipment ?: com.daybook.app.data.workout.Equipment.OTHER
                            )
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(info?.name ?: "Exercise", style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1)
                                Spacer(Modifier.height(4.dp))
                                MusclePill(group = info?.primaryMuscle ?: MuscleGroup.OTHER, tint = tint)
                            }
                        }
                        if (!block.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(Spacing.sm))
                            Text(block.notes!!, style = DaybookText.Caption, color = DaybookColors.TextMuted)
                        }
                        if (blockSets.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.md))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                blockSets.forEach { s ->
                                    Text(formatDetailSetLine(s, weightUnit), style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    UndoSnack(token = errorToken, text = errorMessage ?: "")
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

/** [owner feedback, 2026-09-20] — a plain read-only muscle-group badge for the Exercises list
 *  cards, styled off the same `CardTint` fields `MuscleVolumeBar` (`WorkoutHistoryScreen.kt`)
 *  already uses for a muscle-group label. Not `DaybookChip` directly — that component is
 *  click/selection-shaped (a `selected` boolean drives its visuals) and this badge is never
 *  tappable. */
@Composable
private fun MusclePill(group: MuscleGroup, tint: CardTint) {
    Box(
        Modifier
            .clip(AppShapes.pill)
            .background(tint.fillRaised)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(MuscleGroupLabels[group].orEmpty(), style = DaybookText.Caption, color = tint.onFillMuted)
    }
}

private fun formatDetailSetLine(s: com.daybook.app.data.model.WorkoutSet, weightUnit: com.daybook.app.data.workout.WeightUnit): String {
    val parts = mutableListOf<String>()
    // §2.7/§3 fix — used to hardcode "kg" (ignoring `app_settings.weight_unit`) and print a raw
    // `Float.toString()` ("60.0 kg") instead of trimming the trailing zero like every other
    // per-set display in this feature does.
    if (s.weightKg != null) parts += com.daybook.app.data.workout.formatWeight(s.weightKg, weightUnit)
    if (s.reps != null) parts += "${s.reps} reps"
    if (s.durationSeconds != null) parts += "${s.durationSeconds}s"
    if (s.distanceMeters != null) parts += "${com.daybook.app.data.workout.trimTrailingZero(s.distanceMeters / 1000f)} km"
    val prefix = "Set ${s.setNumber}: "
    return if (parts.isEmpty()) "${prefix}—" else prefix + parts.joinToString(" × ")
}
