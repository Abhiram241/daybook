package com.daybook.app.data.health

import com.daybook.app.data.model.HealthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B6 (§7.4) — `aggregateHealthDays` (R31) and `visibleHealthCards` (R32), the two choke points
 *  the plan requires for Range-mode mean/sum decisions and the per-metric hide rule. */
class HealthAggregationTest {

    private fun day(
        date: String,
        steps: Int? = null,
        sleepMinutes: Int? = null,
        spo2: Float? = null,
        spo2Min: Float? = null,
        spo2Max: Float? = null,
        nutritionCalories: Float? = null,
        nutritionSourceApp: String? = null
    ) = HealthDay(
        localDate = date, steps = steps, sleepMinutes = sleepMinutes, spo2Percent = spo2,
        spo2MinPercent = spo2Min, spo2MaxPercent = spo2Max,
        nutritionCalories = nutritionCalories, nutritionSourceApp = nutritionSourceApp,
        updatedAt = 0L
    )

    // ------------------------------------------------------------ aggregateHealthDays

    @Test
    fun aggregateHealthDays_meansSteps_ignoringNullDays() {
        val days = listOf(day("2026-01-01", steps = 10000), day("2026-01-02", steps = null), day("2026-01-03", steps = 6000))
        val agg = aggregateHealthDays(days)
        assertEquals(8000.0, agg.stepsAvg!!, 0.001)
    }

    @Test
    fun aggregateHealthDays_sumsSleepMinutes_notMean() {
        val days = listOf(day("2026-01-01", sleepMinutes = 400), day("2026-01-02", sleepMinutes = 420))
        val agg = aggregateHealthDays(days)
        assertEquals(820, agg.sleepMinutesTotal)
        assertTrue(agg.hasAnySleep)
    }

    @Test
    fun aggregateHealthDays_emptyList_producesAllNulls() {
        val agg = aggregateHealthDays(emptyList())
        assertEquals(null, agg.stepsAvg)
        assertEquals(0, agg.sleepMinutesTotal)
        assertFalse(agg.hasAnySleep)
        assertEquals(0, agg.daysWithAnyData)
        assertEquals(0, agg.totalDays)
    }

    @Test
    fun aggregateHealthDays_daysWithAnyData_countsOnlyDaysWithSomething() {
        val days = listOf(day("2026-01-01", steps = 100), day("2026-01-02"), day("2026-01-03", spo2 = 97f))
        val agg = aggregateHealthDays(days)
        assertEquals(2, agg.daysWithAnyData)
        assertEquals(3, agg.totalDays)
    }

    @Test
    fun aggregateHealthDays_spo2MinMax_meanedIndependently() {
        val days = listOf(
            day("2026-01-01", spo2Min = 90f, spo2Max = 98f),
            day("2026-01-02", spo2Min = 92f, spo2Max = 96f)
        )
        val agg = aggregateHealthDays(days)
        assertEquals(91.0, agg.spo2MinPercentAvg!!, 0.001)
        assertEquals(97.0, agg.spo2MaxPercentAvg!!, 0.001)
    }

    @Test
    fun aggregateHealthDays_nutritionSourceApp_firstNonNullWins() {
        val days = listOf(
            day("2026-01-01", nutritionCalories = null, nutritionSourceApp = null),
            day("2026-01-02", nutritionCalories = 500f, nutritionSourceApp = "com.myfitnesspal.android")
        )
        val agg = aggregateHealthDays(days)
        assertEquals("com.myfitnesspal.android", agg.nutritionSourceApp)
    }

    // ------------------------------------------------------------ visibleHealthCards (Day mode)

    @Test
    fun visibleHealthCards_day_nullDayAndNoSessions_isEmpty() {
        assertTrue(visibleHealthCards(day = null, hasSessions = false).isEmpty())
    }

    @Test
    fun visibleHealthCards_day_stepsPresent_showsStepsCardOnly() {
        val cards = visibleHealthCards(day = day("2026-01-01", steps = 500), hasSessions = false)
        assertEquals(setOf(HealthCardKind.STEPS), cards)
    }

    @Test
    fun visibleHealthCards_day_sessionsPresent_addsSessionsCard() {
        val cards = visibleHealthCards(day = null, hasSessions = true)
        assertEquals(setOf(HealthCardKind.SESSIONS), cards)
    }

    @Test
    fun visibleHealthCards_day_noSpo2_hidesSpo2Card() {
        val cards = visibleHealthCards(day = day("2026-01-01", steps = 1, spo2 = null), hasSessions = false)
        assertFalse(HealthCardKind.SPO2 in cards)
    }

    // ------------------------------------------------------------ visibleHealthCards (Range mode)

    @Test
    fun visibleHealthCards_range_metricPresentOnSomeDays_stillShows() {
        val days = listOf(day("2026-01-01", steps = 100), day("2026-01-02", steps = null))
        val agg = aggregateHealthDays(days)
        assertTrue(HealthCardKind.STEPS in visibleHealthCards(agg, hasSessions = false))
    }

    @Test
    fun visibleHealthCards_range_metricAbsentEveryDay_hidden() {
        val days = listOf(day("2026-01-01"), day("2026-01-02"))
        val agg = aggregateHealthDays(days)
        assertFalse(HealthCardKind.STEPS in visibleHealthCards(agg, hasSessions = false))
        assertFalse(HealthCardKind.SPO2 in visibleHealthCards(agg, hasSessions = false))
    }
}
