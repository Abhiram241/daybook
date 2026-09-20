package com.daybook.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daybook.app.data.hydration.HydrationUnits
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText

/**
 * Hydration-as-Intake (2026-09-20) — the amount-entry dialog, extracted from the old (now-deleted)
 * `HydrationCard` so it can be reused from both the Today reminder card and the Intake tab's
 * respond flow (see SOURCE_PRIORITY_AND_HYDRATION_PLAN's §2.6). Enter the day's total; saving
 * REPLACES the day's entry (clearing the field removes it — [onSave] is called with 0).
 */
@Composable
fun HydrationAmountDialog(
    unit: String,
    goalMl: Int,
    currentMl: Int,
    onUnitChange: (String) -> Unit,
    onSave: (amountMl: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by rememberSaveable(unit) {
        mutableStateOf(if (currentMl > 0) HydrationUnits.inputValue(currentMl, unit) else "")
    }
    val parsedMl = HydrationUnits.toMl(amountText, unit)
    val clearing = amountText.isBlank()
    DaybookAlertDialog(
        onDismissRequest = onDismiss,
        title = "Water for this day",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the total you drank. This replaces the day's entry.",
                    style = DaybookText.Caption,
                    color = DaybookColors.TextMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DaybookTextField(
                        value = amountText,
                        onValueChange = { amountText = it.take(6) },
                        label = null,
                        placeholder = if (unit == HydrationUnits.L) "1.5" else "1500",
                        isError = !clearing && parsedMl == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f)
                    )
                    UnitDropdown(
                        options = listOf(HydrationUnits.ML to "ml", HydrationUnits.L to "L"),
                        selectedKey = unit,
                        onSelect = { newUnit ->
                            // Keep the typed quantity the same amount of water in the new unit.
                            parsedMl?.let { amountText = HydrationUnits.inputValue(it, newUnit) }
                            onUnitChange(newUnit)
                        }
                    )
                }
                Text(
                    "Goal: ${HydrationUnits.format(goalMl, unit)}",
                    style = DaybookText.Caption,
                    color = DaybookColors.TextMuted
                )
            }
        },
        confirmLabel = if (clearing && currentMl > 0) "Clear" else "Save",
        onConfirm = {
            when {
                clearing -> { if (currentMl > 0) onSave(0) }
                parsedMl != null -> onSave(parsedMl)
            }
        },
        dismissLabel = "Cancel",
        onDismiss = onDismiss
    )
}
