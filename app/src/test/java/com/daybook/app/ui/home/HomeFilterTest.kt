package com.daybook.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 item 7 — drives the pure [homeItemVisible]. Empty [ReminderFilter] set = all buckets;
 * showResolved=false hides anything with a status label (Done/Skipped/Logged/Missed) but keeps
 * future "Upcoming" rows (statusLabel == null).
 */
class HomeFilterTest {

    private fun item(
        isHabit: Boolean = false,
        isJournal: Boolean = false,
        statusLabel: String? = null
    ) = HomeItem(
        id = "x",
        title = "t",
        subtitle = null,
        iconKey = "restaurant",
        colorTag = "AUTO",
        scheduledTime = "09:00",
        scheduledEpoch = 0L,
        isHabit = isHabit,
        detailId = "d",
        occurrenceId = "o",
        canComplete = false,
        canSkip = false,
        canSnooze = false,
        canReply = false,
        responseText = null,
        statusLabel = statusLabel,
        isPast = false,
        isFuture = false,
        isJournal = isJournal
    )

    private val none = emptySet<ReminderFilter>()

    @Test fun defaultHidesResolvedKeepsPendingAndUpcoming() {
        assertTrue(homeItemVisible(item(statusLabel = null), none, showResolved = false))
        assertFalse(homeItemVisible(item(statusLabel = "Done"), none, showResolved = false))
        assertFalse(homeItemVisible(item(statusLabel = "Logged"), none, showResolved = false))
    }

    @Test fun showResolvedRevealsResolved() {
        assertTrue(homeItemVisible(item(statusLabel = "Skipped"), none, showResolved = true))
        assertTrue(homeItemVisible(item(statusLabel = MISSED_LABEL), none, showResolved = true))
    }

    @Test fun missedHiddenByDefault() {
        assertFalse(homeItemVisible(item(statusLabel = MISSED_LABEL), none, showResolved = false))
    }

    @Test fun habitsBucketFiltersOthers() {
        val types = setOf(ReminderFilter.HABITS)
        assertTrue(homeItemVisible(item(isHabit = true), types, showResolved = false))
        assertFalse(homeItemVisible(item(isHabit = false), types, showResolved = false))
        assertFalse(homeItemVisible(item(isHabit = false, isJournal = true), types, showResolved = false))
    }

    @Test fun journalBucketMatchesJournalEvenWhenNotHabit() {
        val types = setOf(ReminderFilter.JOURNAL)
        assertTrue(homeItemVisible(item(isHabit = false, isJournal = true), types, showResolved = false))
        assertFalse(homeItemVisible(item(isHabit = false), types, showResolved = false))
    }

    @Test fun intakeBucket() {
        val types = setOf(ReminderFilter.INTAKE)
        assertTrue(homeItemVisible(item(isHabit = false), types, showResolved = false))
        assertFalse(homeItemVisible(item(isHabit = true), types, showResolved = false))
    }
}
