package com.daybook.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.daybook.app.data.workout.formatElapsed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.R
import com.daybook.app.data.local.RoutineSummary
import com.daybook.app.data.workout.formatVolume
import com.daybook.app.ui.components.BottomSheetMenu
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.ConfirmDeleteDialog
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.IconTile
import com.daybook.app.ui.components.ScreenHeader
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SheetAction
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.UndoSnack
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.workout.beast.BeastPalette
import com.daybook.app.ui.workout.beast.BeastPrimaryButton
import com.daybook.app.ui.workout.beast.BeastText
import com.daybook.app.ui.workout.beast.StatGrid
import com.daybook.app.ui.workout.beast.StatGridItem

/**
 * A6a (§3.7.4) — the Beast Mode landing: "My routines" + the empty-workout button.
 * `contentPadding` (from `DaybookScaffold`) already carries the Beast Mode nav's clearance.
 */
@Composable
fun WorkoutHomeScreen(
    contentPadding: PaddingValues,
    onOpenWorkoutSettings: () -> Unit,
    onStartSession: (sessionId: String) -> Unit,
    onNewRoutine: () -> Unit,
    onEditRoutine: (routineId: String) -> Unit,
    viewModel: WorkoutHomeViewModel = hiltViewModel()
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val newSessionId by viewModel.newSessionId.collectAsStateWithLifecycle()
    val deletedToken by viewModel.deletedToken.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val errorToken by viewModel.errorToken.collectAsStateWithLifecycle()
    val weeklyStats by viewModel.weeklyStats.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val workoutIcon: ImageVector = ImageVector.vectorResource(id = R.drawable.ic_workout)

    var overflowFor by remember { mutableStateOf<RoutineSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<RoutineSummary?>(null) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(newSessionId) {
        newSessionId?.let { onStartSession(it); viewModel.clearNewSessionId() }
    }
    LaunchedEffect(Unit) { viewModel.refreshWeeklyStats() }

    Box(Modifier.fillMaxSize().background(BeastPalette.groundBrush())) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Beast Mode",
                subtitle = if (routines.isEmpty()) "No routines yet" else "${routines.size} routines",
                actions = {
                    CircleIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Beast Mode settings",
                        onClick = onOpenWorkoutSettings,
                        size = 40.dp
                    )
                }
            )
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                item {
                    // §3.3 item 3 / §4.1 — "This week" stat grid, above "My routines".
                    StatGrid(
                        items = listOf(
                            StatGridItem(DaybookIcons.BarChart, formatVolume(weeklyStats.volumeKg, weightUnit), "Volume", CardTints.Mint),
                            StatGridItem(workoutIcon, "${weeklyStats.workouts}", "Workouts", CardTints.SlateBlue),
                            StatGridItem(DaybookIcons.Flame, "${weeklyStats.streakDays}", "Day streak", CardTints.Peach),
                            StatGridItem(DaybookIcons.Star, "${weeklyStats.prs}", "PRs this week", CardTints.Butter)
                        )
                    )
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    if (activeSession != null) {
                        // Bug fix — this used to be a one-shot `remember` snapshot (froze the
                        // instant the card appeared); now a live label backed by the ViewModel's
                        // per-second ticker, same as WorkoutSessionScreen's own Duration stat.
                        val elapsedLabel = formatElapsed(elapsedSeconds)
                        val resumeTint = CardTints.Peach
                        SoftCard(
                            tint = resumeTint,
                            onClick = { activeSession?.let { onStartSession(it.id) } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Item 2/6 (LOCKED) — Bolt for a no-routine/instant session,
                                // the usual dumbbell for a routine-started one, so the two read
                                // as visually distinct at a glance.
                                IconTile(
                                    icon = if (activeSession?.routineId == null) DaybookIcons.Bolt else workoutIcon,
                                    tint = resumeTint,
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Workout in progress", style = DaybookText.CardTitle, color = resumeTint.accent)
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(elapsedLabel, style = BeastText.TileNumber, color = resumeTint.onFill)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "· tap to resume",
                                            style = DaybookText.Caption, color = resumeTint.onFillMuted,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    BeastPrimaryButton(
                        text = "Start an empty workout",
                        onClick = {
                            if (activeSession != null) pendingStart = { viewModel.startEmptyWorkout() }
                            else viewModel.startEmptyWorkout()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                }
                if (routines.isEmpty()) {
                    item {
                        EmptyState(
                            icon = workoutIcon,
                            title = "No routines yet",
                            body = "A routine is a list of exercises you do together — build one once and start it with a tap.",
                            actionLabel = "+ New routine",
                            onAction = onNewRoutine
                        )
                    }
                } else {
                    item { SectionHeader("My routines") }
                    itemsIndexed(routines, key = { _, r -> r.id }) { index, r ->
                        RoutineCard(
                            summary = r,
                            icon = workoutIcon,
                            tint = CardTints.byIndex(index),
                            onClick = {
                                if (activeSession != null) pendingStart = { viewModel.startRoutine(r.id) }
                                else viewModel.startRoutine(r.id)
                            },
                            onOverflow = { overflowFor = r }
                        )
                    }
                    item {
                        GhostButton(text = "+ New routine", onClick = onNewRoutine, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        UndoSnack(token = deletedToken, text = "Routine deleted")
        // Bug fix (BEAST_MODE_BUG_REPORT.md §1.1) — the ViewModel already produced
        // `errorMessage` on a failed start/delete; this screen never collected or showed it, so
        // a DB error left the user with no feedback at all.
        UndoSnack(token = errorToken, text = errorMessage ?: "")
    }

    val overflowTarget = overflowFor
    BottomSheetMenu(
        visible = overflowTarget != null,
        onDismiss = { overflowFor = null },
        actions = listOfNotNull(
            overflowTarget?.let { SheetAction(Icons.Filled.MoreVert, "Edit") { onEditRoutine(it.id); overflowFor = null } },
            overflowTarget?.let { SheetAction(Icons.Filled.MoreVert, "Duplicate") { viewModel.duplicateRoutine(it.id); overflowFor = null } },
            overflowTarget?.let {
                SheetAction(Icons.Filled.MoreVert, "Delete", destructive = true) { deleteTarget = it; overflowFor = null }
            }
        )
    )

    val toDelete = deleteTarget
    ConfirmDeleteDialog(
        visible = toDelete != null,
        itemName = "routine",
        title = "Delete this routine?",
        body = "The workouts you've already done with it are kept.",
        onConfirm = { toDelete?.let { viewModel.deleteRoutine(it.id) } },
        onDismiss = { deleteTarget = null }
    )

    val pending = pendingStart
    if (pending != null) {
        DaybookAlertDialog(
            onDismissRequest = { pendingStart = null },
            title = "Discard the current workout?",
            text = {
                Text(
                    "This permanently deletes the sets you've already logged in the workout that's " +
                        "still running. This can't be undone.",
                    style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary
                )
            },
            confirmLabel = "Discard & start new",
            onConfirm = { pendingStart = null; viewModel.discardActiveSessionAndThen(pending) },
            destructive = true,
            dismissLabel = "Cancel",
            onDismiss = { pendingStart = null }
        )
    }
}

@Composable
private fun RoutineCard(
    summary: RoutineSummary,
    icon: ImageVector,
    tint: com.daybook.app.ui.theme.CardTint,
    onClick: () -> Unit,
    onOverflow: () -> Unit
) {
    SoftCard(tint = tint, onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconTile(icon = icon, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(summary.name, style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1)
                val subtitle = when {
                    summary.exerciseCount == 0 -> "No exercises yet"
                    summary.targetSetCount == 0 -> "${summary.exerciseCount} ${if (summary.exerciseCount == 1) "exercise" else "exercises"}"
                    else -> "${summary.exerciseCount} ${if (summary.exerciseCount == 1) "exercise" else "exercises"} · ${summary.targetSetCount} sets"
                }
                Text(subtitle, style = DaybookText.Caption, color = tint.onFillMuted)
            }
            CircleIconButton(icon = Icons.Filled.MoreVert, contentDescription = "More", onClick = onOverflow)
        }
    }
}
