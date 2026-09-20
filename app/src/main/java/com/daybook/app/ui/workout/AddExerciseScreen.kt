package com.daybook.app.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.MuscleGroupLabels
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.DaybookChip
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SortOption
import com.daybook.app.ui.components.SortSheet
import com.daybook.app.ui.components.UndoSnack
import com.daybook.app.ui.icons.DaybookIcons
import androidx.compose.material3.Text
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.ui.workout.beast.muscleTint

/**
 * A6a (§3.7.3) — the searchable exercise picker (route `"add_exercise"`): a pure chooser, where
 * ticking rows and tapping "Add (n)" calls [onPick] and the caller decides what that means. Opened
 * from the live session's and the routine editor's "+ Add Exercise".
 *
 * §0.1 (Beast Mode round 2026-09-16) — this screen used to be two screens in one, switched by an
 * `ExercisePickerMode` parameter: `PICK` (the above) and `BROWSE`, a catalog manager that was one
 * of Beast Mode's bottom-nav destinations. Round B (§7.4) repurposed that nav slot to the Health
 * tab (`MainActivity`'s `WorkoutRoutes.LIBRARY` branch), which left `BROWSE` with zero call sites —
 * so its header, its row-tap-opens-history behaviour and its Edit/Archive overflow were all
 * unreachable dead code, and are now deleted. The catalog-management actions they used to host
 * (edit an exercise's details, archive it) get a proper home in this same picker in a following
 * round (items 1/2), as a long-press bottom-sheet on a row; until then there is deliberately no
 * in-app entry point to `WorkoutRoutes.EDIT_EXERCISE` (the route itself, `ExerciseFormScreen` and
 * `AddExerciseViewModel.archive` all stay, precisely so that round can re-home them).
 */
