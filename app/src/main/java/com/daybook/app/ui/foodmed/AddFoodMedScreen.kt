package com.daybook.app.ui.foodmed

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AddFoodMedScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AddFoodMedViewModel = hiltViewModel()
) {
    val state = remember { FoodMedFormState() }
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val preview by viewModel.nextReminderPreview.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val prompts by viewModel.prompts.collectAsState()
    // rec 3 (N2) — a NEW reminder form starts at the app-wide default snooze (seed once, only
    // while the field is still untouched so a user edit wins).
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

    FoodMedFormScaffold(
        headline = "New reminder",
        saveLabel = "Save reminder",
        state = state,
        errorMessage = errorMessage,
        nextReminderPreview = preview,
        onNavigateBack = onNavigateBack,
        advancedExpanded = false,
        categories = categories,
        onAddCategory = viewModel::addCategory,
        onRemoveCategory = viewModel::removeCategory,
        prompts = prompts,
        onAddPrompt = viewModel::addPrompt,
        onRemovePrompt = viewModel::removePrompt,
        onSave = {
            viewModel.label = state.label
            viewModel.type = state.type
            viewModel.iconKey = state.iconKey
            viewModel.colorTag = fmTintToColorTag(state.tintName)
            viewModel.customCategory = state.customCategory
                ?: state.newCategoryDraft.trim().takeIf { it.isNotBlank() }?.also(viewModel::addCategory)
            viewModel.promptMessage = state.promptMessage
                ?: state.newPromptDraft.trim().takeIf { it.isNotBlank() }?.also(viewModel::addPrompt)
            viewModel.defaultRedFlag = state.defaultRedFlag
            viewModel.defaultSuspectedFood = state.defaultSuspectedFood
            viewModel.defaultOutsideFood = state.defaultOutsideFood
            viewModel.times = state.times.toMutableList()
            viewModel.activeDays = state.activeDays.toMutableList()
            viewModel.snoozeIntervalMinutes = state.snooze
            viewModel.motivation = state.motivation
            viewModel.saveItem()
        }
    )
}
