package com.daybook.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * UX overhaul item 4 — where the app's theme comes from. `DARK` is the default for every
 * existing and fresh install; nothing changes until the user opts in from Appearance → Theme.
 */
enum class ThemeMode(val storageKey: String) {
    DARK("DARK"),
    LIGHT("LIGHT"),
    SYSTEM("SYSTEM");

    companion object {
        // User request (build 43) — fresh installs start Light (Sepia, Amber, 0.75× corners).
        val DEFAULT = LIGHT
        fun fromKeyOrDefault(k: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == k } ?: DEFAULT
    }
}

// Maps the dark [DaybookColorScheme] roles onto M3. Reads the raw [DaybookColorsDark] fields
// directly (file scope — not the `@Composable`-getter shim). Byte-identical to the pre-overhaul
// scheme apart from the explicit transparent `surfaceTint` (UX overhaul item 2).
private val DarkScheme = darkColorScheme(
    primary = DaybookColorsDark.textPrimary,
    onPrimary = DaybookColorsDark.onSolid,
    primaryContainer = DaybookColorsDark.surfaceElevated,
    onPrimaryContainer = DaybookColorsDark.textPrimary,
    secondary = DaybookColorsDark.textPrimary,
    onSecondary = DaybookColorsDark.onSolid,
    secondaryContainer = DaybookColorsDark.surfaceElevated,
    onSecondaryContainer = DaybookColorsDark.textPrimary,
    tertiary = DaybookColorsDark.textPrimary,
    onTertiary = DaybookColorsDark.onSolid,
    background = DaybookColorsDark.bg,
    onBackground = DaybookColorsDark.textPrimary,
    surface = DaybookColorsDark.surface,
    onSurface = DaybookColorsDark.textPrimary,
    surfaceVariant = DaybookColorsDark.surfaceElevated,
    onSurfaceVariant = DaybookColorsDark.textMuted,
    error = DaybookColorsDark.danger,
    onError = DaybookColorsDark.onSolid,
    outline = DaybookColorsDark.outline,
    outlineVariant = DaybookColorsDark.outline,
    scrim = DaybookColorsDark.bg,
    surfaceTint = Color.Transparent
)

// The light counterpart — same role mapping onto [DaybookColorsLight].
private val LightScheme = lightColorScheme(
    primary = DaybookColorsLight.textPrimary,
    onPrimary = DaybookColorsLight.onSolid,
    primaryContainer = DaybookColorsLight.surfaceElevated,
    onPrimaryContainer = DaybookColorsLight.textPrimary,
    secondary = DaybookColorsLight.textPrimary,
    onSecondary = DaybookColorsLight.onSolid,
    secondaryContainer = DaybookColorsLight.surfaceElevated,
    onSecondaryContainer = DaybookColorsLight.textPrimary,
    tertiary = DaybookColorsLight.textPrimary,
    onTertiary = DaybookColorsLight.onSolid,
    background = DaybookColorsLight.bg,
    onBackground = DaybookColorsLight.textPrimary,
    surface = DaybookColorsLight.surface,
    onSurface = DaybookColorsLight.textPrimary,
    surfaceVariant = DaybookColorsLight.surfaceElevated,
    onSurfaceVariant = DaybookColorsLight.textMuted,
    error = DaybookColorsLight.danger,
    onError = DaybookColorsLight.onSolid,
    outline = DaybookColorsLight.outline,
    outlineVariant = DaybookColorsLight.outline,
    scrim = DaybookColorsLight.bg,
    surfaceTint = Color.Transparent
)

/**
 * [accent] is the user-selectable highlight colour (see [AccentColor]); [fontChoice] swaps the
 * app-wide typeface (see [FontChoice] / [daybookTypography]); [themeMode] picks dark / light /
 * follow-system (UX overhaul item 4). [darkStyle] / [lightStyle] pick the background style within
 * that mode, and [cornerScale] is the global corner-radius multiplier (UX refinement round,
 * Items 3 & 4).
 */
