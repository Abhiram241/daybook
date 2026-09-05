package com.daybook.app.ui.icons

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daybook.app.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIREBASE_0.5_PLAN.md §10 — AAPT2 catches malformed vector XML at `assembleDebug`, but
 * `PathParser` geometry needs a real `Resources`. Inflate all four v0.5 drawables (minSdk 26
 * inflates VectorDrawable natively) and assert non-null with non-zero intrinsic size. A small
 * icon SystemUI cannot inflate makes the whole notification silently vanish.
 */
@RunWith(AndroidJUnit4::class)
class NavIconInflateTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assertInflates(id: Int, name: String) {
        val d = ctx.resources.getDrawable(id, ctx.theme)
        assertNotNull("$name failed to inflate", d)
        assertTrue("$name has zero width", d.intrinsicWidth > 0)
        assertTrue("$name has zero height", d.intrinsicHeight > 0)
    }

    @Test fun notificationIcon() = assertInflates(R.drawable.ic_notification, "ic_notification")
    @Test fun navHome() = assertInflates(R.drawable.ic_nav_home, "ic_nav_home")
    @Test fun navHabits() = assertInflates(R.drawable.ic_nav_habits, "ic_nav_habits")
    @Test fun navIntake() = assertInflates(R.drawable.ic_nav_intake, "ic_nav_intake")

    // v0.5.1 §F — the three per-category notification small icons. This is the ONLY automated
    // protection against the disappearing-notification failure mode (plan R6): a small icon
    // SystemUI cannot inflate makes the entire notification vanish with no error surfaced to the
    // app, so a silent regression here is invisible in production. Keep these tests when the
    // placeholder path data is replaced with the real silhouettes (§F-drawables).
    @Test fun notifHabit() = assertInflates(R.drawable.ic_notif_habit, "ic_notif_habit")
    @Test fun notifFood() = assertInflates(R.drawable.ic_notif_food, "ic_notif_food")
    @Test fun notifMed() = assertInflates(R.drawable.ic_notif_med, "ic_notif_med")
}
