package com.kgyms.app.ui.home

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

enum class WorkoutRecordStatus { Scheduled, Completed, Cancelled }

/** Workout and attendance for the same session must use the same canonical sessionId. */
data class WorkoutRecord(
    val sessionId: String,
    val occurredAtMillis: Long,
    val status: WorkoutRecordStatus,
    val confirmed: Boolean,
)

data class HomeWeeklyGoal(
    val target: Int,
    val weekStartMillis: Long,
    val weekEndExclusiveMillis: Long,
    val records: List<WorkoutRecord>,
    val asOfMillis: Long = Long.MAX_VALUE,
) {
    val completedSessions: List<WorkoutRecord> get() = records.filter {
        it.sessionId.isNotBlank() && it.status == WorkoutRecordStatus.Completed && it.confirmed &&
            it.occurredAtMillis >= weekStartMillis && it.occurredAtMillis < weekEndExclusiveMillis &&
            it.occurredAtMillis <= asOfMillis
    }.distinctBy { it.sessionId }
    val completed: Int get() = completedSessions.size
    val remaining: Int get() = (target - completed).coerceAtLeast(0)
    val progress: Float get() = if (target <= 0) 0f else (completed.toFloat() / target).coerceIn(0f, 1f)
    val percentage: Int get() = (progress * 100).roundToInt()
    val weekEndMillis: Long get() = weekEndExclusiveMillis - 1
}

/** Calendar days rather than 7 * 24h: preserves Monday/Sunday boundaries across DST. */
fun calculateWeeklyGoal(
    target: Int,
    records: List<WorkoutRecord>,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): HomeWeeklyGoal {
    val monday = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        this.set(Calendar.HOUR_OF_DAY, 0); this.set(Calendar.MINUTE, 0)
        this.set(Calendar.SECOND, 0); this.set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
    }
    val nextMonday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
    return HomeWeeklyGoal(target, monday.timeInMillis, nextMonday.timeInMillis, records.toList(), nowMillis)
}

/** Seed records are fixtures, never a claim of real check-in confirmation. */
fun mockWorkoutRecords(nowMillis: Long, timeZone: TimeZone): List<WorkoutRecord> {
    val week = calculateWeeklyGoal(6, emptyList(), nowMillis, timeZone)
    fun at(day: Int): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = week.weekStartMillis
        add(Calendar.DAY_OF_YEAR, day)
        this.set(Calendar.HOUR_OF_DAY, 9)
    }.timeInMillis
    // For early-week previews, use only dates already reached in this week.
    val reachedDays = (0..6).filter { at(it) <= nowMillis }.take(3)
    val completed = reachedDays.map { day -> WorkoutRecord("demo-session-$day", at(day), WorkoutRecordStatus.Completed, true) }
    return completed + listOf(
        WorkoutRecord("demo-booking", at(6), WorkoutRecordStatus.Scheduled, false),
        WorkoutRecord("demo-unconfirmed", nowMillis, WorkoutRecordStatus.Completed, false),
    ) + completed.take(1) // Duplicate attendance event: must not count twice.
}
