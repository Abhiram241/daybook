package com.daybook.app.ui.foodmed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daybook.app.data.model.ColorTag
import com.daybook.app.data.model.DayOfWeek
import com.daybook.app.data.model.RedFlag
import com.daybook.app.data.model.TaskType
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

/**
 * v0.5.3 Phase 4 (§4.9 / UI Q4) — the canonical top-level section order that BOTH the Habit and
 * FoodMed forms lay out, so the two screens don't drift apart. Guarded by `FormFieldOrderTest`.
 */
val CANONICAL_FORM_FIELD_ORDER: List<String> =
    listOf("name", "type", "times", "typeSpecificSections", "advanced")

@Stable
class FoodMedFormState {
    var label by mutableStateOf("")
    var type by mutableStateOf(TaskType.FOOD)
    var iconKey by mutableStateOf("restaurant")
    var iconTouched by mutableStateOf(false)
    var tintName by mutableStateOf("AUTO")
    val times = mutableStateListOf<LocalTime>()
    val activeDays = mutableStateListOf<DayOfWeek>()
    var snooze by mutableStateOf(10)
    // v0.5.2 §4: the saved category for a CUSTOM/JOURNAL reminder, plus the "new category" draft.
    var customCategory by mutableStateOf<String?>(null)
    var newCategoryDraft by mutableStateOf("")
    // v0.5.3 item 8: the per-reminder prompt message, plus the "new prompt" draft.
    var promptMessage by mutableStateOf<String?>(null)
    var newPromptDraft by mutableStateOf("")
    // Customization round (SD-6): per-intake "why this matters" note. Blank => stored NULL.
    var motivation by mutableStateOf("")
    // v0.5.4: FOOD-only Crohn's trigger-flag defaults, pre-filled every time the reminder is logged.
    var defaultRedFlag by mutableStateOf(RedFlag.NONE)
    var defaultSuspectedFood by mutableStateOf("")
    // v0.5.2 build 8: FOOD-only "outside food" default, pre-filled every time the reminder is logged.
    var defaultOutsideFood by mutableStateOf(false)

    fun defaultIconFor(t: TaskType) = when (t) {
        TaskType.FOOD -> "restaurant"
        TaskType.MED -> "medication"
        TaskType.CUSTOM -> "restaurant"
        TaskType.JOURNAL -> "menu_book"
    }
}

