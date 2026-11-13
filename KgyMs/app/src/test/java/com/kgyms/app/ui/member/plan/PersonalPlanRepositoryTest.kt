package com.kgyms.app.ui.member.plan

import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.appendPersonalPlan
import com.kgyms.app.utils.PersonalPlanGenerator as Generator
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
}
