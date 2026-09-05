@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.daybook.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.daybook.app.R

/**
 * App-wide typeface choice. Persisted as [storageKey] in `AppSettings.font_choice` (plain
 * String column, no Room converter). GROTESK reproduces the pre-0.3 look exactly so existing
 * users see no shift. All faces are bundled OFL fonts in `res/font/` so they work offline.
 */
enum class FontChoice(val storageKey: String, val label: String) {
    GROTESK("GROTESK", "Space Grotesk"),
    SYSTEM("SYSTEM", "System"),
    LITERATA("LITERATA", "Literata"),
    NUNITO("NUNITO", "Nunito"),
    MONO("MONO", "Space Mono");

    companion object {
        val DEFAULT = LITERATA
        fun fromKeyOrDefault(key: String?): FontChoice =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}

/* ---- Bundled families ------------------------------------------------------ */

// Display/headline family — Space Grotesk (bundled variable font, OFL). Weights match the
// pre-0.3 definition exactly (Medium/SemiBold/Bold) so GROTESK is byte-for-byte the old look.
private val Grotesk = FontFamily(
    Font(
        R.font.space_grotesk_variable, weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.space_grotesk_variable, weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.space_grotesk_variable, weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

private val Literata = FontFamily(
    Font(
        R.font.literata_variable, weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.literata_variable, weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.literata_variable, weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.literata_variable, weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

private val Nunito = FontFamily(
    Font(
        R.font.nunito_variable, weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.nunito_variable, weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.nunito_variable, weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.nunito_variable, weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

// Space Mono ships as two static files (Regular + Bold); map the mid weights onto them.
private val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, weight = FontWeight.Normal),
    Font(R.font.space_mono_regular, weight = FontWeight.Medium),
    Font(R.font.space_mono_bold, weight = FontWeight.SemiBold),
    Font(R.font.space_mono_bold, weight = FontWeight.Bold),
)

/* ---- Scale --------------------------------------------------------------- */

private val fill = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

/**
 * The 0.3 type scale — sizes, weights and line-heights are identical across every [FontChoice];
 * only the [display] and [body] families swap. For [FontChoice.MONO] the body/label tracking is
 * nudged tighter so the wider monospaced glyphs don't read as over-spaced.
 */
private fun buildTypography(display: FontFamily, body: FontFamily, mono: Boolean = false): Typography {
    val bodyLargeLs: TextUnit = if (mono) (-0.3).sp else 0.sp
    val bodyLs: TextUnit = if (mono) (-0.2).sp else 0.1.sp
    val labelLgLs: TextUnit = if (mono) 0.1.sp else 0.3.sp
    val labelMdLs: TextUnit = if (mono) 0.2.sp else 0.4.sp
    return Typography(
        displayLarge = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1.0).sp, lineHeightStyle = fill
        ),
        displayMedium = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp, lineHeightStyle = fill
        ),
        // v0.5.3 Phase 0 — the 4 slots the pre-0.5.3 scale left on Material defaults (wrong
        // family, deaf to FontChoice). displaySmall/headlineLarge/headlineSmall use the `display`
        // family + Bold like their neighbours; titleSmall mirrors titleMedium at SemiBold.
        displaySmall = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp, lineHeightStyle = fill
        ),
        headlineLarge = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp
        ),
        titleLarge = TextStyle(
            fontFamily = display, fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp
        ),
        titleMedium = TextStyle(
            fontFamily = display, fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp
        ),
        titleSmall = TextStyle(
            fontFamily = display, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = body, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = bodyLargeLs
        ),
        bodyMedium = TextStyle(
            fontFamily = body, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = bodyLs
        ),
        bodySmall = TextStyle(
            fontFamily = body, fontWeight = FontWeight.Normal,
            fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = bodyLs
        ),
        labelLarge = TextStyle(
            fontFamily = body, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = labelLgLs
        ),
        labelMedium = TextStyle(
            fontFamily = body, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = labelMdLs
        ),
        labelSmall = TextStyle(
            fontFamily = body, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = labelMdLs
        )
    )
}

/** GROTESK == the pre-0.3 [Typography] (display = Space Grotesk, body = system default). */
private val GroteskTypography = buildTypography(Grotesk, FontFamily.Default)

/** Build the [Typography] for [choice]; cheap enough to `remember(fontChoice)` in the theme. */
fun daybookTypography(choice: FontChoice): Typography = when (choice) {
    FontChoice.GROTESK -> GroteskTypography
    FontChoice.SYSTEM -> buildTypography(FontFamily.Default, FontFamily.Default)
    FontChoice.LITERATA -> buildTypography(Literata, Literata)
    FontChoice.NUNITO -> buildTypography(Nunito, Nunito)
    FontChoice.MONO -> buildTypography(SpaceMono, SpaceMono, mono = true)
}

/**
 * v0.5.3 Phase 0 — semantic typography roles. Screens must read a role off this object instead
 * of guessing a `MaterialTheme.typography.*` slot (§2.1). These are `@Composable` read-only vals
 * (not a plain `object` of `TextStyle`) so they re-resolve when [FontChoice] changes. Phase 4
 * migrates callers onto them; nothing uses them yet.
 */
object DaybookText {
    val ScreenTitle: TextStyle @Composable get() = MaterialTheme.typography.displayMedium
    val Hero: TextStyle @Composable get() = MaterialTheme.typography.displayLarge
    val SectionTitle: TextStyle @Composable get() = MaterialTheme.typography.titleLarge
    val CardTitle: TextStyle @Composable get() = MaterialTheme.typography.titleMedium
    val CardSubtitle: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium
    val Caption: TextStyle @Composable get() = MaterialTheme.typography.labelSmall

    /** Same slot as [Caption]; named apart so metadata rows stop guessing. */
    val Metadata: TextStyle @Composable get() = MaterialTheme.typography.labelSmall
    val ButtonLabel: TextStyle @Composable get() = MaterialTheme.typography.labelLarge
    val NavLabel: TextStyle @Composable get() = MaterialTheme.typography.labelMedium
    val DialogTitle: TextStyle @Composable get() = MaterialTheme.typography.titleMedium

    /** Calendar cell number; the cell overrides the weight. */
    val CalendarDay: TextStyle @Composable get() = MaterialTheme.typography.titleMedium
}

/** The family for one [FontChoice], for rendering its own name in the picker. */
fun fontChoiceFamily(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.GROTESK -> Grotesk
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.LITERATA -> Literata
    FontChoice.NUNITO -> Nunito
    FontChoice.MONO -> SpaceMono
}
