package com.kgyms.app.ui.member.plan

import com.kgyms.app.data.model.PersonalPlan
import com.kgyms.app.data.model.PersonalPlanAccessDenied
import com.kgyms.app.data.model.PersonalPlanArchive
import com.kgyms.app.data.model.PersonalPlanConfig
import com.kgyms.app.data.model.PersonalPlanOffline
import com.kgyms.app.data.model.PersonalPlanSyncConflict
import com.kgyms.app.data.model.PlanDates
import com.kgyms.app.data.model.WorkoutLevel
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.ActivePersonalPlanRepository
import com.kgyms.app.data.repository.PersonalPlanRepository
import com.kgyms.app.utils.PersonalPlanGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalPlanActivationTest {
    private val catalog = WorkoutReferenceCatalog.snapshot()
    private fun plan(id: String): PersonalPlan {
        val date = "2026-11-16"
        val generated = PersonalPlanGenerator.generate(
            "member-a",
            PersonalPlanConfig(WorkoutLevel.Beginner, 2, date, "Asia/Ho_Chi_Minh"),
            catalog,
            PlanDates.millis(date, "Asia/Ho_Chi_Minh"),
        )
        return generated.copy(id = id, sessions = generated.sessions.mapIndexed { index, session -> session.copy(id = "$id-$index") })
    }

    @Test fun successfulActivationPublishesOnlyRepositoryConfirmedArchive() = runBlocking {
        val first = plan("first"); val second = plan("second")
        val repository = ActivationRepository(PersonalPlanArchive(first.id, listOf(first, second)))
        val state = PersonalPlanState(repository)
        state.load()

        assertTrue(state.selectActive(second.id))
        assertEquals(second.id, state.active?.id)
        assertTrue(state.activationSucceeded)
        assertNull(state.activationFailure)
        assertEquals(1, repository.calls)
    }

    @Test fun offlineFailureKeepsCurrentPlanAndDoesNotPublishSuccess() = runBlocking {
        verifyFailure(PersonalPlanOffline(), PlanActivationFailure.Offline)
    }

    @Test fun conflictKeepsCurrentPlanAndDoesNotPublishSuccess() = runBlocking {
        verifyFailure(PersonalPlanSyncConflict("stale revision"), PlanActivationFailure.Conflict)
    }

    @Test fun accessDeniedAndUnexpectedErrorsNeverEscapeUiOperation() = runBlocking {
        verifyFailure(PersonalPlanAccessDenied(), PlanActivationFailure.AccessDenied)
        verifyFailure(IllegalStateException("repository failure"), PlanActivationFailure.Failed)
    }

    @Test fun repeatedTapWhileActivationIsRunningStartsOnlyOneOperation() = runBlocking {
        val first = plan("first"); val second = plan("second")
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val repository = ActivationRepository(PersonalPlanArchive(first.id, listOf(first, second))).apply {
            beforeSelect = { entered.complete(Unit); gate.await() }
        }
        val state = PersonalPlanState(repository)
        state.load()

        val firstRequest = async { state.selectActive(second.id) }
        entered.await()
        assertFalse(state.selectActive(second.id))
        assertEquals(1, repository.calls)
        gate.complete(Unit)
        assertTrue(firstRequest.await())
        assertEquals(second.id, state.active?.id)
    }

    private suspend fun verifyFailure(error: Exception, expected: PlanActivationFailure) {
        val first = plan("first"); val second = plan("second")
        val repository = ActivationRepository(PersonalPlanArchive(first.id, listOf(first, second))).apply { failure = error }
        val state = PersonalPlanState(repository)
        state.load()

        assertFalse(state.selectActive(second.id))
        assertEquals(first.id, state.active?.id)
        assertEquals(expected, state.activationFailure)
        assertFalse(state.activationSucceeded)
    }

    private class ActivationRepository(
        private var archive: PersonalPlanArchive,
    ) : PersonalPlanRepository, ActivePersonalPlanRepository {
        override val uid = "member-a"
        var calls = 0
        var failure: Exception? = null
        var beforeSelect: (suspend () -> Unit)? = null
        override suspend fun load() = archive
        override suspend fun save(plan: PersonalPlan, expectedActiveId: String?) = error("Not used")
        override suspend fun selectActive(id: String): PersonalPlanArchive {
            calls++
            beforeSelect?.invoke()
            failure?.let { throw it }
            check(archive.plans.any { it.id == id })
            return archive.copy(activeId = id).also { archive = it }
        }
    }
}