@Composable
fun AddExerciseScreen(
    contentPadding: PaddingValues,
    onPick: (exerciseIds: List<String>) -> Unit,
    onBack: () -> Unit,
    onNewExercise: () -> Unit,
    viewModel: AddExerciseViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val errorToken by viewModel.errorToken.collectAsStateWithLifecycle()
    val recentExercises by viewModel.recentExercises.collectAsStateWithLifecycle()
    val frequentlyLogged by viewModel.frequentlyLogged.collectAsStateWithLifecycle()
    var historyFor by remember { mutableStateOf<CatalogExercise?>(null) }
    var showGroupSheet by remember { mutableStateOf(false) }

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
    // Subtracting that known FAB clearance recovers the real nav-only inset (0 on this route, the
    // real nav bar height if this screen is ever hosted under one again) so the bar sits right
    // above the true edge. DO NOT re-derive this arithmetic — it is the fix for a bug that took
    // two rounds to find.
    val fabClearance = com.daybook.app.ui.theme.IconButtonSize.Fab.dp + 16.dp
    val navOnlyBottomInset = (contentPadding.calculateBottomPadding() - fabClearance).coerceAtLeast(0.dp)

    // Item 9 — the list's bottom clearance is now MEASURED off the real docked bar rather than
    // hand-tuned constants (the previous `FilterBarClearance + PickAddBarClearance` pair, whose
    // magic numbers were themselves the cause of the last two rounds of inset bugs). The constant
    // below is only the first-frame estimate, before the bar has been laid out once; it also means
    // the "Add (n)" row animating in and out is accounted for for free.
    val density = LocalDensity.current
    var dockedBarHeight by remember { mutableStateOf(PickerDockedBarClearance) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackHeader(title = "Add exercise", onBack = onBack) {
                CircleIconButton(icon = Icons.Filled.Add, contentDescription = "New exercise", onClick = onNewExercise)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screenH,
                    end = Spacing.screenH,
                    top = contentPadding.calculateTopPadding(),
                    // Clearance for the floating docked bar (filter chips + "Add (n)"), measured
                    // from the bar itself, so the last row never sits behind it.
                    bottom = dockedBarHeight + Spacing.sm
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
                    // §2.9 — the picker's KDoc has always described a "Recent"/"Frequently logged"
                    // default view, but both `WorkoutDao.recentExerciseIds` and
                    // `WorkoutRepository.frequentlyLoggedExercises` had zero call sites; a flat
                    // alphabetical list showed regardless of state. Shown only when idle (no search,
                    // no group filter) — search/filter results should stay exactly what they match.
                    // Item 6 fix #7 — `contentType` lets Compose reuse a recycled row/header
                    // composition across the different `items(...)` calls below instead of only
                    // within each one; a minor win, but free once the keys are already unique.
                    val idle = query.isBlank() && selectedGroup == null
                    if (idle && recentExercises.isNotEmpty()) {
                        item(contentType = "header") { SectionHeader("Recent") }
                        items(recentExercises, key = { "recent_${it.id}" }, contentType = { "row" }) { ex ->
                            ExerciseRow(
                                exercise = ex, selected = ex.id in selectedIds,
                                onClick = { viewModel.toggleSelected(ex.id) },
                                onTrend = { historyFor = ex }
                            )
                        }
                    }
                    if (idle && frequentlyLogged.isNotEmpty()) {
                        item(contentType = "header") { SectionHeader("Frequently logged") }
                        items(frequentlyLogged, key = { "frequent_${it.id}" }, contentType = { "row" }) { ex ->
                            ExerciseRow(
                                exercise = ex, selected = ex.id in selectedIds,
                                onClick = { viewModel.toggleSelected(ex.id) },
                                onTrend = { historyFor = ex }
                            )
                        }
                    }
                    item(contentType = "header") { SectionHeader("All exercises") }
                    items(catalog, key = { it.id }, contentType = { "row" }) { ex ->
                        ExerciseRow(
                            exercise = ex,
                            selected = ex.id in selectedIds,
                            onClick = { viewModel.toggleSelected(ex.id) },
                            onTrend = { historyFor = ex }
                        )
                    }
                }
            }
        }

        // Feature addition (post-A6) — filter the catalog by muscle group + Cardio (the existing
        // A2 `MuscleGroup` taxonomy already has a CARDIO value — no new category system). Item 6
        // (LOCKED): docked to the bottom, similar weight/placement to the main bottom nav but a
        // rounded floating card with side margins (not edge-to-edge like the nav strip), so it
        // reads as a filter bar, not a third nav bar.
        //
        // Item 9 — the filter bar and the "Add (n)" `StickySaveBar` used to be two separate
        // hovering slabs stacked in this Column, each with its own padding, shadow and inset
        // budget, with dead space between them. They are now ONE bar ([PickerDockedBar]). The two
        // things `StickySaveBar` used to contribute are kept here explicitly, because they are
        // load-bearing and not obvious:
        //  - `imePadding()`, so the bar rides above the keyboard raised by the search field (this
        //    app is edge-to-edge, so the window does NOT resize itself);
        //  - `navigationBarsPadding()`, which is what actually lifts the bar off the gesture bar —
        //    `navOnlyBottomInset` is 0 on this route (see its comment above) and covers the
        //    different case of this screen being hosted under an app nav bar.
        // The gradient scrim is also `StickySaveBar`'s, kept so list rows fade out behind the bar
        // instead of butting into it.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                // Measured OUTSIDE `imePadding` on purpose: the list's reserved bottom space must
                // not jump by a whole keyboard height while the user is typing a search.
                .onSizeChanged { dockedBarHeight = with(density) { it.height.toDp() } }
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to DaybookColors.Bg))
                .navigationBarsPadding()
                .padding(bottom = navOnlyBottomInset)
                .padding(start = Spacing.screenH, end = Spacing.screenH, top = 20.dp, bottom = Spacing.sm)
        ) {
            PickerDockedBar(
                selected = selectedGroup,
                onSelect = viewModel::setGroup,
                onOpenGroupSheet = { showGroupSheet = true },
                selectedCount = selectedIds.size,
                onAdd = { onPick(selectedIds.toList()) }
            )
        }
        UndoSnack(token = errorToken, text = errorMessage ?: "")
    }

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
    // asset). Item 3 moved `muscleImageRes` itself next to `ExerciseThumbnail`, which now shares
    // the same mapping for its own fallback chain.
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

/** Item 9 — first-frame estimate of the merged docked bar's rendered height (12dp top padding +
 *  ~40dp chip row + ~12dp bottom padding + the 20dp scrim lead-in and outer margin), used only
 *  until [onSizeChanged] reports the real one. Replaces the old `FilterBarClearance` +
 *  `PickAddBarClearance` pair — one bar, one number, and it is no longer load-bearing. */
private val PickerDockedBarClearance: Dp = 96.dp

