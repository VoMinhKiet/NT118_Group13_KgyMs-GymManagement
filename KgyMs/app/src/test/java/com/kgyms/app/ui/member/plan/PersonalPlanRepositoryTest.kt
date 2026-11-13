package com.kgyms.app.ui.member.plan

import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.appendPersonalPlan
import com.kgyms.app.utils.PersonalPlanGenerator as Generator
import com.kgyms.app.data.repository.MemoryPersonalPlanRepository
import com.kgyms.app.utils.ExerciseEditScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PersonalPlanRepositoryTest {
    private val zone = "Asia/Ho_Chi_Minh"
    private val now = PlanDates.millis("2027-01-01", zone)
    private fun plan(uid: String = "A", count: Int = 3) = Generator.seal(Generator.generate(uid,
        PersonalPlanConfig(WorkoutLevel.Beginner, count, "2027-01-01", zone), WorkoutReferenceCatalog.snapshot(), now))
    @Test fun appendingNewVersionPreservesOldPlanAndRetryIsIdempotent() {
        val a = plan()
        val first = appendPersonalPlan(PersonalPlanArchive(), a, "A", null)
        assertEquals(first, appendPersonalPlan(first, a, "A", null))
        val b = plan(count = 4)
        val second = appendPersonalPlan(first, b, "A", first.activeId)
        assertEquals(listOf(a, b), second.plans)
        assertEquals(b.id, second.activeId)
    }
    @Test fun wrongUidCollisionAndStaleActivePointerAreRejected() {
        val a = plan()
        val first = appendPersonalPlan(PersonalPlanArchive(), a, "A", null)
        assertTrue(runCatching { appendPersonalPlan(first, plan("B"), "A", first.activeId) }.isFailure)
        assertTrue(runCatching { appendPersonalPlan(first, a.copy(repeated = !a.repeated), "A", first.activeId) }.isFailure)
        assertTrue(runCatching { appendPersonalPlan(first, plan(count = 4), "A", null) }.isFailure)
    }
    @Test fun generatedSchedulesNeverIncludeConfirmedHistory() {
        val state = PersonalPlanState(com.kgyms.app.data.repository.MemoryPersonalPlanRepository("A"))
        val base = WorkoutReferenceCatalog.snapshot()
        assertFalse(state.catalog(base).historyAvailable)
        assertTrue(base.sessions.isEmpty())
        assertTrue(plan().sessions.none { it.id.isBlank() })
    }
    @Test fun versionNumbersFollowAppendOrderAndSurviveReloadWithoutChangingPlans() = runBlocking {
        val repo = MemoryPersonalPlanRepository("A")
        val state = PersonalPlanState(repo); state.load()
        assertNull(state.versionNumber("missing"))
        val first = plan(); repo.save(first, null); state.load()
        assertEquals(1, state.versionNumber(first.id))
        val second = plan(count = 4); repo.save(second, first.id); state.load()
        assertEquals(1, state.versionNumber(first.id)); assertEquals(2, state.versionNumber(second.id))
        val reopened = PersonalPlanState(repo); reopened.load()
        assertEquals(listOf(first, second), reopened.archive.plans)
        // Sorting for presentation does not assign the numbers.
        assertEquals(listOf(2, 1), reopened.archive.plans.reversed().map { reopened.versionNumber(it.id) })
    }
    @Test fun editedSessionGetsNextVersionNumberWithoutRenumberingEarlierPlans() = runBlocking {
        val repo = MemoryPersonalPlanRepository("A")
        val first = plan(); val second = plan(count = 4)
        repo.save(first, null); repo.save(second, first.id)
        val state = PersonalPlanState(repo); state.load()
        assertTrue(state.saveExercises(second, second.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, WorkoutReferenceCatalog.snapshot(), second.id))
        val third = state.active!!
        assertEquals(3, state.versionNumber(third.id))
        assertEquals(listOf(first, second), state.archive.plans.take(2))
        assertEquals(second.sessions.map { it.id }, third.sessions.map { it.id })
        assertEquals(listOf(1, 2, 3), state.archive.plans.map { state.versionNumber(it.id) })
    }
    @Test fun changingActivePlanDoesNotChangeVersionNamesOrContent() = runBlocking {
        val repo = MemoryPersonalPlanRepository("A")
        val first = plan(); val second = plan(count = 4)
        repo.save(first, null); repo.save(second, first.id)
        val state = PersonalPlanState(repo); state.load()
        val before = state.archive.plans
        repo.save(first, second.id); state.load()
        assertEquals(first.id, state.active!!.id)
        assertEquals(before, state.archive.plans)
        assertEquals(1, state.versionNumber(first.id)); assertEquals(2, state.versionNumber(second.id))
    }
    @Test fun accountsHaveIndependentVersionSequences() = runBlocking {
        val a = MemoryPersonalPlanRepository("A"); val b = MemoryPersonalPlanRepository("B")
        val a1 = plan(); val a2 = plan(count = 4); val b1 = plan("B")
        a.save(a1, null); a.save(a2, a1.id); b.save(b1, null)
        val stateA = PersonalPlanState(a); stateA.load()
        val stateB = PersonalPlanState(b); stateB.load()
        assertEquals(2, stateA.versionNumber(a2.id)); assertEquals(1, stateB.versionNumber(b1.id))
        assertNull(stateA.versionNumber(b1.id)); assertNull(stateB.versionNumber(a1.id))
    }
}
