package com.kgyms.app.ui.member.workout

import com.kgyms.app.utils.calculateWeeklyGoal
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import com.kgyms.app.data.mock.MockMemberWorkoutDataSource
import com.kgyms.app.data.model.SessionStatus
import com.kgyms.app.data.model.VideoProvider
import com.kgyms.app.data.model.WorkoutLevel
import com.kgyms.app.utils.confirmedRecords
import com.kgyms.app.utils.filterCatalogExercises
import com.kgyms.app.utils.toggleWorkoutMark
import com.kgyms.app.utils.trackingKey

class WorkoutCatalogTest {
    private val monday = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 19, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val friday = Calendar.getInstance().apply { timeInMillis = monday; add(Calendar.DAY_OF_YEAR, 4); set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
    @Test fun allLevelsHaveWorkoutsAndAllReferencesResolve() {
        val c = MockMemberWorkoutDataSource.snapshot(friday)
        WorkoutLevel.entries.forEach { level -> assertTrue(c.workouts.any { it.level == level }) }
        c.workouts.forEach { w -> assertTrue(w.exerciseIds.all { id -> c.exercises.any { it.id == id } }) }
        c.sessions.forEach { s -> assertTrue(c.workouts.any { it.id == s.workoutId }) }
        assertTrue(c.trainerPlan!!.sessionIds.all { id -> c.sessions.any { it.id == id && it.trainerId == c.trainer!!.id } })
    }
    @Test fun weeklyGoalUsesOnlyConfirmedCompletedSessions() {
        val c = MockMemberWorkoutDataSource.snapshot(friday)
        assertEquals(1, calculateWeeklyGoal(5,c.confirmedRecords(),friday).completed)
        val unconfirmed = c.copy(sessions = c.sessions.map { it.copy(confirmedAt = null) })
        assertEquals(0, calculateWeeklyGoal(5,unconfirmed.confirmedRecords(),friday).completed)
        val booked = c.copy(sessions = c.sessions.map { it.copy(status = SessionStatus.Booked) })
        assertEquals(0, calculateWeeklyGoal(5,booked.confirmedRecords(),friday).completed)
    }
    @Test fun openingPlansAndTogglingExercisesCannotChangeConfirmations() {
        val before = MockMemberWorkoutDataSource.snapshot(friday)
        toggleWorkoutMark(emptyList(),trackingKey(monday,"upper-body","press"))
        val after = MockMemberWorkoutDataSource.snapshot(friday)
        assertEquals(before.confirmedRecords(),after.confirmedRecords())
    }
    @Test fun weeklyTargetsRecalculateAndEarlyMondayHasNoFutureCompletion() {
        val c = MockMemberWorkoutDataSource.snapshot(friday)
        assertEquals(.2f,calculateWeeklyGoal(5,c.confirmedRecords(),friday).progress,.001f)
        assertEquals(.25f,calculateWeeklyGoal(4,c.confirmedRecords(),friday).progress,.001f)
        assertEquals(0,calculateWeeklyGoal(5,MockMemberWorkoutDataSource.snapshot(monday).confirmedRecords(),monday).completed)
    }
    @Test fun filtersCombineAndSearchSupportsBothLanguages() {
        val c = MockMemberWorkoutDataSource.snapshot(friday)
        assertEquals("press",filterCatalogExercises(c.exercises,"day nguc","Beginner","Chest","Strength").single().id)
        assertEquals("tempo-squat",filterCatalogExercises(c.exercises,"tempo","Advanced","Legs","Strength").single().id)
        assertTrue(filterCatalogExercises(c.exercises,"zzzz","","","").isEmpty())
    }
    @Test fun unavailableVideosNeverExposeFakePlayableUrls() {
        MockMemberWorkoutDataSource.snapshot(friday).exercises.forEach { assertNull(it.video.sourceUrl); assertEquals(VideoProvider.LocalPreview,it.video.provider) }
    }
}
