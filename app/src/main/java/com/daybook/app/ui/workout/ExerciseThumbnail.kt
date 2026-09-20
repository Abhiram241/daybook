package com.daybook.app.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.daybook.app.R
import com.daybook.app.data.workout.Equipment
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.equipmentImageAsset
import com.daybook.app.ui.components.IconTile
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.DaybookColors

/**
 * Bug fix (post-A6, deviation #1 in HEALTH_AND_WORKOUT_PROGRESS.md, now resolved) — the real
 * RepDB illustration for a builtin exercise (§3.3.3), loaded straight from the bundled asset via
 * Coil's built-in `file:///android_asset/...` support (already a dependency —
 * `ui/components/Avatar.kt` uses the same library for the profile photo). Falls back to the
 * existing taxonomy icon tile for a custom exercise (no [imageId]) or if the asset genuinely
 * fails to decode — never a broken-image glyph.
 *
 * Item 3 (Beast Mode round 2026-09-16) — that two-branch fallback became a four-step chain, so an
 * exercise whose details are fully filled in always gets *some* picture instead of the same grey
 * icon every custom/imported row used to show:
 *
 *  1. the RepDB exercise photo ([imageId] non-null and it decodes) — unchanged;
 *  2. else the bundled **equipment** illustration ([equipmentImageAsset]);
 *  3. else the bundled **muscle** illustration ([muscleImageRes]) — this is what `NONE`
 *     (bodyweight) work lands on, where an apparatus photo would actively mislead;
 *  4. else the plain [IconTile], as before.
 *
 * Steps 2 and 3 are gated on the exercise being **completely described** — both
 * `primaryMuscle` and `equipment` set to something other than `OTHER`. That is deliberate: the
 * bare icon survives for exactly the exercises whose details are still missing, so it doubles as
 * the "this one needs attention" cue the details prompt (item 2) is about.
 *
 * Every image step degrades to the next one on a decode failure (`onError`), so a missing or
 * corrupt asset can never render as a broken glyph.
 */
/** [owner feedback, 2026-09-20] — fixed light backing for the equipment-illustration branch only,
 *  independent of the card's own `CardTint`, so the glyph never reads as a dark blob when the
 *  exercise's muscle-group tint happens to be dark/desaturated (History screenshot, the second
 *  card). A literal warm off-white rather than `Color.White`/`DaybookColors.Surface` — those are
 *  theme-dependent (dark theme's `Surface`/`Bg` are near-black, which would reproduce the exact
 *  bug), and this must stay light regardless of app theme. */
private val EquipmentThumbnailBg = Color(0xFFF5F5F0)

