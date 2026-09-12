package com.daybook.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.model.AppSettings
import com.daybook.app.data.workout.WorkoutFontPrefs
import com.daybook.app.ui.theme.FontChoice
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A7 (§3.8.2) — Beast Mode's own settings screen. Four preferences, its own font choice, and a
 *  door. */
@HiltViewModel
class WorkoutSettingsViewModel @Inject constructor(
    private val repo: AppSettingsRepository,
    private val fontPrefs: WorkoutFontPrefs
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
}
