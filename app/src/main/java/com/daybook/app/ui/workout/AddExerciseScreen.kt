package com.daybook.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.R
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.MuscleGroupLabels
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.BottomSheetMenu
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.DaybookChip
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.ScreenHeader
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SheetAction
import com.daybook.app.ui.components.SortOption
import com.daybook.app.ui.components.SortSheet
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.icons.DaybookIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.ui.workout.beast.muscleTint
import kotlinx.coroutines.launch

/** Which mode the one `AddExerciseScreen` composable is in (§3.7.3). */
enum class ExercisePickerMode { PICK, BROWSE }

/**
 * A6a (§3.7.3) — the searchable exercise picker/library. `PICK` (route `"add_exercise"`) is a
 * pure chooser: tapping a row calls [onPick] and the caller decides what that means. `BROWSE`
 * (route `"workout_library"`, one of Beast Mode's three nav destinations) manages the catalog:
 * tapping opens history, the overflow offers Edit/Archive.
 *
 * Deviation from §3.7.3, documented in HEALTH_AND_WORKOUT_PROGRESS.md: this pass ships search +
 * a flat alphabetical list, no muscle/equipment filter sheets and no RepDB thumbnails (icon+tint
 * fallback for every row, custom or builtin) — the licensed artwork pipeline is deferred to a
 * follow-up pass. The picker is otherwise fully functional: search, create, edit, archive, pick.
 */
