package com.daybook.app.ui.icons

import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon key constants + resolver. Backed by `material-icons-core` (bundled with Compose)
 * and local vectors in [DaybookIcons] — `material-icons-extended` is no longer a dependency.
 */
object Icons {
    // Habit icons
    const val WATER = "water"
    const val PILL = "pill"
    const val MEAL = "restaurant"
    const val FORK = "fork_left_right"
    const val RUN = "directions_run"
    const val BOOK = "menu_book"
    const val SLEEP = "sleep"
    const val MEDITATE = "meditation"
    const val TASK = "task"

    // Food/Med specific icons
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch_dining"
    const val DINNER = "dinner_dining"
    const val SNACK = "fastfood"
    const val MEDICATION = "medication"
    const val VITAMINS = "vitamins"

    // General icons — only the keys with a live call site remain (v0.5.2 §6.2). The resolver
    // below still accepts every string form; these consts are just the named shortcuts in use.
    const val PLUS = "add"
    const val CHECK = "check"
    const val CHECK_CIRCLE = "check_circle"
    const val X = "close"
    const val CLOCK = "access_time"
    const val CALENDAR = "calendar_today"
    const val SETTINGS = "settings"

    private val CURATED = listOf(
        TASK, WATER, PILL, MEAL, FORK, RUN, BOOK, SLEEP, MEDITATE,
        BREAKFAST, LUNCH, DINNER, SNACK, MEDICATION, VITAMINS,
        PLUS, CHECK, X, CLOCK, CALENDAR, SETTINGS,
    )

    /** Curated subset for the icon picker. */
    fun getCuratedIconSet(): List<String> = CURATED

    fun getIcon(name: String): ImageVector = when (name.lowercase()) {
        "water" -> DaybookIcons.WaterDrop
        "pill", "medication", "vitamins" -> DaybookIcons.Medication
        "restaurant", "meal", "fork_left_right",
        "breakfast", "breakfast_dining", "lunch", "lunch_dining",
        "dinner", "dinner_dining", "fastfood", "snack" -> DaybookIcons.Restaurant
        "directions_run", "run" -> DaybookIcons.DirectionsRun
        "menu_book", "book" -> DaybookIcons.MenuBook
        "sleep" -> DaybookIcons.Bedtime
        "meditation", "meditate" -> DaybookIcons.SelfImprovement
        // v0.5.1 §J: aliases for near-miss keys that a hand-edited or legacy backup can carry.
        // ExportImportRepository.importAllData copies `iconKey` through unvalidated, so an
        // unknown key used to land on the generic Category badge.
        "task", "checklist", "habit", "list", "note", "todo" -> DaybookIcons.Task
        "add", "plus" -> MaterialIcons.Filled.Add
        "remove" -> DaybookIcons.Remove
        "check" -> MaterialIcons.Filled.Check
        "check_circle" -> MaterialIcons.Filled.CheckCircle
        "close", "x" -> MaterialIcons.Filled.Close
        "access_time", "clock", "schedule", "snooze" -> DaybookIcons.Clock
        "calendar_today", "calendar" -> MaterialIcons.Filled.DateRange
        "settings" -> MaterialIcons.Filled.Settings
        "notifications", "notification" -> MaterialIcons.Filled.Notifications
        "home" -> MaterialIcons.Filled.Home
        "delete" -> MaterialIcons.Filled.Delete
        "edit" -> MaterialIcons.Filled.Edit
        "fire", "streak" -> DaybookIcons.Flame
        "comment", "reply" -> DaybookIcons.Comment
        "help", "info" -> MaterialIcons.Filled.Info
        "error" -> MaterialIcons.Filled.Warning
        "archive" -> DaybookIcons.Archive
        "unarchive" -> DaybookIcons.Unarchive
        "filter_list" -> DaybookIcons.FilterList
        "check_box" -> DaybookIcons.CheckBox
        "check_box_outline_blank" -> DaybookIcons.CheckBoxBlank
        // v0.5.3 Phase 4 (§4.6 / D1): an unknown key is a visible, logged, deliberate
        // placeholder — never a silent wrong glyph. Was `else -> DaybookIcons.Task`.
        else -> {
            // runCatching: android.util.Log is not mocked under plain JVM unit tests.
            runCatching { android.util.Log.w("Icons", "unknown iconKey '$name' -> Unknown placeholder") }
            DaybookIcons.Unknown
        }
    }
}
