package com.daybook.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.ai.AiProviderId
import com.daybook.app.ui.components.BottomSheetMenu
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.FormGroup
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SheetAction
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.TextLink
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

/**
 * DAILY_REPORT_PLAN.md §3.2 — one row per provider: empty / a key saved (masked, reveal toggle) /
 * a key saved and last verified working ("test this key"). Main-app Settings, not Beast Mode's.
 */
@Composable
fun AiProvidersSettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AiProvidersViewModel = hiltViewModel()
) {
    val states by viewModel.providerStates.collectAsStateWithLifecycle()
    val testingProvider by viewModel.testingProvider.collectAsStateWithLifecycle()
    val testResults by viewModel.testResults.collectAsStateWithLifecycle()
    val modelLists by viewModel.modelLists.collectAsStateWithLifecycle()
    val usingFallbackStorage by viewModel.isUsingFallbackStorage.collectAsStateWithLifecycle()
    val isBusyWithKeysFile by viewModel.isBusyWithKeysFile.collectAsStateWithLifecycle()
    val keysFileResult by viewModel.keysFileResult.collectAsStateWithLifecycle()
    var modelPickerFor by remember { mutableStateOf<AiProviderId?>(null) }
    val haptics = com.daybook.app.ui.theme.rememberDaybookHaptics()

    // User request ("export API keys and import them via JSON — that way it never hits the
    // cloud") — same confirm-first-then-pick order SettingsScreen's own JSON import uses.
    val openKeysFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importApiKeysFromUri(it) } }
    var confirmImportKeys by remember { mutableStateOf(false) }
    if (confirmImportKeys) {
        DaybookAlertDialog(
            onDismissRequest = { confirmImportKeys = false },
            title = "Import API keys?",
            text = {
                Text(
                    "This overwrites any key already saved for a provider named in the file. " +
                        "Providers not in the file are left untouched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DaybookColors.TextMuted
                )
            },
            confirmLabel = "Choose file",
            onConfirm = {
                confirmImportKeys = false
                openKeysFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmImportKeys = false },
            destructive = false
        )
    }

    SettingsSubScreen("AI Providers", onNavigateBack) {
        // H1 — a corrupted/unavailable Keystore made AiKeyStore fall back from encrypted to
        // plaintext storage; previously only a `Log.w`, now surfaced here too.
        if (usingFallbackStorage) {
            SoftCard(
                tint = CardTints.Butter,
                borderColor = DaybookColors.Warning.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Your device's secure storage is unavailable — API keys below are being " +
                        "saved unencrypted on this device.",
                    style = DaybookText.CardSubtitle,
                    color = CardTints.Butter.onFill
                )
            }
            Spacer(Modifier.height(Spacing.listGap))
        }
        SectionHeader(
            "API keys",
            subtitle = "Used only for the Daily Report's AI summary (§3.5) — the static report " +
                "never needs any of this. Keys are stored on this device only and never synced " +
                "to the cloud or included in the main Daybook backup — the separate export below " +
                "is the only way they ever leave this device."
        )
        AiProviderId.entries.forEach { id ->
            val state = states[id]
            var keyDraft by remember(id, state?.hasKey) { mutableStateOf(state?.apiKey.orEmpty()) }
            var modelDraft by remember(id, state) { mutableStateOf(state?.model.orEmpty()) }
            var revealed by remember(id) { mutableStateOf(false) }
            var editingKey by remember(id, state?.hasKey) { mutableStateOf(state?.hasKey != true) }

            FormGroup(title = id.label) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editingKey) {
                        DaybookTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            label = "API key",
                            placeholder = "Paste your key",
                            singleLine = true
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (revealed) state?.apiKey.orEmpty()
                                else viewModel.maskedKey(state?.apiKey) ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = DaybookColors.TextPrimary
                            )
                            TextLink(
                                text = if (revealed) "Hide" else "Reveal",
                                onClick = { revealed = !revealed }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (editingKey) {
                            PrimaryButton(
                                text = "Save key",
                                onClick = {
                                    viewModel.setApiKey(id, keyDraft)
                                    editingKey = false
                                    revealed = false
                                },
                                enabled = keyDraft.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                            if (state?.hasKey == true) {
                                GhostButton(
                                    text = "Cancel",
                                    onClick = {
                                        keyDraft = state.apiKey.orEmpty()
                                        editingKey = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            GhostButton(
                                text = "Change key",
                                onClick = { editingKey = true },
                                modifier = Modifier.weight(1f)
                            )
                            GhostButton(
                                text = "Remove",
                                onClick = {
                                    viewModel.setApiKey(id, null)
                                    keyDraft = ""
                                    editingKey = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Feature 1 — once "Test" has successfully listed models, the picker is the
                    // primary way to set the model; the free-text field stays as a fallback/
                    // override (e.g. a brand-new model id not yet in the list, or a listing
                    // failure) so the user is never blocked from typing one manually.
                    val models = modelLists[id]
                    if (!models.isNullOrEmpty()) {
                        GhostButton(
                            text = if (modelDraft.isBlank()) "Choose a model (${models.size} found)" else modelDraft,
                            onClick = { modelPickerFor = id }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    DaybookTextField(
                        value = modelDraft,
                        onValueChange = {
                            modelDraft = it
                            viewModel.setModel(id, it)
                        },
                        label = if (models.isNullOrEmpty()) "Model" else "Model (manual override)",
                        placeholder = id.modelPlaceholder,
                        singleLine = true,
                        supportingText = "A blank model can't be used to generate a summary — this " +
                            "is a hint, not a default."
                    )

                    val isTesting = testingProvider == id
                    // Polish pass — a light confirm haptic when the key checks out; per the round's
                    // spec, a "reject" haptic on failure is skipped since this Compose version
                    // (ui 1.7.6) doesn't expose HapticFeedbackType.Reject.
                    LaunchedEffect(testResults[id]) {
                        if (testResults[id]?.startsWith("Key verified") == true) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                    GhostButton(
                        text = if (isTesting) "Testing…" else "Test this key",
                        onClick = {
                            val keyToTest = if (editingKey) keyDraft else state?.apiKey.orEmpty()
                            viewModel.testKey(id, keyToTest, modelDraft)
                        },
                        loading = isTesting,
                        enabled = !isTesting && (if (editingKey) keyDraft.isNotBlank() else state?.hasKey == true)
                    )
                    Box(Modifier.fillMaxWidth().heightIn(min = 20.dp)) {
                        val msg = testResults[id]
                        if (msg != null) {
                            Text(
                                msg,
                                style = DaybookText.Caption,
                                color = if (msg.startsWith("Key verified")) DaybookColors.Success else DaybookColors.Danger
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.listGap))
        }

        // User request ("export API keys and import them via JSON — that way it never hits the
        // cloud") — a completely separate flow from the main Daybook backup/restore section
        // (Settings → Backup & data) and from cloud sync: see ui/settings/ApiKeysExport.kt.
        SectionHeader(
            "Export / import API keys",
            subtitle = "Save your keys to a JSON file, or load them back in on another device. " +
                "This file is separate from your Daybook backup and is never uploaded anywhere."
        )
        FormGroup(title = null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.listGap)) {
                Text(
                    "Keys are saved to a plain file — anyone with the file can read them. Keep it " +
                        "somewhere private.",
                    style = DaybookText.Caption,
                    color = DaybookColors.Warning
                )
                GhostButton(
                    text = if (isBusyWithKeysFile) "Working…" else "Export API keys",
                    onClick = { viewModel.exportApiKeys() },
                    enabled = !isBusyWithKeysFile,
                    modifier = Modifier.fillMaxWidth()
                )
                GhostButton(
                    text = if (isBusyWithKeysFile) "Working…" else "Import API keys",
                    onClick = { confirmImportKeys = true },
                    enabled = !isBusyWithKeysFile,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(Modifier.fillMaxWidth().heightIn(min = 20.dp)) {
                    val msg = keysFileResult
                    if (msg != null) {
                        val ok = msg.startsWith("Exported ") || msg.startsWith("Imported ")
                        Text(
                            msg,
                            style = DaybookText.Caption,
                            color = if (ok) DaybookColors.Success else DaybookColors.Danger
                        )
                    }
                }
            }
        }

        val pickerFor = modelPickerFor
        BottomSheetMenu(
            visible = pickerFor != null,
            onDismiss = { modelPickerFor = null },
            actions = (pickerFor?.let { modelLists[it] } ?: emptyList()).map { m ->
                SheetAction(
                    icon = DaybookIcons.Bolt,
                    label = m.id,
                    description = when (m.free) {
                        true -> "Free"
                        false -> "Paid"
                        null -> null
                    },
                    onClick = {
                        pickerFor?.let { viewModel.selectModel(it, m.id) }
                        modelPickerFor = null
                    }
                )
            }
        )
    }
}
