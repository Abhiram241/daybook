package com.daybook.app.ui.routines

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import com.daybook.app.ui.icons.DaybookIcons
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.data.model.ColorTag
import com.daybook.app.data.model.HabitType
import com.daybook.app.ui.components.*
import com.daybook.app.ui.icons.Icons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.IconButtonSize
import com.daybook.app.ui.theme.LocalAccent
import androidx.compose.animation.core.snap
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoutinesScreen(
    onNavigateToAddHabit: () -> Unit = {},
    onNavigateToEditHabit: (String) -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    // v0.5.3 Phase 4 (§4.8) — the scaffold's PaddingValues contract; was a bare `Dp` clearance.
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val habits by viewModel.habits.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val filterActive by viewModel.filterActive.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val typeCounts by viewModel.typeCounts.collectAsState()
    val rmList = LocalReduceMotion.current

    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Habits",
            subtitle = if (showArchived) "${habits.size} habits" else "${habits.size} active",
            actions = {
                Avatar(
                    photoPath = profile.photoPath,
                    name = profile.name,
                    size = 40.dp,
                    onClick = onNavigateToSettings
                )
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // v0.5.3 Phase 4 (§4.8) — the scaffold PaddingValues (nav + FAB clearance) straight through.
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
        ) {
            if (habits.isEmpty()) {
                item {
                    EmptyState(
                        // v0.5.3 Phase 5 (§5.5) — curated habit glyph in a neutral tile, not Material Add in Mint.
                        icon = DaybookIcons.Task,
                        title = "No habits yet",
                        body = "Add your first habit to get started.",
                        actionLabel = "Add habit",
                        onAction = onNavigateToAddHabit,
                        tint = CardTints.Neutral
                    )
                }
            } else {
                itemsIndexed(habits, key = { _, it -> it.id }) { index, habit ->
                    HabitCard(
                        habit = habit,
                        tint = CardTints.resolve(
                            ColorTag.fromNameOrAuto(habit.colorTag).name.takeIf { it != "AUTO" }, index
                        ),
                        modifier = Modifier.animateItem(fadeInSpec = if (rmList) snap() else Motion.medium(), fadeOutSpec = if (rmList) snap() else Motion.fast()),
                        onOpen = { onNavigateToDetail(habit.id) },
                        onEdit = { onNavigateToEditHabit(habit.id) },
                        onArchiveToggle = {
                            if (habit.isArchived) viewModel.unarchiveHabit(habit.id)
                            else viewModel.archiveHabit(habit.id)
                        },
                        onDelete = { viewModel.deleteHabit(habit.id) },
                        onStartStreakAt = { millis -> viewModel.startStreak(habit.id, millis) },
                        onMarkBroken = { viewModel.markStreakBroken(habit.id) }
                    )
                }
            }
        }
      }

        // Single-hand reach: the filter/sort opener lives down here in the bottom thumb zone
        // (bottom-left, mirroring the Add FAB) instead of the old top-right header corner. Left
        // side keeps it clear of the cards' right-edge 3-dot menus.
        val fabBottom = (contentPadding.calculateBottomPadding() - IconButtonSize.Fab.dp)
            .coerceAtLeast(Spacing.lg)

        HabitFilterButton(
            sort = sort,
            showArchived = showArchived,
            active = filterActive,
            selectedTypes = typeFilter,
            typeCounts = typeCounts,
            onToggleType = viewModel::toggleType,
            onSetSort = viewModel::setSort,
            onToggleArchived = viewModel::toggleArchived,
            onReset = viewModel::resetFilter,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = Spacing.screenH,
                    bottom = fabBottom + (IconButtonSize.Fab.dp - IconButtonSize.Lg.dp) / 2
                )
        )

        CircleIconButton(
            icon = MI.Filled.Add,
            contentDescription = "Add habit",
            onClick = onNavigateToAddHabit,
            style = CircleStyle.Solid,
            size = IconButtonSize.Fab.dp,
            // v0.5.3 Phase 4 (§4.8) — the scaffold list padding already reserves nav + FAB
            // clearance; float the FAB just above the nav (list bottom − FAB height).
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.screenH, bottom = fabBottom)
        )
    }
}

