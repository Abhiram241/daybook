package com.daybook.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.hydration.HydrationIntakeSync
import com.daybook.app.data.sync.CloudSyncRepository
import com.daybook.app.util.CrashHandler
import com.daybook.app.util.notification.NotificationUtils
import com.daybook.app.util.work.WindowRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class DaybookApplication : Application(), Configuration.Provider {

    @Inject lateinit var notificationUtils: NotificationUtils
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cloudSyncRepository: CloudSyncRepository
    @Inject lateinit var workoutRepository: WorkoutRepository
    @Inject lateinit var hydrationIntakeSync: HydrationIntakeSync
    @Inject lateinit var settingsRepository: AppSettingsRepository

    // Item 6 fix #1 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — no GlobalScope anywhere in this app
    // (a standing rule, verified by AUDIT_2026-09-16.md's battery section); a dedicated
    // SupervisorJob-backed scope mirrors the shape CloudSyncRepository/AuthRepository already use.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Hilt-injected WorkerFactory so WindowRefreshWorker can take OccurrenceScheduler as a ctor arg.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Install first so any crash from this point on (including during the rest of onCreate)
        // is captured to internal storage for later retrieval (LOGIN_REDESIGN_RISK_FIX_PLAN Phase 0a).
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        // Ensure notification channels exist before the first alarm fires (even after a
        // process restart with no Activity yet).
        notificationUtils.createNotificationChannels()
        // Keep the rolling reminder window topped up even if the app is never opened (REV-06).
        WindowRefreshWorker.enqueue(this)
        // Start observing auth state so cloud sync attaches/detaches with sign-in. Non-blocking:
        // it only launches coroutines and reads the persisted session from local disk (R9).
        cloudSyncRepository.start()
        // Item 6 fix #1 — warm the exercise catalog (the ~1 MB RepDB JSON parse + the one-time
        // rename/addition/dedupe pass, ~1,090 Room queries before this round's fix, ~3 after) well
        // before the picker could ever need it, off the UI thread. `observeExerciseCatalog()` and
        // `AddExerciseViewModel` still call the same idempotent path as a safety net for the
        // cold-start race — this just makes that race vanishingly unlikely to be lost.
        appScope.launch { runCatching { workoutRepository.warmExerciseCatalog() } }
        // Owner feedback round 4 (2026-09-20) — self-healing bootstrap: HydrationIntakeSync.sync()
        // is only otherwise called from HydrationSettingsViewModel's toggle/time-change handlers,
        // so a user who already had `hydration_enabled = true` from before Hydration-as-Intake
        // shipped never gets a `FoodMedTask` row created for it (hydration_task_id stays null
        // forever) — nothing to show in Intake or on Today, regardless of the reminder time. Fire
        // this idempotent sync on every cold start so an already-enabled install self-heals without
        // the user having to re-toggle the setting.
        appScope.launch {
            runCatching {
                val s = settingsRepository.getSettings()
                hydrationIntakeSync.sync(s.hydrationEnabled, s.hydrationReminderTime)
            }.onFailure { com.daybook.app.util.recordUnhandledException(it) }
        }
    }
}