/** Item 3 — the picker's rows are where the new equipment/muscle fallback art actually has to be
 *  identifiable, and a photographic render of a barbell at 44dp reads as a coloured blob. 56dp is
 *  the smallest size at which the equipment pictures are recognisable at a glance (open question
 *  4); it costs roughly one visible row per screen. */
private val PickerThumbnailSize: Dp = 56.dp

@Composable
private fun ExerciseRow(
    exercise: CatalogExercise,
    selected: Boolean,
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
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = LocalAccent.current)
            )
            Spacer(Modifier.width(4.dp))
            ExerciseThumbnail(
                imageId = exercise.imageId,
                hasStartPeak = exercise.hasStartPeak,
                fallbackIcon = DaybookIcons.Category,
                tint = tint,
                size = PickerThumbnailSize,
                // Item 3 — a fully-described exercise with no RepDB photo now falls back to its
                // equipment (then muscle) illustration instead of the same grey icon as every
                // other custom/imported row.
                primaryMuscle = exercise.primaryMuscle,
                equipment = exercise.equipment
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1)
                val subtitle = MuscleGroupLabels[exercise.primaryMuscle].orEmpty() +
                    if (exercise.source == "IMPORTED_HEVY") " · Imported" else ""
                Text(subtitle, style = DaybookText.Caption, color = tint.onFillMuted)
            }
            CircleIconButton(icon = DaybookIcons.Clock, contentDescription = "History", onClick = onTrend)
        }
    }
}

/**
 * Item 9 (Beast Mode round 2026-09-16) — the one docked bar at the bottom of the picker: the
 * filter chips on top, the "Add (n)" button underneath, inside a single surface with one shadow,
 * one border, one clip and one inset budget. It replaces the old `MuscleGroupFilterBar` +
 * `StickySaveBar` pair, which read as two hovering slabs with dead space between them.
 *
 * Item 6 (Workout UI fixes plan, LOCKED), preserved verbatim from that bar: a "Cardio" button (the
 * only group with its own direct toggle — A2's taxonomy already models Cardio as a `MuscleGroup`
 * value, no separate category system) plus a "Muscle Groups" button that opens the existing
 * half-page picker sheet for the other 19. Both write to the same `selectedGroup` state, so
 * picking one already clears the other — no extra mutual-exclusion code needed. Tapping the
 * currently-active button again clears back to "All" (`onSelect(null)`) instead of re-opening the
 * sheet — that's the only way to get back to "All" from this row, since there's no separate "All"
 * chip anymore.
 *
 * Deliberately still a rounded floating card (shadow + side margins from the caller) rather than
 * an edge-to-edge strip like `FloatingPillNav` — "similar weight/placement to the main bottom nav,
 * but should not look like a 3rd nav bar" (item 6). `AppShapes.card` rather than the old
 * `AppShapes.pill`, because a pill radius on a two-row surface reads as a lozenge.
 *
 * The Add row is hidden entirely until something is ticked (open question 7), animating with the
 * app's own `Motion.softSpring`. That is the one moment this bar is allowed to change height: the
 * user just caused it by ticking a checkbox, so it reads as a response rather than as reflow — and
 * it removes a permanently-disabled button from an otherwise idle screen.
 */
@Composable
private fun PickerDockedBar(
    selected: MuscleGroup?,
    onSelect: (MuscleGroup?) -> Unit,
    onOpenGroupSheet: () -> Unit,
    selectedCount: Int,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedNonCardioGroup = selected?.takeIf { it != MuscleGroup.CARDIO }
    val cardioActive = selected == MuscleGroup.CARDIO
    val shape = AppShapes.card
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, clip = false, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
            .clip(shape)
            .background(DaybookColors.Surface)
            .border(1.dp, DaybookColors.Hairline, shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)
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
        AnimatedVisibility(
            visible = selectedCount > 0,
            enter = expandVertically(Motion.softSpring()) + fadeIn(Motion.fast()),
            exit = shrinkVertically(Motion.softSpring()) + fadeOut(Motion.fast())
        ) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(Spacing.sm))
                // The divider is what makes this read as one bar with two rows rather than a
                // button that happens to sit under some chips.
                Box(Modifier.fillMaxWidth().height(1.dp).background(DaybookColors.Hairline))
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton(
                    text = "Add ($selectedCount)",
                    // Still gated, so the button cannot be tapped during the exit animation after
                    // the last checkbox is cleared.
                    enabled = selectedCount > 0,
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
