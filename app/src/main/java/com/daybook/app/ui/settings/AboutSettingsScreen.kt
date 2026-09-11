package com.daybook.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.BuildConfig
import com.daybook.app.R
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.SettingsRow
import com.daybook.app.ui.components.clickableImpl
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

/**
 * UX overhaul item 5 — the new "About & help" hub destination. Collects the version block, the
 * "Replay the tour" entry (item 1), the moved "Check for updates" toggle (item 8.4) and
 * "Diagnostics" buttons (item 8.5), and "Copy crash log" (moved out of the hub footer).
 */
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit = {},
    onReplayTour: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val clipboard = LocalClipboardManager.current
    val hasCrashLog = remember { viewModel.hasCrashLog() }
    val checkForUpdatesEnabled by viewModel.checkForUpdatesEnabled.collectAsStateWithLifecycle()
    val testNotificationResult by viewModel.testNotificationResult.collectAsStateWithLifecycle()

    SettingsSubScreen("About & help", onNavigateBack) {
        // ---- Version block --------------------------------------------------------------
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(56.dp).padding(bottom = 8.dp)
            )
            Text("Daybook", style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = DaybookText.Metadata,
                color = DaybookColors.TextFaint
            )
        }

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader("Help", subtitle = "Take the intro tour again any time.")
        SettingsGroup {
            SettingsRow(
                icon = DaybookIcons.MenuBook,
                title = "Replay the tour",
                subtitle = "Walk through what each part of Daybook does.",
                onClick = onReplayTour
            )
        }

        // ---- Updates (moved from Reminders & notifications — item 8.4) -----------------
        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "Updates",
            subtitle = "Checks Firebase App Distribution for a newer build each time you open the app."
        )
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                SettingsToggleRow(
                    label = "Check for updates",
                    checked = checkForUpdatesEnabled,
                    onCheckedChange = viewModel::setCheckForUpdatesEnabled
                )
            }
        }

        // ---- Diagnostics (moved from Reminders & notifications — item 8.5) -------------
        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader("Diagnostics")
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                Text(
                    "Send a test notification to check the notification path on its own, " +
                        "without waiting for a reminder time.",
                    style = DaybookText.Caption,
                    color = DaybookColors.TextMuted
                )
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = "Send test notification",
                    onClick = { viewModel.sendTestNotification() },
                    modifier = Modifier.fillMaxWidth()
                )
                testNotificationResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = DaybookText.Caption, color = DaybookColors.TextMuted)
                }
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = "Re-arm all reminders",
                    onClick = { viewModel.resyncReminders() },
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasCrashLog) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        text = "Copy crash log",
                        onClick = {
                            val text = viewModel.crashLogText()
                            if (!text.isNullOrEmpty()) clipboard.setText(AnnotatedString(text))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
