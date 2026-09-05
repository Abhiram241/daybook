package com.daybook.app.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daybook.app.data.model.ColorTag
import com.daybook.app.data.model.DayOfWeek
import com.daybook.app.data.model.HabitType
import com.daybook.app.ui.ReminderTimesEditor
import com.daybook.app.ui.components.*
import com.daybook.app.ui.icons.Icons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.IconButtonSize
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing
import java.time.LocalTime

@Stable
class HabitFormState {
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    // v0.5.1 §J: named constant, not the literal "task" — this default is duplicated in
    // AddHabitViewModel (field + clearForm) and a rename must not desynchronise them.
    var iconKey by mutableStateOf(Icons.TASK)
    var tintName by mutableStateOf("AUTO")
    var type by mutableStateOf(HabitType.INDIVIDUAL)
    val times = mutableStateListOf<LocalTime>()
    val activeDays = mutableStateListOf<DayOfWeek>()
    var snooze by mutableStateOf(10)
    // Customization round (rec 8): per-habit custom notification text + "why this matters" note.
    var promptMessage by mutableStateOf("")
    var motivation by mutableStateOf("")
    // v0.5.5: non-UI passthrough — an edit that keeps the type STREAK must not wipe the running
    // count. Hydrated from the habit in EditHabitScreen, carried back into the VM on save.
    var streakStartedAt: Long? = null
    var streakLongest: Int = 0
    // Journal-as-habit round: the per-habit ordered question list. A plain in-memory list — this
    // IS the form field, saved as JSON on saveHabit(). Hydrated from Habit.journalQuestionsJson in
    // EditHabitScreen; seeded with one default question the first time the Type chip switches to
    // JOURNAL on a brand-new habit (see the chip's onClick below).
    val journalQuestions = mutableStateListOf<String>()
}

