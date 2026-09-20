package com.daybook.app.ui.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.daybook.app.R
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.local.SessionAggregate
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSessionPhoto
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
import com.daybook.app.ui.components.SortOption
import com.daybook.app.ui.components.SortSheet
import com.daybook.app.ui.components.UndoSnack
import com.daybook.app.ui.components.clickableImpl
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing
import java.io.File
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
    onOpenWorkoutSettings: () -> Unit,
    viewModel: WorkoutHistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val availableExercises by viewModel.availableExercises.collectAsStateWithLifecycle()
    val selectedExercise by viewModel.selectedExercise.collectAsStateWithLifecycle()
    val selectedMuscleGroupFilter by viewModel.selectedMuscleGroupFilter.collectAsStateWithLifecycle()
    val groupingMode by viewModel.groupingMode.collectAsStateWithLifecycle()
    val deletedToken by viewModel.deletedToken.collectAsStateWithLifecycle()
    val actionToken by viewModel.actionToken.collectAsStateWithLifecycle()
    val actionText by viewModel.actionText.collectAsStateWithLifecycle()
    val newSessionId by viewModel.newSessionId.collectAsStateWithLifecycle()

    var overflowFor by remember { mutableStateOf<WorkoutSession?>(null) }
    var renaming by remember { mutableStateOf<WorkoutSession?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMuscleGroupSheet by remember { mutableStateOf(false) }
    val workoutIcon: ImageVector = ImageVector.vectorResource(id = R.drawable.ic_workout)

    LaunchedEffect(newSessionId) {
        newSessionId?.let { onOpenSession(it); viewModel.clearNewSessionId() }
    }

    Box(Modifier.fillMaxSize().background(BeastPalette.groundBrush())) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "History",
                subtitle = "${sessions.size} workouts",
                actions = {
                    // UI fix (AUDIT_2026-09-20.md §3) — History had no settings entry point at all.
                    // Same Tonal/Lg style as the Health tab's gear (added alongside its existing
                    // filter button) so the two Beast Mode header buttons match.
                    CircleIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Beast Mode settings",
                        onClick = onOpenWorkoutSettings,
                        style = com.daybook.app.ui.components.CircleStyle.Tonal,
                        size = com.daybook.app.ui.theme.IconButtonSize.Lg.dp
                    )
                }
            )
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
                // Item 13 (§13b) — pinned under ScreenHeader, OUTSIDE the LazyColumn: this used to
                // be a single scroll-away chip (`ExerciseFilterRow`, an `item{}` inside the list)
                // — a persistent bar is what makes "filter, then scroll" actually usable instead
                // of the filter control disappearing the moment you scroll past it.
                HistoryFilterBar(
                    selectedMuscleGroup = selectedMuscleGroupFilter,
                    onOpenMuscleGroupSheet = { showMuscleGroupSheet = true },
                    onClearMuscleGroup = { viewModel.selectMuscleGroupFilter(null) },
                    selectedExercise = selectedExercise,
                    onOpenExerciseSheet = { showFilterSheet = true },
                    onClearExercise = { viewModel.selectExerciseFilter(null) },
                    groupingMode = groupingMode,
                    onSetGroupingMode = { viewModel.setGroupingMode(it) }
                )
                if (sections.isEmpty()) {
                    EmptyState(
                        icon = DaybookIcons.Category,
                        title = "No matching workouts",
                        body = when {
                            selectedExercise != null && selectedMuscleGroupFilter != null ->
                                "No session with \"${selectedExercise?.name}\" in that muscle group yet."
                            selectedExercise != null -> "No session with \"${selectedExercise?.name}\" yet."
                            selectedMuscleGroupFilter != null -> "No session in that muscle group yet."
                            else -> "Nothing to show."
                        }
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                        sections.forEach { (label, rows) ->
                            item { SectionHeader(label) }
                            items(rows, key = { it.session.id }) { row ->
                                SessionRow(
                                    row = row,
                                    weightUnit = weightUnit,
                                    // Item 2/6 (LOCKED) — Bolt for a no-routine/instant session's
                                    // row, the usual dumbbell for a routine-started one.
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

    // Item 13 (§13b) — "the same muscleImageRes leading thumbnails the picker's sheet already
    // renders" (AddExerciseScreen's own "Muscle group" SortSheet). Unlike that sheet, CARDIO is
    // NOT excluded here — there's no separate Cardio chip in this 2-chip bar, so it must be
    // reachable through this one picker like every other group.
    SortSheet(
        visible = showMuscleGroupSheet,
        onDismiss = { showMuscleGroupSheet = false },
        title = "Muscle",
        sortOptions = MuscleGroup.entries.map { g ->
            val imageRes = muscleImageRes(g)
            SortOption(
                key = g.name,
                label = MuscleGroupLabels[g].orEmpty(),
                leadingImageRes = imageRes,
                leadingIconVector = if (imageRes == null) DaybookIcons.Category else null
            )
        },
        selectedSortKey = selectedMuscleGroupFilter?.name ?: "",
        onSelectSort = { viewModel.selectMuscleGroupFilter(MuscleGroup.valueOf(it)) },
        dismissOnSelect = true,
        neutralHeader = true
    )
}

/**
 * Item 13 (§13a/§13b) — replaces the old scroll-away `ExerciseFilterRow` with a persistent bar:
 * "Muscle Group ▾" / "Exercise ▾" (the two filters intersect when both are set — see
 * `WorkoutHistoryViewModel.sections`), plus a third "Date ▾ / Muscle ▾" grouping-mode chip (open
 * question 8's own recommendation). Reuses `DaybookChip` — the same component the picker's own
 * filter bar (`AddExerciseScreen.PickerDockedBar`) is built from, per the plan's explicit "reuse
 * the exact two-chip pattern" instruction — rather than that bar's Cardio-specific split, which
 * doesn't apply here. Tapping an already-active chip clears it, identical semantics to before.
 */
@Composable
private fun HistoryFilterBar(
    selectedMuscleGroup: MuscleGroup?,
    onOpenMuscleGroupSheet: () -> Unit,
    onClearMuscleGroup: () -> Unit,
    selectedExercise: CatalogExercise?,
    onOpenExerciseSheet: () -> Unit,
    onClearExercise: () -> Unit,
    groupingMode: HistoryGroupingMode,
    onSetGroupingMode: (HistoryGroupingMode) -> Unit
) {
    // Owner feedback (2026-09-20) — all three chips must sit on one row, not two. `DaybookChip`
    // already sizes to its own content (its inner `Text` is `maxLines = 1` +
    // `TextOverflow.Ellipsis` inside a `weight(1f, fill = false)`), so dropping the old
    // `Modifier.weight(1f)` constraint and putting all three in one horizontally-scrollable Row —
    // instead of splitting the grouping chip onto a second Row — keeps every label fully legible
    // (ellipsis only kicks in for a pathologically long exercise name) while guaranteeing the row
    // never wraps, on any screen width.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)
    ) {
        DaybookChip(
            label = selectedMuscleGroup?.let { "${MuscleGroupLabels[it].orEmpty()} ▾" } ?: "Muscle ▾",
            selected = selectedMuscleGroup != null,
            onClick = { if (selectedMuscleGroup != null) onClearMuscleGroup() else onOpenMuscleGroupSheet() },
            modifier = Modifier.weight(1f)
        )
        DaybookChip(
            label = selectedExercise?.let { "${it.name} ▾" } ?: "Exercise ▾",
            selected = selectedExercise != null,
            onClick = { if (selectedExercise != null) onClearExercise() else onOpenExerciseSheet() },
            modifier = Modifier.weight(1f)
        )
        DaybookChip(
            label = if (groupingMode == HistoryGroupingMode.DATE) "Date ▾" else "Muscle ▾",
            selected = false,
            onClick = {
                onSetGroupingMode(
                    if (groupingMode == HistoryGroupingMode.DATE) HistoryGroupingMode.MUSCLE else HistoryGroupingMode.DATE
                )
            },
            modifier = Modifier.weight(1f)
        )
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
                        // Item 13 (§13a) fix — this used to be a raw `Modifier.clickable`, the one
                        // spot in this screen that skipped the app's no-ripple press wrapper every
                        // other tappable row already uses.
                        val interaction = remember { MutableInteractionSource() }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickableImpl(interaction) { onPick(ex) }
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
                size = 52.dp,
                // Item 3 — a session whose chosen exercise has no RepDB photo (item 11 already
                // preferred one that does, when the session had any) still gets equipment or
                // muscle art rather than the generic icon.
                primaryMuscle = row.thumbnail?.primaryMuscle ?: MuscleGroup.OTHER,
                equipment = row.thumbnail?.equipment ?: com.daybook.app.data.workout.Equipment.OTHER
            )
            Spacer(Modifier.width(Spacing.md))
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
                                tint = tint,
                                maxBars = if (row.photos.isNotEmpty()) 3 else 4
                            )
                        }
                        // Item 12 — below the muscle-group chart, i.e. below everything else in
                        // the text column, per the spec ("below the workout image" — the image is
                        // the 52dp thumbnail top-left; everything in this column is already below
                        // it). A session with no photos is byte-identical to before this item.
                        if (row.photos.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            SessionPhotoStrip(photos = row.photos)
                        }
                    }
                }
            }
            Spacer(Modifier.width(Spacing.xs))
            CircleIconButton(icon = Icons.Filled.MoreVert, contentDescription = "More", onClick = onOverflow)
        }
    }
}

