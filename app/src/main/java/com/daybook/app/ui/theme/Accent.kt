package com.daybook.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * User-selectable accent. Separate from the pastel [CardTint]s — this is the single colour
 * that pops on the app ground and drives interactive state (nav, chips, switch, primary buttons,
 * week-strip pill, progress fills). Persisted as [storageKey] in `AppSettings.accent_color`
 * (Room converts via [com.daybook.app.util.enums.Converters]); serialized by name, which
 * equals [storageKey], so exported backups are unchanged.
 *
 * UX overhaul item 4 — each accent now carries a [dark] and a [light] value; [colorFor] resolves
 * per theme. The dark values are byte-identical to the pre-overhaul single `color`.
 */
@Serializable
enum class AccentColor(val storageKey: String, val dark: Color, val light: Color) {
    MINT("MINT", Color(0xFF2DD4BF), Color(0xFF0F9488)),
    LAVENDER("LAVENDER", Color(0xFFA78BFA), Color(0xFF7C5CE0)),
    CORAL("CORAL", Color(0xFFFB7185), Color(0xFFE23D5B)),
    SKY("SKY", Color(0xFF60A5FA), Color(0xFF2563EB)),
    AMBER("AMBER", Color(0xFFFBBF24), Color(0xFFB7791F));

    /** The accent colour for the active theme. */
    fun colorFor(dark: Boolean): Color = if (dark) this.dark else this.light

    companion object {
        val DEFAULT = LAVENDER
        fun fromKey(k: String?): AccentColor = entries.firstOrNull { it.storageKey == k } ?: DEFAULT
    }
}

/** The chosen accent colour, resolved for the active theme and provided by [DaybookTheme]. */
val LocalAccent = staticCompositionLocalOf { AccentColor.DEFAULT.dark }

// UX refinement round — LD13: on-accent ink is per-accent, computed, not a fixed per-mode
// constant. The plan's earlier claim that all five light accents clear 4.5:1 against white is
// wrong — measured, Mint #0F9488 is 3.74:1, Amber #B7791F 3.64:1 and Coral #E23D5B 4.16:1, so
// the selected segmented label / chip label / primary-button label fail AA in light mode today.
// Picking whichever ink (near-black / white) has the higher ratio fixes all three and returns
// near-black for EVERY accent in dark mode — i.e. dark mode is byte-identical to today.

private const val INK_DARK = 0xFF0B0D0FL // == DarkSignals.onSolid
private const val INK_LIGHT = 0xFFFFFFFFL // == LightSignals.onSolid

/** WCAG relative luminance of a Compose [Color]. Pure — see `OnAccentInkTest`. */
internal fun relativeLuminance(c: Color): Double {
    fun channel(v: Float): Double {
        val cLin = v.toDouble()
        return if (cLin <= 0.03928) cLin / 12.92 else Math.pow((cLin + 0.055) / 1.055, 2.4)
    }
    val r = channel(c.red)
    val g = channel(c.green)
    val b = channel(c.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun contrast(a: Color, b: Color): Double {
    val l1 = relativeLuminance(a)
    val l2 = relativeLuminance(b)
    val hi = maxOf(l1, l2)
    val lo = minOf(l1, l2)
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * The higher-contrast ink for content sitting ON a fill painted with [accent]. Pure — see
 * `OnAccentInkTest`.
 */
fun onAccentInk(accent: Color): Color {
    val dark = Color(INK_DARK)
    val light = Color(INK_LIGHT)
    return if (contrast(accent, dark) >= contrast(accent, light)) dark else light
}

/** Resolved ink for the current accent; provided by [DaybookTheme]. */
val LocalOnAccent = staticCompositionLocalOf { onAccentInk(AccentColor.DEFAULT.dark) }

/**
 * rec 4 (A10) — true when springy animations / page slides should be swapped for instant
 * `snap()` / `tween(0)` transitions. Provided by [DaybookTheme]; read at the ~13 animation call
 * sites the build plan enumerates (SD-7). Default false so a preview / test tree animates normally.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * The effective reduce-motion state: the user's `reduce_motion` preference OR the OS-wide
 * "Remove animations" setting (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`). Pure — see
 * `ReduceMotionTest`.
 */
fun effectiveReduceMotion(pref: Boolean, animatorScale: Float): Boolean =
    pref || animatorScale == 0f
