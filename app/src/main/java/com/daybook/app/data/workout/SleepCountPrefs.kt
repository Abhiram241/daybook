package com.daybook.app.data.workout

import android.content.Context
import com.daybook.app.data.health.SleepCountDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Beast Mode settings > "Count sleep hours on". Device-local SharedPreferences, like
 *  [WorkoutFontPrefs] — no schema change. Starts on [SleepCountDay.WAKE]. */
@Singleton
class SleepCountPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("beast_mode_prefs", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        runCatching { SleepCountDay.fromKey(prefs.getString(KEY, null)) }.getOrDefault(SleepCountDay.DEFAULT)
    )
    val mode: StateFlow<SleepCountDay> = _mode

    fun setMode(mode: SleepCountDay) {
        runCatching { prefs.edit().putString(KEY, mode.storageKey).apply() }
        _mode.value = mode
    }

    private companion object { const val KEY = "sleep_count_day" }
}
