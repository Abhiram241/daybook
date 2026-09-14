package com.daybook.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.health.HealthConnectAvailability
import com.daybook.app.data.health.HealthConnectHydrationWriter
import com.daybook.app.data.health.HealthConnectSdkState
import com.daybook.app.data.health.HealthPermissions
import com.daybook.app.data.hydration.HydrationRepository
import com.daybook.app.data.hydration.HydrationUnits
import com.daybook.app.data.model.AppSettings
import com.daybook.app.ui.components.FormGroup
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.UnitDropdown
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HydrationSettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val hydrationRepository: HydrationRepository,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {
    val settings = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as AppSettings?)

    private val _hcWriteGranted = MutableStateFlow(false)
    val hcWriteGranted = _hcWriteGranted.asStateFlow()

    val healthConnectAvailable: Boolean
        get() = HealthConnectAvailability.state(appContext) == HealthConnectSdkState.AVAILABLE

    fun refreshPermission() = safeLaunch { _hcWriteGranted.value = hydrationRepository.hasHealthConnectWrite() }

    fun setEnabled(enabled: Boolean) = safeLaunch { settingsRepository.setHydrationEnabled(enabled) }
    fun setUnit(unit: String) = safeLaunch { settingsRepository.setHydrationUnit(unit) }
    fun setGoalMl(ml: Int) = safeLaunch { settingsRepository.setHydrationGoalMl(ml) }
}

/**
 * Settings > Reminders & notifications > Hydration. Turning the habit on asks for Health Connect
 * "write hydration" access (the water is still saved in Daybook if that's denied). No reminders —
 * the habit is logged from its card on Today.
 */
@Composable
fun HydrationSettingsSection(viewModel: HydrationSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val granted by viewModel.hcWriteGranted.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { viewModel.refreshPermission(); onPauseOrDispose { } }
    val permissionLauncher = rememberLauncherForActivityResult(HealthPermissions.requestPermissionsContract()) {
        viewModel.refreshPermission()
    }
    val s = settings ?: return

    Spacer(Modifier.height(Spacing.listGap))
    SectionHeader(
        "Hydration",
        subtitle = "A daily water habit on Today. It counts as done once you reach your goal."
    )
    FormGroup(title = null) {
        SettingsToggleRow(
            label = "Track hydration",
            checked = s.hydrationEnabled,
            onCheckedChange = { on ->
                viewModel.setEnabled(on)
                if (on && viewModel.healthConnectAvailable && !granted) {
                    runCatching { permissionLauncher.launch(setOf(HealthConnectHydrationWriter.WRITE_PERMISSION)) }
                }
            }
        )
        if (s.hydrationEnabled) {
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
            Spacer(Modifier.height(Spacing.md))

            Text("Daily goal", style = DaybookText.Caption, color = DaybookColors.TextMuted)
            Spacer(Modifier.height(6.dp))
            var goalText by rememberSaveable(s.hydrationUnit) {
                mutableStateOf(HydrationUnits.inputValue(s.hydrationGoalMl, s.hydrationUnit))
            }
            LaunchedEffect(s.hydrationGoalMl, s.hydrationUnit) {
                goalText = HydrationUnits.inputValue(s.hydrationGoalMl, s.hydrationUnit)
            }
            val goalMl = HydrationUnits.toMl(goalText, s.hydrationUnit)
            val dirty = goalMl != null && goalMl != s.hydrationGoalMl
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                com.daybook.app.ui.components.DaybookTextField(
                    value = goalText,
                    onValueChange = { goalText = it.take(6) },
                    label = null,
                    placeholder = if (s.hydrationUnit == HydrationUnits.L) "2" else "2000",
                    isError = goalText.isNotBlank() && goalMl == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                UnitDropdown(
                    options = listOf(HydrationUnits.ML to "ml", HydrationUnits.L to "L"),
                    selectedKey = s.hydrationUnit,
                    onSelect = viewModel::setUnit
                )
                PrimaryButton(
                    text = "Save",
                    onClick = { goalMl?.let(viewModel::setGoalMl) },
                    enabled = dirty,
                    modifier = Modifier.width(88.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "The ml / L choice is also used on the Today card.",
                style = DaybookText.Caption,
                color = DaybookColors.TextMuted
            )

            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Write to Health Connect", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
                    Text(
                        when {
                            !viewModel.healthConnectAvailable -> "Health Connect isn't available on this phone. Water is still saved in Daybook."
                            granted -> "Allowed · each day's amount is copied to Health Connect"
                            else -> "Not allowed · water is only saved in Daybook"
                        },
                        style = DaybookText.Metadata,
                        color = if (granted) DaybookColors.Success else DaybookColors.Warning
                    )
                }
                if (viewModel.healthConnectAvailable && !granted) {
                    Spacer(Modifier.width(12.dp))
                    PrimaryButton(
                        text = "Allow",
                        onClick = {
                            runCatching { permissionLauncher.launch(setOf(HealthConnectHydrationWriter.WRITE_PERMISSION)) }
                        },
                        modifier = Modifier.width(88.dp)
                    )
                }
            }
        }
    }
}
