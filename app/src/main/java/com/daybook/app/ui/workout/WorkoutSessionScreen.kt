package com.daybook.app.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.workout.SetColumn
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.columnsFor
import com.daybook.app.data.workout.formatElapsed
import com.daybook.app.data.workout.formatVolume
import com.daybook.app.data.workout.formatWeight
import com.daybook.app.data.workout.isPersonalRecord
import com.daybook.app.data.workout.kgToLb
import com.daybook.app.data.workout.lbToKg
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.BottomSheetMenu
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.CircleStyle
import com.daybook.app.ui.components.ConfirmDeleteDialog
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.SheetAction
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.SortOption
import com.daybook.app.ui.components.SortSheet
import com.daybook.app.ui.components.UndoSnack
import com.daybook.app.ui.components.clickableImpl
import com.daybook.app.ui.components.combinedClickableImpl
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.ui.workout.beast.BeastPalette
import com.daybook.app.ui.workout.beast.BeastPrimaryButton
import com.daybook.app.ui.workout.beast.BeastStickyBar
import com.daybook.app.ui.workout.beast.BeastText
import com.daybook.app.ui.workout.beast.PrBadge
import com.daybook.app.ui.workout.beast.RingStat
import com.daybook.app.ui.workout.beast.StatGridTile
import com.daybook.app.ui.workout.beast.muscleTint
import com.daybook.app.ui.workout.beast.prCelebration
import kotlinx.coroutines.flow.collectLatest

private val REST_OPTIONS = linkedMapOf(0 to "Off", 30 to "30s", 60 to "60s", 90 to "90s", 120 to "2m", 180 to "3m", 300 to "5m")

// Bug fix round 2 — how much extra room `EditableSetCell`'s bring-into-view request reserves
// below a focused field, so it clears the floating Discard/Finish bar (`BeastStickyBar`) and not
// just the keyboard. Matches the 120dp trailing spacer the exercise list already appends after
// its last item for the same reason (see the `item { ... Spacer(height = 120.dp) }` block above),
// so a field in the very last exercise card has exactly that much real scroll room to use.
private val STICKY_BAR_CLEARANCE = 120.dp

/**
 * A6b (§3.7.1) — the live log, the richest screen in Round A.
 *
 * Deviations from §3.7.1, documented in HEALTH_AND_WORKOUT_PROGRESS.md: no RepDB thumbnail per
 * block (icon fallback throughout, matching the picker — §3.3's asset pipeline is deferred); no
 * muscle silhouettes (already deferred by the plan itself, §3.1.2); the header does not swap its
 * title to elapsed time on scroll — elapsed time is always shown in the stats row instead. Every
 * P1–P3 rule (immediate per-set writes, no whole-screen recompose on the ticker, no whole-history
 * query on this screen) is honoured as specified.
 */
