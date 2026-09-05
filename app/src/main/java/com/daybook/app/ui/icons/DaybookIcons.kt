package com.daybook.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Local vector icons that used to come from `material-icons-extended`.
 * Built straight from SVG path data so the ~2MB extended library can be dropped.
 * Colour is irrelevant here — the `Icon` composable tints via its `tint` param.
 */
object DaybookIcons {

    private fun v(name: String, path: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = "Daybook.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(path),
            fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        ).build()

    /**
     * v0.5.5 — stroke-style glyph builder (HugeIcons "stroke-rounded" family), for icons that
     * need to match the app's stroke-only iconography instead of the filled Material weight
     * used by [v]. See [Send]: replaces `MI.AutoMirrored.Filled.Send`, which was a filled
     * Material paper-plane inconsistent with the rest of the app's icon set.
     */
    private fun vStroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = "Daybook.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { p ->
                addPath(
                    pathData = addPathNodes(p),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    // ---- Tier A: hand-authored ------------------------------------------------

    val Remove: ImageVector by lazy { v("Remove", "M19,13H5v-2h14v2z") }

    /**
     * v0.5.3 Phase 4 (§4.6 / D1) — converted from the lone `vStroke` glyph to a **filled**
     * alarm (Material `alarm` path data) so `DaybookIcons` is one visual weight. Name kept.
     */
    val AlarmClock: ImageVector by lazy {
        v(
            "AlarmClock",
            "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72z" +
                "M7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85z" +
                "M12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8z" +
                "M12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
                "M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
            evenOdd = true,
        )
    }

    /**
     * v0.5.3 Phase 4 (§4.6) — a bar-chart glyph for Detail's "Stats" tab (`DaybookIcons.Flame`
     * there was semantically wrong — Flame = streak). `material-icons-core` has no chart glyph.
     */
    val BarChart: ImageVector by lazy {
        v("BarChart", "M5,9.2h3V19H5V9.2zM10.6,5h2.8v14h-2.8V5zm5.6,8H19v6h-2.8v-6z")
    }

    /**
     * v0.5.3 Phase 5 (§5.17) — a picture/photo glyph (Material `image` path). Used on the Account
     * screen's "Use Google photo" row, which was on the placeholder `Category` glyph.
     */
    val Image: ImageVector by lazy {
        v(
            "Image",
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2z" +
                "M8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
        )
    }

    val Clock: ImageVector by lazy {
        v(
            "Clock",
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
                "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
                "M12.5,7H11v5.25l4.5,2.67 0.75,-1.23 -3.75,-2.22z",
            evenOdd = true,
        )
    }

    val Comment: ImageVector by lazy {
        v(
            "Comment",
            "M21.99,4c0,-1.1 -0.89,-2 -1.99,-2H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h14l4,4 -0.01,-18z" +
                "M18,14H6v-2h12v2zM18,11H6V9h12v2zM18,8H6V6h12v2z",
        )
    }

    /**
     * v0.5.5 — HugeIcons "send-horizontal" stroke-rounded glyph, replacing
     * `MI.AutoMirrored.Filled.Send` on HomeScreen's reply/log trigger buttons (that filled
     * Material paper-plane was visually inconsistent with this stroke-only icon family).
     */
    val Send: ImageVector by lazy {
        vStroke(
            "Send",
            "M10.325 5.33455L16.1084 8.24495C19.3643 9.88342 20.9922 10.7027 20.9922 12C20.9922 13.2973 19.3643 14.1166 16.1084 15.7551L10.325 18.6655C6.63532 20.5223 4.79046 21.4507 3.7862 20.7851C3.57349 20.6441 3.38825 20.4651 3.23962 20.2569C2.53788 19.2741 3.3843 17.381 5.07715 13.5948C5.39957 12.8736 5.56078 12.5131 5.58462 12.1319C5.59011 12.044 5.59011 11.956 5.58462 11.8681C5.56078 11.4869 5.39957 11.1264 5.07715 10.4052C3.3843 6.61898 2.53788 4.72586 3.23962 3.74307C3.38825 3.53492 3.57349 3.35593 3.7862 3.21495C4.79046 2.54933 6.63532 3.47774 10.325 5.33455Z",
            "M9.49219 12H13.4922",
        )
    }

    val Archive: ImageVector by lazy {
        v(
            "Archive",
            "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.16,0.55L3.46,5.23" +
                "C3.17,5.57 3,6.02 3,6.5V19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27z" +
                "M12,17.5L6.5,12H10v-2h4v2h3.5L12,17.5zM5.12,5l0.81,-1h12l0.94,1H5.12z",
        )
    }

    val Unarchive: ImageVector by lazy {
        v(
            "Unarchive",
            "M20.55,5.22l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.15,0.55L3.46,5.22" +
                "C3.17,5.57 3,6.01 3,6.5V19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.49 -0.17,-0.93 -0.45,-1.28z" +
                "M12,9.5l5.5,5.5H14v2h-4v-2H6.5L12,9.5zM5.12,5l0.82,-1h12l0.93,1H5.12z",
        )
    }

    val FilterList: ImageVector by lazy {
        v("FilterList", "M10,18h4v-2h-4v2zM3,6v2h18V6H3zm3,7h12v-2H6v2z")
    }

    val CheckBox: ImageVector by lazy {
        v(
            "CheckBox",
            "M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
                "M10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z",
        )
    }

    val CheckBoxBlank: ImageVector by lazy {
        v(
            "CheckBoxBlank",
            "M19,5v14H5V5h14m0,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z",
        )
    }

    val Category: ImageVector by lazy {
        v(
            "Category",
            "M12,2l-5.5,9h11L12,2zM17.5,13a4.5,4.5 0 1 0 0,9 4.5,4.5 0 0 0 0,-9zM3,13.5h8v8H3z",
        )
    }

    val Task: ImageVector by lazy {
        v(
            "Task",
            // v0.5.1 §J: the third subpath used to be
            //   "M8,15.01l1.41,1.41 2.09,-2.09 4.09,4.09 1.41,-1.41 -5.5,-5.5z"
            // whose centreline runs up-right then down-right — an inverted V, i.e. a mountain
            // peak, not a check mark. Inside the document outline that reads exactly like the
            // system broken-image placeholder, which is what users reported. Replaced with
            // Material `task`'s real tick (down-right to the vertex, then up-right).
            "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6z" +
                "M6,20V4h7v5h5v11L6,20z" +
                "M8.82,13.05L7.4,14.46 10.94,18l5.66,-5.66 -1.41,-1.41 -4.24,4.24z",
        )
    }

    val Bedtime: ImageVector by lazy {
        v(
            "Bedtime",
            "M9.5,2c-1.82,0 -3.53,0.5 -5,1.35 2.99,1.73 5,4.95 5,8.65s-2.01,6.92 -5,8.65" +
                "C5.97,21.5 7.68,22 9.5,22c5.52,0 10,-4.48 10,-10S15.02,2 9.5,2z",
        )
    }

    // ---- Tier B: standard Material glyphs, path data inlined -----------------

    val WaterDrop: ImageVector by lazy {
        v(
            "WaterDrop",
            "M12,2c-5.33,4.55 -8,8.48 -8,11.8 0,4.98 3.8,8.2 8,8.2s8,-3.22 8,-8.2c0,-3.32 -2.67,-7.25 -8,-11.8z",
        )
    }

    val Medication: ImageVector by lazy {
        v(
            "Medication",
            "M19,8H5c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2z" +
                "M17,16h-3v3h-2v-3H9v-2h3v-3h2v3h3v2zM8.81,6C9.42,5.55 10.18,5.28 11,5.28V4H8V2h8v2h-3v1.28" +
                "c0.82,0 1.58,0.27 2.19,0.72H8.81z",
        )
    }

    val Restaurant: ImageVector by lazy {
        v(
            "Restaurant",
            "M11,9H9V2H7v7H5V2H3v7c0,2.12 1.66,3.84 3.75,3.97L6.75,22h2.5l0,-9.03C11.34,12.84 13,11.12 13,9V2h-2v7z" +
                "M16,6v8h2.5v8H21V2c-2.76,0 -5,2.24 -5,4z",
        )
    }

    val DirectionsRun: ImageVector by lazy {
        v(
            "DirectionsRun",
            "M13.49,5.48c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z" +
                "M9.89,19.38l1,-4.4 2.1,2v6h2v-7.5l-2.1,-2 0.6,-3c1.3,1.5 3.3,2.5 5.5,2.5v-2" +
                "c-1.9,0 -3.5,-1 -4.3,-2.4l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1 -0.3,0 -0.5,0.1 -0.8,0.1L6.5,7.28V12h2V8.6" +
                "l1.8,-0.7 -1.6,8.1 -4.9,-1 -0.4,2 7,1.4z",
        )
    }

    val MenuBook: ImageVector by lazy {
        v(
            "MenuBook",
            "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5 -1.95,0 -4.05,0.4 -5.5,1.5 -1.45,-1.1 -3.55,-1.5 -5.5,-1.5" +
                "S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5 0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20" +
                "c1.95,0 4.05,0.4 5.5,1.5 1.35,-0.85 3.8,-1.5 5.5,-1.5 1.65,0 3.35,0.3 4.75,1.05 0.1,0.05 0.15,0.05 0.25,0.05" +
                "0.25,0 0.5,-0.25 0.5,-0.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5" +
                "-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.2,0 2.4,0.15 3.5,0.5v11.5z",
        )
    }

    val SelfImprovement: ImageVector by lazy {
        v(
            "SelfImprovement",
            "M12,6c1.11,0 2,-0.9 2,-2 0,-0.38 -0.1,-0.73 -0.29,-1.03L12,0l-1.71,2.97c-0.19,0.3 -0.29,0.65 -0.29,1.03" +
                "0,1.1 0.9,2 2,2zM19,15c-1.86,0 -3.42,1.28 -3.86,3h-1.79l3.09,-8.28c0.28,-0.76 -0.1,-1.6 -0.86,-1.88" +
                "-0.76,-0.28 -1.6,0.1 -1.88,0.86L12,12.11l-1.7,-4.55C10.04,6.86 9.35,6.42 8.6,6.5c-0.05,0 -0.09,0 -0.14,0.01" +
                "-0.79,0.11 -1.36,0.83 -1.31,1.62 0.01,0.13 0.03,0.26 0.08,0.39L10.35,18H8.86c-0.44,-1.72 -2,-3 -3.86,-3" +
                "-2.21,0 -4,1.79 -4,4 0,0.55 0.45,1 1,1s1,-0.45 1,-1c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,0.55 0.45,1 1,1h12" +
                "c0.55,0 1,-0.45 1,-1 0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,0.55 0.45,1 1,1s1,-0.45 1,-1c0,-2.21 -1.79,-4 -4,-4z",
        )
    }

    val Flame: ImageVector by lazy {
        v(
            "Flame",
            "M13.5,0.67s0.74,2.65 0.74,4.8c0,2.06 -1.35,3.73 -3.41,3.73 -2.07,0 -3.63,-1.67 -3.63,-3.73l0.03,-0.36" +
                "C5.21,7.51 4,10.62 4,14c0,4.42 3.58,8 8,8s8,-3.58 8,-8C20,8.61 17.41,3.8 13.5,0.67z" +
                "M11.71,19c-1.78,0 -3.22,-1.4 -3.22,-3.14 0,-1.62 1.05,-2.76 2.81,-3.12 1.77,-0.36 3.6,-1.21 4.62,-2.58" +
                "0.39,1.29 0.59,2.65 0.59,4.04 0,2.65 -2.15,4.8 -4.8,4.8z",
        )
    }

    val ChevronRight: ImageVector by lazy {
        v("ChevronRight", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    }

    val Lock: ImageVector by lazy {
        v(
            "Lock",
            "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12" +
                "c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z" +
                "M15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z",
        )
    }

    val Fingerprint: ImageVector by lazy {
        v(
            "Fingerprint",
            "M17.81,4.47c-0.08,0 -0.16,-0.02 -0.23,-0.06C15.66,3.42 14,3 12.01,3c-1.98,0 -3.86,0.47 -5.57,1.41" +
                "-0.24,0.13 -0.54,0.04 -0.68,-0.2 -0.13,-0.24 -0.04,-0.55 0.2,-0.68C7.82,2.52 9.86,2 12.01,2" +
                "c2.13,0 3.99,0.47 6.03,1.52 0.25,0.13 0.34,0.43 0.21,0.68 -0.09,0.18 -0.26,0.27 -0.44,0.27z" +
                "M3.5,9.72c-0.1,0 -0.2,-0.03 -0.29,-0.09 -0.23,-0.16 -0.28,-0.47 -0.12,-0.7 0.99,-1.4 2.25,-2.5 3.75,-3.27" +
                "C9.98,3.98 14,3.97 17.15,5.65c1.5,0.8 2.76,1.89 3.75,3.28 0.16,0.22 0.11,0.54 -0.12,0.7 -0.23,0.16 -0.54,0.11 -0.7,-0.12" +
                "-0.9,-1.26 -2.04,-2.25 -3.39,-2.97 -2.87,-1.52 -6.54,-1.52 -9.4,0.01 -1.36,0.73 -2.5,1.73 -3.4,2.99 -0.08,0.14 -0.23,0.2 -0.39,0.2z" +
                "M9.75,21.79c-0.13,0 -0.26,-0.05 -0.35,-0.15 -0.87,-0.87 -1.34,-1.43 -2.01,-2.64 -0.69,-1.23 -1.05,-2.73 -1.05,-4.34" +
                "0,-2.97 2.54,-5.39 5.66,-5.39s5.66,2.42 5.66,5.39c0,0.28 -0.22,0.5 -0.5,0.5s-0.5,-0.22 -0.5,-0.5" +
                "c0,-2.42 -2.09,-4.39 -4.66,-4.39s-4.66,1.97 -4.66,4.39c0,1.44 0.32,2.77 0.93,3.85 0.64,1.15 1.08,1.64 1.85,2.42" +
                "0.19,0.2 0.19,0.51 0,0.71 -0.11,0.1 -0.24,0.15 -0.37,0.15z" +
                "M16.92,19.94c-1.19,0 -2.24,-0.3 -3.1,-0.89 -1.49,-1.01 -2.38,-2.65 -2.38,-4.39 0,-0.28 0.22,-0.5 0.5,-0.5s0.5,0.22 0.5,0.5" +
                "c0,1.41 0.72,2.74 1.94,3.56 0.71,0.48 1.54,0.71 2.54,0.71 0.24,0 0.64,-0.03 1.04,-0.1 0.27,-0.05 0.53,0.13 0.58,0.41" +
                "0.05,0.27 -0.13,0.53 -0.41,0.58 -0.57,0.11 -1.07,0.12 -1.25,0.12z" +
                "M14.91,22c-0.04,0 -0.09,-0.01 -0.13,-0.02 -1.59,-0.44 -2.63,-1.03 -3.72,-2.1 -1.4,-1.39 -2.17,-3.24 -2.17,-5.22" +
                "0,-1.62 1.38,-2.94 3.08,-2.94s3.08,1.32 3.08,2.94c0,1.07 0.93,1.94 2.08,1.94s2.08,-0.87 2.08,-1.94" +
                "c0,-3.77 -3.25,-6.83 -7.25,-6.83 -2.84,0 -5.44,1.58 -6.61,4.03 -0.39,0.81 -0.59,1.76 -0.59,2.8" +
                "0,0.78 0.07,2.01 0.67,3.61 0.1,0.26 -0.03,0.55 -0.29,0.64 -0.26,0.1 -0.55,-0.04 -0.64,-0.29 -0.49,-1.31 -0.73,-2.61 -0.73,-3.96" +
                "0,-1.2 0.23,-2.29 0.68,-3.24 1.33,-2.79 4.28,-4.6 7.51,-4.6 4.55,0 8.25,3.51 8.25,7.83 0,1.62 -1.38,2.94 -3.08,2.94" +
                "s-3.08,-1.32 -3.08,-2.94c0,-1.07 -0.93,-1.94 -2.08,-1.94s-2.08,0.87 -2.08,1.94c0,1.71 0.66,3.31 1.87,4.51" +
                "0.95,0.94 1.86,1.46 3.27,1.85 0.27,0.07 0.42,0.35 0.35,0.61 -0.06,0.23 -0.26,0.38 -0.48,0.38z",
        )
    }

    val Palette: ImageVector by lazy {
        v(
            "Palette",
            "M12,2C6.49,2 2,6.49 2,12s4.49,10 10,10c1.38,0 2.5,-1.12 2.5,-2.5 0,-0.61 -0.23,-1.2 -0.64,-1.67" +
                "-0.08,-0.1 -0.13,-0.21 -0.13,-0.33 0,-0.28 0.22,-0.5 0.5,-0.5H16c3.31,0 6,-2.69 6,-6 0,-4.96 -4.49,-9 -10,-9z" +
                "M6.5,13C5.67,13 5,12.33 5,11.5S5.67,10 6.5,10 8,10.67 8,11.5 7.33,13 6.5,13z" +
                "M9.5,9C8.67,9 8,8.33 8,7.5S8.67,6 9.5,6 11,6.67 11,7.5 10.33,9 9.5,9z" +
                "M14.5,9c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,6 14.5,6 16,6.67 16,7.5 15.33,9 14.5,9z" +
                "M17.5,13c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
        )
    }

    val Backup: ImageVector by lazy {
        v(
            "Backup",
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14" +
                "c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z" +
                "M14,13v4h-4v-4H7l5,-5 5,5h-3z",
        )
    }

    val ImportExport: ImageVector by lazy {
        v(
            "ImportExport",
            "M9,3L5,6.99h3V14h2V6.99h3L9,3z" +
                "M16,17.01V10h-2v7.01h-3L15,21l4,-3.99h-3z",
        )
    }

    /**
     * v0.5.3 Phase 0 (§2.9 / D1 / backlog #13) — a visible "?" in a circle outline. The
     * deliberate placeholder for `Icons.getIcon`'s logged unknown-key fallback (wired in
     * Phase 4 §D1, replacing the silent `else -> Task`).
     */
    val Unknown: ImageVector by lazy {
        v(
            "Unknown",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" +
                "M11,18h2v-2h-2v2z" +
                "M12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z",
            evenOdd = true,
        )
    }
}
