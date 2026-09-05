package com.daybook.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.daybook.app.ui.components.FormGroup
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

/**
 * Journal-as-habit round (Phase 5) — the plain, conventional edit-form for a resolved Journal-habit
 * entry (B8): every saved question with its answer, all editable at once, a standard Save bar.
 * Deliberately NOT chat-shaped — the spec calls for the chat only on first answering; revisiting an
 * answer is a form, matching [com.daybook.app.ui.detail.DetailScreen]'s stacked Q&A display of the
 * same `qa_json` data.
 */
@Composable
fun HabitJournalEditScreen(
    onNavigateBack: () -> Unit = {},
    vm: HabitJournalEditViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.saved, state.missing) {
        if (state.saved || state.missing) onNavigateBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        BackHeader(title = state.title.ifBlank { "Entry" }, onBack = onNavigateBack)

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
                    BigHeadline(state.title.ifBlank { "Entry" })
                    Text(
                        "Scheduled ${state.scheduledTime}",
                        style = DaybookText.Metadata,
                        color = DaybookColors.TextMuted
                    )
                }
                itemsIndexed(vm.fields) { index, field ->
                    FormGroup(title = null) {
                        Text(field.question, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        DaybookTextField(
                            value = field.answer,
                            onValueChange = { vm.onAnswerChange(index, it) },
                            label = null,
                            singleLine = false,
                            minLines = 2
                        )
                    }
                }
            }

            StickySaveBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                PrimaryButton(
                    text = "Save",
                    onClick = vm::save,
                    enabled = !state.busy
                )
            }
        }
    }
}