@Composable
fun ExerciseThumbnail(
    imageId: String?,
    hasStartPeak: Boolean,
    fallbackIcon: ImageVector,
    tint: CardTint,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    // Item 3 — both are already on `CatalogExercise`/`BlockUi` at every call site. Defaulted to
    // OTHER so a caller that genuinely has neither (an unresolved exercise id) keeps the old
    // icon-only behaviour rather than guessing at a picture.
    primaryMuscle: MuscleGroup = MuscleGroup.OTHER,
    equipment: Equipment = Equipment.OTHER
) {
    var photoFailed by remember(imageId) { mutableStateOf(false) }
    var equipmentFailed by remember(equipment) { mutableStateOf(false) }

    val tileModifier = modifier
        .size(size)
        .clip(AppShapes.tile)
        .background(tint.fillRaised)
        .border(1.dp, DaybookColors.Hairline, AppShapes.tile)

    // "Fully described" — the gate on steps 2 and 3. Both axes must be known: an exercise with a
    // real muscle group but `equipment = OTHER` is still half-filled, and showing it a picture
    // would hide that.
    val complete = primaryMuscle != MuscleGroup.OTHER && equipment != Equipment.OTHER
    val equipmentSlug = if (complete && !equipmentFailed) equipmentImageAsset(equipment) else null
    val muscleRes = if (complete) muscleImageRes(primaryMuscle) else null

    if (imageId != null && !photoFailed) {
        val suffix = if (hasStartPeak) "start" else "main"
        AsyncImage(
            model = "file:///android_asset/exercises/images/flat/$imageId-$suffix.webp",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { photoFailed = true },
            modifier = tileModifier
        )
    } else if (equipmentSlug != null) {
        AsyncImage(
            model = "file:///android_asset/exercises/images/equipment/$equipmentSlug.webp",
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // Falls through to the muscle illustration (step 3), then the icon, on a decode
            // failure — same `onError` guard the RepDB photo above has always had.
            onError = { equipmentFailed = true },
            // [owner feedback, 2026-09-20] — the equipment icons are flat-colored glyphs on
            // transparent background designed to sit on a light backing. Every other branch here
            // fills the tile with `tint.fillRaised` (the card's own muscle-group pastel, which can
            // be dark/desaturated); that reads fine for full-bleed photo/illustration content but
            // turns a dark-stroke equipment glyph into an unreadable blob when the tint is dark.
            // Fixed, theme-independent light backing instead, only for this branch.
            modifier = modifier
                .size(size)
                .clip(AppShapes.tile)
                .background(EquipmentThumbnailBg)
                .border(1.dp, DaybookColors.Hairline, AppShapes.tile)
        )
    } else if (muscleRes != null) {
        Image(
            painter = painterResource(muscleRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = tileModifier
        )
    } else {
        IconTile(icon = fallbackIcon, tint = tint, modifier = modifier, size = size)
    }
}

/** Item 6 (Workout UI fixes plan, LOCKED) — the confirmed many-to-one mapping from the app's
 *  20-value [MuscleGroup] taxonomy to the user-supplied RepDB muscle illustration library (26
 *  `.webp` files, bundled as drawables under `res/drawable/muscle_*`). `null` for the four groups
 *  with no single anatomical asset (CARDIO, FULL_BODY, NECK, OTHER), which keep the plain icon
 *  fallback instead.
 *
 *  Item 3 (Beast Mode round 2026-09-16) — moved here out of `AddExerciseScreen`, where it was
 *  `private` and used only by the Muscle Groups filter sheet: [ExerciseThumbnail]'s fallback
 *  chain now needs the same mapping, and two copies of it would drift. The filter sheet still
 *  calls it (same package, no import needed). */
fun muscleImageRes(group: MuscleGroup): Int? = when (group) {
    MuscleGroup.ABDOMINALS -> R.drawable.muscle_abdominals
    MuscleGroup.ABDUCTORS -> R.drawable.muscle_abductors
    MuscleGroup.ADDUCTORS -> R.drawable.muscle_adductors
    MuscleGroup.BICEPS -> R.drawable.muscle_biceps
    MuscleGroup.CALVES -> R.drawable.muscle_calves
    MuscleGroup.CHEST -> R.drawable.muscle_chest
    MuscleGroup.FOREARMS -> R.drawable.muscle_forearms
    MuscleGroup.GLUTES -> R.drawable.muscle_glutes
    MuscleGroup.HAMSTRINGS -> R.drawable.muscle_hamstrings
    MuscleGroup.LATS -> R.drawable.muscle_lats
    MuscleGroup.LOWER_BACK -> R.drawable.muscle_lower_back
    MuscleGroup.QUADRICEPS -> R.drawable.muscle_quadriceps
    MuscleGroup.SHOULDERS -> R.drawable.muscle_shoulders
    MuscleGroup.TRAPS -> R.drawable.muscle_traps
    MuscleGroup.TRICEPS -> R.drawable.muscle_triceps
    MuscleGroup.UPPER_BACK -> R.drawable.muscle_upper_back
    MuscleGroup.CARDIO, MuscleGroup.FULL_BODY, MuscleGroup.NECK, MuscleGroup.OTHER -> null
}
