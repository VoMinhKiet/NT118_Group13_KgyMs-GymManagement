package com.kgyms.app.data.mock

import java.util.Calendar
import java.util.TimeZone
import com.kgyms.app.data.model.WorkoutRecord
import com.kgyms.app.data.model.WorkoutRecordStatus
import com.kgyms.app.utils.calculateWeeklyGoal

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
