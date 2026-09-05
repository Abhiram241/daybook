package com.daybook.app.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.BigHeadline
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.components.TextLink
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing

/**
 * v0.5.2 §3 / 5B.5 — the dedicated journal page.
 *
 * v0.5.4 Phase 4: a step-by-step conversational (question, answer) flow. One question + one answer
 * field on screen at a time, Back / Next to move, Save on the last step. `index > 0` reinterprets
 * the header back arrow (and the system back gesture) as "step back a question"; only step 0 pops
 * the nav stack — so typed answers are never silently discarded by a stray back tap mid-flow.
 */
@Composable
fun JournalScreen(
    onNavigateBack: () -> Unit = {},
    onOpenHistory: (itemType: String, itemId: String) -> Unit = { _, _ -> },
    vm: JournalViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.saved, state.missing) {
        // v0.5.3 Phase 5 (§5.8) — a missing occurrence pops straight back with no UI. Accepted as
        // a rare race (the row was deleted/synced away while this screen opened); breadcrumb only.
        if (state.missing) android.util.Log.i("JournalScreen", "occurrence missing — popping back")
        if (state.saved || state.missing) onNavigateBack()
    }

    // v0.5.4 Phase 4 — header-back vs in-flow back: on step > 0, back steps to the previous
    // question; only step 0 pops.
    val onBack: () -> Unit = { if (state.index > 0) vm.back() else onNavigateBack() }
    BackHandler(enabled = state.index > 0) { vm.back() }

    val steps = state.steps
    val currentStep = steps.getOrNull(state.index)
    val lastStep = isLastStep(state.index, steps.size)
    val anyAnswerNonBlank = steps.any { it.answer.isNotBlank() }

    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        // v0.5.3 Phase 4 (§4.1) — pinned back header (owns the status-bar inset).
        BackHeader(title = state.title.ifBlank { "Entry" }, onBack = onBack)

        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screenH,
                    end = Spacing.screenH,
                    top = Spacing.listTop,
                    bottom = Spacing.formSaveBarClearance
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
            ) {
                item {
                    BigHeadline(state.title.ifBlank { "Entry" })   // v0.5.3 Phase 7 (#37)
                    Text(
                        "Scheduled ${state.scheduledTime}",
                        style = DaybookText.Metadata,
                        color = DaybookColors.TextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    // v0.5.3 Phase 4 (§4.3) — TextLink primitive (44dp tap target). itemId is
                    // resolved asynchronously in the VM init, so a blank id is guarded out.
                    TextLink(
                        "View history",
                        onClick = { if (vm.itemId.isNotBlank()) onOpenHistory(vm.itemType, vm.itemId) },
                        color = DaybookColors.TextMuted,
                        leadingIcon = DaybookIcons.Clock
                    )
                }

                if (currentStep != null) {
                    item {
                        // v0.5.4 Phase 4 — the "3 / 7" step indicator + a thin progress bar.
                        Text(
                            state.progressLabel,
                            style = DaybookText.Metadata,
                            color = DaybookColors.TextMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (state.index + 1).toFloat() / steps.size },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        // v0.5.4 Phase 4 — question + answer field swap, keyed on the step index.
                        Crossfade(
                            targetState = state.index,
                            animationSpec = Motion.fast(),
                            label = "journalStep"
                        ) { idx ->
                            val step = steps.getOrNull(idx) ?: return@Crossfade
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.listGap)) {
                                Text(
                                    step.question,
                                    style = DaybookText.SectionTitle,
                                    color = DaybookColors.TextPrimary
                                )
                                DaybookTextField(
                                    value = step.answer,
                                    onValueChange = vm::onAnswerChange,
                                    label = null,
                                    placeholder = "Your answer",
                                    singleLine = false,
                                    minLines = 3
                                )
                            }
                        }
                    }
                }
            }

            // v0.5.3 Phase 4 (§4.2) — shared sticky save bar (scrim + nav/IME padding).
            StickySaveBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): a rejected save (canBackfill /
                // month not resident / row gone) now surfaces here instead of silently popping
                // back as if it had saved.
                state.rejectedMessage?.let {
                    Text(it, style = DaybookText.Metadata, color = DaybookColors.Warning)
                    Spacer(Modifier.height(Spacing.sm))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.listGap)
                ) {
                    if (state.index > 0) {
                        GhostButton(
                            text = "Back",
                            onClick = vm::back,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (lastStep) {
                        PrimaryButton(
                            text = "Save",
                            onClick = { vm.save() },
                            enabled = anyAnswerNonBlank && !state.busy,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        PrimaryButton(
                            text = "Next",
                            onClick = vm::next,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