@Composable
fun AddExerciseScreen(
    mode: ExercisePickerMode,
    contentPadding: PaddingValues,
    onPick: (exerciseIds: List<String>) -> Unit,
    onBack: (() -> Unit)?,
    onNewExercise: () -> Unit,
    onEditExercise: (exerciseId: String) -> Unit,
    onOpenHistory: (exerciseId: String) -> Unit,
    viewModel: AddExerciseViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    var overflowFor by remember { mutableStateOf<CatalogExercise?>(null) }
    var historyFor by remember { mutableStateOf<CatalogExercise?>(null) }
    var showGroupSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScopeCompat()

    // Item 6 (Workout UI fixes plan, LOCKED) — the Cardio/Muscle Groups filter buttons moved off
    // the scrolling list onto a bar docked to the bottom of the screen, so the whole screen is now
    // a Box (list + floating bar), not a plain Column. Item 5 (folded into 6) — the list's own
    // `Modifier.padding(horizontal = Spacing.screenH)` used to DOUBLE UP with `contentPadding`
    // (itself already carrying `start/end = Spacing.screenH` from `DaybookScaffold`), unlike every
    // other screen's list (Habits/Intake pass `contentPadding` alone) — removed, so this screen's
    // horizontal rhythm now matches theirs.
    //
    // Round 2 fix — `contentPadding` is the app-wide `scaffoldPadding`, and `DaybookScaffold` is
    // called with `fabPresent = true` unconditionally (that flag folds in clearance for Habits/
    // Intake's own FAB, but is never turned off per-route). This screen has no FAB of its own, so
    // reusing `contentPadding.calculateBottomPadding()` as-is floated the filter bar ~72dp higher
    // than the real bottom edge, leaving a whole exercise row visible, uncovered, underneath it.
    // Subtracting that known FAB clearance recovers the real nav-only inset (0 in PICK mode, the
    // actual Beast Mode nav bar height in BROWSE mode) so the bar sits right above the true edge.
    val fabClearance = com.daybook.app.ui.theme.IconButtonSize.Fab.dp + 16.dp
    val navOnlyBottomInset = (contentPadding.calculateBottomPadding() - fabClearance).coerceAtLeast(0.dp)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (mode == ExercisePickerMode.PICK) {
                BackHeader(title = "Add exercise", onBack = onBack ?: {}) {
                    CircleIconButton(icon = Icons.Filled.Add, contentDescription = "New exercise", onClick = onNewExercise)
                }
            } else {
                ScreenHeader(
                    title = "Exercises",
                    subtitle = "${catalog.size} exercises",
                    actions = {
                        CircleIconButton(icon = Icons.Filled.Add, contentDescription = "New exercise", onClick = onNewExercise, size = 40.dp)
                    }
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screenH,
                    end = Spacing.screenH,
                    top = contentPadding.calculateTopPadding(),
                    // Clearance for the new floating filter bar, on top of whatever real nav
                    // clearance applies here, so the last row never sits behind it. PICK mode
                    // also docks the multi-select "Add (n)" bar below it — reserve for that too.
                    bottom = navOnlyBottomInset + FilterBarClearance +
                        (if (mode == ExercisePickerMode.PICK) PickAddBarClearance else 0.dp)
                )
            ) {
                item {
                    DaybookTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = null,
                        placeholder = "Search exercise"
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (catalog.isEmpty()) {
                    item {
                        EmptyState(
                            icon = DaybookIcons.Category,
                            title = "No exercises found",
                            body = if (query.isNotBlank()) "Nothing matches \"$query\"." else "Nothing in this group yet.",
                            actionLabel = if (query.isNotBlank()) "Create \"$query\"" else null,
                            onAction = if (query.isNotBlank()) { { onNewExercise() } } else null
                        )
                    }
                } else {
                    item { SectionHeader("All exercises") }
                    items(catalog, key = { it.id }) { ex ->
                        ExerciseRow(
                            exercise = ex,
                            mode = mode,
                            selected = ex.id in selectedIds,
                            showOverflow = mode == ExercisePickerMode.BROWSE,
                            onOverflow = { overflowFor = ex },
                            onClick = {
                                if (mode == ExercisePickerMode.PICK) viewModel.toggleSelected(ex.id) else historyFor = ex
                            },
                            onTrend = { historyFor = ex; onOpenHistory(ex.id) }
                        )
                    }
                }
            }
        }

        // Feature addition (post-A6) — filter the catalog by muscle group + Cardio (the existing
        // A2 `MuscleGroup` taxonomy already has a CARDIO value — no new category system). Item 6
        // (LOCKED): docked to the bottom, similar weight/placement to the main bottom nav but a
        // rounded floating pill card with side margins (not edge-to-edge like the nav strip), so
        // it reads as a 2-button filter bar, not a third nav bar.
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            MuscleGroupFilterBar(
                selected = selectedGroup,
                onSelect = viewModel::setGroup,
                onOpenGroupSheet = { showGroupSheet = true },
                modifier = Modifier
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.sm)
                    // PICK mode docks the "Add (n)" bar right below this one, which already
                    // supplies the real nav-bar inset via its own StickySaveBar padding.
                    .padding(bottom = if (mode == ExercisePickerMode.PICK) 0.dp else navOnlyBottomInset)
            )
            if (mode == ExercisePickerMode.PICK) {
                StickySaveBar {
                    PrimaryButton(
                        text = if (selectedIds.isEmpty()) "Add" else "Add (${selectedIds.size})",
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { onPick(selectedIds.toList()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    val overflowTarget = overflowFor
    BottomSheetMenu(
        visible = overflowTarget != null,
        onDismiss = { overflowFor = null },
        actions = listOfNotNull(
            overflowTarget?.let {
                SheetAction(Icons.Filled.MoreVert, "Edit") { onEditExercise(it.id); overflowFor = null }
            },
            overflowTarget?.let {
                SheetAction(Icons.Filled.MoreVert, "Archive") {
                    scope.launch { viewModel.archive(it.id, true) }
                    overflowFor = null
                }
            }
        )
    )

    val historyTarget = historyFor
    if (historyTarget != null) {
        ExerciseHistorySheet(
            exerciseId = historyTarget.id,
            exerciseName = historyTarget.name,
            trackingMode = historyTarget.trackingMode,
            onDismiss = { historyFor = null }
        )
    }

    // Round 2 — every MuscleGroup except CARDIO (Cardio has its own dedicated button and is
    // deliberately not duplicated here). Same `SortSheet` single-choice list ExerciseFormScreen's
    // "Primary muscle" picker already uses for this exact enum, so this isn't a new UI pattern.
    // Item 6 (LOCKED) — each row now carries a leading thumbnail: the real per-muscle `.webp`
    // asset when [muscleImageRes] has one, else the existing generic icon fallback (CARDIO is
    // excluded above; FULL_BODY/NECK/OTHER are the only remaining groups with no anatomical
    // asset).
    SortSheet(
        visible = showGroupSheet,
        onDismiss = { showGroupSheet = false },
        title = "Muscle group",
        sortOptions = MuscleGroup.entries
            .filter { it != MuscleGroup.CARDIO }
            .map { g ->
                val imageRes = muscleImageRes(g)
                SortOption(
                    key = g.name,
                    label = MuscleGroupLabels[g].orEmpty(),
                    leadingImageRes = imageRes,
                    leadingIconVector = if (imageRes == null) DaybookIcons.Category else null
                )
            },
        selectedSortKey = selectedGroup?.takeIf { it != MuscleGroup.CARDIO }?.name ?: "",
        onSelectSort = { viewModel.setGroup(MuscleGroup.valueOf(it)) },
        dismissOnSelect = true,
        neutralHeader = true
    )
}

/** Item 6 (Workout UI fixes plan, LOCKED) — the confirmed many-to-one mapping from the app's
 *  20-value [MuscleGroup] taxonomy to the user-supplied RepDB muscle illustration library (26
 *  `.webp` files, bundled as drawables under `res/drawable/muscle_*`). `null` for the four groups
 *  with no single anatomical asset (CARDIO — excluded from this sheet entirely — FULL_BODY, NECK,
 *  OTHER), which keep the plain icon fallback instead. Scope check: this mapping is used ONLY by
 *  the Muscle Groups filter sheet this round — exercise thumbnails elsewhere are untouched. */
private fun muscleImageRes(group: MuscleGroup): Int? = when (group) {
    MuscleGroup.ABDOMINALS -> R.drawable.muscle_abdominals
    MuscleGroup.ABDUCTORS -> R.drawable.muscle_abductors
    MuscleGroup.ADDUCTORS -> R.drawable.muscle_adductors
    MuscleGroup.BICEPS -> R.drawable.muscle_biceps
    MuscleGroup.CALVES -> R.drawable.muscle_calves
    MuscleGroup.CHEST -> R.drawable.muscle_chest
    MuscleGroup.FOREARMS -> R.drawable.muscle_forearms
    MuscleGroup.GLUTES -> R.drawable.muscle_glutes
    MuscleGroup.HAMSTRINGS -> R.drawable.muscle_hamstrings
    MuscleGroup.LATS -> R.drawable.muscle_lats
    MuscleGroup.LOWER_BACK -> R.drawable.muscle_lower_back
    MuscleGroup.QUADRICEPS -> R.drawable.muscle_quadriceps
    MuscleGroup.SHOULDERS -> R.drawable.muscle_shoulders
    MuscleGroup.TRAPS -> R.drawable.muscle_traps
    MuscleGroup.TRICEPS -> R.drawable.muscle_triceps
    MuscleGroup.UPPER_BACK -> R.drawable.muscle_upper_back
    MuscleGroup.CARDIO, MuscleGroup.FULL_BODY, MuscleGroup.NECK, MuscleGroup.OTHER -> null
}

/** Approximate rendered height of [MuscleGroupFilterBar] (chip row + its own vertical padding)
 *  plus a small margin, so the exercise list's last row never sits behind the floating bar. */
private val FilterBarClearance = 64.dp

/** Approximate rendered height of the PICK-mode "Add (n)" [StickySaveBar] docked below the
 *  filter bar, so the exercise list's last row never sits behind either. */
private val PickAddBarClearance = 96.dp

@Composable
private fun ExerciseRow(
    exercise: CatalogExercise,
    mode: ExercisePickerMode,
    selected: Boolean,
    showOverflow: Boolean,
    onOverflow: () -> Unit,
    onClick: () -> Unit,
    onTrend: () -> Unit
) {
    val tint = muscleTint(exercise.primaryMuscle)
    com.daybook.app.ui.components.SoftCard(
        tint = tint,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (mode == ExercisePickerMode.PICK) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = LocalAccent.current)
                )
                Spacer(Modifier.width(4.dp))
            }
            ExerciseThumbnail(
                imageId = exercise.imageId,
                hasStartPeak = exercise.hasStartPeak,
                fallbackIcon = DaybookIcons.Category,
                tint = tint
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1)
                val subtitle = MuscleGroupLabels[exercise.primaryMuscle].orEmpty() +
                    if (exercise.source == "IMPORTED_HEVY") " · Imported" else ""
                Text(subtitle, style = DaybookText.Caption, color = tint.onFillMuted)
            }
            if (showOverflow) {
                CircleIconButton(icon = Icons.Filled.MoreVert, contentDescription = "More", onClick = onOverflow)
            } else {
                CircleIconButton(icon = DaybookIcons.Clock, contentDescription = "History", onClick = onTrend)
            }
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

/**
 * Item 6 (Workout UI fixes plan, LOCKED) — Round 2's inline row moved into a small bar docked to
 * the bottom of the screen: a "Cardio" button (the only group with its own direct toggle — A2's
 * taxonomy already models Cardio as a `MuscleGroup` value, no separate category system, and it
 * stays a direct on/off toggle with no menu) plus a "Muscle Groups" button that opens the existing
 * half-page picker sheet for the other 19. Both write to the same `selectedGroup` state, so
 * picking one already clears the other — no extra mutual-exclusion code needed.
 *
 * Tapping the currently-active button again clears back to "All" (`onSelect(null)`) instead of
 * re-opening the sheet — that's the only way to get back to "All" from this row, since there's no
 * separate "All" chip anymore.
 *
 * Deliberately a rounded floating pill card (shadow + `AppShapes.pill` + side margins from the
 * caller) rather than an edge-to-edge strip like `FloatingPillNav` — "similar weight/placement to
 * the main bottom nav, but should not look like a 3rd nav bar" (item 6).
 */
@Composable
private fun MuscleGroupFilterBar(
    selected: MuscleGroup?,
    onSelect: (MuscleGroup?) -> Unit,
    onOpenGroupSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedNonCardioGroup = selected?.takeIf { it != MuscleGroup.CARDIO }
    val cardioActive = selected == MuscleGroup.CARDIO
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, AppShapes.pill, clip = false, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
            .clip(AppShapes.pill)
            .background(DaybookColors.Surface)
            .border(1.dp, DaybookColors.Hairline, AppShapes.pill)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(com.daybook.app.ui.theme.Spacing.chipGap)
    ) {
        DaybookChip(
            label = "Cardio",
            selected = cardioActive,
            onClick = { onSelect(if (cardioActive) null else MuscleGroup.CARDIO) },
            modifier = Modifier.weight(1f)
        )
        DaybookChip(
            label = selectedNonCardioGroup?.let { "${MuscleGroupLabels[it].orEmpty()} ▾" } ?: "Muscle Groups ▾",
            selected = selectedNonCardioGroup != null,
            onClick = { if (selectedNonCardioGroup != null) onSelect(null) else onOpenGroupSheet() },
            modifier = Modifier.weight(1f)
        )
    }
}
