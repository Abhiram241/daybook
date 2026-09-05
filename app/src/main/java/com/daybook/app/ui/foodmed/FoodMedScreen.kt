package com.daybook.app.ui.foodmed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Add
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
import com.daybook.app.data.model.TaskType
import com.daybook.app.ui.components.*
import com.daybook.app.ui.icons.Icons
import com.daybook.app.ui.theme.CardTint
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
fun FoodMedScreen(
    onNavigateToAddFoodMed: () -> Unit = {},
    onNavigateToEditFoodMed: (String) -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    // v0.5.3 Phase 4 (§4.8) — the scaffold PaddingValues contract.
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: FoodMedViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val counts by viewModel.typeCounts.collectAsState()
    val selectedTypes by viewModel.typeFilter.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val filterActive by viewModel.filterActive.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val rmList = LocalReduceMotion.current

    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Intake",
            subtitle = if (items.size == 1) "1 reminder" else "${items.size} reminders",
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
            contentPadding = contentPadding, // v0.5.3 Phase 4 (§4.8)
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = MI.Filled.Add,
                        title = "No reminders yet",
                        body = "Add your first intake reminder.",
                        actionLabel = "Add reminder",
                        onAction = onNavigateToAddFoodMed
                    )
                }
            } else {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    FoodMedCard(
                        item = item,
                        tint = CardTints.resolve(
                            ColorTag.fromNameOrAuto(item.colorTag).name.takeIf { it != "AUTO" }, index
                        ),
                        modifier = Modifier.animateItem(fadeInSpec = if (rmList) snap() else Motion.medium(), fadeOutSpec = if (rmList) snap() else Motion.fast()),
                        onOpenDetail = { onNavigateToDetail(item.id) },
                        onEdit = { onNavigateToEditFoodMed(item.id) },
                        onArchiveToggle = {
                            if (item.isArchived) viewModel.unarchiveItem(item.id) else viewModel.archiveItem(item.id)
                        },
                        onDelete = { viewModel.deleteItem(item.id) }
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

        IntakeFilterButton(
            counts = counts,
            selectedTypes = selectedTypes,
            sort = sort,
            showArchived = showArchived,
            active = filterActive,
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
            contentDescription = "Add reminder",
            onClick = onNavigateToAddFoodMed,
            style = CircleStyle.Solid,
            size = IconButtonSize.Fab.dp,
            // v0.5.3 Phase 4 (§4.8) — float the FAB just above the nav (list bottom − FAB height).
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.screenH, bottom = fabBottom)
        )
    }
}

// v0.5.3 Phase 5 (§5.6) — JOURNAL is intentionally a filterable Intake type: a journal prompt is
// an intake reminder that asks for a note rather than a log, so it belongs in this facet list.
private val FILTER_TYPES = listOf(
    TaskType.FOOD to "Food",
    TaskType.MED to "Med",
    TaskType.CUSTOM to "Custom",
    TaskType.JOURNAL to "Journal"
)

@Composable
private fun IntakeFilterButton(
    counts: Map<String, Int>,
    selectedTypes: Set<TaskType>,
    sort: IntakeSort,
    showArchived: Boolean,
    active: Boolean,
    onToggleType: (TaskType) -> Unit,
    onSetSort: (IntakeSort) -> Unit,
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
        sortOptions = IntakeSort.entries.map { SortOption(it.name, it.label) },
        selectedSortKey = sort.name,
        onSelectSort = { key -> onSetSort(IntakeSort.valueOf(key)) },
        facetTitle = "Type",
        facetOptions = FILTER_TYPES.map { (type, key) -> FacetOption(type.name, key, counts[key] ?: 0) },
        selectedFacetKeys = selectedTypes.map { it.name }.toSet(),
        onToggleFacet = { key -> onToggleType(TaskType.valueOf(key)) },
        showArchived = showArchived,
        onToggleArchived = onToggleArchived,
        // v0.5.3 Phase 5 (§5.6) — unified with Habits: "Show archived only" (accurate: the toggle
        // shows *only* archived).
        archivedRowLabel = "Show archived only",
        onReset = onReset,
    )
}

@Composable
private fun FoodMedCard(
    item: FoodMedItem,
    tint: CardTint,
    modifier: Modifier = Modifier,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    SoftCard(tint = tint, onClick = onOpenDetail, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = Icons.getIcon(item.iconKey), tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.label, style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // v0.5.3 Phase 5 (§5.6) — subtitle string is formatted in the ViewModel now.
                Text(
                    item.subtitle,
                    style = DaybookText.CardSubtitle,
                    color = tint.onFillMuted
                )
                if (item.times.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // v0.5.3 Phase 4 (§4.11) — inert time pills; was DaybookChip(onClick = {}).
                        item.times.take(3).forEach { t -> TimeTag(t) }
                        if (item.times.size > 3) {
                            TimeTag("+${item.times.size - 3}")
                        }
                    }
                }
                item.nextText?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = DaybookText.Metadata, color = tint.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(icon = MI.Filled.MoreVert, contentDescription = "More", onClick = { sheetOpen = true }, size = IconButtonSize.Sm.dp)
        }
    }
    BottomSheetMenu(
        visible = sheetOpen,
        onDismiss = { sheetOpen = false },
        actions = listOf(
            SheetAction(MI.Filled.Edit, "Edit", onClick = onEdit),
            if (item.isArchived) SheetAction(DaybookIcons.Unarchive, "Unarchive", onClick = onArchiveToggle)
            else SheetAction(DaybookIcons.Archive, "Archive", onClick = onArchiveToggle),
            SheetAction(MI.Filled.Delete, "Delete", destructive = true, onClick = { confirmDelete = true })
        )
    )
    ConfirmDeleteDialog(
        visible = confirmDelete,
        itemName = item.label,
        onConfirm = onDelete,
        onDismiss = { confirmDelete = false }
    )
}
