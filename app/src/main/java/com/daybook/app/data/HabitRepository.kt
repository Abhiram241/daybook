package com.daybook.app.data

import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.Habit
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    val database: AppDatabase
) {
    suspend fun getActiveHabits(): List<Habit> =
        database.habitDao().getActiveHabits().first()

    suspend fun getHabitById(id: String): Habit? =
        database.habitDao().getHabitById(id)

    /** Earliest still-pending occurrence at or after now, for showing "Next: ..." on a card. */
    suspend fun nextOccurrenceMillis(habitId: String): Long? =
        database.habitOccurrenceDao()
            .getNextPendingForHabit(habitId, System.currentTimeMillis())?.scheduledFor

    /**
     * Reactive "next reminder" millis for every habit in one grouped query — replaces an
     * N+1 of [nextOccurrenceMillis] per habit in the Habits list (re-emits on occurrence writes).
     */
    fun observeNextPendingMillis(now: Long) =
        database.habitOccurrenceDao().observeNextPendingMillisByHabit(now)

    suspend fun save(habit: Habit) {
        database.habitDao().insertAll(habit)
    }

    suspend fun delete(habit: Habit) {
        database.habitDao().delete(habit)
    }

    fun observeActiveHabits() = database.habitDao().getActiveHabits()

    fun observeAllHabits() = database.habitDao().observeAllHabits()

    suspend fun archiveHabit(id: String) = database.habitDao().archiveHabit(id)
    suspend fun unarchiveHabit(id: String) = database.habitDao().unarchiveHabit(id)

    /** Customization round (rec 8): per-habit custom notification text / "why" note. */
    suspend fun setPromptMessage(id: String, v: String?) = database.habitDao().updatePromptMessage(id, v)
    suspend fun setMotivation(id: String, v: String?) = database.habitDao().updateMotivation(id, v)

    /** v0.5.5: start the running day-count for an "Ongoing" (STREAK) habit. */
    suspend fun startStreak(id: String, nowMillis: Long = System.currentTimeMillis()) =
        database.habitDao().startStreak(id, nowMillis)

    /**
     * v0.5.5: records longest = max(longest, current inclusive run) then nulls streak_started_at.
     * No-op if the habit is missing or not started.
     */
    suspend fun markStreakBroken(id: String, nowMillis: Long = System.currentTimeMillis()) {
        val h = database.habitDao().getHabitById(id) ?: return
        val started = h.streakStartedAt ?: return
        val run = com.daybook.app.util.streak.daysSince(started, nowMillis)
        database.habitDao().clearStreak(id, maxOf(h.streakLongest, run))
    }
}
