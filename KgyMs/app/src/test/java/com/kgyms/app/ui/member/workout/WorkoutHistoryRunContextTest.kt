package com.kgyms.app.ui.member.workout

import com.kgyms.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutHistoryRunContextTest {
    private val workout = WorkoutSummaryUiModel(
        id = "full-body",
        title = WorkoutText("Toàn thân", "Full body"),
        durationMinutes = 40,
        calories = 0,
        level = WorkoutLevel.Beginner,
        category = WorkoutCategory.Strength,
        thumbnailRes = 0,
        exerciseIds = listOf("squat", "row"),
        tags = emptyList(),
    )

    @Test fun localMidnightChangesTheProposedPracticeKeyButPersistedDraftStillWins() {
        val zone = "Asia/Ho_Chi_Minh"
        val before = PlanDates.millis("2026-11-15", zone) + 23 * 60 * 60 * 1000L + 59 * 60 * 1000L
        val after = before + 2 * 60 * 1000L
        val started = workoutHistoryRunContext(workout, null, null, workout.exerciseIds, before, zone)
        val proposedAfterMidnight = workoutHistoryRunContext(workout, null, null, workout.exerciseIds, after, zone)
        assertEquals("practice:2026-11-15:full-body", started.key)
        assertEquals("practice:2026-11-16:full-body", proposedAfterMidnight.key)

        val draft = WorkoutDraft(started, "attempt-one", before, setOf("squat"))
        val archive = WorkoutHistoryArchive(drafts = mapOf(started.key to draft))
        assertEquals(draft, archive.activeDraft(proposedAfterMidnight))
    }

    @Test fun plannedSessionUsesStableLogicalIdAcrossImmutablePlanRevision() {
        val session = PersonalPlanSession("logical-session", "2026-11-16", "full-body", workout.exerciseIds)
        val original = PersonalPlan("plan-1", "A", PersonalPlanConfig(WorkoutLevel.Beginner, 1,
            "2026-11-16", "UTC", listOf(0)), listOf(session), false)
        val revision = original.copy(id = "plan-2", revisionOf = original.id)
        val runtime = revision.runtimeSessionId(session)
        val uiSession = WorkoutSessionUiModel(runtime, "personal-workout-$runtime", 0,
            SessionStatus.Planned, PlanSource.Self)

        val context = workoutHistoryRunContext(workout, uiSession,
            PersonalPlanArchive(revision.id, listOf(original, revision)), workout.exerciseIds, 0, "UTC")
        assertEquals(session.id, context.logicalSessionId)
        assertEquals(revision.id, context.planVersionId)
    }
}
