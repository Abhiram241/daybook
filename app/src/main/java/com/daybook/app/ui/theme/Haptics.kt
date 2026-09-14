package com.daybook.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * App-wide "Vibration / Haptics" gate (Settings > Appearance > Feel), sourced from
 * [com.daybook.app.data.HapticsPrefs] and provided by [DaybookTheme]. Defaults to `true` so
 * anything composed outside `DaybookTheme` (there shouldn't be any) still vibrates rather than
 * silently doing nothing.
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Drop-in replacement for `LocalHapticFeedback.current`: same [HapticFeedback] interface, but
 * every call silently no-ops once the user turns off Settings > Appearance > Feel > Vibration.
 * One central gate instead of checking the setting at each of the app's ~20
 * `performHapticFeedback` call sites — swap `LocalHapticFeedback.current` for
 * `rememberDaybookHaptics()` at the point each screen grabs its `haptics` value; every existing
 * `haptics.performHapticFeedback(...)` call downstream is unchanged.
 */
@Composable
fun rememberDaybookHaptics(): HapticFeedback {
    val real = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(real, enabled) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (enabled) real.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
}
