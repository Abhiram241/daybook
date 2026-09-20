package com.daybook.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daybook.app.data.health.SourceAppLabels
import com.daybook.app.ui.components.SheetSectionLabel
import com.daybook.app.ui.components.clickableImpl
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent

/**
 * Owner feedback round 3 (2026-09-20), plan §1.8 — the "Data source priority" picker upgraded
 * from a single-select [com.daybook.app.ui.components.SortSheet] to a reorderable ranked list:
 * a user can now say "prefer Google Fit, but fall back to Mi Fitness on days Google Fit has
 * nothing". Same visual chrome as [com.daybook.app.ui.components.SortSheet] (a content-sized
 * `ModalBottomSheet` on `DaybookColors.Surface`/`AppShapes.sheet`), but a different body: no
 * single "Save" — every reorder/add/remove tap calls [onRankedChange] immediately, and only
 * [onDismiss] (tap outside, or "Done") closes the sheet.
 *
 * [ranked] empty = "Auto" (Daybook picks). Tapping "Choose specific apps" reveals the two editable
 * sections without itself changing [ranked] — it stays empty (still Auto) until the user actually
 * taps an app into "Available".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankedSourceSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    available: List<String>,
    ranked: List<String>,
    onRankedChange: (List<String>) -> Unit,
) {
    if (!visible) return
    val accent = LocalAccent.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
    // Reset "show the editable sections" back to auto-derived (ranked.isNotEmpty()) each time the
    // sheet opens fresh, but let the user reveal it early from the Auto empty-state.
    var expanded by rememberSaveable(title, visible) { mutableStateOf(ranked.isNotEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(2.dp))
            SheetSectionLabel(title, accent = accent, neutral = true)

            if (ranked.isEmpty() && !expanded) {
                RankedRow(
                    label = "Auto (let Daybook pick)",
                    accent = accent,
                    active = true,
                    onClick = {},
                    trailing = {}
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Choose specific apps",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier.clickableImpl(remember { MutableInteractionSource() }) {
                            expanded = true
                        }
                    )
                }
            } else {
                SheetSectionLabel("Priority order", accent = accent, neutral = true)
                if (ranked.isEmpty()) {
                    Text(
                        "No apps chosen yet — tap one below to add it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DaybookColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                    )
                }
                ranked.forEachIndexed { index, pkg ->
                    RankedRow(
                        label = SourceAppLabels.labelFor(pkg) ?: pkg,
                        accent = accent,
                        active = true,
                        onClick = {},
                        trailing = {
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val next = ranked.toMutableList()
                                    val tmp = next[index - 1]
                                    next[index - 1] = next[index]
                                    next[index] = tmp
                                    onRankedChange(next)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    tint = if (index > 0) accent else DaybookColors.TextFaint
                                )
                            }
                            IconButton(
                                enabled = index < ranked.lastIndex,
                                onClick = {
                                    val next = ranked.toMutableList()
                                    val tmp = next[index + 1]
                                    next[index + 1] = next[index]
                                    next[index] = tmp
                                    onRankedChange(next)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    tint = if (index < ranked.lastIndex) accent else DaybookColors.TextFaint
                                )
                            }
                            IconButton(onClick = { onRankedChange(ranked - pkg) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = DaybookColors.TextMuted
                                )
                            }
                        }
                    )
                }

                val remainingAvailable = available.filter { it !in ranked }
                if (remainingAvailable.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SheetSectionLabel("Available", accent = accent, neutral = true)
                    remainingAvailable.forEach { pkg ->
                        RankedRow(
                            label = SourceAppLabels.labelFor(pkg) ?: pkg,
                            accent = accent,
                            active = false,
                            onClick = { onRankedChange(ranked + pkg) },
                            trailing = {}
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

private val RankedRowShape = RoundedCornerShape(10.dp)

@Composable
private fun RankedRow(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    active: Boolean,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RankedRowShape)
            .background(if (active) accent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickableImpl(remember { MutableInteractionSource() }, onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) DaybookColors.TextPrimary else DaybookColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}
