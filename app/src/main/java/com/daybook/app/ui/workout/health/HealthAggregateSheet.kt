package com.daybook.app.ui.workout.health

import androidx.compose.runtime.Composable
import com.daybook.app.ui.components.BottomSheetMenu
import com.daybook.app.ui.components.SheetAction
import com.daybook.app.ui.icons.DaybookIcons

/**
 * B6 (§7.4 "Range mode") — the four presets plus "Custom range". The custom-range date pickers
 * themselves are hosted by the caller ([HealthTabScreen]) using the existing
 * `DaybookDatePickerDialog` ×2 pattern `DataSettingsScreen`'s "Export a date range" already uses —
 * this sheet only decides which of the five options was tapped.
 */
@Composable
fun HealthAggregateSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectPreset: (HealthRangePreset) -> Unit,
    onSelectCustom: () -> Unit
) {
    BottomSheetMenu(
        visible = visible,
        onDismiss = onDismiss,
        actions = listOf(
            SheetAction(DaybookIcons.Clock, "Last 7 days") { onSelectPreset(HealthRangePreset.LAST_7_DAYS) },
            SheetAction(DaybookIcons.Clock, "Last 30 days") { onSelectPreset(HealthRangePreset.LAST_30_DAYS) },
            SheetAction(DaybookIcons.Clock, "This month") { onSelectPreset(HealthRangePreset.THIS_MONTH) },
            SheetAction(DaybookIcons.Clock, "Last 3 months") { onSelectPreset(HealthRangePreset.LAST_3_MONTHS) },
            SheetAction(DaybookIcons.Clock, "Custom range") { onSelectCustom() }
        )
    )
}
