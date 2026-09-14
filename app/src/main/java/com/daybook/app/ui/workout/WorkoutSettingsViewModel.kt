package com.daybook.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.ExportImportRepository
import com.daybook.app.data.HealthRepository
import com.daybook.app.data.health.HealthPermissions
import com.daybook.app.data.model.AppSettings
import com.daybook.app.data.sync.CloudSyncRepository
import com.daybook.app.data.workout.WorkoutFontPrefs
import com.daybook.app.ui.theme.FontChoice
import com.daybook.app.util.StorageUtils
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A7 (§3.8.2) — Beast Mode's own settings screen. Four preferences, its own font choice, and a
 *  door. Round B (§7.4) adds one more `SettingsGroup`: Health Connect connect/disconnect, status,
 *  refresh, import-past-data, which-data-shared — plus a `Backup & data` sub-group for the Beast
 *  Mode JSON import (§7.5.1). */
@HiltViewModel
class WorkoutSettingsViewModel @Inject constructor(
    private val repo: AppSettingsRepository,
    private val fontPrefs: WorkoutFontPrefs,
    private val healthRepository: HealthRepository,
    private val exportImportRepository: ExportImportRepository,
    private val storageUtils: StorageUtils,
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    val settings = repo.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** `null` = "match the app's own font choice" (still bold — see [WorkoutFontPrefs]'s KDoc). */
    val fontChoice = fontPrefs.fontChoice

    fun setWeightUnit(v: String) = safeLaunch { repo.setWeightUnit(v) }
    fun setWorkoutAccentColor(v: String) = safeLaunch { repo.setWorkoutAccentColor(v) }
    fun setRestTimerDefaultSeconds(v: Int) = safeLaunch { repo.setRestTimerDefaultSeconds(v) }
    fun setShowOnToday(v: Boolean) = safeLaunch { repo.setWorkoutTodayCardEnabled(v) }
    /** Feature addition (post-A6) — the Add-Exercise picker's default active filter chip. */
    fun setDefaultExerciseGroup(v: String?) = safeLaunch { repo.setDefaultExerciseGroup(v) }
    fun setFontChoice(choice: FontChoice?) = fontPrefs.setFontChoice(choice)

    // ------------------------------------------------------------------- Round B (Health Connect)

    private val _healthRefreshTick = MutableStateFlow(0)
    private val _healthGrantedCount = MutableStateFlow(0)
    private val _healthMissingLabels = MutableStateFlow<List<String>>(emptyList())
    private val _healthActionResult = MutableStateFlow<String?>(null)
    val healthActionResult = _healthActionResult.asStateFlow()
    // M5 fix — this used to be read straight off the repository's global last-PULL outcome
    // (`healthStatusIsFailure()`) to colour whatever the LAST ACTION's caption said, so e.g.
    // `importPastData()`'s `PermissionsMissing` message rendered green whenever the last
    // background pull happened to succeed, and a genuine success rendered red whenever it hadn't.
    // Now tracked per-action, right alongside `_healthActionResult`.
    private val _healthActionIsFailure = MutableStateFlow(false)
    val healthActionIsFailure = _healthActionIsFailure.asStateFlow()
    private val _healthIsBusy = MutableStateFlow(false)
    val healthIsBusy = _healthIsBusy.asStateFlow()

    // M5 fix — `healthStatusLine()`/`healthStatusIsFailure()` used to be plain functions called
    // straight out of composition (`WorkoutSettingsScreen.kt`), reading `SharedPreferences`
    // on the composition thread and never triggering recomposition on their own — the Status row
    // only ever refreshed as a side effect of some OTHER collected flow emitting, so a background
    // pull failure while the screen was open left it showing the previous success indefinitely.
    // Now genuine observable `StateFlow`s, refreshed on the same ticks
    // `refreshHealthPermissionState()` already runs on (init, post-refresh, post-permission-flow).
    private val _healthStatusLine = MutableStateFlow<String?>(null)
    val healthStatusLine = _healthStatusLine.asStateFlow()
    private val _healthStatusIsFailure = MutableStateFlow(false)
    val healthStatusIsFailure = _healthStatusIsFailure.asStateFlow()

    val healthGrantedCount = _healthGrantedCount.asStateFlow()
    val healthMissingLabels = _healthMissingLabels.asStateFlow()

    init {
        refreshHealthPermissionState()
    }

    fun refreshHealthPermissionState() {
        safeLaunch {
            val granted = healthRepository.grantedPermissions()
            _healthGrantedCount.value = granted.size
            _healthMissingLabels.value = healthRepository.missingPermissions(granted)
                .mapNotNull { HealthPermissions.LABELS[it] }.sorted()
            _healthStatusLine.value = healthRepository.statusLine()
            _healthStatusIsFailure.value = healthRepository.statusIsFailure()
        }
    }

    fun initialHealthPermissionSet() = HealthPermissions.initialRequestSet()
    fun requestHealthPermissionsContract() = HealthPermissions.requestPermissionsContract()
    fun optionalHealthExtras() = HealthPermissions.optionalExtras()

    /** H6 fix — `optionalExtras()` was defined and exposed but had no call site anywhere, so
     *  `READ_HEALTH_DATA_HISTORY`/`READ_HEALTH_DATA_IN_BACKGROUND` were never actually requested
     *  from the OS. "Import my past data" now launches this consent sheet first (at most once,
     *  per `historyExtrasAlreadyRequested`) before running the import. */
    fun historyExtrasAlreadyRequested(): Boolean = healthRepository.historyExtrasAlreadyRequested()

    fun onExtrasPermissionFlowFinished() {
        healthRepository.markHistoryExtrasRequested()
        importHealthPastData()
    }

    fun refreshHealthNow() {
        safeLaunch {
            _healthIsBusy.value = true
            _healthActionResult.value = null
            _healthActionIsFailure.value = false
            try {
                when (val r = healthRepository.refreshNow()) {
                    is HealthRepository.PullOutcome.Success -> {
                        _healthActionResult.value = if (r.hasNewData) "Last updated just now." else "Up to date — nothing new from your band."
                        _healthActionIsFailure.value = false
                    }
                    is HealthRepository.PullOutcome.Failure -> {
                        _healthActionResult.value = r.message
                        _healthActionIsFailure.value = true
                    }
                    // H5 fix — used to blank the message; `pull()` also never called `setStatus`
                    // for this outcome, so the persisted Status row kept implying success days
                    // after access was revoked. `pull()` now sets it too; this mirrors the same
                    // copy so "Refresh now"'s own result caption isn't left blank.
                    HealthRepository.PullOutcome.PermissionsMissing -> {
                        _healthActionResult.value = "Daybook no longer has access to your health data. Tap Connect to share it again."
                        _healthActionIsFailure.value = true
                    }
                }
            } finally {
                _healthIsBusy.value = false
                refreshHealthPermissionState()
            }
        }
    }

    fun importHealthPastData() {
        safeLaunch {
            _healthIsBusy.value = true
            _healthActionResult.value = null
            _healthActionIsFailure.value = false
            try {
                when (val r = healthRepository.importPastData()) {
                    is HealthRepository.PullOutcome.Success -> {
                        _healthActionResult.value = "Import complete."
                        _healthActionIsFailure.value = false
                    }
                    is HealthRepository.PullOutcome.Failure -> {
                        _healthActionResult.value = r.message
                        _healthActionIsFailure.value = true
                    }
                    HealthRepository.PullOutcome.PermissionsMissing -> {
                        _healthActionResult.value = "Daybook can only see the last 30 days without access to your history."
                        _healthActionIsFailure.value = true
                    }
                }
            } finally {
                _healthIsBusy.value = false
                refreshHealthPermissionState()
            }
        }
    }

    fun onHealthPermissionFlowFinished(grantedOrEmpty: Set<String>) {
        if (grantedOrEmpty.isEmpty()) {
            _healthActionResult.value =
                "No health data was shared. You can tap Connect again any time, or choose which " +
                    "data to share in the Health Connect app."
        }
        refreshHealthPermissionState()
        refreshHealthNow()
    }

    // -------------------------------------------------------------- B5a: Beast Mode JSON import

    private val _beastImportResult = MutableStateFlow<String?>(null)
    val beastImportResult = _beastImportResult.asStateFlow()
    private val _isImportingBeast = MutableStateFlow(false)
    val isImportingBeast = _isImportingBeast.asStateFlow()

    fun importBeastModeFromUri(uri: android.net.Uri) {
        safeLaunch {
            _isImportingBeast.value = true
            _beastImportResult.value = null
            try {
                val size = storageUtils.fileSizeBytes(uri)
                if (size != null && size > 10 * 1024 * 1024) {
                    _beastImportResult.value = "That file is too large to be a Daybook backup"
                    return@safeLaunch
                }
                val json = storageUtils.readText(uri)
                if (json.isNullOrBlank()) {
                    _beastImportResult.value = "Could not read the selected file"
                    return@safeLaunch
                }
                val result = exportImportRepository.importBeastModeBackup(json)
                _beastImportResult.value = if (result.success) {
                    runCatching { cloudSyncRepository.onLocalDataReplaced(result.coveredMonths) }
                    "Import successful: ${result.message ?: "Beast Mode data imported"}"
                } else {
                    "Import failed: ${result.message}"
                }
            } catch (t: Throwable) {
                _beastImportResult.value = "Import failed: ${t.message}"
            } finally {
                _isImportingBeast.value = false
            }
        }
    }
}
