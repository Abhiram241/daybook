package com.daybook.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.daybook.app.data.sync.CloudSyncRepository
import com.daybook.app.util.CrashHandler
import com.daybook.app.util.notification.NotificationUtils
import com.daybook.app.util.work.WindowRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DaybookApplication : Application(), Configuration.Provider {

    @Inject lateinit var notificationUtils: NotificationUtils
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cloudSyncRepository: CloudSyncRepository

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
    }
}
