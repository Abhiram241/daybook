package com.daybook.app.ui.workout

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.daybook.app.data.workout.isPersonalRecord
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val REST_OPTIONS = linkedMapOf(0 to "Off", 30 to "30s", 60 to "60s", 90 to "90s", 120 to "2m", 180 to "3m", 300 to "5m")

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
                        onNotesChange = { viewModel.setExerciseNotes(blockUi.block.id, it) },
                        onRestClick = { restSheetForBlock = blockUi.block.id },
                        onAddSet = { viewModel.addSet(blockUi.block.id, blockUi.block.exerciseId) },
                        onToggleComplete = { viewModel.toggleSetComplete(it) },
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
            CircleIconButton(
                icon = Icons.Filled.KeyboardArrowUp, contentDescription = "Move up",
                onClick = onMoveUp, enabled = canMoveUp, size = 32.dp
            )
            CircleIconButton(
                icon = Icons.Filled.KeyboardArrowDown, contentDescription = "Move down",
                onClick = onMoveDown, enabled = canMoveDown, size = 32.dp
            )
            CircleIconButton(icon = Icons.Filled.MoreVert, contentDescription = "More", onClick = onOverflow)
        }
        Spacer(Modifier.height(4.dp))
        DaybookTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = null, placeholder = "Add notes here…",
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
            blockUi = blockUi, tint = tint, onToggleComplete = onToggleComplete,
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
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            columns.forEach { c ->
                // Round 2 fix — these used to left-align, so adjacent long labels (e.g. "PREVIOUS"
                // next to "DISTANCE") ran together with no visible gap between columns. Centering
                // each label over its own column (matching the centered body cells below) is what
                // actually separates them.
                Text(
                    columnLabel(c), style = DaybookText.Caption, color = DaybookColors.TextMuted,
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
            val isPr = s.completedAt != null && isPersonalRecord(s, blockUi.best, blockUi.trackingMode)
            val prevForSet = blockUi.previous[s.setNumber]
            Row(
                Modifier
                    .fillMaxWidth()
                    // §3.5/§4.2 — a completed set's highlight is now the block's own muscle tint
                    // (still clearly "done" at a glance) instead of a flat green alpha wash; the
                    // one-shot celebration pulse plays only the instant a set becomes a PR.
                    .then(if (s.completedAt != null) Modifier.background(tint.fillRaised) else Modifier)
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
                            SetColumn.PREVIOUS -> Text(formatPrevious(prevForSet), style = DaybookText.Caption, color = DaybookColors.TextMuted)
                            // Item 4 (Workout UI fixes plan, LOCKED) — these four columns used to
                            // be plain read-only Text (the bug: a new set could only ever be
                            // marked complete, never actually filled in). Now a compact numeric
                            // table-cell field, saving via column-scoped commits (§2.13) on
                            // focus-loss (not per-keystroke, per the decided fix).
                            SetColumn.WEIGHT -> EditableSetCell(
                                initialValue = formatEditableNumber(s.weightKg),
                                keyboardType = KeyboardType.Decimal,
                                placeholder = "kg",
                                onCommit = { text -> onUpdateWeight(s.id, text.toFloatOrNull()) }
                            )
                            SetColumn.REPS -> EditableSetCell(
                                initialValue = s.reps?.toString() ?: "",
                                keyboardType = KeyboardType.Number,
                                placeholder = "reps",
                                onCommit = { text -> onUpdateReps(s.id, text.toIntOrNull()) }
                            )
                            SetColumn.DURATION -> EditableSetCell(
                                initialValue = s.durationSeconds?.toString() ?: "",
                                keyboardType = KeyboardType.Number,
                                placeholder = "sec",
                                onCommit = { text -> onUpdateDuration(s.id, text.toIntOrNull()) }
                            )
                            SetColumn.DISTANCE -> EditableSetCell(
                                // Displayed/edited in km (matches `formatPrevious`'s convention);
                                // stored in metres.
                                initialValue = formatEditableNumber(s.distanceMeters?.let { it / 1000f }),
                                keyboardType = KeyboardType.Decimal,
                                placeholder = "km",
                                onCommit = { text -> onUpdateDistance(s.id, text.toFloatOrNull()?.let { it * 1000f }) }
                            )
                            SetColumn.COMPLETE -> CircleIconButton(
                                icon = Icons.Filled.Check, contentDescription = "Complete set",
                                onClick = { onToggleComplete(s.id) },
                                style = if (s.completedAt != null) CircleStyle.Success else CircleStyle.Ghost,
                                size = 32.dp
                            )
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
    onCommit: (String) -> Unit
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
    // Bug fix — this screen's cells had no bring-into-view request of their own; on a session
    // with several sets, focusing one of the lower rows' KG/REPS/etc. cells left it sitting right
    // under (or fully behind) the keyboard with no scroll to reveal it, since `imePadding()` on
    // the root only reserves space, it doesn't reposition the focused child. Requesting it
    // explicitly — after a short delay so the IME's own resize animation has settled — makes the
    // list scroll the focused cell above the keyboard reliably.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = DaybookColors.TextPrimary, textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(LocalAccent.current),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = Modifier
            .width(52.dp)
            .clip(AppShapes.field)
            .background(DaybookColors.SurfaceElevated)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusEvent { state ->
                if (state.isFocused) {
                    scope.launch {
                        delay(250)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
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
private fun columnLabel(c: SetColumn): String = when (c) {
    SetColumn.SET -> "SET"
    SetColumn.PREVIOUS -> "PREV"
    SetColumn.WEIGHT -> "KG"
    SetColumn.REPS -> "REPS"
    SetColumn.DURATION -> "SEC"
    SetColumn.DISTANCE -> "KM"
    SetColumn.COMPLETE -> "✓"
}

private fun formatPrevious(s: WorkoutSet?): String {
    if (s == null) return "–"
    val w = s.weightKg
    val r = s.reps
    return when {
        w != null && r != null -> "${w}kg × $r"
        r != null -> "$r"
        // §3 fix — a weight-only PREVIOUS (weight logged, reps left blank — reachable from a Hevy
        // import or from filling only the KG cell) used to fall through every branch to "–" even
        // though the data was there.
        w != null -> "${w}kg"
        s.durationSeconds != null -> "${s.durationSeconds}s"
        s.distanceMeters != null -> "${s.distanceMeters / 1000f}km"
        else -> "–"
    }
}