@Composable
fun DaybookTheme(
    accent: AccentColor = AccentColor.DEFAULT,
    fontChoice: FontChoice = FontChoice.DEFAULT,
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    darkStyle: DarkStyle = DarkStyle.DEFAULT,
    lightStyle: LightStyle = LightStyle.DEFAULT,
    cornerScale: Float = DEFAULT_CORNER_SCALE,
    // rec 4 — the user preference; OR-ed here with the OS ANIMATOR_DURATION_SCALE == 0 setting.
    reduceMotion: Boolean = false,
    // Settings > Appearance > Feel — app-wide "Vibration / Haptics" toggle, gating every
    // `rememberDaybookHaptics()` call site via `LocalHapticsEnabled` (see Haptics.kt).
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val osNoAnim = remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        }.getOrDefault(1f) == 0f
    }
    val reduce = effectiveReduceMotion(reduceMotion, if (osNoAnim) 0f else 1f)
    val typography = remember(fontChoice) { daybookTypography(fontChoice) }

    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = remember(dark, darkStyle, lightStyle) {
        if (dark) darkSchemeFor(darkStyle) else lightSchemeFor(lightStyle)
    }
    val accentColor = accent.colorFor(dark)
    val onAccent = remember(accentColor) { onAccentInk(accentColor) } // LD13

    // v0.5.3 Phase 0 (§3.12 / backlog #16) — key the scheme on theme + accent so every default M3
    // control (Switch/RadioButton/Checkbox/CircularProgressIndicator/text-selection handles)
    // picks up the user accent instead of near-white `TextPrimary`. UX refinement round: also
    // re-key on darkStyle/lightStyle and copy the resolved ground roles across, so a non-default
    // style is fully applied to M3 too (not just the fixed Charcoal/Paper constants).
    val scheme = remember(dark, accent, darkStyle, lightStyle) {
        materialSchemeFor(dark, colors, accentColor, onAccent)
    }
    val appShapes = remember(cornerScale) { scaledAppShapes(cornerScale) }
    val m3Shapes = remember(cornerScale) { scaledM3Shapes(cornerScale) }

    CompositionLocalProvider(
        LocalDaybookColors provides colors,
        LocalIsDark provides dark,
        LocalAccent provides accentColor,
        LocalOnAccent provides onAccent,
        LocalDaybookShapes provides appShapes,
        LocalReduceMotion provides reduce,
        LocalHapticsEnabled provides hapticsEnabled
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = m3Shapes,
            content = content
        )
    }
}

private fun materialSchemeFor(dark: Boolean, colors: DaybookColorScheme, accentColor: Color, onAccent: Color) =
    (if (dark) DarkScheme else LightScheme).copy(
        primary = accentColor,
        onPrimary = onAccent,
        secondary = accentColor,
        tertiary = accentColor,
        background = colors.bg,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceElevated,
        onSurfaceVariant = colors.textMuted,
        outline = colors.outline,
        outlineVariant = colors.outline,
        scrim = colors.bg
    )

/**
 * Beast Mode's own theme — re-resolves dark/light + background style for a subtree that sits
 * inside the app-wide [DaybookTheme], keeping its typography, shapes, motion and haptics. When
 * [enabled] is false it re-provides the current values unchanged; the composable is always called
 * (never conditionally wrapped) so entering/leaving Beast Mode doesn't restructure — and reset —
 * the NavHost subtree beneath it. [accentFor] resolves the subtree's accent for the chosen mode.
 */
@Composable
fun DaybookModeOverride(
    enabled: Boolean,
    themeMode: ThemeMode,
    darkStyle: DarkStyle,
    lightStyle: LightStyle,
    accentFor: (dark: Boolean) -> Color,
    content: @Composable () -> Unit
) {
    // One call site for `content` in both states — an if/else around it would give the subtree a
    // different composition group and discard its state on every Beast Mode enter/leave.
    val systemDark = isSystemInDarkTheme()
    val outerColors = LocalDaybookColors.current
    val outerDark = LocalIsDark.current
    val outerAccent = LocalAccent.current
    val outerOnAccent = LocalOnAccent.current
    val outerScheme = MaterialTheme.colorScheme
    val dark = if (!enabled) outerDark else when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = remember(enabled, dark, darkStyle, lightStyle, outerColors) {
        if (!enabled) outerColors else if (dark) darkSchemeFor(darkStyle) else lightSchemeFor(lightStyle)
    }
    val accentColor = if (enabled) accentFor(dark) else outerAccent
    val onAccent = remember(enabled, accentColor, outerOnAccent) {
        if (enabled) onAccentInk(accentColor) else outerOnAccent
    }
    val scheme = remember(enabled, dark, colors, accentColor, outerScheme) {
        if (enabled) materialSchemeFor(dark, colors, accentColor, onAccent) else outerScheme
    }
    CompositionLocalProvider(
        LocalDaybookColors provides colors,
        LocalIsDark provides dark,
        LocalAccent provides accentColor,
        LocalOnAccent provides onAccent
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}
