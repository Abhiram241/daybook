package com.daybook.app.ui.respond

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.BigHeadline
import com.daybook.app.ui.components.DaybookChip
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.RedFlagPicker
import com.daybook.app.ui.components.StatPill
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.components.TextLink
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

@Composable
fun RespondScreen(
    onDone: () -> Unit,
    onOpenHistory: (itemType: String, itemId: String) -> Unit,
    vm: RespondViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.done, state.missing) {
        if (state.done || state.missing) onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        // v0.5.3 Phase 4 (§4.1) — pinned back header (owns the status-bar inset).
        BackHeader(title = state.title.ifBlank { "Reminder" }, onBack = onDone)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Spacing.screenH,
                end = Spacing.screenH,
                top = Spacing.listTop,
                bottom = Spacing.listGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
        ) {
            item {
                BigHeadline(state.title.ifBlank { "Reminder" })
                Text(
                    "Scheduled ${state.scheduledTime}",
                    style = DaybookText.Metadata,
                    color = DaybookColors.TextMuted
                )
                Spacer(Modifier.height(6.dp))
                // v0.5.3 Phase 4 (§4.3) — TextLink primitive. itemId is resolved
                // asynchronously in the VM's init; navigating with a blank id would build the
                // unmatched route "detail/<type>/" and throw, so it is guarded out.
                TextLink(
                    "View history",
                    onClick = { if (vm.itemId.isNotBlank()) onOpenHistory(vm.itemType, vm.itemId) },
                    color = DaybookColors.TextMuted,
                    leadingIcon = DaybookIcons.Clock
                )
            }

            when (state.kind) {
                RespondViewModel.Kind.HABIT -> {
                    if (state.streak > 0) {
                        item {
                            StatPill(
                                icon = DaybookIcons.Flame,
                                value = "${state.streak}",
                                label = if (state.streak == 1) "day" else "days",
                                // v0.5.3 Phase 5 (§5.9) — the item's own tint, not a hardcoded Mint.
                                tint = CardTints.byId(vm.itemId)
                            )
                        }
                    }
                    if (state.readOnly) {
                        item {
                            // v0.5.3 Phase 5 (§5.9) — a locked-state chip with a lock glyph, not a
                            // bare muted sentence. The "Undo" action moved to the StickySaveBar so
                            // the primary action is in the same place regardless of state.
                            Row(
                                modifier = Modifier
                                    .clip(AppShapes.pill)
                                    .background(DaybookColors.SurfaceElevated)
                                    .border(1.dp, DaybookColors.Border, AppShapes.pill)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    DaybookIcons.Lock,
                                    contentDescription = null,
                                    tint = DaybookColors.TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Already ${state.statusLabel ?: "resolved"}",
                                    style = DaybookText.Metadata,
                                    color = DaybookColors.TextMuted
                                )
                            }
                        }
                    }
                }
                RespondViewModel.Kind.INTAKE -> {
                    item {
                        // Journal Mode: INTAKE always opens editable + pre-filled — no
                        // read-only recorded-text / "Undo" / "Edit" intermediate. A resolved
                        // log re-saves in place through the same log() path.
                        DaybookTextField(
                            value = state.reply,
                            onValueChange = vm::onReplyChange,
                            label = "Your answer",
                            placeholder = state.promptPlaceholder,
                            singleLine = true
                        )
                        // v0.5.4: FOOD reminders get the Crohn's trigger-flag capture.
                        if (state.isFood) {
                            Spacer(Modifier.height(16.dp))
                            // v0.5.3 Phase 5 (§5.9) — small Metadata label; matched on the Home
                            // inline reply so the flag picker is labelled everywhere.
                            Text(
                                "Trigger flag",
                                style = DaybookText.Metadata,
                                color = DaybookColors.TextMuted
                            )
                            Spacer(Modifier.height(8.dp))
                            RedFlagPicker(selected = state.redFlag, onSelect = vm::onRedFlagChange)
                            Spacer(Modifier.height(10.dp))
                            DaybookTextField(
                                value = state.suspectedFood,
                                onValueChange = vm::onSuspectedFoodChange,
                                label = "Suspected trigger food",
                                placeholder = "e.g. dairy, gluten, spicy food",
                                singleLine = true
                            )
                            Spacer(Modifier.height(10.dp))
                            DaybookChip(
                                label = "Outside food",
                                selected = state.outsideFood,
                                onClick = { vm.onOutsideFoodChange(!state.outsideFood) }
                            )
                        }
                    }
                }
            }
        }

        // v0.5.3 Phase 5 (§5.9) — the sticky bar is always present now; the read-only HABIT
        // "Undo" lives here too, so the primary action never moves between the list and the bar.
        StickySaveBar {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): a rejected log() (canBackfill /
            // month not resident / row gone) surfaces here instead of silently popping back as
            // if it had saved. Only `log()` (INTAKE's Log/Save) can ever set this.
            state.rejectedMessage?.let {
                Text(it, style = DaybookText.Metadata, color = DaybookColors.Warning)
                Spacer(Modifier.height(8.dp))
            }
            when (state.kind) {
                RespondViewModel.Kind.HABIT -> {
                    if (state.readOnly) {
                        GhostButton(text = "Undo", onClick = { vm.undo() }, modifier = Modifier.fillMaxWidth())
                    } else {
                        PrimaryButton(text = "Complete", onClick = { vm.complete() }, enabled = !state.busy)
                        Spacer(Modifier.height(8.dp))
                        GhostButton(text = "Skip", onClick = { vm.skip() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                RespondViewModel.Kind.INTAKE -> {
                    PrimaryButton(
                        text = if (state.isEdit) "Save" else "Log",
                        onClick = { vm.log() },
                        enabled = state.reply.isNotBlank() && !state.busy
                    )
                    // Journal Mode: Skip is meaningless while editing an existing log.
                    if (!state.isEdit) {
                        Spacer(Modifier.height(8.dp))
                        GhostButton(text = "Skip", onClick = { vm.skip() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
