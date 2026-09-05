package com.daybook.app.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.ConfirmDeleteDialog
import com.daybook.app.ui.components.FormLoadingState
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.util.DateTimeUtils

@Composable
fun EditHabitScreen(
    habitId: String,
    onNavigateBack: () -> Unit = {},
    routinesViewModel: RoutinesViewModel = hiltViewModel(),
    formViewModel: AddHabitViewModel = hiltViewModel()
) {
    val state = remember { HabitFormState() }
    var initialized by remember { mutableStateOf(false) }
    val errorMessage by formViewModel.errorMessage.collectAsState()
    val successMessage by formViewModel.successMessage.collectAsState()

    LaunchedEffect(habitId) {
        routinesViewModel.getHabitById(habitId) { habit ->
            habit?.let {
                state.title = it.title
                state.description = it.description
                state.iconKey = it.iconKey
                state.tintName = it.colorTag.name
                state.type = it.type
                state.times.clear(); state.times.addAll(DateTimeUtils.jsonToTimes(it.timesJson))
                state.activeDays.clear(); state.activeDays.addAll(DateTimeUtils.jsonToDays(it.activeDaysJson))
                state.snooze = it.snoozeIntervalMinutes
                state.promptMessage = it.promptMessage.orEmpty()
                state.motivation = it.motivation.orEmpty()
                state.streakStartedAt = it.streakStartedAt
                state.streakLongest = it.streakLongest
                state.journalQuestions.clear()
                state.journalQuestions.addAll(DateTimeUtils.jsonToJournalQuestions(it.journalQuestionsJson))
                initialized = true
            }
        }
    }
    LaunchedEffect(successMessage) {
        if (!successMessage.isNullOrBlank()) onNavigateBack()
    }

    // v0.5.3 Phase 4 (§4.9 / backlog #7) — show the header + a loading spinner while the VM
    // hydrates, instead of flashing a blank screen.
    if (!initialized) {
        Column(Modifier.fillMaxSize().background(DaybookColors.Bg)) {
            BackHeader(title = "Edit habit", onBack = onNavigateBack)
            FormLoadingState(Modifier.weight(1f))
        }
        return
    }

    var confirmDelete by remember { mutableStateOf(false) }
    ConfirmDeleteDialog(
        visible = confirmDelete,
        itemName = state.title.ifBlank { "this habit" },
        onConfirm = { formViewModel.deleteHabit(habitId) },
        onDismiss = { confirmDelete = false }
    )

    HabitFormScaffold(
        headline = "Edit habit",
        saveLabel = "Update habit",
        state = state,
        errorMessage = errorMessage,
        nextReminderPreview = null,
        onNavigateBack = onNavigateBack,
        // v0.5.3 Phase 4 (§4.9) — collapsed by default like Add; auto-expand only if the user
        // previously customised an Advanced field.
        advancedExpanded = anyAdvancedFieldNonDefault(state),
        onDelete = { confirmDelete = true },
        onSave = {
            formViewModel.title = state.title
            formViewModel.description = state.description
            formViewModel.iconKey = state.iconKey
            formViewModel.colorTag = tintToColorTag(state.tintName)
            formViewModel.type = state.type
            formViewModel.times = state.times.toMutableList()
            formViewModel.activeDays = state.activeDays.toMutableList()
            formViewModel.snoozeIntervalMinutes = state.snooze
            formViewModel.promptMessage = state.promptMessage
            formViewModel.motivation = state.motivation
            formViewModel.streakStartedAt = state.streakStartedAt
            formViewModel.streakLongest = state.streakLongest
            formViewModel.journalQuestions = state.journalQuestions.toMutableList()
            formViewModel.saveHabit(habitId)
        }
    )
}
