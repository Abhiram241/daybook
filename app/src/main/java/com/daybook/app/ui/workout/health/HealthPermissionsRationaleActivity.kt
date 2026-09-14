package com.daybook.app.ui.workout.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.DaybookTheme
import com.daybook.app.ui.theme.Spacing

/**
 * B1 (§6.2) — the manifest-required Health Connect privacy-policy rationale activity, in both its
 * forms (`ACTION_SHOW_PERMISSIONS_RATIONALE` and the `ViewPermissionUsageActivity` alias). Doubles
 * as the app's own health-privacy statement. A plain `ComponentActivity`, not `MainActivity`'s
 * `FragmentActivity` — it hosts no `BiometricPrompt` (§7.3).
 *
 * Not itself a Beast Mode screen — it has no entry point from inside the app and is drawn in the
 * main `DaybookTheme`, matching every other stacked system-facing surface.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DaybookTheme {
                RationaleScreen(onBack = { finish() })
            }
        }
    }
}

private data class RationaleItem(val title: String, val body: String)

private val ITEMS = listOf(
    RationaleItem(
        "What Daybook reads",
        "Steps, distance, calories, workouts, sleep, heart rate, resting heart rate, blood " +
            "oxygen (SpO₂), weight, hydration, and nutrition totals from apps like Mi Fitness, " +
            "Samsung Health or MyFitnessPal, if you choose to share them."
    ),
    RationaleItem(
        "Why",
        "So your health context sits next to your journal on the Health tab inside Beast Mode, " +
            "without opening a separate app."
    ),
    RationaleItem(
        "What Daybook never does",
        "Daybook never writes anything to Health Connect. It only reads what you explicitly " +
            "share, and you can change or revoke that at any time from the Health Connect app."
    ),
    RationaleItem(
        "Where your data goes",
        "The data you share stays on this device. If you're signed in to a Daybook account, it " +
            "also syncs to your own encrypted cloud backup the same way the rest of your journal " +
            "does — it is never sent anywhere else."
    )
)

@Composable
private fun RationaleScreen(onBack: () -> Unit) {
    Column(Modifier) {
        BackHeader(title = "Health data & privacy", onBack = onBack)
        LazyColumn(
            modifier = Modifier.padding(horizontal = Spacing.screenH),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(ITEMS) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
                    Text(item.body, style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
                }
            }
        }
    }
}
