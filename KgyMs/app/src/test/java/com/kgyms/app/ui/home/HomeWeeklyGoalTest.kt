package com.kgyms.app.ui.home

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class HomeWeeklyGoalTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, zone: TimeZone = utc): Long =
        Calendar.getInstance(zone).apply {
            clear(); set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis
    private val sunday get() = at(2026, 10, 25, 23)
    private fun record(id: String, time: Long, status: WorkoutRecordStatus = WorkoutRecordStatus.Completed,
        confirmed: Boolean = true) = WorkoutRecord(id, time, status, confirmed)

    @Test fun mondayToSundayWindowIncludesMondayAndExcludesNextMonday() {
        val monday = at(2026, 10, 19)
        val next = at(2026, 10, 26)
        val records = listOf(record("previous", monday - 1), record("first", monday),
            record("last", sunday), record("next", next))
        val goal = calculateWeeklyGoal(6, records, sunday, utc)
        assertEquals(monday, goal.weekStartMillis)
        assertEquals(next, goal.weekEndExclusiveMillis)
        assertEquals(next - 1, goal.weekEndMillis)
        assertEquals(listOf("first", "last"), goal.completedSessions.map { it.sessionId })
    }

    @Test fun bookingsCancelledUnconfirmedAndBlankIdsNeverCount() {
        val t = at(2026, 10, 21, 10)
        val goal = calculateWeeklyGoal(6, listOf(
            record("scheduled", t, WorkoutRecordStatus.Scheduled, true),
            record("cancelled", t, WorkoutRecordStatus.Cancelled, true),
            record("unconfirmed", t, confirmed = false), record(" ", t), record("valid", t),
        ), sunday, utc)
        assertEquals(1, goal.completed)
    }

    @Test fun workoutAndAttendanceWithSameCanonicalIdCountOnce() {
        val t = at(2026, 10, 21, 10)
        val goal = calculateWeeklyGoal(6, listOf(record("session-1", t), record("session-1", t + 1000), record("session-2", t)), sunday, utc)
        assertEquals(2, goal.completed)
    }

    @Test fun changingTargetRecalculatesProgressWithoutAddingRecords() {
        val records = (0..2).map { record("session-$it", at(2026, 10, 19 + it, 10)) }
        val six = calculateWeeklyGoal(6, records, sunday, utc)
        val three = calculateWeeklyGoal(3, records, sunday, utc)
        assertEquals(3, six.completed); assertEquals(3, three.completed)
        assertEquals(50, six.percentage); assertEquals(100, three.percentage)
        assertEquals(3, six.remaining); assertEquals(0, three.remaining)
    }

    @Test fun newWeekDoesNotCarryOverCompletedSessions() {
        val records = listOf(record("old", at(2026, 10, 25, 10)))
        assertEquals(1, calculateWeeklyGoal(6, records, sunday, utc).completed)
        assertEquals(0, calculateWeeklyGoal(6, records, at(2026, 10, 26), utc).completed)
    }

    @Test fun futureConfirmedTimestampIsNotACompletedSessionYet() {
        val goal = calculateWeeklyGoal(6, listOf(record("future", at(2026, 10, 23))), at(2026, 10, 21), utc)
        assertEquals(0, goal.completed)
    }

    @Test fun timezoneAndYearBoundaryUseLocalMonday() {
        val zone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        val goal = calculateWeeklyGoal(6, emptyList(), at(2027, 1, 1, zone = zone), zone)
        assertEquals(at(2026, 12, 28, zone = zone), goal.weekStartMillis)
        assertEquals(at(2027, 1, 4, zone = zone), goal.weekEndExclusiveMillis)
    }

    @Test fun daylightSavingWeekUsesCalendarDaysRatherThanFixedHours() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val goal = calculateWeeklyGoal(6, emptyList(), at(2026, 3, 8, 15, zone), zone)
        assertEquals(at(2026, 3, 2, zone = zone), goal.weekStartMillis)
        assertEquals(at(2026, 3, 9, zone = zone), goal.weekEndExclusiveMillis)
        assertEquals(167L, (goal.weekEndExclusiveMillis - goal.weekStartMillis) / 3_600_000)
    }

    @Test fun percentageIsClampedAndUnsetTargetNeverDividesByZero() {
        val records = (0..3).map { record("session-$it", at(2026, 10, 21, 10)) }
        val exceeded = calculateWeeklyGoal(2, records, sunday, utc)
        assertEquals(4, exceeded.completed); assertEquals(100, exceeded.percentage)
        assertEquals(0, exceeded.remaining)
        assertEquals(0f, calculateWeeklyGoal(0, records, sunday, utc).progress, 0f)
    }
}
