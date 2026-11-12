package com.kgyms.app.ui.member.workout

import com.kgyms.app.data.mock.MockMemberWorkoutDataSource
import com.kgyms.app.data.mock.ReferenceMemberWorkoutDataSource
import com.kgyms.app.utils.calculateWeeklyGoal
import com.kgyms.app.utils.confirmedRecords
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WorkoutStabilityTest {
    private val zone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    private fun at(year: Int, month: Int, day: Int) = Calendar.getInstance(zone).apply {
        clear(); set(year, month - 1, day, 12, 0, 0)
    }.timeInMillis
    private val now = at(2027, 1, 1)
    private val catalog get() = MockMemberWorkoutDataSource.snapshot(now)

    @Test fun finishIsImmediateKeepsReviewAndRestartClearsOnlyThisRun() {
        val a = workoutRunKey("session:A", 1)
        val b = workoutRunKey("session:B", 1)
        val finished = WorkoutRehearsal().start(a).toggle(a, "press").start(b).toggle(b, "press").finish(a)
        assertFalse(a in finished.active)
        assertTrue(a in finished.finished)
        assertTrue(finished.isDone(a, "press"))
        assertEquals(finished, finished.toggle(a, "press"))
        val restart = finished.start(a)
        assertTrue(a in restart.active)
        assertFalse(a in restart.finished)
        assertFalse(restart.isDone(a, "press"))
        assertTrue(restart.isDone(b, "press"))
    }

    @Test fun inactiveRunCannotFinishOrBeMarked() {
        val empty = WorkoutRehearsal()
        assertEquals(empty, empty.finish("missing"))
        assertEquals(empty, empty.toggle("missing", "press"))
    }

    @Test fun twoSessionsWithSameWorkoutResolveIndependentlyFromPractice() {
        val first = catalog.sessions.first().copy(id = "A")
        val second = first.copy(id = "B", scheduledAt = first.scheduledAt + 1)
        val data = catalog.copy(sessions = listOf(first, second))
        assertEquals(first, data.destination("session:A").session)
        assertEquals(second, data.destination("session:B").session)
        assertEquals(data.destination("session:A").workout, data.destination("session:B").workout)
        assertNull(data.destination("workout:${first.workoutId}").session)
        assertNotEquals(workoutRunKey("session:A", 1), workoutRunKey("session:B", 1))
        assertEquals(workoutRunKey("session:A", 1), workoutRunKey("session:A", 2))
        assertNotEquals(workoutRunKey("workout:${first.workoutId}", 1), workoutRunKey("workout:${first.workoutId}", 2))
    }

    @Test fun invalidAndMissingReferencesDoNotResolveToAnotherWorkout() {
        assertNull(catalog.destination("workout:missing").workout)
        assertNull(catalog.destination("session:missing").workout)
        val broken = catalog.copy(sessions = listOf(catalog.sessions.first().copy(workoutId = "missing")))
        assertNull(broken.destination("session:${broken.sessions.first().id}").workout)
        assertNull(catalog.copy(workouts = emptyList(), sessions = emptyList()).destination("workout:upper-body").workout)
    }

    @Test fun duplicateIdsAndExerciseReferencesAreSafeForLazyLists() {
        val data = catalog.copy(exercises = catalog.exercises + catalog.exercises,
            workouts = catalog.workouts.map { it.copy(exerciseIds = it.exerciseIds + it.exerciseIds) } + catalog.workouts,
            sessions = catalog.sessions + catalog.sessions, messages = catalog.messages + catalog.messages).safeCatalog()
        assertEquals(catalog.exercises, data.exercises)
        assertEquals(catalog.workouts, data.workouts)
        assertEquals(catalog.sessions, data.sessions)
        assertEquals(catalog.messages, data.messages)
    }

    @Test fun weekFilteringIncludesDecemberAndJanuaryButExcludesOtherYearsAndNextMonday() {
        val template = catalog.sessions.first()
        val data = catalog.copy(sessions = listOf(
            template.copy(id = "previous", scheduledAt = at(2026, 12, 27)),
            template.copy(id = "monday", scheduledAt = at(2026, 12, 28)),
            template.copy(id = "friday", scheduledAt = now),
            template.copy(id = "sunday", scheduledAt = at(2027, 1, 3)),
            template.copy(id = "next", scheduledAt = at(2027, 1, 4)),
            template.copy(id = "other-year", scheduledAt = at(2026, 1, 1)),
        ))
        assertEquals(listOf("monday", "friday", "sunday"), data.weekSessions(now, zone).map { it.id })
    }

    @Test fun referenceHistoryIsUnavailableAndLocalCompletionCannotChangeIt() {
        val data = ReferenceMemberWorkoutDataSource.snapshot(now)
        val before = data.confirmedRecords()
        WorkoutRehearsal().start("run").toggle("run", "press").finish("run")
        assertFalse(data.historyAvailable)
        assertTrue(data.sessions.isEmpty())
        assertEquals(before, data.confirmedRecords())
        assertEquals(0, calculateWeeklyGoal(6, data.confirmedRecords(), now).completed)
        assertTrue(catalog.historyAvailable)
    }
}