@Composable
fun HabitFormScaffold(
    headline: String,
    saveLabel: String,
    state: HabitFormState,
    errorMessage: String?,
    nextReminderPreview: String?,
    onNavigateBack: () -> Unit,
    advancedExpanded: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onSave: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        // v0.5.3 Phase 4 (§4.1) — pinned back header; the Delete action moves into its trailing
        // slot. The headline no longer has its own first list item (§4.1).
        BackHeader(
            title = headline,
            onBack = onNavigateBack,
            actions = {
                if (onDelete != null) {
                    CircleIconButton(
                        icon = MI.Filled.Delete,
                        contentDescription = "Delete",
                        onClick = onDelete,
                        style = CircleStyle.Danger,
                        size = IconButtonSize.Lg.dp
                    )
                }
            }
        )

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
                    // v0.5.3 Phase 4 (§4.9) — canonical order: name (+ helper) → Type → times →
                    // type-specific sections → Advanced. Helper text matches FoodMed's tone.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DaybookTextField(
                            value = state.title,
                            onValueChange = { state.title = it },
                            label = "Habit name",
                            placeholder = "Drink water"
                        )
                        Text(
                            "You'll be reminded at each time you set, and asked to check it off.",
                            style = DaybookText.Caption,
                            color = DaybookColors.TextMuted
                        )
                    }
                }
                item {
                    FormGroup("Type") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DaybookChip(label = "Individual", selected = state.type == HabitType.INDIVIDUAL,
                                onClick = { state.type = HabitType.INDIVIDUAL })
                            DaybookChip(label = "Batch", selected = state.type == HabitType.BATCH,
                                onClick = { state.type = HabitType.BATCH })
                            // v0.5.5: "Ongoing" (enum value STREAK) — a passive running day-count.
                            DaybookChip(label = "Ongoing", selected = state.type == HabitType.STREAK,
                                onClick = { state.type = HabitType.STREAK })
                            // Journal-as-habit round: asks the saved questions at each reminder
                            // time, in a chat. Seed one default question the first time a NEW
                            // (still-empty) form switches into Journal — never overwrites an
                            // already-populated list (an edit, or flipping the chip back and forth).
                            DaybookChip(label = "Journal", selected = state.type == HabitType.JOURNAL,
                                onClick = {
                                    state.type = HabitType.JOURNAL
                                    if (state.journalQuestions.isEmpty()) {
                                        state.journalQuestions.add("What's on your mind?")
                                    }
                                })
                        }
                        Text(
                            when (state.type) {
                                HabitType.BATCH ->
                                    "Batch habits share one daily check-in notification, at the time set in " +
                                        "Settings → Notifications & alarms."
                                HabitType.STREAK ->
                                    "Ongoing habits track a running day count. No reminders — start and stop " +
                                        "them from the Habits tab."
                                HabitType.INDIVIDUAL ->
                                    "This habit gets its own reminder at each time you set."
                                HabitType.JOURNAL ->
                                    "Journal habits ask your saved questions at each reminder time, in a chat."
                            },
                            style = DaybookText.Caption, color = DaybookColors.TextMuted
                        )
                    }
                }
                if (state.type == HabitType.INDIVIDUAL || state.type == HabitType.JOURNAL) {
                    item {
                        ReminderTimesEditor(
                            times = state.times,
                            onAdd = { if (state.times.none { t -> t == it }) { state.times.add(it); state.times.sort() } },
                            onUpdate = { i, t -> state.times[i] = t; state.times.sort() },
                            onRemove = { state.times.removeAt(it) }
                        )
                    }
                }
                item {
                    AdvancedSection(expandedInitially = advancedExpanded) {
                        FormGroup("Description") {
                            DaybookTextField(
                                value = state.description,
                                onValueChange = { state.description = it },
                                label = "Notes (optional)",
                                singleLine = false
                            )
                        }
                        FormGroup("Icon") {
                            // v0.5.3 Phase 5 (§5.10) — the picker item is an IconTile with a selected
                            // accent ring, matching how the icon renders on the card (was a circular
                            // CircleIconButton, a different shape from the card's rounded-square tile).
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(Icons.getCuratedIconSet()) { ic ->
                                    val selected = state.iconKey == ic
                                    Box(
                                        modifier = Modifier
                                            .clip(AppShapes.tile)
                                            .then(
                                                if (selected)
                                                    Modifier.border(2.dp, LocalAccent.current, AppShapes.tile)
                                                else Modifier
                                            )
                                            .clickableImpl(remember { MutableInteractionSource() }) { state.iconKey = ic }
                                    ) {
                                        IconTile(icon = Icons.getIcon(ic), tint = CardTints.Neutral, size = IconButtonSize.Lg.dp)
                                    }
                                }
                            }
                        }
                        FormGroup("Card color") {
                            TintPicker(selectedName = state.tintName, onSelect = { state.tintName = it })
                        }
                        // v0.5.5: an Ongoing (STREAK) habit has no schedule and no reminders, so
                        // Active days / Snooze do not apply — hide them. Description / Icon / Card
                        // color still apply.
                        // Customization round (rec 8): per-habit reminder text (hidden for STREAK —
                        // no notifications) + a "why this matters" note (all types).
                        // Journal-as-habit round: also hidden for JOURNAL — its notification body is
                        // the fixed "Tap to write today's entry" (Phase 2), so a custom prompt has
                        // nowhere to surface, mirroring why STREAK hides it.
                        if (state.type != HabitType.STREAK && state.type != HabitType.JOURNAL) {
                            FormGroup("Reminder text") {
                                DaybookTextField(
                                    value = state.promptMessage,
                                    onValueChange = { state.promptMessage = it },
                                    label = "Shown in the notification (optional)",
                                    placeholder = "Time to complete this habit"
                                )
                            }
                        }
                        if (state.type == HabitType.JOURNAL) {
                            JournalQuestionsFormGroup(state)
                        }
                        FormGroup("Why this matters") {
                            DaybookTextField(
                                value = state.motivation,
                                onValueChange = { state.motivation = it },
                                label = "A note to your future self (optional)",
                                singleLine = false
                            )
                        }
                        if (state.type != HabitType.STREAK) {
                            FormGroup("Active days (all = every day)") {
                                DayOfWeekSelector(
                                    selected = state.activeDays,
                                    showAllWhenEmpty = true,
                                    onToggle = { day ->
                                        when {
                                            state.activeDays.isEmpty() -> {
                                                state.activeDays.addAll(DayOfWeek.entries); state.activeDays.remove(day)
                                            }
                                            state.activeDays.contains(day) -> state.activeDays.remove(day)
                                            else -> state.activeDays.add(day)
                                        }
                                        if (state.activeDays.size == 7) state.activeDays.clear()
                                    }
                                )
                            }
                            FormGroup("Snooze") {
                                SnoozeStepper(minutes = state.snooze, onChange = { state.snooze = it })
                            }
                        }
                    }
                }
                if (state.type == HabitType.INDIVIDUAL && !nextReminderPreview.isNullOrBlank()) {
                    item {
                        Text("Next reminder: $nextReminderPreview", style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
                    }
                }
            }

            // v0.5.3 Phase 4 (§4.2 / §4.9) — shared sticky save bar; the validation error now
            // renders inside the bar's column, above the button (was below the fold, under Save).
            StickySaveBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, style = DaybookText.CardSubtitle, color = DaybookColors.Danger)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    text = saveLabel,
                    onClick = onSave,
                    enabled = habitFormSaveEnabled(state.title, state.type, state.times.size)
                )
            }
        }
    }
}

fun tintToColorTag(name: String): ColorTag = ColorTag.fromNameOrAuto(name)

/**
 * v0.5.5 — pure Save-enabled predicate. INDIVIDUAL still needs ≥1 reminder time; BATCH and STREAK
 * ("Ongoing") need only a non-blank title. Journal-as-habit round: JOURNAL schedules exactly like
 * INDIVIDUAL (B4) so it needs ≥1 reminder time too. See `HabitFormSaveEnabledTest`.
 */
fun habitFormSaveEnabled(title: String, type: HabitType, timeCount: Int): Boolean =
    title.isNotBlank() && (type !in setOf(HabitType.INDIVIDUAL, HabitType.JOURNAL) || timeCount > 0)

