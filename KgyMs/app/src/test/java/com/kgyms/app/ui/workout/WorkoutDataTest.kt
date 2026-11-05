package com.kgyms.app.ui.workout

import org.junit.Assert.*
import org.junit.Test

class WorkoutDataTest {
    @Test fun trackingIsReversibleAndIsolatedBySessionAndWeek() {
        val first = trackingKey(1L, "first", "press")
        val second = trackingKey(1L, "second", "press")
        val nextWeek = trackingKey(2L, "first", "press")
        val marks = toggleWorkoutMark(emptyList(), first)
        assertFalse(second in marks)
        assertFalse(nextWeek in marks)
        assertTrue(toggleWorkoutMark(marks, first).isEmpty())
    }
}
