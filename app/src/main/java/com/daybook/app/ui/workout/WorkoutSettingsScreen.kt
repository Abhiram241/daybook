package com.daybook.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
// `Icons.AutoMirrored.Filled.ExitToApp` needs this extension import in scope even when
// referenced fully-qualified at the call site — Kotlin resolves extension properties by import,
// not just by qualifier.
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.R
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SegmentSpec
import com.daybook.app.ui.components.SegmentedControl
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.SettingsRow
import com.daybook.app.ui.components.SettingsRowDivider
import com.daybook.app.ui.components.SortOption
import com.daybook.app.ui.components.SortSheet
import com.daybook.app.ui.components.Swatch
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.MuscleGroupLabels
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.FontChoice
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalIsDark
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.ui.workout.beast.BeastAccentColor

private val REST_OPTIONS = linkedMapOf(
    0 to "Off", 30 to "30s", 60 to "60s", 90 to "90s", 120 to "2m", 180 to "3m", 300 to "5m"
)

/**
 * A7 (§3.8.2) — reachable ONLY from the gear on Beast Mode's landing header. Route
 * `"workout_settings"`, stacked, no pill nav. Wrapped in the Beast accent like every other
 * workout route, so its switches/segmented control are Coral.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutSettingsScreen(
    onNavigateBack: () -> Unit,
    onLeaveBeastMode: () -> Unit,
    onImportFromHevy: () -> Unit,
    viewModel: WorkoutSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fontChoice by viewModel.fontChoice.collectAsStateWithLifecycle()
    var showRestSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackHeader(title = "Beast Mode settings", onBack = onNavigateBack)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH)) {
            // Bug fix (organization) — this used to be five separate single/double-row
            // `SettingsGroup` cards stacked with equal 12dp gaps, so nothing distinguished "these
            // three rows are one topic" from "these two cards are unrelated" — the accent swatch
            // grid in particular floated as its own bare card with no row above it. Regrouped by
            // topic with a `SectionHeader` per group (the same pattern the main app's own
            // Settings screen uses for "Font"), so the hierarchy reads as: Appearance ->
            // Preferences -> Data -> leave.
            item { SectionHeader("Appearance") }
            item {
                SettingsGroup {
                    val isDark = LocalIsDark.current
                    val current = BeastAccentColor.fromKey(settings.workoutAccentColor)
                    SettingsRow(
                        icon = DaybookIcons.Palette,
                        title = "Beast Mode accent",
                        subtitle = "Re-tints the mode, its nav bar, and the session ring/glow.",
                        trailing = {}
                    )
                    // User request — Beast Mode's own accent palette (`BeastAccentColor`), not a
                    // reuse of the app-wide 5. `Arrangement.spacedBy` (not `SpaceBetween`) so a
                    // wrapped second row stays left-aligned under the first instead of
                    // stretching its 4 leftover items across the full row width.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = 70.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BeastAccentColor.entries.forEach { a ->
                            Swatch(
                                color = a.colorFor(isDark),
                                selected = a == current,
                                onClick = { viewModel.setWorkoutAccentColor(a.storageKey) },
                                contentDescription = a.name.lowercase().replaceFirstChar { it.uppercase() }
                            )
                        }
                    }
                    SettingsRowDivider()
                    // User request — "I want different fonts for that mode": a font choice
                    // independent of Settings > Appearance > Font, scoped to Beast Mode only.
                    SettingsRow(
                        icon = DaybookIcons.MenuBook,
                        title = "Beast Mode font",
                        subtitle = fontChoice?.label ?: "Match app font",
                        onClick = { showFontSheet = true }
                    )
                }
            }
            item {
                Spacer(Modifier.height(Spacing.sectionGap))
                SectionHeader("Preferences")
            }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = ImageVector.vectorResource(R.drawable.ic_workout),
                        title = "Weight unit",
                        trailing = {
                            SegmentedControl(
                                options = listOf(SegmentSpec("KG", "kg"), SegmentSpec("LB", "lb")),
                                selectedKey = settings.weightUnit,
                                onSelect = viewModel::setWeightUnit,
                                modifier = Modifier.width0(120.dp)
                            )
                        }
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = DaybookIcons.Clock,
                        title = "Default rest timer",
                        subtitle = "The starting value for a new exercise block",
                        onClick = { showRestSheet = true },
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = DaybookIcons.Category,
                        title = "Default exercise group",
                        subtitle = settings.defaultExerciseGroup
                            ?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }
                            ?.let { MuscleGroupLabels[it] }
                            ?: "All",
                        onClick = { showGroupSheet = true }
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Filled.DateRange,
                        title = "Show Beast Mode on Today",
                        trailing = {
                            Switch(
                                checked = settings.workoutTodayCardEnabled,
                                onCheckedChange = viewModel::setShowOnToday,
                                colors = SwitchDefaults.colors(checkedTrackColor = LocalAccent.current)
                            )
                        }
                    )
                }
            }
            item {
                Spacer(Modifier.height(Spacing.sectionGap))
                SectionHeader("Data")
            }
            item {
                SettingsGroup {
                    // Moved here from the History tab's empty state — a data-management action
                    // belongs in Settings, not floating in the middle of a list screen.
                    SettingsRow(
                        icon = DaybookIcons.ImportExport,
                        title = "Import from Hevy",
                        subtitle = "Bring your workout history over from a Hevy CSV export",
                        onClick = onImportFromHevy
                    )
                }
                Spacer(Modifier.height(Spacing.sectionGap))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = "Leave Beast Mode",
                        onClick = onLeaveBeastMode,
                        trailing = {}
                    )
                }
            }
        }
    }

    SortSheet(
        visible = showRestSheet,
        onDismiss = { showRestSheet = false },
        title = "Default rest timer",
        sortOptions = REST_OPTIONS.map { (k, v) -> SortOption(k.toString(), v) },
        selectedSortKey = settings.restTimerDefaultSeconds.toString(),
        onSelectSort = { viewModel.setRestTimerDefaultSeconds(it.toInt()) },
        dismissOnSelect = true,
        neutralHeader = true
    )

    // Feature addition (post-A6) — the Add-Exercise picker's default active filter chip.
    // "ALL" is a sentinel sort-sheet key (not a real MuscleGroup) mapped to `null` on write.
    SortSheet(
        visible = showGroupSheet,
        onDismiss = { showGroupSheet = false },
        title = "Default exercise group",
        sortOptions = listOf(SortOption("ALL", "All")) + MuscleGroup.entries.map { SortOption(it.name, MuscleGroupLabels[it].orEmpty()) },
        selectedSortKey = settings.defaultExerciseGroup ?: "ALL",
        onSelectSort = { key -> viewModel.setDefaultExerciseGroup(key.takeIf { it != "ALL" }) },
        dismissOnSelect = true,
        neutralHeader = true
    )

    // User request — Beast Mode's own font choice. "APP" is a sentinel sort-sheet key (not a
    // real FontChoice) mapped to `null` on write, meaning "match the app's own font choice".
    SortSheet(
        visible = showFontSheet,
        onDismiss = { showFontSheet = false },
        title = "Beast Mode font",
        sortOptions = listOf(SortOption("APP", "Match app font")) +
            FontChoice.entries.map { SortOption(it.storageKey, it.label) },
        selectedSortKey = fontChoice?.storageKey ?: "APP",
        onSelectSort = { key ->
            viewModel.setFontChoice(key.takeIf { it != "APP" }?.let { FontChoice.fromKeyOrDefault(it) })
        },
        dismissOnSelect = true,
        neutralHeader = true
    )
}

private fun Modifier.width0(w: androidx.compose.ui.unit.Dp) = this.width(w)
