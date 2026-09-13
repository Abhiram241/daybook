package com.daybook.app.ui.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.R
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.local.SessionAggregate
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.MuscleGroupLabels
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.formatVolume
import com.daybook.app.ui.components.BottomSheetMenu
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.DaybookChip
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.ScreenHeader
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SheetAction
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.UndoSnack
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.workout.beast.BeastPalette
import com.daybook.app.ui.workout.beast.BeastText
import com.daybook.app.ui.workout.beast.muscleTint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * History tab redesign — date-bucketed, richer rows (§2.1/§2.2), a single-exercise filter (open
 * question 3) and Edit/Duplicate-as-routine overflow actions (open question 4) on top of A6a's
 * original flat list. §2.5 unchanged: tapping still opens `WorkoutDetailScreen`/resumes the active
 * session, no new nav destinations. No header stat strip (§2.3 — skipped per the app owner's
 * decision). "Import from Hevy" moved to Beast Mode's own Settings screen — this tab no longer
 * carries it.
 */
@Composable
fun WorkoutHistoryScreen(
    contentPadding: PaddingValues,
    onOpenSession: (sessionId: String) -> Unit,
    onOpenDetail: (sessionId: String) -> Unit,
    viewModel: WorkoutHistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val availableExercises by viewModel.availableExercises.collectAsStateWithLifecycle()
    val selectedExercise by viewModel.selectedExercise.collectAsStateWithLifecycle()
    val deletedToken by viewModel.deletedToken.collectAsStateWithLifecycle()
    val actionToken by viewModel.actionToken.collectAsStateWithLifecycle()
    val actionText by viewModel.actionText.collectAsStateWithLifecycle()
    val newSessionId by viewModel.newSessionId.collectAsStateWithLifecycle()

    var overflowFor by remember { mutableStateOf<WorkoutSession?>(null) }
    var renaming by remember { mutableStateOf<WorkoutSession?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val workoutIcon: ImageVector = ImageVector.vectorResource(id = R.drawable.ic_workout)

    LaunchedEffect(newSessionId) {
        newSessionId?.let { onOpenSession(it); viewModel.clearNewSessionId() }
    }

    Box(Modifier.fillMaxSize().background(BeastPalette.groundBrush())) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "History", subtitle = "${sessions.size} workouts")
            if (sessions.isEmpty()) {
                EmptyState(
                    icon = workoutIcon,
                    title = "No workouts yet",
                    body = "Start a workout to log your first session, or bring your history over from Hevy — " +
                        "that's in Beast Mode settings (the gear on Routines).",
                    actionLabel = "Start an empty workout",
                    // §2.14 fix — this used to be a no-op; the CTA now actually starts a session.
                    onAction = { viewModel.startEmptyWorkout() },
                    modifier = Modifier.padding(contentPadding)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                    item {
                        ExerciseFilterRow(
                            selected = selectedExercise,
                            onOpenPicker = { showFilterSheet = true },
                            onClear = { viewModel.selectExerciseFilter(null) }
                        )
                    }
                    if (selectedExercise != null && sections.isEmpty()) {
                        item {
                            EmptyState(
                                icon = DaybookIcons.Category,
                                title = "No matching workouts",
                                body = "No session with \"${selectedExercise?.name}\" yet."
                            )
                        }
                    }
                    sections.forEach { (label, rows) ->
                        item { SectionHeader(label) }
                        items(rows, key = { it.session.id }) { row ->
                            SessionRow(
                                row = row,
                                weightUnit = weightUnit,
                                // Item 2/6 (LOCKED) — Bolt for a no-routine/instant session's row,
                                // the usual dumbbell for a routine-started one.
                                icon = if (row.session.routineId == null) DaybookIcons.Bolt else workoutIcon,
                                onClick = {
                                    if (row.session.status == "ACTIVE") onOpenSession(row.session.id)
                                    else onOpenDetail(row.session.id)
                                },
                                onOverflow = { overflowFor = row.session }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
        UndoSnack(token = deletedToken, text = "Workout deleted")
        UndoSnack(token = actionToken, text = actionText)
    }

    val overflowTarget = overflowFor
    BottomSheetMenu(
        visible = overflowTarget != null,
        onDismiss = { overflowFor = null },
        actions = listOfNotNull(
            overflowTarget?.let {
                SheetAction(Icons.Filled.MoreVert, "Edit") { renaming = it; overflowFor = null }
            },
            overflowTarget?.let {
                SheetAction(Icons.Filled.MoreVert, "Duplicate as routine") {
                    viewModel.duplicateAsRoutine(it); overflowFor = null
                }
            },
            overflowTarget?.let {
                SheetAction(Icons.Filled.MoreVert, "Delete", destructive = true) {
                    viewModel.deleteSession(it.id); overflowFor = null
                }
            }
        )
    )

    val renameTarget = renaming
    if (renameTarget != null) {
        RenameSessionDialog(
            session = renameTarget,
            onDismiss = { renaming = null },
            onConfirm = { newTitle -> viewModel.renameSession(renameTarget.id, newTitle); renaming = null }
        )
    }

    if (showFilterSheet) {
        ExerciseFilterSheet(
            exercises = availableExercises,
            onPick = { viewModel.selectExerciseFilter(it); showFilterSheet = false },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun ExerciseFilterRow(selected: CatalogExercise?, onOpenPicker: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DaybookChip(
            label = selected?.name ?: "Filter by exercise",
            selected = selected != null,
            onClick = onOpenPicker,
            leadingIcon = DaybookIcons.FilterList
        )
        if (selected != null) {
            Spacer(Modifier.width(8.dp))
            CircleIconButton(icon = Icons.Filled.Close, contentDescription = "Clear filter", onClick = onClear, size = 32.dp)
        }
    }
}

@Composable
private fun RenameSessionDialog(session: WorkoutSession, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(session.id) { mutableStateOf(session.title ?: "") }
    DaybookAlertDialog(
        onDismissRequest = onDismiss,
        title = "Rename workout",
        text = {
            DaybookTextField(
                value = text,
                onValueChange = { text = it },
                label = null,
                placeholder = session.localDate
            )
        },
        confirmLabel = "Save",
        onConfirm = { onConfirm(text) },
        dismissLabel = "Cancel",
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseFilterSheet(
    exercises: List<CatalogExercise>,
    onPick: (CatalogExercise) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, exercises) {
        if (query.isBlank()) exercises else exercises.filter { it.name.contains(query, ignoreCase = true) }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.sheet
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Filter by exercise", style = DaybookText.SectionTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            DaybookTextField(value = query, onValueChange = { query = it }, label = null, placeholder = "Search exercise")
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                EmptyState(
                    icon = DaybookIcons.Category,
                    title = "No exercises found",
                    body = if (query.isNotBlank()) "Nothing matches \"$query\"." else "Log a workout to filter by exercise."
                )
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(filtered, key = { it.id }) { ex ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(ex) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ex.name, style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SessionRow(
    row: WorkoutHistoryViewModel.HistoryRow,
    weightUnit: WeightUnit,
    icon: ImageVector,
    onClick: () -> Unit,
    onOverflow: () -> Unit
) {
    val session = row.session
    val isActive = session.status == "ACTIVE"
    // §4.5 — tinted card instead of Neutral, keyed off the session's first exercise's muscle
    // group (falls back to Neutral for an active/instant session with no thumbnail resolved yet).
    val tint = row.thumbnail?.primaryMuscle?.let { muscleTint(it) } ?: CardTints.Neutral
    SoftCard(tint = tint, onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        // Bug fix (scaling) — this Row used to center the 44dp thumbnail against the whole text
        // column, so once that column grew past two lines (volume line, and now the muscle-group
        // chart below) the thumbnail read as small and adrift in the middle of a much taller
        // card instead of anchoring it. Top-aligned, slightly larger (52dp) thumbnail reads as a
        // proper avatar for a card whose content now varies in height.
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            ExerciseThumbnail(
                imageId = row.thumbnail?.imageId,
                hasStartPeak = row.thumbnail?.hasStartPeak ?: false,
                fallbackIcon = icon,
                tint = tint,
                size = 52.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isActive) "Workout in progress" else (session.title ?: session.localDate),
                    style = DaybookText.CardTitle,
                    color = if (isActive) LocalAccent.current else tint.onFill,
                    maxLines = 1
                )
                if (isActive) {
                    Text("Tap to carry on", style = DaybookText.Caption, color = tint.onFillMuted)
                } else {
                    Text(sessionDateTimeLine(session), style = DaybookText.Caption, color = tint.onFillMuted)
                    val agg = row.aggregate
                    if (agg != null && agg.setCount > 0) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            // §4.5 — the volume figure (the number people actually scan for)
                            // gets the bold-number treatment; sets/exercises stay a plain caption.
                            Text(
                                formatVolume(agg.totalVolumeKg.toFloat(), weightUnit),
                                style = BeastText.TileNumber, color = tint.onFill
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "· ${agg.setCount} sets · ${agg.exerciseCount} exercises",
                                style = DaybookText.Caption, color = tint.onFillMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        if (row.muscleVolume.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            MuscleVolumeChart(
                                breakdown = row.muscleVolume,
                                weightUnit = weightUnit,
                                tint = tint
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            CircleIconButton(icon = Icons.Filled.MoreVert, contentDescription = "More", onClick = onOverflow)
        }
    }
}

/**
 * Feature addition — "what muscle groups were worked on that day and how much": a compact
 * horizontal bar per muscle group, heaviest first, inside the workout card itself (this screen's
 * `HistoryRow.muscleVolume`, one batched query per page — see
 * `WorkoutRepository.muscleGroupVolumeForSessions`). Capped at the top 4 groups so a session
 * touching many muscles still fits a list card instead of turning it into a full chart screen.
 */
@Composable
private fun MuscleVolumeChart(
    breakdown: List<Pair<MuscleGroup, Float>>,
    weightUnit: WeightUnit,
    tint: com.daybook.app.ui.theme.CardTint,
    modifier: Modifier = Modifier
) {
    val top = remember(breakdown) { breakdown.take(4) }
    val maxVolume = top.first().second
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        top.forEach { (group, volume) ->
            MuscleVolumeBar(group = group, volume = volume, maxVolume = maxVolume, weightUnit = weightUnit, tint = tint)
        }
    }
}

@Composable
private fun MuscleVolumeBar(
    group: MuscleGroup,
    volume: Float,
    maxVolume: Float,
    weightUnit: WeightUnit,
    tint: com.daybook.app.ui.theme.CardTint
) {
    val rm = LocalReduceMotion.current
    // A floor fraction keeps a much-smaller-but-still-worked group visible as a sliver rather
    // than an invisible zero-width bar.
    val targetFraction = (volume / maxVolume).coerceIn(0.06f, 1f)
    val fraction by animateFloatAsState(
        targetFraction, if (rm) snap() else Motion.softSpring(), label = "muscleVolumeBar"
    )
    val barColor = muscleTint(group).accent
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            MuscleGroupLabels[group].orEmpty(),
            style = DaybookText.Caption,
            color = tint.onFillMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(74.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(AppShapes.pill)
                .background(tint.fillRaised)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(AppShapes.pill)
                    .background(barColor)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatVolume(volume, weightUnit),
            style = DaybookText.Caption,
            color = tint.onFillMuted,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 52.dp)
        )
    }
}

private val rowDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

/** "Tue, 9 Sep · 52 min" (§2.1) — the minutes clause is omitted for a completed session with no
 *  recorded `endedAt` (shouldn't normally happen, but never worth crashing a list row over). */
private fun sessionDateTimeLine(session: WorkoutSession): String {
    val date = runCatching { LocalDate.parse(session.localDate) }.getOrNull()
    val dateLabel = date?.format(rowDateFormatter) ?: session.localDate
    val minutes = session.endedAt?.let { ((it - session.startedAt) / 60000).coerceAtLeast(0) }
    return if (minutes != null) "$dateLabel · ${minutes} min" else dateLabel
}

