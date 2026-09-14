package com.daybook.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daybook.app.data.hydration.HydrationUnits
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.IconTile
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.TextLink
import com.daybook.app.ui.components.UnitDropdown
import com.daybook.app.ui.icons.Icons
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText

/**
 * Hydration habit — the Today row. One entry per day: "Log water" / "Edit" opens a dialog with an
 * amount + ml/L dropdown that REPLACES the day's amount (clearing the field removes it).
 */
@Composable
fun HydrationCard(
    item: HomeItem,
    tint: CardTint,
    onSave: (amountMl: Int) -> Unit,
    onUnitChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = item.hydration ?: return
    var dialogOpen by rememberSaveable(item.id) { mutableStateOf(false) }

    SoftCard(tint = tint, onClick = { dialogOpen = true }, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = Icons.getIcon(item.iconKey), tint = tint, contentDescription = item.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.subtitle.orEmpty(),
                    style = DaybookText.CardSubtitle,
                    color = tint.onFillMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            if (item.statusLabel != null) {
                Text(
                    item.statusLabel,
                    style = DaybookText.ButtonLabel,
                    color = if (item.statusLabel == MISSED_LABEL) tint.onFillMuted else tint.accent
                )
                Spacer(Modifier.width(8.dp))
            }
            TextLink(
                text = if (ui.amountMl > 0) "Edit" else "Log water",
                onClick = { dialogOpen = true },
                color = tint.accent
            )
        }
        Spacer(Modifier.height(10.dp))
        // Progress toward the daily goal.
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tint.fillRaised)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ui.progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint.accent)
            )
        }
    }

    if (dialogOpen) {
        var amountText by rememberSaveable(item.id, ui.unit) {
            mutableStateOf(if (ui.amountMl > 0) HydrationUnits.inputValue(ui.amountMl, ui.unit) else "")
        }
        val parsedMl = HydrationUnits.toMl(amountText, ui.unit)
        val clearing = amountText.isBlank()
        DaybookAlertDialog(
            onDismissRequest = { dialogOpen = false },
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
                            placeholder = if (ui.unit == HydrationUnits.L) "1.5" else "1500",
                            isError = !clearing && parsedMl == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f)
                        )
                        UnitDropdown(
                            options = listOf(HydrationUnits.ML to "ml", HydrationUnits.L to "L"),
                            selectedKey = ui.unit,
                            onSelect = { unit ->
                                // Keep the typed quantity the same amount of water in the new unit.
                                parsedMl?.let { amountText = HydrationUnits.inputValue(it, unit) }
                                onUnitChange(unit)
                            }
                        )
                    }
                    Text(
                        "Goal: ${HydrationUnits.format(ui.goalMl, ui.unit)}",
                        style = DaybookText.Caption,
                        color = DaybookColors.TextMuted
                    )
                }
            },
            confirmLabel = if (clearing && ui.amountMl > 0) "Clear" else "Save",
            onConfirm = {
                when {
                    clearing -> { if (ui.amountMl > 0) onSave(0); dialogOpen = false }
                    parsedMl != null -> { onSave(parsedMl); dialogOpen = false }
                }
            },
            dismissLabel = "Cancel",
            onDismiss = { dialogOpen = false }
        )
    }
}
