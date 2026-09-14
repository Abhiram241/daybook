package com.daybook.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.SettingsRowDivider
import com.daybook.app.ui.components.TextLink
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

/**
 * AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.1 — the "Hidden from AI Summary" / "Hidden
 * from Chat" picker. One screen, driven by `scope` in the nav route ("SUMMARY" | "CHAT").
 */
@Composable
fun AiExclusionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AiExclusionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsSubScreen("Hidden from ${state.scopeLabel}", onNavigateBack) {
        Text(
            "Hide specific habits, reminders or individual entries from ${state.scopeLabel}.",
            style = DaybookText.Caption, color = DaybookColors.TextMuted
        )
        Spacer(Modifier.height(Spacing.listGap))
        DaybookTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = null,
            placeholder = "Search habits & reminders…",
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        TextLink(
            text = "Copy from ${viewModel.otherScopeLabel} list",
            onClick = { viewModel.copyFromOtherScope() }
        )
        Spacer(Modifier.height(Spacing.listGap))

        val active = state.items.filterNot { it.isArchived }
        val archived = state.items.filter { it.isArchived }

        if (state.items.isEmpty()) {
            EmptyState(icon = DaybookIcons.Lock, title = "No habits or reminders yet")
        } else {
            if (active.isNotEmpty()) {
                SettingsGroup {
                    Column(Modifier.padding(Spacing.cardInner)) {
                        active.forEachIndexed { i, item ->
                            if (i > 0) SettingsRowDivider()
                            AiExclusionItemRow(item, state, viewModel)
                        }
                    }
                }
            }
            if (archived.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.listGap))
                Text("Archived", style = DaybookText.Caption, color = DaybookColors.TextMuted)
                Spacer(Modifier.height(4.dp))
                SettingsGroup {
                    Column(Modifier.padding(Spacing.cardInner)) {
                        archived.forEachIndexed { i, item ->
                            if (i > 0) SettingsRowDivider()
                            AiExclusionItemRow(item, state, viewModel)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AiExclusionItemRow(
    item: AiExclusionItem,
    state: AiExclusionsUiState,
    viewModel: AiExclusionsViewModel
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { viewModel.toggleExpanded(item) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = DaybookColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (item.hiddenAll) {
                Text("Hide all", style = DaybookText.Caption, color = DaybookColors.TextMuted)
                Spacer(Modifier.width(8.dp))
            }
            Switch(
                checked = item.hiddenAll,
                onCheckedChange = { viewModel.toggleItem(item, it) }
            )
        }
        if (state.expandedItemId == item.id) {
            Column(Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                when {
                    item.hiddenAll -> Text(
                        "Everything from this item is hidden",
                        style = DaybookText.Caption, color = DaybookColors.TextMuted
                    )
                    state.expandedEntries.isEmpty() && !state.loadingEntries -> Text(
                        "No logged entries yet", style = DaybookText.Caption, color = DaybookColors.TextMuted
                    )
                }
                state.expandedEntries.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = item.hiddenAll || entry.hidden,
                            enabled = !item.hiddenAll,
                            onCheckedChange = { viewModel.toggleEntry(entry.id, item.isHabit, it) }
                        )
                        Text(
                            "${entry.timeLabel} · ${entry.preview}",
                            style = DaybookText.CardSubtitle,
                            color = if (item.hiddenAll) DaybookColors.TextFaint else DaybookColors.TextPrimary
                        )
                    }
                }
                if (state.expandedHasMore && !item.hiddenAll) {
                    TextLink(
                        text = if (state.loadingEntries) "Loading…" else "Load older entries",
                        onClick = { if (!state.loadingEntries) viewModel.loadMoreEntries(item) }
                    )
                }
            }
        }
    }
}
