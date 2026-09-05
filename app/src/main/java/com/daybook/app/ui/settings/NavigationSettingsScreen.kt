package com.daybook.app.ui.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
 * rec 7 (SD-2) — default landing tab + hide/show tabs. Reorder is NOT offered this round.
 * Today is a locked row ("Always shown"); it stays present and first everywhere.
 */
@Composable
fun NavigationSettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val navTabs by viewModel.navTabs.collectAsState()
    val landing by viewModel.defaultLandingTab.collectAsState()
    val visible = remember(navTabs) { NavConfig.visibleRoutesFrom(navTabs) }
    // A landing tab that got hidden falls back to Today.
    val effectiveLanding = if (landing in visible) landing else "home"

    val labelFor = mapOf("home" to "Today", "routines" to "Habits", "foodmed" to "Intake")

    SettingsSubScreen("Navigation", onNavigateBack) {
        SectionHeader("Default tab", subtitle = "Which tab opens when you start Daybook.")
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
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
            "Tabs",
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
                    label = "Show tab: Habits",
                    checked = "routines" in visible,
                    onCheckedChange = { viewModel.setNavTabs(NavConfig.toggleRoute(navTabs, "routines")) }
                )
                HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                SettingsToggleRow(
                    label = "Show tab: Intake",
                    checked = "foodmed" in visible,
                    onCheckedChange = { viewModel.setNavTabs(NavConfig.toggleRoute(navTabs, "foodmed")) }
                )
            }
        }
    }
}