@Composable
private fun HabitFilterButton(
    sort: HabitSort,
    showArchived: Boolean,
    active: Boolean,
    selectedTypes: Set<HabitType>,
    typeCounts: Map<String, Int>,
    onToggleType: (HabitType) -> Unit,
    onSetSort: (HabitSort) -> Unit,
    onToggleArchived: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    Box(modifier) {
        CircleIconButton(
            icon = DaybookIcons.FilterList,
            contentDescription = "Filter and sort",
            onClick = { open = true },
            style = CircleStyle.Tonal,
            size = IconButtonSize.Lg.dp
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }

    SortSheet(
        visible = open,
        onDismiss = { open = false },
        sortOptions = HabitSort.entries.map { SortOption(it.name, it.label) },
        selectedSortKey = sort.name,
        onSelectSort = { key -> onSetSort(HabitSort.valueOf(key)) },
        facetTitle = "Type",
        facetOptions = listOf(
            FacetOption(HabitType.INDIVIDUAL.name, "Individual", typeCounts[HabitType.INDIVIDUAL.name] ?: 0),
            FacetOption(HabitType.BATCH.name, "Batch", typeCounts[HabitType.BATCH.name] ?: 0),
            // v0.5.5: the facet list is hardcoded (typeCounts already covers STREAK). Add by hand.
            FacetOption(HabitType.STREAK.name, "Ongoing", typeCounts[HabitType.STREAK.name] ?: 0)
        ),
        selectedFacetKeys = selectedTypes.map { it.name }.toSet(),
        onToggleFacet = { key -> onToggleType(HabitType.valueOf(key)) },
        showArchived = showArchived,
        onToggleArchived = onToggleArchived,
        archivedRowLabel = "Show archived only",
        onReset = onReset,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitCard(
    habit: RoutineItem,
    tint: com.daybook.app.ui.theme.CardTint,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    onStartStreakAt: (Long) -> Unit,
    onMarkBroken: () -> Unit
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmBroken by remember { mutableStateOf(false) }
    // Task C (C1/Phase 9) — the backdated "Start" date picker, capped at today.
    var showStartPicker by remember { mutableStateOf(false) }
    if (showStartPicker) {
        com.daybook.app.ui.DaybookDatePickerDialog(
            initial = java.time.LocalDate.now(),
            maxDate = java.time.LocalDate.now(),
            onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
                showStartPicker = false
                onStartStreakAt(
                    picked.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            }
        )
    }
    SoftCard(tint = tint, onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = Icons.getIcon(habit.iconKey), tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        habit.title,
                        style = DaybookText.CardTitle,
                        color = tint.onFill,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (habit.isBatch) {
                        Spacer(Modifier.width(6.dp))
                        // v0.5.3 Phase 5 (§5.5) — shared MiniBadge, not a bespoke 18dp circle.
                        MiniBadge(text = "B", tint = tint, contentDescription = "Batch habit")
                    }
                    if (habit.isStreak) {
                        Spacer(Modifier.width(6.dp))
                        MiniBadge(text = "~", tint = tint, contentDescription = "Ongoing habit")
                    }
                }
                habit.description?.let {
                    Text(it, style = DaybookText.CardSubtitle, color = tint.onFillMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when {
                    // v0.5.5 — Ongoing habit, not started: a distinct "Start" control. The whole
                    // card still opens Detail; only this link begins the count.
                    habit.isStreak && habit.streakStartedAt == null -> {
                        // v0.5.5 — Start now begins the streak immediately with today, no
                        // confirmation dialog: the date-picker moved to the 3-dot menu's
                        // "Choose start date" action for anyone who wants to backdate it.
                        //
                        // Accent-updates round (Phase 5): this used to be a `TextLink`, which
                        // bakes in a 44dp min-height + 8dp/6dp padding for its own standalone
                        // tap-target contract. Sitting in the same Column slot as the plain,
                        // unpadded "running" Row below, that made the flame icon visibly jump
                        // ~8dp right and the card jump height the moment a streak starts. This
                        // bespoke Row matches the running row's geometry (6dp top spacer, 16dp
                        // icon, 4dp gap, flush-left, no min-height) while deliberately KEEPING
                        // ButtonLabel's bolder call-to-action text style — only the alignment
                        // mismatch is a bug, not the type style.
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(AppShapes.pill)
                                .clickableImpl(remember { MutableInteractionSource() }) {
                                    onStartStreakAt(
                                        java.time.LocalDate.now()
                                            .atStartOfDay(java.time.ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli()
                                    )
                                }
                        ) {
                            androidx.compose.material3.Icon(
                                DaybookIcons.Flame, contentDescription = null,
                                tint = tint.accent, modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Start", style = DaybookText.ButtonLabel, color = tint.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // Task C (Phase 8) — Ongoing habit, running: the inclusive day count + a muted
                    // best. The "Mark as broken" danger icon used to trail this Row, but that made
                    // it vertically centered against just this inner Row instead of the outer
                    // trailing-icon group (Clock/MoreVert) — visibly misaligned. It now lives in
                    // that outer Row (below), so this Row is just the flame + text.
                    habit.isStreak -> {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                DaybookIcons.Flame, contentDescription = null,
                                tint = tint.accent, modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (habit.streakDays == 1) "1 day" else "${habit.streakDays} days",
                                style = DaybookText.Metadata, color = tint.accent, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (habit.streakLongest > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Best ${habit.streakLongest}",
                                    style = DaybookText.Metadata, color = tint.onFillMuted, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    else -> {
                        if (habit.times.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // v0.5.3 Phase 4 (§4.11) — inert time pills; was DaybookChip(onClick = {}).
                                habit.times.take(3).forEach { t -> TimeTag(t) }
                                if (habit.times.size > 3) {
                                    TimeTag("+${habit.times.size - 3}")
                                }
                            }
                        }
                        habit.nextText?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = DaybookText.Metadata, color = tint.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // v0.5.5 — "Mark as broken" moved here from the inner streak-status Row so it's
            // vertically centered against the whole card (same context as Clock/MoreVert below)
            // instead of just the inner Row — that mismatch was the card's alignment bug.
            // Only shown once a streak is actually running (nothing to mark broken before Start).
            if (habit.isStreak && habit.streakStartedAt != null) {
                CircleIconButton(
                    icon = MI.Filled.Close,
                    contentDescription = "Mark as broken",
                    onClick = { confirmBroken = true },
                    style = CircleStyle.Danger,
                    size = IconButtonSize.Sm.dp
                )
                Spacer(Modifier.width(6.dp))
            }
            // Task C (Phase 8) — an explicit History/Stats affordance for an Ongoing habit,
            // alongside the existing whole-card tap (same destination, `onOpen` — C4).
            if (habit.isStreak) {
                CircleIconButton(icon = DaybookIcons.Clock, contentDescription = "History and stats", onClick = onOpen, size = IconButtonSize.Sm.dp)
                Spacer(Modifier.width(6.dp))
            }
            CircleIconButton(icon = MI.Filled.MoreVert, contentDescription = "More", onClick = { sheetOpen = true }, size = IconButtonSize.Sm.dp)
        }
    }
    BottomSheetMenu(
        visible = sheetOpen,
        onDismiss = { sheetOpen = false },
        actions = buildList {
            add(SheetAction(MI.Filled.Edit, "Edit", onClick = onEdit))
            // v0.5.5 — "Choose start date" is now the ONLY way to reach the backdating date
            // picker (Start on the card itself begins immediately with today). Only relevant
            // before a streak has started — once running there's nothing left to "choose".
            if (habit.isStreak && habit.streakStartedAt == null) {
                add(SheetAction(DaybookIcons.Flame, "Choose start date", onClick = { showStartPicker = true }))
            }
            // Task C (C3) — "Mark as broken" moved fully onto the card itself (above); no longer
            // duplicated in this menu.
            add(
                if (habit.isArchived) SheetAction(DaybookIcons.Unarchive, "Unarchive", onClick = onArchiveToggle)
                else SheetAction(DaybookIcons.Archive, "Archive", onClick = onArchiveToggle)
            )
            add(SheetAction(MI.Filled.Delete, "Delete", destructive = true, onClick = { confirmDelete = true }))
        }
    )
    ConfirmDeleteDialog(
        visible = confirmDelete,
        itemName = habit.title,
        onConfirm = onDelete,
        onDismiss = { confirmDelete = false }
    )
    // v0.5.5 sub-decision (f) — a lightweight DaybookAlertDialog, not ConfirmDeleteDialog.
    if (confirmBroken) {
        val run = habit.streakDays ?: 0
        DaybookAlertDialog(
            onDismissRequest = { confirmBroken = false },
            title = "Mark as broken?",
            text = {
                Text(
                    "This clears the current run of ${if (run == 1) "1 day" else "$run days"}. " +
                        "Your longest streak is kept.",
                    style = DaybookText.CardSubtitle,
                    color = DaybookColors.TextMuted
                )
            },
            confirmLabel = "Mark as broken",
            onConfirm = { confirmBroken = false; onMarkBroken() },
            dismissLabel = "Cancel",
            onDismiss = { confirmBroken = false }
        )
    }
}
