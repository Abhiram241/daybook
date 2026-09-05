package com.daybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.util.DateTimeUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Configurable reminder times: an "Add time" button opens a 12-hour clock picker (with an
 * AM/PM toggle), and each added time is an accent pill you tap to edit (x to remove).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderTimesEditor(
    times: List<LocalTime>,
    onAdd: (LocalTime) -> Unit,
    onUpdate: (index: Int, value: LocalTime) -> Unit,
    onRemove: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    // rec 1 (C2): null → follow the system 12h/24h preference; non-null → force it.
    clock24h: Boolean? = null
) {
    // null = closed, -1 = adding, >=0 = editing that index
    var editing by remember { mutableStateOf<Int?>(null) }
    val accent = LocalAccent.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (times.isNotEmpty()) {
            FlowRow {
                times.forEachIndexed { index, time ->
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp, bottom = 8.dp)
                            .clip(AppShapes.pill)
                            .background(accent.copy(alpha = 0.16f))
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            DateTimeUtils.formatTime(time, clock24h ?: false),
                            style = MaterialTheme.typography.titleMedium,
                            color = DaybookColors.TextPrimary,
                            modifier = Modifier.clickable { editing = index }
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(DaybookColors.SurfaceElevated)
                                .clickable { onRemove(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                DaybookIcons.Remove,
                                contentDescription = "Remove time",
                                tint = DaybookColors.TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                "Add at least one time",
                style = MaterialTheme.typography.bodySmall,
                color = DaybookColors.TextFaint
            )
        }

        // v0.5.3 Phase 0: GhostButton no longer bakes fillMaxWidth — pass it here.
        GhostButton(text = "Add time", onClick = { editing = -1 }, modifier = Modifier.fillMaxWidth())
    }

    editing?.let { idx ->
        val initial = if (idx >= 0 && idx < times.size) times[idx] else LocalTime.of(9, 0)
        TimePickerDialog(
            initial = initial,
            onDismiss = { editing = null },
            onConfirm = { picked ->
                if (idx >= 0) onUpdate(idx, picked) else onAdd(picked)
                editing = null
            },
            force24h = clock24h
        )
    }
}

/**
 * v0.5.3 Phase 4 (§4.5) — the **reference** dialog. Already styled with `AppShapes.dialog`,
 * `DaybookColors.Surface` and accent text buttons, so it is deliberately NOT routed through
 * [com.daybook.app.ui.components.DaybookAlertDialog] (that wrapper is an `AlertDialog`, which
 * can't host the analog dial at the width this needs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    // rec 1 (C2): non-null forces the dial's 12h/24h mode (from `clock_24h`); null follows the OS.
    force24h: Boolean? = null
) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    // Follow the device 12h/24h preference unless the app setting overrides. Storage stays "HH:mm".
    val use24h = force24h
        ?: remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = use24h
    )
    // usePlatformDefaultWidth = false: the analog dial has a fixed intrinsic width (~256.dp plus
    // this Column's 20.dp padding). The platform dialog width is a percentage of the screen, which
    // clips the dial on narrow devices — and verticalScroll only rescues the vertical axis.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .clip(AppShapes.dialog)
                .background(DaybookColors.Surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    selectorColor = accent,
                    containerColor = DaybookColors.SurfaceElevated,
                    timeSelectorSelectedContainerColor = accent.copy(alpha = 0.22f),
                    periodSelectorSelectedContainerColor = accent.copy(alpha = 0.22f),
                    clockDialColor = DaybookColors.SurfaceElevated
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = DaybookColors.TextMuted)
                ) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
                    colors = ButtonDefaults.textButtonColors(contentColor = accent)
                ) { Text("Set") }
            }
        }
    }
}

/**
 * v0.5.3 Phase 6 (D2) — a single-date picker styled to match [TimePickerDialog] (accent selection,
 * `AppShapes.dialog`, `DaybookColors.Surface`). Used by the date-range export in Data settings.
 * Dates are handled as `LocalDate`; the M3 picker's UTC-millis convention is bridged here so the
 * calendar day the user taps is the day that comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaybookDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    // Task C (C1) — when set, any date after this one is unselectable (structurally, not just
    // validated after the fact). The two pre-existing export-range call sites pass none, so their
    // behaviour is unchanged.
    maxDate: LocalDate? = null
) {
    val accent = LocalAccent.current
    val selectableDates = remember(maxDate) {
        if (maxDate == null) DatePickerDefaults.AllDates
        else object : SelectableDates {
            private val maxUtcMillis = maxDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxUtcMillis
            override fun isSelectableYear(year: Int): Boolean = year <= maxDate.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(AppShapes.dialog)
                .background(DaybookColors.Surface)
                .padding(vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DatePicker(
                state = state,
                showModeToggle = false,
                title = null,
                colors = DatePickerDefaults.colors(
                    containerColor = DaybookColors.Surface,
                    selectedDayContainerColor = accent,
                    todayDateBorderColor = accent,
                    todayContentColor = accent
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = DaybookColors.TextMuted)
                ) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        if (millis != null) {
                            onConfirm(
                                java.time.Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        } else {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = accent)
                ) { Text("Set") }
            }
        }
    }
}