/**
 * Journal-as-habit round: the per-habit ordered Questions editor, a straight port of the (now
 * deleted, global) `JournalQuestionsSettingsScreen`'s add/edit/delete/reorder UX, operating on
 * [HabitFormState.journalQuestions] — an in-memory list, no Room table. Each row: the question
 * text (tap to edit via [DaybookAlertDialog]) + Up/Down/Delete [CircleIconButton]s. Below the list:
 * a plain [DaybookTextField] + full-width [PrimaryButton] ("Add question") — the
 * field-then-button-below shape, NOT a compact side-by-side "field + small Add button" row, so this
 * sidesteps Task A's `GhostButton` shrink-wrap pattern entirely (Phase 0's note).
 */
@Composable
private fun JournalQuestionsFormGroup(state: HabitFormState) {
    FormGroup("Questions") {
        Text(
            "Asked in this order, every time this habit's reminder fires.",
            style = DaybookText.Caption, color = DaybookColors.TextMuted
        )
        Spacer(Modifier.height(8.dp))
        var editingIndex by remember { mutableStateOf(-1) }
        var editingDraft by remember { mutableStateOf("") }
        state.journalQuestions.forEachIndexed { index, question ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.field)
                    .background(DaybookColors.SurfaceElevated)
                    .clickableImpl(remember { MutableInteractionSource() }) {
                        editingIndex = index; editingDraft = question
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    question,
                    style = DaybookText.CardTitle,
                    color = DaybookColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = MI.Filled.KeyboardArrowUp,
                    contentDescription = "Move up",
                    enabled = index > 0,
                    onClick = {
                        val moved = com.daybook.app.data.moveInList(state.journalQuestions.toList(), index, index - 1)
                        state.journalQuestions.clear(); state.journalQuestions.addAll(moved)
                    },
                    size = IconButtonSize.Sm.dp
                )
                Spacer(Modifier.width(4.dp))
                CircleIconButton(
                    icon = MI.Filled.KeyboardArrowDown,
                    contentDescription = "Move down",
                    enabled = index < state.journalQuestions.size - 1,
                    onClick = {
                        val moved = com.daybook.app.data.moveInList(state.journalQuestions.toList(), index, index + 1)
                        state.journalQuestions.clear(); state.journalQuestions.addAll(moved)
                    },
                    size = IconButtonSize.Sm.dp
                )
                Spacer(Modifier.width(4.dp))
                CircleIconButton(
                    icon = MI.Filled.Delete,
                    contentDescription = "Delete question",
                    style = CircleStyle.Danger,
                    enabled = com.daybook.app.data.canDelete(state.journalQuestions.size),
                    onClick = { state.journalQuestions.removeAt(index) },
                    size = IconButtonSize.Sm.dp
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (editingIndex >= 0) {
            DaybookAlertDialog(
                onDismissRequest = { editingIndex = -1 },
                title = "Edit question",
                text = {
                    DaybookTextField(
                        value = editingDraft,
                        onValueChange = { editingDraft = it },
                        label = null
                    )
                },
                confirmLabel = "Save",
                onConfirm = {
                    com.daybook.app.data.normaliseQuestionText(editingDraft)?.let {
                        state.journalQuestions[editingIndex] = it
                    }
                    editingIndex = -1
                },
                dismissLabel = "Cancel"
            )
        }
        var newQuestion by remember { mutableStateOf("") }
        DaybookTextField(
            value = newQuestion,
            onValueChange = { newQuestion = it },
            label = null,
            placeholder = "Add a question"
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = "Add question",
            enabled = newQuestion.isNotBlank(),
            onClick = {
                com.daybook.app.data.normaliseQuestionText(newQuestion)?.let {
                    state.journalQuestions.add(it)
                    newQuestion = ""
                }
            }
        )
    }
}

/**
 * v0.5.3 Phase 4 (§4.9) — pure: does any field inside the collapsed "Advanced" section hold a
 * non-default value? Drives the Edit form's auto-expand so a user's earlier customisation isn't
 * hidden behind a collapsed section. See `AdvancedDefaultTest`.
 *
 * v0.5.5 — type-aware: a STREAK form cannot set Active days / Snooze, so those must not force the
 * section open for it (they may hold stale values carried from a previous type).
 *
 * Journal-as-habit round: a JOURNAL form's Questions list is non-default the moment it holds
 * anything beyond the single seeded default question — same "force Advanced open" treatment STREAK
 * already gets for its own type-specific state.
 */
fun anyAdvancedFieldNonDefault(state: HabitFormState): Boolean {
    val base = state.description.isNotBlank() ||
        state.iconKey != Icons.TASK ||
        state.tintName != "AUTO" ||
        state.promptMessage.isNotBlank() ||   // rec 8
        state.motivation.isNotBlank()         // rec 8
    if (state.type == HabitType.STREAK) return base
    if (state.type == HabitType.JOURNAL) {
        val defaultQuestions = state.journalQuestions.size <= 1 &&
            state.journalQuestions.getOrNull(0)?.let { it == "What's on your mind?" } != false
        return base || state.activeDays.isNotEmpty() || state.snooze != 10 || !defaultQuestions
    }
    return base || state.activeDays.isNotEmpty() || state.snooze != 10
}
