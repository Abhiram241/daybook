package com.daybook.app.ui.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.NavConfig
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.clickableImpl
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing

/**
 * UX overhaul item 5 / 8.6 — the standalone "Navigation" sub-screen is folded into
 * Appearance → Layout. These two sections (default landing tab + which tabs show) are exported as
 * a reusable [NavigationLayoutSections] and called from `AppearanceSettingsScreen`. The old
 * `NavigationSettingsScreen` composable + its `settings_navigation` route are gone.
 *
 * rec 7 (SD-2) — default landing tab + hide/show tabs. Reorder is NOT offered. Today is a locked
 * row ("Always shown"); it stays present and first everywhere.
 */
@Composable
fun ColumnScope.NavigationLayoutSections(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val navTabs by viewModel.navTabs.collectAsStateWithLifecycle()
    val landing by viewModel.defaultLandingTab.collectAsStateWithLifecycle()
    val visible = remember(navTabs) { NavConfig.visibleRoutesFrom(navTabs) }
    // A landing tab that got hidden falls back to Today.
    val effectiveLanding = if (landing in visible) landing else "home"

    val labelFor = mapOf(
        "home" to "Today", "routines" to "Habits", "foodmed" to "Intake",
        // DAILY_REPORT_PLAN.md §2 — the fourth tab.
        "report" to "Daily Report"
    )

    SectionHeader("Layout", subtitle = "Which tab opens on launch, and which tabs show in the bottom bar.")
    SettingsGroup {
        Column(Modifier.padding(Spacing.cardInner)) {
            Text("Default tab", style = DaybookText.Caption, color = DaybookColors.TextMuted)
            visible.forEachIndexed { i, route ->
                if (i > 0) HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableImpl(remember(route) { MutableInteractionSource() }) {
                            viewModel.setDefaultLandingTab(route)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        labelFor[route] ?: route,
                        style = MaterialTheme.typography.bodyLarge,
                        color = DaybookColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (effectiveLanding == route) {
                        Icon(MI.Filled.Check, contentDescription = "Selected", tint = LocalAccent.current, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    SectionHeader(
        "Show tabs",
        subtitle = "Turn a tab off to hide it from the bottom bar. Today is always shown.",
        modifier = Modifier.padding(top = Spacing.listGap)
    )
    SettingsGroup {
        Column(Modifier.padding(Spacing.cardInner), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Today — locked.
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Today", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary, modifier = Modifier.weight(1f))
                Text("Always shown", style = DaybookText.Caption, color = DaybookColors.TextMuted)
            }
            HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
            SettingsToggleRow(
                label = "Show Habits tab",
                checked = "routines" in visible,
                onCheckedChange = { viewModel.setNavTabs(NavConfig.toggleRoute(navTabs, "routines")) }
            )
            HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
            SettingsToggleRow(
                label = "Show Intake tab",
                checked = "foodmed" in visible,
                onCheckedChange = { viewModel.setNavTabs(NavConfig.toggleRoute(navTabs, "foodmed")) }
            )
            HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
            SettingsToggleRow(
                label = "Show Daily Report tab",
                checked = "report" in visible,
                onCheckedChange = { viewModel.setNavTabs(NavConfig.toggleRoute(navTabs, "report")) }
            )
        }
    }
}
