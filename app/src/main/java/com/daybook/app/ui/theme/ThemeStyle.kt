package com.daybook.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * UX refinement round — Item 3 (Themed tint variants). A "style" swaps the **ground + text**
 * roles of a [DaybookColorScheme] only; `success` / `warning` / `danger` / `onSolid` are
 * per-*mode* constants shared by every style of that mode (identical to today's values — see
 * [DarkSignals] / [LightSignals]).
 *
 * `CHARCOAL` / `PAPER` are byte-identical to the pre-refinement `DaybookColorsDark` /
 * `DaybookColorsLight` constants (guarded by `DarkStyleTest` / `LightStyleTest`, D8).
 */
internal data class Signals(val success: Color, val warning: Color, val danger: Color, val onSolid: Color)

private val DarkSignals = Signals(
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFACC15),
    danger = Color(0xFFF87171),
    onSolid = Color(0xFF0B0D0F)
)

private val LightSignals = Signals(
    success = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    danger = Color(0xFFDC2626),
    onSolid = Color(0xFFFFFFFF)
)

/** The 9 roles a style controls. */
internal data class StyleGround(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val hairline: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color
)

// ---------------------------------------------------------------------------------------------
// Dark grounds (LD1 / LD3 — exactly four). Hexes + measured contrast ratios are in
// UX_REFINEMENT_PLAN.md §3.5. Charcoal == today's DaybookColorsDark, byte-identical (D8).
// ---------------------------------------------------------------------------------------------

private val GroundCharcoal = StyleGround(
    bg = Color(0xFF0B0D0F),
    surface = Color(0xFF16181B),
    surfaceElevated = Color(0xFF1E2124),
    outline = Color(0xFF2A2D31),
    hairline = Color(0x14FFFFFF),
    border = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF2F3F5),
    textMuted = Color(0xFF9AA0A6),
    textFaint = Color(0xFF6B7178)
)

private val GroundAmoled = StyleGround(
    bg = Color(0xFF000000),
    surface = Color(0xFF0C0D0F),
    surfaceElevated = Color(0xFF16181B),
    outline = Color(0xFF26292E),
    hairline = Color(0x14FFFFFF),
    border = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF2F3F5),
    textMuted = Color(0xFF9AA0A6),
    textFaint = Color(0xFF6B7178)
)

private val GroundWarm = StyleGround(
    bg = Color(0xFF14100D),
    surface = Color(0xFF1E1813),
    surfaceElevated = Color(0xFF271F18),
    outline = Color(0xFF362B22),
    hairline = Color(0x16FFF3E6),
    border = Color(0x16FFF3E6),
    textPrimary = Color(0xFFF4EFE9),
    textMuted = Color(0xFFA79E92),
    textFaint = Color(0xFF766C60)
)

private val GroundNavy = StyleGround(
    bg = Color(0xFF0A0E16),
    surface = Color(0xFF121826),
    surfaceElevated = Color(0xFF1A2233),
    outline = Color(0xFF283349),
    hairline = Color(0x14FFFFFF),
    border = Color(0x14FFFFFF),
    textPrimary = Color(0xFFEEF1F6),
    textMuted = Color(0xFF97A0B2),
    textFaint = Color(0xFF667085)
)

// ---------------------------------------------------------------------------------------------
// Light grounds (LD2 / LD3 — exactly four). Paper == today's DaybookColorsLight, byte-identical.
// ---------------------------------------------------------------------------------------------

private val GroundPaper = StyleGround(
    bg = Color(0xFFFBFBF9),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF2F2EF),
    outline = Color(0xFFE2E2DE),
    hairline = Color(0x14000000),
    border = Color(0x14000000),
    textPrimary = Color(0xFF1B1D20),
    textMuted = Color(0xFF5B6068),
    textFaint = Color(0xFF8A9099)
)

private val GroundPure = StyleGround(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF4F5F7),
    outline = Color(0xFFE4E6EA),
    hairline = Color(0x0F000000),
    border = Color(0x0F000000),
    textPrimary = Color(0xFF16181B),
    textMuted = Color(0xFF565B63),
    textFaint = Color(0xFF878D96)
)

private val GroundCream = StyleGround(
    bg = Color(0xFFFBF6EC),
    surface = Color(0xFFFFFDF7),
    surfaceElevated = Color(0xFFF3EBDA),
    outline = Color(0xFFE6DCC6),
    hairline = Color(0x14000000),
    border = Color(0x14000000),
    textPrimary = Color(0xFF23201A),
    textMuted = Color(0xFF6A6253),
    textFaint = Color(0xFF8C8472)
)

private val GroundSepia = StyleGround(
    bg = Color(0xFFF3E9D8),
    surface = Color(0xFFFBF3E4),
    surfaceElevated = Color(0xFFEADFC8),
    outline = Color(0xFFD9CBAD),
    hairline = Color(0x1A000000),
    border = Color(0x1A000000),
    textPrimary = Color(0xFF2E2718),
    textMuted = Color(0xFF665B45),
    textFaint = Color(0xFF8A7F63)
)

enum class DarkStyle(val storageKey: String, val label: String, internal val ground: StyleGround) {
    CHARCOAL("CHARCOAL", "Charcoal", GroundCharcoal),
    AMOLED("AMOLED", "True black", GroundAmoled),
    WARM("WARM", "Espresso", GroundWarm),
    NAVY("NAVY", "Midnight", GroundNavy);

    companion object {
        val DEFAULT = CHARCOAL
        fun fromKeyOrDefault(k: String?): DarkStyle = entries.firstOrNull { it.storageKey == k } ?: DEFAULT
    }
}

enum class LightStyle(val storageKey: String, val label: String, internal val ground: StyleGround) {
    PAPER("PAPER", "Paper", GroundPaper),
    PURE("PURE", "Pure white", GroundPure),
    CREAM("CREAM", "Warm cream", GroundCream),
    SEPIA("SEPIA", "Sepia", GroundSepia);

    companion object {
        val DEFAULT = SEPIA
        fun fromKeyOrDefault(k: String?): LightStyle = entries.firstOrNull { it.storageKey == k } ?: DEFAULT
    }
}

private fun StyleGround.toScheme(s: Signals) = DaybookColorScheme(
    bg = bg,
    surface = surface,
    surfaceElevated = surfaceElevated,
    outline = outline,
    hairline = hairline,
    border = border,
    textPrimary = textPrimary,
    textMuted = textMuted,
    textFaint = textFaint,
    success = s.success,
    warning = s.warning,
    danger = s.danger,
    onSolid = s.onSolid
)

fun darkSchemeFor(style: DarkStyle): DaybookColorScheme = style.ground.toScheme(DarkSignals)
fun lightSchemeFor(style: LightStyle): DaybookColorScheme = style.ground.toScheme(LightSignals)
