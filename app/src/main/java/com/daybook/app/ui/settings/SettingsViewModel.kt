package com.daybook.app.ui.settings

import com.daybook.app.util.safeLaunch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.ExportImportRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.ProfilePhotoStore
import com.daybook.app.data.model.AppSettings
import com.daybook.app.data.sync.CloudSyncRepository
import com.daybook.app.data.sync.HydrateResult
import com.daybook.app.ui.theme.FontChoice
import com.daybook.app.util.CrashHandler
import com.daybook.app.util.JsonUtils
import com.daybook.app.util.StorageUtils
import com.daybook.app.util.notification.NotificationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val exportImportRepository: ExportImportRepository,
    private val storageUtils: StorageUtils,
    private val notificationUtils: NotificationUtils,
    private val occurrenceScheduler: OccurrenceScheduler,
    private val profilePhotoStore: ProfilePhotoStore,
    private val cloudSync: CloudSyncRepository,
    private val jsonUtils: JsonUtils,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 0a — retrieval side of the local crash logger.
    // `hasCrashLog()` is read once per composition (the file only appears after a real crash,
    // never mid-session for a running process), `crashLogText()` is read on-demand when the user
    // taps "Copy crash log" so we never hold a potentially-256KB string in memory otherwise.
    fun hasCrashLog(): Boolean = CrashHandler.crashLogFile(appContext).exists()

    fun crashLogText(): String? =
        runCatching { CrashHandler.crashLogFile(appContext).takeIf { it.exists() }?.readText() }
            .getOrNull()

    /** Null when notifications can actually be posted; otherwise why they can't. */
    fun notificationBlockReason(): String? = notificationUtils.notificationBlockReason()

    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 15 (N-10): surfaces postTestNotification's block
    // reason to the UI instead of the old bare Log.w-only no-op.
    private val _testNotificationResult = MutableStateFlow<String?>(null)
    val testNotificationResult: StateFlow<String?> = _testNotificationResult.asStateFlow()

    /** Posts a notification right now, bypassing the alarm pipeline entirely. */
    fun sendTestNotification() {
        notificationUtils.createNotificationChannels()
        _testNotificationResult.value = notificationUtils.postTestNotification()
            ?: "Test notification sent"
    }

    /** Regenerates the occurrence window and re-arms every alarm. */
    fun resyncReminders() {
        safeLaunch(Dispatchers.IO) {
            runCatching { occurrenceScheduler.syncAll() }
        }
    }

    /** Room-backed; both this screen and MainActivity observe the same stream. */
    val settings: StateFlow<AppSettings?> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // v0.5.2 SD-g: app-wide BATCH habit check-in time. Device-local — not synced, not backed up.
    val habitCheckinTime: StateFlow<String> =
        settingsRepository.observeSettings().map { it.habitCheckinTime }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "21:00")

    fun setHabitCheckinTime(t: LocalTime) {
        safeLaunch {
            settingsRepository.setHabitCheckinTime(t.format(DateTimeFormatter.ofPattern("HH:mm")))
            // Rewrites the BATCH occurrence rows at the new time AND re-arms the check-in alarm.
            runCatching { occurrenceScheduler.syncAll() }
        }
    }

    // v0.5.1 §M: `daysSinceLastBackup` lived here to caption the monthly-backup-reminder row.
    // Both the row and the flow are gone. v0.5.2 D2: `last_backup_export_at` was write-only, so
    // the export path no longer records it and MIGRATION_11_12 drops the column.

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // v0.5.3 Phase 6 (D2): (done, total) while a signed-in date-range export hydrates the cloud
    // months it spans; null when no hydration is running.
    private val _hydrateProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val hydrateProgress: StateFlow<Pair<Int, Int>?> = _hydrateProgress.asStateFlow()

    // ---- Profile photo (L5). UI: pick a Uri with ActivityResultContracts.PickVisualMedia and
    // hand it to onProfilePhotoPicked; render `profilePhotoPath` with Coil AsyncImage(File(path)).
    /** Absolute path of the stored profile photo, or null when none is set. */
    val profilePhotoPath: StateFlow<String?> = settingsRepository.observeSettings()
        .map { it.profilePhotoPath }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _photoError = MutableStateFlow<String?>(null)
    /** Non-null when the last pick could not be read/decoded; UI may show it and then ignore it. */
    val photoError: StateFlow<String?> = _photoError.asStateFlow()

    fun onProfilePhotoPicked(uri: android.net.Uri) {
        safeLaunch(Dispatchers.IO) {
            _photoError.value = null
            android.util.Log.i("ProfilePhoto", "picked uri=$uri")
            try {
                val path = profilePhotoStore.save(uri)
                settingsRepository.setProfilePhotoPath(path)
                android.util.Log.i("ProfilePhoto", "persisted path=$path")
            } catch (e: Exception) {
                android.util.Log.w("ProfilePhoto", "save failed for $uri", e)
                _photoError.value = "Couldn't use that photo: ${e.message}"
            }
        }
    }

    fun onRemoveProfilePhoto() {
        safeLaunch(Dispatchers.IO) {
            _photoError.value = null
            runCatching { profilePhotoStore.clear() }
            settingsRepository.setProfilePhotoPath(null)
        }
    }

    // Editable name draft: seeded once from the first persisted value and never recreated by a
    // DB echo, so the debounced save can't drop keystrokes typed during the round trip (REV-30).
    private val _nameDraft = MutableStateFlow<String?>(null)
    val nameDraft: StateFlow<String?> = _nameDraft.asStateFlow()

    init {
        safeLaunch {
            val first = settingsRepository.observeSettings().first()
            if (_nameDraft.value == null) _nameDraft.value = first.userName
        }
    }

    fun onNameDraftChange(value: String) { _nameDraft.value = value }

    /** Debounced by the screen; writes only the user_name column (REV-25). */
    fun commitName() {
        val value = _nameDraft.value?.trim() ?: return
        safeLaunch { settingsRepository.setUserName(value) }
    }

    fun setAccentColor(storageKey: String) {
        safeLaunch { settingsRepository.setAccentColor(storageKey) }
    }

    /** App-wide typeface. The theme is reactive (Section 1.3) so the whole app restyles at once. */
    val fontChoice: StateFlow<FontChoice> = settingsRepository.observeSettings()
        .map { FontChoice.fromKeyOrDefault(it.fontChoice) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FontChoice.DEFAULT)

    fun setFontChoice(key: String) {
        safeLaunch { settingsRepository.setFontChoice(key) }
    }

    // ---------------------------------------------------------------- Customization round (DB v16)
    // All device-local. Pure-display prefs (rec 1/2/6/7) need NO syncAll(); only the quiet-hours
    // and default-snooze setters below re-run syncAll() so armed alarms re-evaluate immediately.

    private fun <T> col(sel: (AppSettings) -> T, initial: T): StateFlow<T> =
        settingsRepository.observeSettings().map(sel)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // rec 1 — Today & calendar
    val weekStart: StateFlow<String> = col({ it.weekStart }, "MONDAY")
    val clock24h: StateFlow<Boolean> = col({ it.clock24h }, false)
    val calendarDefaultExpanded: StateFlow<Boolean> = col({ it.calendarDefaultExpanded }, false)
    fun setWeekStart(v: String) { safeLaunch { settingsRepository.setWeekStart(v) } }
    fun setClock24h(v: Boolean) { safeLaunch { settingsRepository.setClock24h(v) } }
    fun setCalendarDefaultExpanded(v: Boolean) { safeLaunch { settingsRepository.setCalendarDefaultExpanded(v) } }

    // rec 2 — greeting
    val greetingTone: StateFlow<String> = col({ it.greetingTone }, "WARM")
    val greetingTimeWord: StateFlow<Boolean> = col({ it.greetingTimeWord }, true)
    val heroStyle: StateFlow<String> = col({ it.heroStyle }, "COUNT_LEFT")
    fun setGreetingTone(v: String) { safeLaunch { settingsRepository.setGreetingTone(v) } }
    fun setGreetingTimeWord(v: Boolean) { safeLaunch { settingsRepository.setGreetingTimeWord(v) } }
    fun setHeroStyle(v: String) { safeLaunch { settingsRepository.setHeroStyle(v) } }

    // rec 3 — hide-resolved default (list sort / show-archived have no Settings row by design)
    val homeHideResolved: StateFlow<Boolean> = col({ it.homeHideResolved }, false)
    fun setHomeHideResolved(v: Boolean) { safeLaunch { settingsRepository.setHomeHideResolved(v) } }

    // rec 3 (N2) — default snooze. Re-arm so the batch check-in picks up the new interval.
    val defaultSnoozeMinutes: StateFlow<Int> = col({ it.defaultSnoozeMinutes }, 10)
    fun setDefaultSnoozeMinutes(v: Int) {
        safeLaunch {
            settingsRepository.setDefaultSnoozeMinutes(v.coerceIn(5, 120))
            runCatching { occurrenceScheduler.syncAll() }
        }
    }

    // rec 4 — reduce motion
    val reduceMotion: StateFlow<Boolean> = col({ it.reduceMotion }, false)
    fun setReduceMotion(v: Boolean) { safeLaunch { settingsRepository.setReduceMotion(v) } }

    // "Check for updates" toggle round — gates whether MainActivity.onResume() calls
    // InAppUpdateChecker at all. Auto-flipped off by MainActivity on an explicit sign-in decline;
    // this is the manual on/off switch in Settings.
    val checkForUpdatesEnabled: StateFlow<Boolean> = col({ it.checkForUpdatesEnabled }, true)
    fun setCheckForUpdatesEnabled(v: Boolean) { safeLaunch { settingsRepository.setCheckForUpdatesEnabled(v) } }

    // rec 5 — quiet hours. Re-arm so held/deferred alarms are recomputed.
    val quietHoursEnabled: StateFlow<Boolean> = col({ it.quietHoursEnabled }, false)
    val quietStart: StateFlow<String> = col({ it.quietStart }, "22:00")
    val quietEnd: StateFlow<String> = col({ it.quietEnd }, "07:00")
    private fun reArm() = safeLaunch { runCatching { occurrenceScheduler.syncAll() } }
    fun setQuietHoursEnabled(v: Boolean) { safeLaunch { settingsRepository.setQuietHoursEnabled(v); reArm() } }
    fun setQuietStart(t: LocalTime) { safeLaunch { settingsRepository.setQuietStart(t.format(DateTimeFormatter.ofPattern("HH:mm"))); reArm() } }
    fun setQuietEnd(t: LocalTime) { safeLaunch { settingsRepository.setQuietEnd(t.format(DateTimeFormatter.ofPattern("HH:mm"))); reArm() } }

    // rec 6 — streak display
    val streakMode: StateFlow<String> = col({ it.streakMode }, "STRICT")
    val showStreaks: StateFlow<Boolean> = col({ it.showStreaks }, true)
    val streakRestDays: StateFlow<String> = col({ it.streakRestDays }, "")
    fun setStreakMode(v: String) { safeLaunch { settingsRepository.setStreakMode(v) } }
    fun setShowStreaks(v: Boolean) { safeLaunch { settingsRepository.setShowStreaks(v) } }
    fun setStreakRestDays(v: String) { safeLaunch { settingsRepository.setStreakRestDays(v) } }

    // rec 7 — navigation
    val navTabs: StateFlow<String> = col({ it.navTabs }, "home,routines,foodmed")
    val defaultLandingTab: StateFlow<String> = col({ it.defaultLandingTab }, "home")
    fun setNavTabs(v: String) { safeLaunch { settingsRepository.setNavTabs(v) } }
    fun setDefaultLandingTab(v: String) { safeLaunch { settingsRepository.setDefaultLandingTab(v) } }

    /**
     * v0.5.3 Phase 6 (D2): export just `[start, end]` as a range-scoped v2 JSON file.
     *
     * For a signed-in account this first hydrates every cloud month the range spans (older months
     * may have been evicted from Room). If any month can't be reached the export is **aborted with
     * a clear message and no file written** — never a silently-truncated backup. A signed-out
     * account skips straight to the file write (all its data is already local).
     */
    fun exportRange(start: LocalDate, end: LocalDate) {
        safeLaunch {
            _isExporting.value = true
            _exportResult.value = null
            _importResult.value = null
            _hydrateProgress.value = null
            val lo = if (start.isAfter(end)) end else start
            val hi = if (start.isAfter(end)) start else end
            try {
                val startMonth = YearMonth.from(lo).toString()
                val endMonth = YearMonth.from(hi).toString()
                when (val r = cloudSync.hydrateRange(startMonth, endMonth) { done, total ->
                    _hydrateProgress.value = done to total
                }) {
                    is HydrateResult.Offline -> {
                        _exportResult.value =
                            "Couldn't reach the cloud for ${r.month}. Connect to the internet and try again."
                        return@safeLaunch
                    }
                    HydrateResult.NoAccount, HydrateResult.Ok -> { /* proceed to the file write */ }
                }
                val backup = exportImportRepository.exportRange(lo, hi)
                val json = jsonUtils.encode(backup)
                val location = storageUtils.saveExport(json, backup.meta.rangeStart, backup.meta.rangeEnd)
                _exportResult.value =
                    "Exported ${backup.days.size} days ($lo – $hi) to $location"
            } catch (t: Throwable) {
                // Phase 13 (C-14): Throwable, not Exception — see importFromUri's identical note.
                _exportResult.value = "Export failed: ${t.message}"
            } finally {
                cloudSync.endRangeExport()
                _hydrateProgress.value = null
                _isExporting.value = false
            }
        }
    }

    /** Shares the most recent export file via the system share sheet. */
    fun shareLatestExport(onNoFile: () -> Unit, onShare: (android.net.Uri) -> Unit) {
        safeLaunch {
            try {
                val json = exportImportRepository.exportAllData()
                val uri = storageUtils.writeShareFile(json)
                onShare(uri)
            } catch (t: Throwable) {
                // Phase 13 (C-14): Throwable, not Exception — see importFromUri's identical note.
                _exportResult.value = "Share failed: ${t.message}"
                onNoFile()
            }
        }
    }

    fun importFromUri(uri: android.net.Uri) {
        safeLaunch {
            _isImporting.value = true
            _importResult.value = null
            try {
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 13 (C-14, Medium): a legitimate multi-year
                // backup is well under 1MB (the Firestore 1MiB-per-month-doc bound cited elsewhere
                // in the audit puts a hard ceiling on what a real export can contain) — refuse
                // anything over 10MB before ever reading it, rather than risking an uncatchable OOM
                // trying to load a huge file into a single String.
                val size = storageUtils.fileSizeBytes(uri)
                if (size != null && size > MAX_IMPORT_BYTES) {
                    _importResult.value = "That file is too large to be a Daybook backup"
                    return@safeLaunch
                }
                val json = storageUtils.readText(uri)
                if (json.isNullOrBlank()) {
                    _importResult.value = "Could not read the selected file"
                    return@safeLaunch
                }
                val result = exportImportRepository.importAllData(json)
                _importResult.value = if (result.success) {
                    // v0.5.3 Phase 6 (S5): pin the sync bookkeeping to exactly the months this file
                    // covered, so the next push's changedMonths diff can't phantom-delete a cloud
                    // month that simply wasn't in the file (range files especially).
                    runCatching { cloudSync.onLocalDataReplaced(result.coveredMonths) }
                    // Restored rows are inert until something re-arms their alarms (REV-13).
                    runCatching { occurrenceScheduler.syncAll() }
                    "Import successful: ${result.message ?: "Data imported"}"
                } else {
                    "Import failed: ${result.message}"
                }
            } catch (t: Throwable) {
                // Phase 13 (C-14): was `catch (e: Exception)` — a slipped-through OutOfMemoryError
                // (an `Error`, not an `Exception`) used to propagate past this handler entirely
                // instead of surfacing as a message. The size check above should make this rare in
                // practice; this is the belt-and-braces backstop for whatever it doesn't catch.
                _importResult.value = "Import failed: ${t.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    private companion object {
        /** LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 13 (C-14). */
        const val MAX_IMPORT_BYTES = 10L * 1024 * 1024
    }
}