@Composable
fun FoodMedFormScaffold(
    headline: String,
    saveLabel: String,
    state: FoodMedFormState,
    errorMessage: String?,
    nextReminderPreview: String?,
    onNavigateBack: () -> Unit,
    advancedExpanded: Boolean = false,
    onDelete: (() -> Unit)? = null,
    categories: List<String> = emptyList(),
    onAddCategory: (String) -> Unit = {},
    onRemoveCategory: (String) -> Unit = {},
    prompts: List<String> = emptyList(),
    onAddPrompt: (String) -> Unit = {},
    onRemovePrompt: (String) -> Unit = {},
    onSave: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        // v0.5.3 Phase 4 (§4.1) — pinned back header; Delete moves into its trailing slot; the
        // headline no longer has its own first list item.
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
                // v0.5.3 Phase 4 (§4.9) — canonical order shared with HabitForm: name (+ helper)
                // → Type → times → type-specific sections → Advanced.
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DaybookTextField(
                            value = state.label,
                            onValueChange = { state.label = it },
                            label = "Reminder name",
                            placeholder = "Lunch, Blood pressure med"
                        )
                        Text(
                            "You'll be asked what you had at each of these times.",
                            style = DaybookText.Caption,
                            color = DaybookColors.TextMuted
                        )
                    }
                }
                item {
                    // v0.5.3 Phase 4 (§4.9) — Type now sits above times to match HabitForm.
                    FormGroup("Type") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Journal-as-habit round (B3): JOURNAL is retired as an Intake concept —
                            // kept in the enum only so an old backup's TaskType.valueOf("JOURNAL")
                            // still decodes losslessly, but never offered here again.
                            TaskType.entries.filter { it != TaskType.JOURNAL }.forEach { t ->
                                DaybookChip(
                                    label = t.name.lowercase().replaceFirstChar { it.uppercase() },
                                    selected = state.type == t,
                                    onClick = {
                                        state.type = t
                                        if (!state.iconTouched) state.iconKey = state.defaultIconFor(t)
                                    }
                                )
                            }
                        }
                        // v0.5.3 Phase 5 (§5.11) — make the silent icon-follows-type behaviour explicit.
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Icon follows the type until you pick one.",
                            style = DaybookText.Caption,
                            color = DaybookColors.TextMuted
                        )
                    }
                }
                item {
                    ReminderTimesEditor(
                        times = state.times,
                        onAdd = { if (state.times.none { x -> x == it }) { state.times.add(it); state.times.sort() } },
                        onUpdate = { i, t -> state.times[i] = t; state.times.sort() },
                        onRemove = { state.times.removeAt(it) }
                    )
                }
                // v0.5.3 Phase 4 (§4.9) — FOOD-specific sections are progressively disclosed
                // behind reminder type = FOOD.
                if (state.type == TaskType.FOOD) {
                    item {
                        // v0.5.4: Crohn's food-diary trigger flag. These are DEFAULTS — every log of
                        // this reminder pre-fills with them, and you adjust per entry when logging.
                        FormGroup("Red-flag tracking") {
                            Text(
                                "Pre-fills each time you log this reminder. Change it per entry when " +
                                    "you answer “what did you have?”.",
                                style = DaybookText.Caption,
                                color = DaybookColors.TextMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            RedFlagPicker(
                                selected = state.defaultRedFlag,
                                onSelect = { state.defaultRedFlag = it }
                            )
                            Spacer(Modifier.height(10.dp))
                            DaybookTextField(
                                value = state.defaultSuspectedFood,
                                onValueChange = { state.defaultSuspectedFood = it },
                                label = "Default suspected trigger food",
                                placeholder = "e.g. dairy, gluten, spicy food"
                            )
                        }
                    }
                    item {
                        // v0.5.2 build 8: "outside food" default marker. Pre-fills each log; adjust per entry.
                        FormGroup("Outside food") {
                            Text(
                                "Pre-fills each time you log this reminder. Change it per entry when you answer.",
                                style = DaybookText.Caption,
                                color = DaybookColors.TextMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            DaybookChip(
                                label = "Outside food",
                                selected = state.defaultOutsideFood,
                                onClick = { state.defaultOutsideFood = !state.defaultOutsideFood }
                            )
                        }
                    }
                }
                if (state.type == TaskType.CUSTOM || state.type == TaskType.JOURNAL) {
                    item {
                        // v0.5.3 item 3: inline Category picker for CUSTOM *and* JOURNAL, on the main form.
                        FormGroup("Category") {
                            if (categories.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(categories) { name ->
                                        DaybookChip(
                                            label = name,
                                            selected = state.customCategory == name,
                                            onClick = {
                                                state.customCategory =
                                                    if (state.customCategory == name) null else name
                                            }
                                        )
                                    }
                                }
                            }
                            // v0.5.3 Phase 5 (§5.11 / backlog #33) — GhostButton no longer bakes
                            // fillMaxWidth, so it sizes to content; Alignment.Bottom lands it on the
                            // input box baseline (the field's label row sits above the box).
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DaybookTextField(
                                    value = state.newCategoryDraft,
                                    onValueChange = { state.newCategoryDraft = it },
                                    label = "New category",
                                    placeholder = "Snacks",
                                    modifier = Modifier.weight(1f)
                                )
                                GhostButton(
                                    text = "Add",
                                    onClick = {
                                        val raw = state.newCategoryDraft.trim()
                                        if (raw.isNotEmpty()) {
                                            onAddCategory(raw)
                                            state.customCategory = raw
                                            state.newCategoryDraft = ""
                                        }
                                    }
                                )
                            }
                            state.customCategory?.let { sel ->
                                if (sel in categories) {
                                    // v0.5.3 Phase 4 (§4.3) — TextLink primitive (44dp tap target).
                                    TextLink(
                                        "Remove \"$sel\"",
                                        onClick = {
                                            onRemoveCategory(sel)
                                            state.customCategory = null
                                        },
                                        color = DaybookColors.Danger
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    AdvancedSection(expandedInitially = advancedExpanded) {
                        FormGroup("Prompt message") {
                            Text(
                                "Shown instead of “What did you have?” in the reminder and its notification.",
                                style = DaybookText.Caption, color = DaybookColors.TextMuted
                            )
                            Spacer(Modifier.height(8.dp))
                            if (prompts.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(prompts) { msg ->
                                        DaybookChip(
                                            label = msg,
                                            selected = state.promptMessage == msg,
                                            onClick = { state.promptMessage = if (state.promptMessage == msg) null else msg }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            // v0.5.3 Phase 5 (§5.11 / backlog #33) — see the Category row.
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DaybookTextField(
                                    value = state.newPromptDraft,
                                    onValueChange = { state.newPromptDraft = it },
                                    label = "New prompt",
                                    placeholder = "What did you take?",
                                    modifier = Modifier.weight(1f)
                                )
                                GhostButton(text = "Add", onClick = {
                                    val raw = state.newPromptDraft.trim()
                                    if (raw.isNotEmpty()) { onAddPrompt(raw); state.promptMessage = raw; state.newPromptDraft = "" }
                                })
                            }
                            state.promptMessage?.let { sel ->
                                if (sel in prompts) {
                                    // v0.5.3 Phase 4 (§4.3) — TextLink primitive.
                                    TextLink(
                                        "Remove \"$sel\"",
                                        onClick = { onRemovePrompt(sel); state.promptMessage = null },
                                        color = DaybookColors.Danger
                                    )
                                }
                            }
                        }
                        FormGroup("Icon") {
                            // v0.5.3 Phase 5 (§5.11, aligning with §5.10) — IconTile + selected accent
                            // ring, matching the card's rounded-square tile (was a circular button).
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
                                            .clickableImpl(remember { MutableInteractionSource() }) {
                                                state.iconKey = ic; state.iconTouched = true
                                            }
                                    ) {
                                        IconTile(icon = Icons.getIcon(ic), tint = CardTints.Neutral, size = IconButtonSize.Lg.dp)
                                    }
                                }
                            }
                        }
                        // Customization round (SD-6): per-intake "why this matters" note.
                        FormGroup("Why this matters") {
                            DaybookTextField(
                                value = state.motivation,
                                onValueChange = { state.motivation = it },
                                label = "A note to your future self (optional)",
                                singleLine = false
                            )
                        }
                        FormGroup("Card color") {
                            TintPicker(selectedName = state.tintName, onSelect = { state.tintName = it })
                        }
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
                if (!nextReminderPreview.isNullOrBlank()) {
                    item {
                        Text("Next reminder: $nextReminderPreview", style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
                    }
                }
            }

            // v0.5.3 Phase 4 (§4.2 / §4.9) — shared sticky save bar; the validation error now
            // renders inside the bar's column, above the button.
            StickySaveBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, style = DaybookText.CardSubtitle, color = DaybookColors.Danger)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    text = saveLabel,
                    onClick = onSave,
                    enabled = state.label.isNotBlank() && state.times.isNotEmpty()
                )
            }
        }
    }
}

fun fmTintToColorTag(name: String): ColorTag = ColorTag.fromNameOrAuto(name)

/**
 * v0.5.3 Phase 4 (§4.9) — pure: does any collapsed "Advanced" field hold a non-default value?
 * Drives the Edit form's auto-expand. Mirrors the HabitForm helper of the same name.
 */
fun anyAdvancedFieldNonDefault(state: FoodMedFormState): Boolean =
    state.iconKey != state.defaultIconFor(state.type) ||
        state.tintName != "AUTO" ||
        state.activeDays.isNotEmpty() ||
        state.snooze != 10 ||
        !state.promptMessage.isNullOrBlank() ||
        state.motivation.isNotBlank() ||   // SD-6
        !state.customCategory.isNullOrBlank()