@Composable
fun WorkoutSessionScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onPickExercise: () -> Unit,
    onFinished: () -> Unit,
    viewModel: WorkoutSessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val restRemaining by viewModel.restRemainingSeconds.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val errorToken by viewModel.errorToken.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    var restSheetForBlock by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var blockOverflowFor by remember { mutableStateOf<String?>(null) }
    var historyFor by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    // Feature addition — "I forgot to end this on time": the duration ring is tappable, opening
    // an editor that corrects the elapsed time before Finish (or any time mid-session).
    var showEditDuration by remember { mutableStateOf(false) }
    // §2.12 fix — a set cell only commits on focus-loss; tapping Finish/Back/+Add Exercise while a
    // cell is still focused could otherwise leave its typed-but-uncommitted value never written.
    // Clearing focus here forces that commit (via `EditableSetCell`'s own `onFocusChanged`) before
    // any of the three actions run.
    val focusManager = LocalFocusManager.current

    LaunchedEffect(finished) { if (finished) onFinished() }

    // Bug fix — this screen had no `imePadding()` anywhere. In this edge-to-edge app
    // (`WindowCompat.setDecorFitsSystemWindows(window, false)`), the system keyboard does NOT
    // resize the window on its own; without this, the keyboard drew straight over the bottom
    // Discard/Finish bar and the tail of the exercise list with no reserved space to scroll past
    // it — editing a set's last row made both unreachable. `imePadding()` on the root shrinks
    // this Box by the keyboard's height while it's shown, so the `weight(1f)` LazyColumn and the
    // BottomCenter bar both reflow above it instead of being covered.
    Box(Modifier.fillMaxSize().imePadding().background(BeastPalette.groundBrush())) {
    Column(Modifier.fillMaxSize()) {
        // Bug fix (round 3) — root cause found: this header was a hand-rolled Row with NO
        // status-bar inset handling, unlike every other screen in the app (which uses
        // `BackHeader`/`ScreenHeader`, both of which explicitly add
        // `WindowInsets.statusBars.asPaddingValues().calculateTopPadding()`). Since
        // `MainActivity` calls `WindowCompat.setDecorFitsSystemWindows(window, false)` app-wide
        // (edge-to-edge drawing), this header was rendering partly UNDER the status bar, and the
        // content below it did not start at the same rhythm (`Spacing.listTop`) every other
        // screen uses — reusing `BackHeader` fixes both in one move instead of re-deriving the
        // inset math by hand a second time.
        //
        // Item 1/6 (Workout UI fixes plan, LOCKED) — "Finish" moved out of the header's trailing
        // slot into a floating pill docked bottom-right (see `FinishFab` below); the header now
        // shows just the back arrow and the session's own title (falling back to the local date,
        // then a generic label — same fallback chain `WorkoutDetailScreen` already uses).
        BackHeader(
            title = state.session?.title ?: state.session?.localDate ?: "Workout",
            onBack = { focusManager.clearFocus(force = true); onNavigateBack() }
        )

        // Bug fix (post-A6) — the stats card and the +Add Exercise/Discard actions used to live
        // as `item {}` entries inside the same `fillMaxSize()` LazyColumn as the (often empty)
        // exercise blocks list. With zero blocks that put a huge dead area wherever the
        // LazyColumn's layout pass placed the short content, reading as broken rather than an
        // intentional empty state. Now: the stats card is a plain, non-lazy child directly under
        // the header (no gap), and the blocks region is the ONLY flexible (`weight(1f)`) child —
        // it only claims space once there is content to show. Empty, it renders inline (zero
        // height); non-empty, it becomes the scrollable list with the action buttons at its end.
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH)) {
            SoftCard(tint = CardTints.Neutral, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // §3.2/§4.2 — the elapsed-time ring against a typical 60-minute session
                    // baseline. Kept a LIVE ticker off `viewModel.elapsedSeconds`, same as the
                    // StatCol it replaces — see [[appforfood-workout-feedback]].
                    val elapsedLabel = formatElapsed(elapsed)
                    val ringInteraction = remember { MutableInteractionSource() }
                    Box {
                        RingStat(
                            progress = (elapsed / 3600f),
                            ringColor = BeastPalette.hotAccent(),
                            size = 108.dp,
                            strokeWidth = 9.dp,
                            // Feature addition — tapping the ring opens the duration editor, for
                            // the "I forgot to end this on time" case (or any mid-session fix).
                            modifier = Modifier.clickableImpl(ringInteraction) { showEditDuration = true }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Bug fix — an hours-long session ("1:53:32") at a fixed 22sp
                                // overflowed the ring's inner circle and crossed the stroke.
                                // Sessions with an hour digit get a smaller size so it stays inside.
                                Text(
                                    elapsedLabel,
                                    style = BeastText.BigNumber.copy(fontSize = if (elapsedLabel.count { it == ':' } > 1) 15.sp else 20.sp),
                                    color = LocalAccent.current,
                                    maxLines = 1
                                )
                                Text("DURATION", style = BeastText.MicroLabel, color = DaybookColors.TextMuted)
                            }
                        }
                        // A small edit badge makes the ring's tap target discoverable rather than
                        // a hidden gesture.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(DaybookColors.SurfaceElevated)
                                .border(1.dp, DaybookColors.Hairline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit duration",
                                tint = DaybookColors.TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatGridTile(
                            icon = DaybookIcons.BarChart,
                            value = formatVolume(state.stats.totalVolumeKg, weightUnit),
                            label = "Volume",
                            tint = CardTints.Mint
                        )
                        StatGridTile(
                            icon = DaybookIcons.Task,
                            value = "${state.stats.setCount}",
                            label = "Sets",
                            tint = CardTints.SlateBlue
                        )
                    }
                }
                if (restRemaining != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .clip(AppShapes.pill)
                            .background(LocalAccent.current.copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (restRemaining!! > 0) "Rest: ${restRemaining}s" else "Rest over",
                            style = DaybookText.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = LocalAccent.current
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (state.blocks.isEmpty()) {
            // No dead scroll area: the action sits directly under the stats card, and a single
            // trailing Spacer absorbs whatever room is left at the BOTTOM of the screen only.
            // Round 2 fix — "Discard" moved out of here into the bottom Discard/Finish bar below,
            // so it's always in the same place regardless of whether there are any blocks yet.
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH)) {
                GhostButton(
                    text = "+ Add Exercise",
                    onClick = { focusManager.clearFocus(force = true); onPickExercise() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screenH)) {
                itemsIndexed(state.blocks, key = { _, it -> it.block.id }) { index, blockUi ->
                    ExerciseBlockCard(
                        blockUi = blockUi,
                        weightUnit = weightUnit,
                        onNotesChange = { viewModel.setExerciseNotes(blockUi.block.id, it) },
                        onRestClick = { restSheetForBlock = blockUi.block.id },
                        onAddSet = { viewModel.addSet(blockUi.block.id, blockUi.block.exerciseId) },
                        onToggleComplete = { setId ->
                            // H8 fix — completing a set is the trigger the per-block rest timer was
                            // built for; previously the only way to start it was re-opening the rest
                            // picker sheet. `set.completedAt == null` here means the tap is about to
                            // MARK the set complete (toggleSetComplete flips it after this reads).
                            val wasIncomplete = blockUi.sets.firstOrNull { s -> s.id == setId }?.completedAt == null
                            viewModel.toggleSetComplete(setId)
                            val rest = blockUi.block.restSeconds
                            if (wasIncomplete) {
                                if (rest != null && rest > 0) viewModel.startRest(rest)
                            } else {
                                viewModel.cancelRest()
                            }
                        },
                        onUpdateWeight = { setId, v -> viewModel.updateSetWeight(setId, v) },
                        onUpdateReps = { setId, v -> viewModel.updateSetReps(setId, v) },
                        onUpdateDuration = { setId, v -> viewModel.updateSetDuration(setId, v) },
                        onUpdateDistance = { setId, v -> viewModel.updateSetDistance(setId, v) },
                        onDeleteSet = { viewModel.deleteSet(it) },
                        onOverflow = { blockOverflowFor = blockUi.block.id },
                        onOpenHistory = { historyFor = Triple(blockUi.block.exerciseId, blockUi.exerciseName, blockUi.trackingMode) },
                        // §2.9 — wires up the previously-dead `WorkoutRepository.reorderExercises`;
                        // up/down move buttons, matching RoutineEditScreen's own reorder affordance
                        // (no drag-and-drop primitive exists elsewhere in this codebase).
                        canMoveUp = index > 0,
                        canMoveDown = index < state.blocks.lastIndex,
                        onMoveUp = {
                            val ids = state.blocks.map { it.block.id }.toMutableList()
                            ids.add(index - 1, ids.removeAt(index))
                            viewModel.reorderExercises(ids)
                        },
                        onMoveDown = {
                            val ids = state.blocks.map { it.block.id }.toMutableList()
                            ids.add(index + 1, ids.removeAt(index))
                            viewModel.reorderExercises(ids)
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    GhostButton(
                        text = "+ Add Exercise",
                        onClick = { focusManager.clearFocus(force = true); onPickExercise() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }

    // Item 1 (Workout UI fixes plan, LOCKED) + round 2 feedback — "Discard" and "Finish" now dock
    // together as one bottom bar (Discard left, Finish right) instead of Discard living inside the
    // scrolling list and Finish floating alone; both stay visible while scrolling, never push
    // content. Finish keeps its compact-pill styling (PrimaryButton itself always fills its row).
    // Bug fix — this used to be a bare Row with no backing, so scrolled list content showed
    // through/behind it; `BeastStickyBar` gives it a scrim + solid footer, proper insets, padding.
    BeastStickyBar(modifier = Modifier.align(Alignment.BottomCenter)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GhostButton(text = "Discard", onClick = { showDiscardConfirm = true }, filled = true)
            FinishFab(onClick = { focusManager.clearFocus(force = true); viewModel.finish() })
        }
    }
    UndoSnack(token = errorToken, text = errorMessage ?: "")
    }

    // §2.15 fix — this used to hardcode "0" ("Off"), so the sheet never reflected the block's
    // actual rest setting (visible correctly on the card header just above it).
    val restSheetBlockUi = state.blocks.firstOrNull { it.block.id == restSheetForBlock }
    SortSheet(
        visible = restSheetForBlock != null,
        onDismiss = { restSheetForBlock = null },
        title = "Rest timer",
        sortOptions = REST_OPTIONS.map { (k, v) -> SortOption(k.toString(), v) },
        selectedSortKey = (restSheetBlockUi?.block?.restSeconds ?: 0).toString(),
        onSelectSort = { key ->
            val blockId = restSheetForBlock
            if (blockId != null) {
                val seconds = key.toInt()
                viewModel.setExerciseRest(blockId, seconds.takeIf { it > 0 })
                if (seconds > 0) viewModel.startRest(seconds) else viewModel.cancelRest()
            }
        },
        dismissOnSelect = true,
        neutralHeader = true
    )

    val overflowBlockId = blockOverflowFor
    BottomSheetMenu(
        visible = overflowBlockId != null,
        onDismiss = { blockOverflowFor = null },
        actions = listOfNotNull(
            overflowBlockId?.let {
                SheetAction(Icons.Filled.MoreVert, "Remove exercise", destructive = true) {
                    viewModel.removeExercise(it); blockOverflowFor = null
                }
            }
        )
    )

    ConfirmDeleteDialog(
        visible = showDiscardConfirm,
        itemName = "workout",
        title = "Discard this workout?",
        body = "This can't be undone.",
        onConfirm = { viewModel.discard() },
        onDismiss = { showDiscardConfirm = false }
    )

    val history = historyFor
    if (history != null) {
        ExerciseHistorySheet(
            exerciseId = history.first,
            exerciseName = history.second,
            trackingMode = history.third,
            onDismiss = { historyFor = null }
        )
    }

    if (showEditDuration) {
        EditDurationDialog(
            currentElapsedSeconds = elapsed,
            onDismiss = { showEditDuration = false },
            onConfirm = { newSeconds -> viewModel.setElapsedSeconds(newSeconds); showEditDuration = false }
        )
    }
}

/** Feature addition — "I forgot to end this on time": lets the elapsed time be corrected by
 *  hand, in H/M, rather than only ever ticking forward from when the session actually started. */
@Composable
private fun EditDurationDialog(
    currentElapsedSeconds: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var hours by remember { mutableStateOf((currentElapsedSeconds / 3600).toString()) }
    var minutes by remember { mutableStateOf(((currentElapsedSeconds % 3600) / 60).toString()) }
    DaybookAlertDialog(
        onDismissRequest = onDismiss,
        title = "Edit duration",
        text = {
            Column {
                Text(
                    "Forgot to end the session on time? Set how long you actually worked out.",
                    style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DaybookTextField(
                        value = hours,
                        // §3 fix — no upper clamp meant a stray `999` (or a fat-fingered `500`)
                        // was happily accepted as an hour count.
                        onValueChange = { v -> if (v.all { it.isDigit() } && (v.toIntOrNull() ?: 0) <= 99) hours = v },
                        label = "Hours", placeholder = "0",
                        modifier = Modifier.weight(1f)
                    )
                    DaybookTextField(
                        value = minutes,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) minutes = v },
                        label = "Minutes", placeholder = "0",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmLabel = "Save",
        onConfirm = {
            val h = hours.toLongOrNull() ?: 0L
            val m = (minutes.toLongOrNull() ?: 0L).coerceIn(0, 59)
            onConfirm(h * 3600 + m * 60)
        },
        dismissLabel = "Cancel",
        onDismiss = onDismiss
    )
}

/** Item 1 (Workout UI fixes plan, LOCKED) + §3.6/§4.2 — a compact pill FAB, now skinned as
 *  [BeastPrimaryButton]'s pill variant (sharper corners, accent-glow shadow) instead of
 *  `PrimaryButton`'s soft styling. Same click target/position/behaviour as before. */
@Composable
private fun FinishFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    BeastPrimaryButton(text = "Finish", onClick = onClick, modifier = modifier, pill = true)
}

@Composable
private fun ExerciseBlockCard(
    blockUi: BlockUi,
    weightUnit: WeightUnit,
    onNotesChange: (String) -> Unit,
    onRestClick: () -> Unit,
    onAddSet: () -> Unit,
    onToggleComplete: (String) -> Unit,
    onUpdateWeight: (String, Float?) -> Unit,
    onUpdateReps: (String, Int?) -> Unit,
    onUpdateDuration: (String, Int?) -> Unit,
    onUpdateDistance: (String, Float?) -> Unit,
    onDeleteSet: (String) -> Unit,
    onOverflow: () -> Unit,
    onOpenHistory: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    // §3 fix — this used to call `onNotesChange` (a DB write) on every keystroke, contradicting
    // this composable's own stated "save on focus-loss, never per-keystroke" convention
    // (`EditableSetCell`'s KDoc below). Now commits only on focus-loss, matching that cell.
    var notesText by remember(blockUi.block.id) { mutableStateOf(blockUi.block.notes.orEmpty()) }
    var notesWasFocused by remember(blockUi.block.id) { mutableStateOf(false) }
    val nameInteraction = remember { MutableInteractionSource() }
    val restInteraction = remember { MutableInteractionSource() }
    // §3.7/§4.2 — the block picks up its exercise's muscle-group tint instead of always Neutral.
    val tint = muscleTint(blockUi.primaryMuscle)
    SoftCard(tint = tint, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ExerciseThumbnail(
                imageId = blockUi.imageId,
                hasStartPeak = blockUi.hasStartPeak,
                fallbackIcon = DaybookIcons.Category,
                tint = tint,
                size = 40.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                blockUi.exerciseName, style = DaybookText.CardTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = tint.accent,
                modifier = Modifier.weight(1f).clickableImpl(nameInteraction, onOpenHistory)
            )
            // §2.9 — up/down move buttons: the previously-dead `WorkoutRepository.reorderExercises`
            // wired up, matching RoutineEditScreen's own reorder affordance.
            // User feedback round — these read as too small next to the enlarged set-complete
            // toggle below; bumped from 32.dp to the app-wide `CircleIconButton` default (44.dp,
            // already used by the unlabeled "More" button on the same row) for visual consistency.
            CircleIconButton(
                icon = Icons.Filled.KeyboardArrowUp, contentDescription = "Move up",
                onClick = onMoveUp, enabled = canMoveUp, size = 44.dp
            )
            CircleIconButton(
                icon = Icons.Filled.KeyboardArrowDown, contentDescription = "Move down",
                onClick = onMoveDown, enabled = canMoveDown, size = 44.dp
            )
            CircleIconButton(icon = Icons.Filled.MoreVert, contentDescription = "More", onClick = onOverflow)
        }
        Spacer(Modifier.height(4.dp))
        DaybookTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = null, placeholder = "Add notes here…",
            // User feedback round — this rendered as a hardcoded dark `SurfaceElevated` box that
            // clashed against the card's own muscle-group tint (e.g. Rose), reading as a harsh,
            // attention-grabbing black rectangle for what should be a quiet, optional field.
            // Passing the card's own `tint` makes it use `tint.fillRaised`/`onFill`/`accent`
            // instead, so it reads as a recessed area of the same card rather than a foreign block.
            tint = tint,
            modifier = Modifier.onFocusChanged { state ->
                if (notesWasFocused && !state.isFocused) onNotesChange(notesText)
                notesWasFocused = state.isFocused
            }
        )
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().clickableImpl(restInteraction, onRestClick).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // Bug fix — casing mismatch: this read "Rest Timer: OFF" (all-caps value) while
                // the rest-timer picker sheet it opens labels the same state "Off" (sentence
                // case) via REST_OPTIONS. Matching that here so the same setting isn't shown two
                // different ways a tap apart.
                "Rest Timer: ${blockUi.block.restSeconds?.let { "${it}s" } ?: "Off"}",
                style = DaybookText.Caption, color = tint.onFillMuted
            )
        }
        Spacer(Modifier.height(8.dp))
        SetTable(
            blockUi = blockUi, tint = tint, weightUnit = weightUnit, onToggleComplete = onToggleComplete,
            onUpdateWeight = onUpdateWeight, onUpdateReps = onUpdateReps,
            onUpdateDuration = onUpdateDuration, onUpdateDistance = onUpdateDistance,
            onDeleteSet = onDeleteSet
        )
        Spacer(Modifier.height(8.dp))
        GhostButton(text = "+ Add Set", onClick = onAddSet, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SetTable(
    blockUi: BlockUi,
    tint: com.daybook.app.ui.theme.CardTint,
    weightUnit: WeightUnit,
    onToggleComplete: (String) -> Unit,
    onUpdateWeight: (String, Float?) -> Unit,
    onUpdateReps: (String, Int?) -> Unit,
    onUpdateDuration: (String, Int?) -> Unit,
    onUpdateDistance: (String, Float?) -> Unit,
    onDeleteSet: (String) -> Unit
) {
    val columns = columnsFor(blockUi.trackingMode)
    // Redesign — the per-row bin icon is gone: it was both a mis-tap hazard (sitting right next
    // to the COMPLETE checkmark, same row height) and, since it had no matching header cell, the
    // reason the header row and the data rows didn't line up column-for-column. Deleting a set is
    // now a deliberate long-press on its SET number, opening the same bottom-sheet-menu pattern
    // the block header's own "More" overflow already uses.
    var deleteSetTarget by remember(blockUi.block.id) { mutableStateOf<String?>(null) }
    val haptics = com.daybook.app.ui.theme.rememberDaybookHaptics()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            columns.forEach { c ->
                // Round 2 fix — these used to left-align, so adjacent long labels (e.g. "PREVIOUS"
                // next to "DISTANCE") ran together with no visible gap between columns. Centering
                // each label over its own column (matching the centered body cells below) is what
                // actually separates them.
                Text(
                    columnLabel(c, weightUnit), style = DaybookText.Caption, color = DaybookColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        blockUi.sets.forEach { s ->
            // §3 fix — `prCelebration`'s internal `remember` used to be positional inside this
            // plain `forEach` loop (not a keyed `items`), so once sets became deletable (§2.9) a
            // deletion could shift every later row's position and mis-attribute its PR pulse to
            // the wrong set. `key(s.id)` gives each row's subtree its own remembered slot.
            key(s.id) {
            val prevForSet = blockUi.previous[s.setNumber]
            val completed = s.completedAt != null
            // Bug fix round 3 (coordinator-confirmed) — `EditableSetCell` only commits typed text
            // to the DB (and so to `s.weightKg`/`s.reps`/etc.) on focus-loss, by design. Evaluating
            // `isPersonalRecord` straight off `s` therefore judged the PR badge/highlight against
            // the OLD, already-committed number while the user was still mid-keystroke on a new
            // one — the badge could show (or fail to show) a PR that plainly didn't match the
            // digits on screen until the field lost focus and re-committed.
            //
            // Fix: mirror each PR-relevant cell's LIVE (uncommitted) text up to this row via
            // `EditableSetCell`'s `onTextChange`, and evaluate against a "candidate" `WorkoutSet`
            // built from that live text where it parses, falling back to the last COMMITTED value
            // (`s.<field>`) whenever the live text is blank/unparsable — the same "bad input ->
            // null -> falls back" shape `onCommit`'s own `toFloatOrNull()/toIntOrNull()` already
            // uses, so a cleared/ambiguous field reverts to the true committed state rather than
            // ever crashing or silently zeroing a PR check. The commit-on-blur persistence timing
            // itself is untouched — this only changes what the badge/highlight are computed from.
            // C1 fix — the WEIGHT cell is now shown/edited in the user's `weightUnit`, so its live
            // text is in that unit; seed and reconvert through `kgToLb`/`lbToKg` to keep this
            // (kg-space) candidate consistent with the committed `s.weightKg` column.
            var liveWeightText by remember { mutableStateOf(formatEditableNumber(displayWeight(s.weightKg, weightUnit))) }
            var liveRepsText by remember { mutableStateOf(s.reps?.toString() ?: "") }
            var liveDurationText by remember { mutableStateOf(s.durationSeconds?.toString() ?: "") }
            var liveDistanceText by remember { mutableStateOf(formatEditableNumber(s.distanceMeters?.let { it / 1000f })) }
            val candidateSet = s.copy(
                weightKg = liveWeightText.toFloatOrNull()?.let { storedWeightKg(it, weightUnit) } ?: s.weightKg,
                reps = liveRepsText.toIntOrNull() ?: s.reps,
                durationSeconds = liveDurationText.toIntOrNull() ?: s.durationSeconds,
                // DISTANCE is edited/displayed in km (matches the cell below) but stored in metres.
                distanceMeters = liveDistanceText.toFloatOrNull()?.let { it * 1000f } ?: s.distanceMeters
            )
            val isPr = completed && isPersonalRecord(candidateSet, blockUi.best, blockUi.trackingMode)
            // Screenshot-confirmed fix — `tint.fillRaised` alone read as almost no different from
            // the block's own resting background, so a completed set barely registered at a
            // glance. Still the block's own muscle tint (§3.5/§4.2's "no flat green wash" call
            // stands), just at a strength that actually reads as "done": accent-tinted fill,
            // animated in the instant `completedAt` flips from null so the transition itself
            // draws the eye, plus a solid accent bar down the row's left edge for a highlight that
            // survives even if the fill tint is close to the block colour on a given tint/theme.
            val rowBg by animateColorAsState(
                if (completed) tint.accent.copy(alpha = 0.22f) else Color.Transparent,
                animationSpec = Motion.softSpring(),
                label = "setRowBg"
            )
            val edgeBarColor = tint.accent
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .then(
                        if (completed) {
                            Modifier.drawBehind {
                                drawRect(color = edgeBarColor, size = size.copy(width = 3.dp.toPx()))
                            }
                        } else Modifier
                    )
                    .prCelebration(trigger = isPr)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEach { c ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when (c) {
                            // Redesign — hold the SET number to delete this set, replacing the
                            // old always-visible bin icon column. Same `combinedClickableImpl`
                            // long-press pattern the bottom nav already uses; only this cell
                            // carries the gesture so the editable WEIGHT/REPS/etc. cells keep
                            // their normal tap-to-focus behaviour untouched.
                            SetColumn.SET -> {
                                val setInteraction = remember { MutableInteractionSource() }
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .combinedClickableImpl(
                                            interaction = setInteraction,
                                            onLongClickLabel = "Delete set",
                                            onLongClick = { deleteSetTarget = s.id },
                                            onClick = {}
                                        )
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPr) {
                                        PrBadge()
                                    } else {
                                        Text("${s.setNumber}", style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary)
                                    }
                                }
                            }
                            // User feedback round — bumping this to CardSubtitle (14sp, kept per
                            // user's "as expected" confirmation) made longer values like
                            // "20.0kg × 12" wrap to a second line inside this equal-weight column,
                            // unlike every other single-line cell in the row. Force single line
                            // with ellipsis truncation rather than reverting the size.
                            SetColumn.PREVIOUS -> Text(
                                formatPrevious(prevForSet, weightUnit),
                                style = DaybookText.CardSubtitle,
                                color = DaybookColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            // Item 4 (Workout UI fixes plan, LOCKED) — these four columns used to
                            // be plain read-only Text (the bug: a new set could only ever be
                            // marked complete, never actually filled in). Now a compact numeric
                            // table-cell field, saving via column-scoped commits (§2.13) on
                            // focus-loss (not per-keystroke, per the decided fix).
                            SetColumn.WEIGHT -> EditableSetCell(
                                // C1 fix — was hard-coded to kg regardless of `weightUnit`; the
                                // cell now displays/accepts values in the user's chosen unit and
                                // converts back to kg on commit so `workout_sets.weight_kg` keeps
                                // meaning kilograms no matter what the user typed.
                                initialValue = formatEditableNumber(displayWeight(s.weightKg, weightUnit)),
                                keyboardType = KeyboardType.Decimal,
                                placeholder = if (weightUnit == WeightUnit.LB) "lb" else "kg",
                                onCommit = { text ->
                                    onUpdateWeight(s.id, text.toFloatOrNull()?.let { storedWeightKg(it, weightUnit) })
                                },
                                onTextChange = { liveWeightText = it }
                            )
                            SetColumn.REPS -> EditableSetCell(
                                initialValue = s.reps?.toString() ?: "",
                                keyboardType = KeyboardType.Number,
                                placeholder = "reps",
                                onCommit = { text -> onUpdateReps(s.id, text.toIntOrNull()) },
                                onTextChange = { liveRepsText = it }
                            )
                            SetColumn.DURATION -> EditableSetCell(
                                initialValue = s.durationSeconds?.toString() ?: "",
                                keyboardType = KeyboardType.Number,
                                placeholder = "sec",
                                onCommit = { text -> onUpdateDuration(s.id, text.toIntOrNull()) },
                                onTextChange = { liveDurationText = it }
                            )
                            SetColumn.DISTANCE -> EditableSetCell(
                                // Displayed/edited in km (matches `formatPrevious`'s convention);
                                // stored in metres.
                                initialValue = formatEditableNumber(s.distanceMeters?.let { it / 1000f }),
                                keyboardType = KeyboardType.Decimal,
                                placeholder = "km",
                                onCommit = { text -> onUpdateDistance(s.id, text.toFloatOrNull()?.let { it * 1000f }) },
                                onTextChange = { liveDistanceText = it }
                            )
                            // Reference-screenshot fix — the old `CircleIconButton` toggle
                            // (32/36dp circle) read as a small, tentative tap target next to a
                            // reference app's large filled-square checkbox. Swapped to a
                            // rounded-square target (`AppShapes.tile`, the same shape convention
                            // `IconTile`/`TintPicker` swatches already use elsewhere) at a
                            // meaningfully bigger 46dp. A raw solid `Success` fill clashed against
                            // an exercise card's own accent tint (e.g. Rose/Lavender cards), so the
                            // completed state instead reuses the same muted mint tonal look already
                            // used by the Volume/Sets stat tiles above, which reads as "confident"
                            // without fighting the surrounding card color. Row-level green highlight
                            // above is untouched.
                            SetColumn.COMPLETE -> {
                                val completeInteraction = remember { MutableInteractionSource() }
                                val completePressed by completeInteraction.collectIsPressedAsState()
                                val completeScale by animateFloatAsState(
                                    if (completePressed) 0.92f else 1f, Motion.pressSpring(), label = "setCompleteScale"
                                )
                                val toggleSize = 46.dp
                                val mintTint = CardTints.Mint
                                val completeBg by animateColorAsState(
                                    if (completed) mintTint.fillRaised else DaybookColors.SurfaceElevated,
                                    label = "setCompleteBg"
                                )
                                val completeBorder = if (completed) mintTint.accent.copy(alpha = 0.4f) else DaybookColors.Hairline
                                val completeFg = if (completed) mintTint.accent else DaybookColors.TextPrimary
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = completeScale; scaleY = completeScale }
                                        .size(toggleSize)
                                        .clip(AppShapes.tile)
                                        .background(completeBg)
                                        .border(1.dp, completeBorder, AppShapes.tile)
                                        .clickableImpl(completeInteraction) {
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            onToggleComplete(s.id)
                                        }
                                        .semantics { contentDescription = "Complete set" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = completeFg,
                                        modifier = Modifier.size(toggleSize * 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
    // §2.9 — the previously-dead `WorkoutSessionViewModel.deleteSet` wired up: a mistyped or
    // accidentally-added set is no longer permanent for the session. Now reached via long-press
    // on the set's number (see the SET column above) rather than an always-visible bin icon.
    BottomSheetMenu(
        visible = deleteSetTarget != null,
        onDismiss = { deleteSetTarget = null },
        actions = listOfNotNull(
            deleteSetTarget?.let { id ->
                SheetAction(Icons.Filled.Delete, "Delete set", destructive = true) {
                    onDeleteSet(id)
                    deleteSetTarget = null
                }
            }
        )
    )
}

/** Item 4 — a compact "table-cell" numeric input, distinct from the full-width `DaybookTextField`
 *  (which always carries a label row + 14dp padding sized for a form, not a dense table). Commits
 *  via [onCommit] only on focus-loss, never per-keystroke — a per-digit DB write would be wasteful
 *  and would fight the user mid-type ("Decided: save on focus-loss / moving to the next field").
 *  Local `text` is keyed on [initialValue] so a value pushed from Room (e.g. another device's
 *  sync, or "+ Add Set" seeding PREVIOUS) overwrites the field only when it's not the one
 *  currently being edited. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EditableSetCell(
    initialValue: String,
    keyboardType: KeyboardType,
    // Round 2 fix — empty DISTANCE/TIME (and WEIGHT/REPS) cells used to render as identical blank
    // boxes with nothing to tell them apart short of tracing back up to the (also-too-cramped)
    // column header. A per-column unit hint, shown only while the cell is empty (same convention
    // `DaybookTextField`'s own `placeholder` uses), makes each field self-explanatory on its own.
    placeholder: String,
    onCommit: (String) -> Unit,
    // Bug fix round 3 — mirrors this cell's live (not-yet-committed) text up to the set-row level,
    // on every change, for whichever cell governs the current tracking mode's PR-relevant value(s)
    // (see `SetTable`'s `candidateSet` above). Fired from a `LaunchedEffect(text)` below rather
    // than from `onValueChange` directly so it reflects `text`'s true current value regardless of
    // WHY it changed — a keystroke, or the `remember(initialValue)` reset when a fresh committed
    // value arrives from Room. Defaults to a no-op so every other (non-PR-relevant) caller is
    // unaffected.
    onTextChange: (String) -> Unit = {}
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    var wasFocused by remember { mutableStateOf(false) }
    // §2.12 fix — focus-loss is the only commit path; if this cell (and its focused BasicTextField)
    // leaves composition without ever losing focus first — the block was removed, "+ Add Exercise"
    // or Finish navigated away before a recomposition moved focus elsewhere — `onFocusChanged`
    // never fires and the typed value is lost. Committing again on teardown, when still focused,
    // is the belt to the screen-level `focusManager.clearFocus()` suspenders (WorkoutSessionScreen).
    val onCommitState = rememberUpdatedState(onCommit)
    val textState = rememberUpdatedState(text)
    val wasFocusedState = rememberUpdatedState(wasFocused)
    DisposableEffect(Unit) {
        onDispose { if (wasFocusedState.value) onCommitState.value(textState.value) }
    }
    LaunchedEffect(text) { onTextChange(text) }
    // Bug fix round 2 (screen-recording) — the original fix (a single `bringIntoView()` call
    // after a flat `delay(250)`) wasn't reliable: on a real device the IME's own resize
    // animation — especially with a tall system keyboard like Gboard's suggestion-strip toolbar,
    // which adds real height on top of the key rows — can easily run longer than 250ms. That
    // race had two visible symptoms in the recording: (1) the fixed delay sometimes fired the
    // single `bringIntoView()` call MID-animation, against layout coordinates that were still
    // sliding as `imePadding()` on the root Box kept resizing after the call had already
    // finished, so the field settled back under the keyboard once the resize caught up; and
    // (2) because it was a one-shot call instead of tracking the animation, the LazyColumn could
    // jump to a stale scroll position and then jump again when the resize completed a moment
    // later — the "sticky bar overlapping oddly" jump in the video.
    //
    // Fix: drive `bringIntoView()` off the SAME animated value that drives `imePadding()` itself
    // — `WindowInsets.ime`'s bottom inset — instead of a guessed duration. `collectLatest` means
    // every intermediate frame of the IME's resize animation cancels the previous (now-stale)
    // scroll request and re-issues it against the current inset, so this cell is chased into view
    // in lockstep with the keyboard for the whole animation; the LAST emission (once the inset
    // stops changing) is always the one that actually completes, landing the field in its final,
    // correct position with no separate settle step needed.
    //
    // Second fix: `bringIntoView()` with no `Rect` only guarantees the field's own tiny bounds
    // clear the *keyboard* — the sticky Discard/Finish bar (`BeastStickyBar`, a ~96dp overlay:
    // ~50dp Finish/Discard row + its own 24dp top / 12dp bottom padding) is a separate overlay on
    // top of the LazyColumn, not something the list's own viewport math knows to avoid. A field
    // could clear the keyboard and still land right behind that bar. Passing a [Rect] that
    // extends STICKY_BAR_CLEARANCE below the field's own measured bounds asks the scrollable
    // parent to keep that much extra room clear too — the same 120dp margin the list's own
    // trailing spacer already budgets for this bar, so it's guaranteed to have that much scroll
    // room even for a field in the very last exercise card.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    var isFocused by remember { mutableStateOf(false) }
    val imeInsets = WindowInsets.ime
    LaunchedEffect(isFocused) {
        if (isFocused) {
            snapshotFlow { imeInsets.getBottom(density) }
                .collectLatest {
                    val clearancePx = with(density) { STICKY_BAR_CLEARANCE.toPx() }
                    val rect = Rect(
                        left = 0f,
                        top = 0f,
                        right = fieldSize.width.toFloat(),
                        bottom = fieldSize.height.toFloat() + clearancePx
                    )
                    bringIntoViewRequester.bringIntoView(rect)
                }
        }
    }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        // User feedback round — enlarged alongside the set-complete toggle (46.dp) so the row
        // doesn't read as one big target next to several small ones: bigger cell + larger digits.
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = DaybookColors.TextPrimary, textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(LocalAccent.current),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = Modifier
            .width(58.dp)
            .clip(AppShapes.field)
            .background(DaybookColors.SurfaceElevated)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .onSizeChanged { fieldSize = it }
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusEvent { state -> isFocused = state.isFocused }
            .onFocusChanged { state ->
                if (wasFocused && !state.isFocused) onCommit(text)
                wasFocused = state.isFocused
            },
        decorationBox = { innerField ->
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) {
                    // Bug fix — the unit placeholder ("kg"/"reps"/"sec"/"km") used to render at
                    // the same size/weight as a real typed value, just in TextMuted — easy to
                    // mistake for an already-filled cell at a glance in a dense table. Smaller +
                    // lower alpha keeps it readable as a hint without competing with real digits.
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = DaybookColors.TextMuted.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )
                }
                innerField()
            }
        }
    )
}

/** Trims a trailing ".0" the same way a set's PREVIOUS/PR text already does, and renders `null` as
 *  an empty string (an empty editable cell, not a "–" dash — the dash is for read-only display). */
private fun formatEditableNumber(v: Float?): String {
    if (v == null) return ""
    return if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()
}

// Bug fix (units clarity) — WEIGHT already showed its unit ("KG") as the header instead of a
// generic "WEIGHT" label, but DURATION/DISTANCE showed the generic "TIME"/"DISTANCE" — the unit
// hint only lived in the empty-cell placeholder, so it vanished the moment a value was entered
// and there was nothing to tell "12" apart from "12 seconds" vs "12 km". DURATION/DISTANCE now
// follow the same "header IS the unit" convention as WEIGHT, for every tracking mode.
// C1 fix — the stored `weight_kg` column always means kilograms; these two are the single
// choke point that converts to/from whatever unit the WEIGHT cell is currently displaying, so a
// typed value round-trips correctly (type 135 in lb mode -> ~61.23 kg stored -> displays as 135
// lb again) instead of being persisted verbatim regardless of unit.
private fun displayWeight(kg: Float?, unit: WeightUnit): Float? =
    kg?.let { if (unit == WeightUnit.LB) kgToLb(it) else it }

private fun storedWeightKg(typed: Float, unit: WeightUnit): Float =
    if (unit == WeightUnit.LB) lbToKg(typed) else typed

private fun columnLabel(c: SetColumn, weightUnit: WeightUnit): String = when (c) {
    SetColumn.SET -> "SET"
    SetColumn.PREVIOUS -> "PREV"
    SetColumn.WEIGHT -> if (weightUnit == WeightUnit.LB) "LB" else "KG"
    SetColumn.REPS -> "REPS"
    SetColumn.DURATION -> "SEC"
    SetColumn.DISTANCE -> "KM"
    SetColumn.COMPLETE -> "✓"
}

private fun formatPrevious(s: WorkoutSet?, weightUnit: WeightUnit): String {
    if (s == null) return "–"
    val w = s.weightKg
    val r = s.reps
    // User feedback round — bumping this column's text style to CardSubtitle (14sp) made the old
    // "20.0kg × 12" formatting (padded spaces, an always-shown ".0") wrap to a second line inside
    // the column's equal-weight width. Ellipsis-truncating that string hides the rep count
    // entirely, which is just as bad as wrapping, so the format itself is tightened first: reuse
    // `formatEditableNumber`'s trailing-".0" trim (20.0 -> "20") and drop the spaces around "×"
    // (matches the compact convention header labels already use). This alone fits the common
    // case on one line; `maxLines = 1` + ellipsis at the call site is kept only as a safety net
    // for genuinely long values (e.g. 3-digit reps with a decimal weight).
    // C1 fix — this used to hardcode "kg" regardless of `weightUnit`, unlike every other weight
    // display in the app (`formatWeight`). Reuse `formatWeight` for the weight portion so PREVIOUS
    // matches the unit the WEIGHT cell above it is showing.
    return when {
        w != null && r != null -> "${formatWeight(w, weightUnit).replace(" ", "")}×$r"
        r != null -> "$r"
        // §3 fix — a weight-only PREVIOUS (weight logged, reps left blank — reachable from a Hevy
        // import or from filling only the KG cell) used to fall through every branch to "–" even
        // though the data was there.
        w != null -> formatWeight(w, weightUnit).replace(" ", "")
        s.durationSeconds != null -> "${s.durationSeconds}s"
        s.distanceMeters != null -> "${formatEditableNumber(s.distanceMeters / 1000f)}km"
        else -> "–"
    }
}