/** Item 12 — up to 3 tiles, tap one for a full-screen pager viewer. Which photo (if any) is being
 *  viewed full-screen is local state here, never needed by the caller. Fix 3 (owner report,
 *  2026-09-20) — de-privatized and renamed from `HistoryPhotoStrip` so `WorkoutDetailScreen` can
 *  reuse it verbatim instead of duplicating it. */
@Composable
fun SessionPhotoStrip(photos: List<WorkoutSessionPhoto>) {
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
        photos.forEachIndexed { index, photo ->
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(AppShapes.tile)
                    .background(DaybookColors.SurfaceElevated)
                    .clickableImpl(interaction) { viewerStartIndex = index }
            ) {
                AsyncImage(
                    model = remember(photo.filePath) {
                        ImageRequest.Builder(context)
                            .data(File(photo.filePath))
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .memoryCacheKey(photo.filePath)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = "Workout photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    val startIndex = viewerStartIndex
    if (startIndex != null) {
        PhotoViewerDialog(photos = photos, startIndex = startIndex, onDismiss = { viewerStartIndex = null })
    }
}

/** Item 12 — a plain full-screen dialog, horizontal pager across the session's photos, tap to
 *  dismiss. No zoom library, matching the plan's explicit scope. */
@Composable
fun PhotoViewerDialog(
    photos: List<WorkoutSessionPhoto>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { photos.size })
        Box(Modifier.fillMaxSize().background(BeastPalette.ground())) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxSize().clickableImpl(interaction, onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = remember(photos[page].filePath) {
                            ImageRequest.Builder(context)
                                .data(File(photos[page].filePath))
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .memoryCacheKey(photos[page].filePath)
                                .build()
                        },
                        contentDescription = "Workout photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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
fun MuscleVolumeChart(
    breakdown: List<Pair<MuscleGroup, Float>>,
    weightUnit: WeightUnit,
    tint: com.daybook.app.ui.theme.CardTint,
    // Item 13 (§13c) — the plan's own deliberate card-height guard: 4 bars normally, capped to 3
    // when the card also has a photo strip below it, so the two additions together don't grow the
    // card unboundedly. A session with no photos is byte-identical to before this item.
    maxBars: Int = 4,
    modifier: Modifier = Modifier
) {
    val top = remember(breakdown, maxBars) { breakdown.take(maxBars) }
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

