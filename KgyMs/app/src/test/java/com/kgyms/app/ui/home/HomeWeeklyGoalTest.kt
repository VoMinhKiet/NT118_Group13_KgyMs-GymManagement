package com.kgyms.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeWeeklyGoalTest {
    @Test fun normalWeeklyGoalHasHalfProgressAndThreeRemaining() {
        val goal = HomeWeeklyGoal(3, 6)
        assertEquals(0.5f, goal.progress, 0f)
        assertEquals(3, goal.remaining)
    }

    @Test fun exceededGoalNeverOverflowsTheProgressRing() {
        val goal = HomeWeeklyGoal(8, 6)
        assertEquals(1f, goal.progress, 0f)
        assertEquals(0, goal.remaining)
    }

    @Test fun unsetGoalDoesNotProduceNaNOrInfinity() {
        assertEquals(0f, HomeWeeklyGoal(0, 0).progress, 0f)
        assertEquals(0f, HomeWeeklyGoal(3, -1).progress, 0f)
    }
}
