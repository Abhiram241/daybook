package com.daybook.app.ui.foodmed

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
fun EditFoodMedScreen(
    taskId: String,
    onNavigateBack: () -> Unit = {},
    foodMedViewModel: FoodMedViewModel = hiltViewModel(),
    formViewModel: AddFoodMedViewModel = hiltViewModel()
) {
    val state = remember { FoodMedFormState() }
    var initialized by remember { mutableStateOf(false) }
    val errorMessage by formViewModel.errorMessage.collectAsState()
    val successMessage by formViewModel.successMessage.collectAsState()
    val categories by formViewModel.categories.collectAsState()
    val prompts by formViewModel.prompts.collectAsState()

    LaunchedEffect(taskId) {
        foodMedViewModel.getItemById(taskId) { task ->
            task?.let {
                state.label = it.label
                state.type = it.type
                state.iconKey = it.iconKey
                state.tintName = it.colorTag.name
                state.customCategory = it.customCategory
                state.promptMessage = it.promptMessage
                state.motivation = it.motivation.orEmpty()
                state.defaultRedFlag = it.defaultRedFlag ?: com.daybook.app.data.model.RedFlag.NONE
                state.defaultSuspectedFood = it.defaultSuspectedFood.orEmpty()
                state.defaultOutsideFood = it.defaultOutsideFood == true
                state.times.clear(); state.times.addAll(DateTimeUtils.jsonToTimes(it.timesJson))
                state.activeDays.clear(); state.activeDays.addAll(DateTimeUtils.jsonToDays(it.activeDaysJson))
                state.snooze = it.snoozeIntervalMinutes
                initialized = true
            }
        }
    }
    LaunchedEffect(successMessage) {
        if (!successMessage.isNullOrBlank()) onNavigateBack()
    }

    // v0.5.3 Phase 4 (§4.9 / backlog #7) — header + spinner while the VM hydrates.
    if (!initialized) {
        Column(Modifier.fillMaxSize().background(DaybookColors.Bg)) {
            BackHeader(title = "Edit reminder", onBack = onNavigateBack)
            FormLoadingState(Modifier.weight(1f))
        }
        return
    }

    var confirmDelete by remember { mutableStateOf(false) }
    ConfirmDeleteDialog(
        visible = confirmDelete,
        itemName = state.label.ifBlank { "this reminder" },
        onConfirm = { formViewModel.deleteItem(taskId) },
        onDismiss = { confirmDelete = false }
    )

    FoodMedFormScaffold(
        headline = "Edit reminder",
        saveLabel = "Update reminder",
        state = state,
        errorMessage = errorMessage,
        nextReminderPreview = null,
        onNavigateBack = onNavigateBack,
        // v0.5.3 Phase 4 (§4.9) — collapsed like Add unless a customised Advanced field exists.
        advancedExpanded = anyAdvancedFieldNonDefault(state),
        onDelete = { confirmDelete = true },
        categories = categories,
        onAddCategory = formViewModel::addCategory,
        onRemoveCategory = formViewModel::removeCategory,
        prompts = prompts,
        onAddPrompt = formViewModel::addPrompt,
        onRemovePrompt = formViewModel::removePrompt,
        onSave = {
            formViewModel.label = state.label
            formViewModel.type = state.type
            formViewModel.iconKey = state.iconKey
            formViewModel.colorTag = fmTintToColorTag(state.tintName)
            formViewModel.customCategory = state.customCategory
                ?: state.newCategoryDraft.trim().takeIf { it.isNotBlank() }?.also(formViewModel::addCategory)
            formViewModel.promptMessage = state.promptMessage
                ?: state.newPromptDraft.trim().takeIf { it.isNotBlank() }?.also(formViewModel::addPrompt)
            formViewModel.defaultRedFlag = state.defaultRedFlag
            formViewModel.defaultSuspectedFood = state.defaultSuspectedFood
            formViewModel.defaultOutsideFood = state.defaultOutsideFood
            formViewModel.times = state.times.toMutableList()
            formViewModel.activeDays = state.activeDays.toMutableList()
            formViewModel.snoozeIntervalMinutes = state.snooze
            formViewModel.motivation = state.motivation
            formViewModel.saveItem(taskId)
        }
    )
}
