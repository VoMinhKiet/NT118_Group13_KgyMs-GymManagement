package com.kgyms.app.ui.member.progress

import com.kgyms.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class ProgressAnalyticsTest {
    private val zone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    private val now = day("2027-01-14") + 12 * HOUR

    @Test fun fourWeeksUseLocalMondayAndIncludeCurrentWeek() {
        val history = recorded(
            attempt("outside", "2026-12-20"),
            attempt("first", "2026-12-21"),
            attempt("second", "2026-12-28"),
            attempt("currentOne", "2027-01-11"),
            attempt("currentTwo", "2027-01-14"),
            attempt("future", "2027-01-15"),
        )
        val stats = progressAnalytics(history, 2, now, zone)
        assertEquals(listOf("2026-12-21", "2026-12-28", "2027-01-04", "2027-01-11"),
            stats.weeks.map { PlanDates.date(it.start, zone.id) })
        assertEquals(listOf(1, 1, 0, 2), stats.weeks.map { it.completed })
        assertEquals(day("2027-01-18"), stats.currentWeek.endExclusive)
        assertEquals(5, stats.totalSessions)
        assertEquals(5, stats.performedExercises)
        assertEquals(1, stats.weeksAtCurrentTarget)
        assertEquals(0, stats.remaining)
        assertEquals(8, progressAnalytics(history, 2, now, zone, weekCount = 8).weeks.size)
        assertNull(progressAnalytics(history, 0, now, zone).weeksAtCurrentTarget)
    }

    @Test fun weekBoundariesFollowLocalTimeAcrossDaylightSaving() {
        val local = TimeZone.getTimeZone("America/New_York")
        val monday = PlanDates.millis("2027-03-15", local.id)
        val attempts = listOf(
            attempt("sunday", "2027-01-10").copy(finishedAt = monday - 1),
            attempt("monday", "2027-01-11").copy(finishedAt = monday),
        )
        val archive = WorkoutHistoryArchive(attempts, remoteKnown = true)
        val stats = progressAnalytics(progressHistory("A", "A", archive, monday), 3, monday, local)
        val previous = stats.weeks[2]
        assertEquals(167 * HOUR, previous.endExclusive - previous.start)
        assertEquals(monday, previous.endExclusive)
        assertEquals(1, previous.completed)
        assertEquals(1, stats.currentWeek.completed)
        assertEquals(2, stats.remaining)
    }

    @Test fun retakesStayInFirstWeekButPerformedExercisesCountActualAttempts() {
        val first = attempt("first", "2027-01-10", logical = "session", version = "original")
        val retake = attempt("retake", "2027-01-12", logical = "session", version = "edited")
            .copy(exercises = listOf(AttemptExercise("one", true), AttemptExercise("two", true)))
        val other = attempt("other", "2027-01-13")
        val history = recorded(retake, first, retake, other)
        val stats = progressAnalytics(history, 3, now, zone)
        assertEquals(listOf(0, 0, 1, 1), stats.weeks.map { it.completed })
        assertEquals(2, stats.totalSessions)
        assertEquals(4, stats.performedExercises)
        val archive = requireNotNull(history.archive)
        assertEquals(listOf("other", "retake", "first"), archive.attempts.map { it.id })
        assertEquals(workoutHistorySummary(archive, 3, now, zone).unique, stats.currentWeek.completed)
    }

    @Test fun activePlanMatchesStableAndRuntimeIdsThroughTheRevisionLineage() {
        val original = plan("original")
        val revision = plan("edit", revision = "original")
        val active = plan("current", revision = "edit")
        val sibling = plan("sibling", revision = "original")
        val other = plan("unrelated")
        val history = recorded(
            attempt("first", "2027-01-11", logical = "first", version = "original"),
            attempt("retake", "2027-01-12", logical = "first", version = "current"),
            attempt("legacy-runtime", "2027-01-13", logical = "edit~today", version = "edit"),
            attempt("other-plan", "2027-01-13", logical = "past", version = "unrelated"),
            attempt("sibling-record", "2027-01-12", logical = "sibling~last", version = "sibling"),
            attempt("future-record", "2027-01-15", logical = "future", version = "current"),
        )
        val archive = PersonalPlanArchive(active.id, listOf(original, revision, active, sibling, other))
        val stats = requireNotNull(activePlanProgress("A", archive, history, now))
        assertEquals(active, stats.plan)
        assertEquals(5, stats.scheduled)
        assertEquals(3, stats.completed)
        assertEquals(1, stats.upcoming)
        assertEquals(1, stats.pastIncomplete)
        val reactivated = activePlanProgress("A", archive.copy(activeId = original.id), history, now)
        assertEquals(stats.completed, reactivated?.completed)
    }

    @Test fun todayAndFutureAreNeverMissedAndMissingPlansHaveNoProgress() {
        val plan = plan("active")
        val archive = PersonalPlanArchive(plan.id, listOf(plan))
        val verified = requireNotNull(activePlanProgress("A", archive, recorded(), now))
        assertEquals(3, verified.upcoming)
        assertEquals(2, verified.pastIncomplete)
        assertEquals(0, verified.completed)
        val missing = ProgressHistory(ProgressLoad.Unavailable)
        val partial = requireNotNull(activePlanProgress("A", archive, missing, now))
        assertNull(partial.completed)
        assertNull(partial.pastIncomplete)
        assertNull(activePlanProgress("A", PersonalPlanArchive(), missing, now))
        assertNull(activePlanProgress("B", archive, recorded(), now))
    }

    @Test fun removedSessionsAreIgnoredAndRescheduledSessionsUseTheirCurrentDate() {
        val original = plan("original")
        val edited = plan("edited", revision = original.id).copy(sessions = listOf(
            session("today", "2027-01-15"), session("last", "2027-01-12"),
        ))
        val history = recorded(attempt("removed", "2027-01-12", logical = "first", version = original.id))
        val stats = requireNotNull(activePlanProgress("A",
            PersonalPlanArchive(edited.id, listOf(original, edited)), history, now))
        assertEquals(0, stats.completed)
        assertEquals(1, stats.upcoming)
        assertEquals(1, stats.pastIncomplete)
    }

    @Test fun loadingOfflineAndReadFailureAreDistinctFromVerifiedZero() {
        val archive = WorkoutHistoryArchive()
        val states = listOf(
            progressHistory("A", "A", archive, now, loading = true),
            progressHistory("A", "A", archive, now, failed = true),
            progressHistory("A", "A", archive, now),
        )
        assertEquals(listOf(ProgressLoad.Loading, ProgressLoad.Failed, ProgressLoad.Unavailable), states.map { it.load })
        states.forEach {
            val stats = progressAnalytics(it, 3, now, zone)
            assertTrue(stats.weeks.all { week -> week.completed == null })
            assertNull(stats.totalSessions)
            assertNull(stats.performedExercises)
            assertNull(stats.remaining)
        }
        val verified = recorded()
        assertEquals(ProgressLoad.Ready, verified.load)
        assertEquals(listOf(0, 0, 0, 0), progressAnalytics(verified, 3, now, zone).weeks.map { it.completed })
        val local = progressHistory("A", "A", WorkoutHistoryArchive(listOf(attempt("current", "2027-01-12"))), now)
        assertEquals(listOf(null, null, null, 1), progressAnalytics(local, 3, now, zone).weeks.map { it.completed })
    }

    @Test fun switchingUsersRemovesOtherAttemptsConfirmationsAndTotals() {
        val a = attempt("only-A", "2027-01-10")
        val retry = attempt("only-A-retry", "2027-01-12")
        val b = attempt("only-B", "2027-01-11").copy(uid = "B")
        val c = attempt("only-A-confirmed", "2027-01-11")
        val archive = WorkoutHistoryArchive(
            listOf(a, b, retry, c), setOf(a.id, c.id, b.id),
            listOf(WorkoutConfirmation("B", a.id, "coach", now), WorkoutConfirmation("A", c.id, "coach", now)), true,
        )
        val aArchive = requireNotNull(progressHistory("A", "A", archive, now).archive)
        assertEquals(listOf(retry, c, a), aArchive.attempts)
        assertEquals(AttemptState.LocalPending, aArchive.state(retry))
        assertEquals(AttemptState.SelfReported, aArchive.state(a))
        assertEquals(AttemptState.Confirmed, aArchive.state(c))
        assertNull(progressHistory("B", "A", archive, now).archive)
        assertNull(progressHistory(null, "A", archive, now).archive)
        val bArchive = requireNotNull(progressHistory("B", "B", archive, now).archive)
        assertEquals(listOf(b), bArchive.attempts)
        assertTrue(bArchive.confirmations.isEmpty())
        assertFalse(bArchive.remoteKnown)
    }

    private fun day(date: String) = PlanDates.millis(date, zone.id)

    private fun attempt(id: String, date: String, logical: String = id, version: String = "") = WorkoutAttempt(
        id, "A", logical, version, "workout", "Buổi tập", "Workout",
        day(date) - HOUR, day(date), listOf(AttemptExercise("one", true), AttemptExercise("two", false)),
    )

    private fun recorded(vararg attempts: WorkoutAttempt) =
        progressHistory("A", "A", WorkoutHistoryArchive(attempts.toList(), remoteKnown = true), now)

    private fun plan(id: String, revision: String? = null) = PersonalPlan(
        id, "A", PersonalPlanConfig(WorkoutLevel.Beginner, 3, "2027-01-11", zone.id),
        listOf(session("first", "2027-01-11"), session("past", "2027-01-13"), session("today", "2027-01-14"),
            session("future", "2027-01-15"), session("last", "2027-01-17")),
        repeated = false, revisionOf = revision,
    )

    private fun session(id: String, date: String) = PersonalPlanSession(id, date, "template", listOf("one"))

    private companion object {
        const val HOUR = 3_600_000L
    }
}
