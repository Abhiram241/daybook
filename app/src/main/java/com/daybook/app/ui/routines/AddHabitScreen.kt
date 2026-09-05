package com.daybook.app.ui.routines

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AddHabitScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AddHabitViewModel = hiltViewModel()
) {
    val state = remember { HabitFormState() }
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val preview by viewModel.nextReminderPreview.collectAsState()
    // rec 3 (N2) — a NEW habit form starts at the app-wide default snooze. Seed once, and only
    // while the field is still untouched (== the HabitFormState default), so a user edit wins.
    val defaultSnooze by viewModel.defaultSnooze.collectAsState()
    var snoozeSeeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(defaultSnooze) {
        if (!snoozeSeeded && state.snooze == 10) state.snooze = defaultSnooze
        snoozeSeeded = true
    }

    LaunchedEffect(successMessage) {
        if (!successMessage.isNullOrBlank()) onNavigateBack()
    }
    LaunchedEffect(state.times.toList(), state.activeDays.toList()) {
        viewModel.times = state.times.toMutableList()
        viewModel.activeDays = state.activeDays.toMutableList()
        viewModel.updateNextReminderPreview()
    }

    HabitFormScaffold(
        headline = "New habit",
        saveLabel = "Save habit",
        state = state,
        errorMessage = errorMessage,
        nextReminderPreview = preview,
        onNavigateBack = onNavigateBack,
        advancedExpanded = false,
        onSave = {
            viewModel.title = state.title
            viewModel.description = state.description
            viewModel.iconKey = state.iconKey
            viewModel.colorTag = tintToColorTag(state.tintName)
            viewModel.type = state.type
            viewModel.times = state.times.toMutableList()
            viewModel.activeDays = state.activeDays.toMutableList()
            viewModel.snoozeIntervalMinutes = state.snooze
            viewModel.promptMessage = state.promptMessage
            viewModel.motivation = state.motivation
            viewModel.streakStartedAt = state.streakStartedAt
            viewModel.streakLongest = state.streakLongest
            viewModel.journalQuestions = state.journalQuestions.toMutableList()
            viewModel.saveHabit()
        }
    )
}
