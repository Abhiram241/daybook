package com.daybook.app.ui.journal

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons as MI
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.CircleStyle
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing

/**
 * Journal-as-habit round (Phase 4) — the chat-style entry flow for a JOURNAL-type habit: one
 * question at a time, "like a friend asking you questions," per the user's spec. Contrast with
 * [JournalScreen] (the pre-existing FoodMed-side stepper, unchanged) — this is a genuinely
 * different UI shape, not a reskin.
 */
@Composable
fun HabitJournalChatScreen(
    onNavigateBack: () -> Unit = {},
    vm: HabitJournalChatViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.saved, state.missing) {
        if (state.saved || state.missing) onNavigateBack()
    }

    // Per B1: nothing is lost on back mid-chat (every send is already persisted as a draft), so a
    // plain pop is the correct behaviour here — no "step back a question" interception like the old
    // stepper's; the default system/header back already does the right thing.
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        BackHeader(title = state.title.ifBlank { "Entry" }, onBack = onNavigateBack)

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screenH,
                    end = Spacing.screenH,
                    top = Spacing.listTop,
                    bottom = Spacing.formSaveBarClearance
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
            ) {
                itemsIndexed(state.messages) { _, message ->
                    when (message) {
                        is ChatMessage.Question -> QuestionBubble(message.text)
                        is ChatMessage.Answer -> AnswerBubble(message.text)
                    }
                }
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): this used to unconditionally show
                // "✓ Entry saved" the moment every question was answered, before the save had
                // actually been confirmed — a rejection (canBackfill/month-residency/missing-row)
                // left that optimistic bubble as the only feedback the user ever saw.
                if (state.allAnswered && !state.saved && state.rejectedMessage == null) {
                    item { SavedBubble() }
                }
                state.rejectedMessage?.let { reason ->
                    item { RejectedBubble(reason) }
                }
            }

            StickySaveBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                if (!state.allAnswered) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.listGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DaybookTextField(
                            value = state.draftAnswer,
                            onValueChange = vm::onDraftChange,
                            label = null,
                            placeholder = "Type your answer…",
                            singleLine = false,
                            minLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        CircleIconButton(
                            icon = DaybookIcons.Send,
                            contentDescription = "Send",
                            onClick = vm::sendAnswer,
                            style = CircleStyle.Tonal,
                            enabled = state.draftAnswer.isNotBlank() && !state.busy
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        SoftCard(
            tint = CardTints.Neutral,
            contentPadding = 14.dp,
            elevation = 0.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            androidx.compose.material3.Text(text, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
        }
    }
}

@Composable
private fun AnswerBubble(text: String) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .background(accent, com.daybook.app.ui.theme.AppShapes.card)
        ) {
            androidx.compose.material3.Text(
                text,
                style = DaybookText.CardTitle,
                color = DaybookColors.OnSolid,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
private fun SavedBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            "✓ Entry saved",
            style = DaybookText.Metadata,
            color = DaybookColors.TextMuted
        )
    }
}

/** LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4) — the honest counterpart to [SavedBubble]. */
@Composable
private fun RejectedBubble(reason: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            "Couldn't save: $reason",
            style = DaybookText.Metadata,
            color = DaybookColors.Warning
        )
    }
}
