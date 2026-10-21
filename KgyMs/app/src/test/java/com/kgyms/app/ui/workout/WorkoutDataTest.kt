package com.kgyms.app.ui.workout

import org.junit.Assert.*
import org.junit.Test

class WorkoutDataTest {
    @Test fun searchAcceptsVietnameseWithoutAccentsAndEnglish() {
        assertEquals("press", searchExercises("day nguc", "", "", "").single().id)
        assertEquals("row", searchExercises("cable row", "", "", "").single().id)
    }
    @Test fun filtersIntersectAndMayReturnEmpty() {
        assertEquals(listOf("squat"), searchExercises("", "Legs", "Beginner", "Dumbbells").map { it.id })
        assertTrue(searchExercises("", "Chest", "Intermediate", "").isEmpty())
    }
    @Test fun allSessionReferencesResolveAndIdsAreUnique() {
        val ids = WorkoutMockData.exercises.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        WorkoutMockData.sessions.forEach { session ->
            assertTrue(session.exercises.all { it in ids })
            assertEquals(session.exercises.size, session.exercises.distinct().size)
            assertTrue(session.dayOffset in 0..6)
        }
    }
    @Test fun trackingIsReversibleAndIsolatedBySessionAndWeek() {
        val first = trackingKey(1L, "first", "press")
        val second = trackingKey(1L, "second", "press")
        val nextWeek = trackingKey(2L, "first", "press")
        val marks = toggleWorkoutMark(emptyList(), first)
        assertFalse(second in marks)
        assertFalse(nextWeek in marks)
        assertTrue(toggleWorkoutMark(marks, first).isEmpty())
        assertEquals(SessionStatus.Confirmed, WorkoutMockData.sessions.first().status)
    }
}
