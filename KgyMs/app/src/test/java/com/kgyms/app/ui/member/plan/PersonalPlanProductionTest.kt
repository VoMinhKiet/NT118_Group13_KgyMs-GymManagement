package com.kgyms.app.ui.member.plan

import com.kgyms.app.data.firebase.PersonalPlanProductionPolicy as Policy
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.PersonalPlanSyncCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class PersonalPlanProductionTest {
    @Test fun endpointCanOnlyAddressOneOwnerOnTheExistingDatabase() {
        assertEquals("https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/memberWorkoutPlans/A.json",Policy.endpoint("A"))
        listOf("", "A/B", "../users", "A.json?shallow=true", "A#", "a".repeat(129)).forEach {
            assertTrue(runCatching { Policy.endpoint(it) }.isFailure)
        }
    }
    @Test fun tokenMustMatchProductionProjectIssuerAndCurrentUid() {
        Policy.requireIdentity("kgyms-72c74","https://securetoken.google.com/kgyms-72c74","A","A")
        assertTrue(runCatching { Policy.requireIdentity("demo-kgyms-personal-plans","https://securetoken.google.com/demo-kgyms-personal-plans","A","A") }.isFailure)
        assertTrue(runCatching { Policy.requireIdentity("kgyms-72c74","https://other.example","A","A") }.isFailure)
        assertTrue(runCatching { Policy.requireIdentity("kgyms-72c74","https://securetoken.google.com/kgyms-72c74","B","A") }.isFailure)
    }
    private class Fixture {
        val signals=MutableStateFlow(PersonalPlanSyncSignal(true,0,1))
        val edits=MutableSharedFlow<Unit>(extraBufferCapacity=1)
        var revision:Long?=null
        var status=PersonalPlanSyncStatus.Pending
        var calls=0
        suspend fun settle() { withTimeout(2_000) { while(calls==0) delay(5) }; delay(30) }
        fun run(scope:CoroutineScope, result:PersonalPlanSyncStatus=PersonalPlanSyncStatus.Synced, delays:List<Long> = emptyList()):Job = scope.launch {
            PersonalPlanSyncCoordinator(delays,0).run(signals,edits,{revision},{status}) {
                calls++; revision=signals.value.revision; status=result
            }
        }
    }
    @Test fun initialSyncAndNewRemoteRevisionWakeOnceWithoutPeriodicArchivePolling() = runBlocking {
        val f=Fixture(); val job=f.run(this)
        try {
            f.settle(); assertEquals(1,f.calls)
            f.signals.value=f.signals.value.copy(revision=1)
            withTimeout(2_000){while(f.calls<2)delay(5)}
            delay(50); assertEquals(2,f.calls)
            f.signals.value=f.signals.value.copy(revision=0) // a stale cached callback cannot restart sync
            delay(30); assertEquals(2,f.calls)
        } finally { job.cancelAndJoin() }
    }
    @Test fun offlineEditWaitsForReconnectAndCancellationStopsAutomaticRequests() = runBlocking {
        val f=Fixture(); f.signals.value=PersonalPlanSyncSignal(false,0,0)
        val job=f.run(this)
        delay(30); f.edits.emit(Unit); delay(30); assertEquals(0,f.calls)
        f.signals.value=PersonalPlanSyncSignal(true,0,1); f.settle(); assertEquals(1,f.calls)
        job.cancelAndJoin()
        f.signals.value=PersonalPlanSyncSignal(true,10,2); f.edits.emit(Unit); delay(30)
        assertEquals(1,f.calls)
    }
    @Test fun localSaveSchedulesSyncWithoutWaitingForARevisionChange() = runBlocking {
        val f=Fixture(); val job=f.run(this)
        try { f.settle(); f.status=PersonalPlanSyncStatus.Pending; f.edits.emit(Unit)
            withTimeout(2_000){while(f.calls<2)delay(5)}; assertEquals(2,f.calls)
        } finally { job.cancelAndJoin() }
    }
    @Test fun transientOfflineRetryIsBoundedAndPermissionDenialIsNotPolled() = runBlocking {
        val f=Fixture(); val job=f.run(this,PersonalPlanSyncStatus.Pending,listOf(5,10,15))
        try { withTimeout(2_000){while(f.calls<4)delay(5)}; delay(50); assertEquals(4,f.calls) }
        finally { job.cancelAndJoin() }
        val denied=Fixture(); val deniedJob=denied.run(this,PersonalPlanSyncStatus.AccessDenied,listOf(5,10,15))
        try { denied.settle(); delay(50); assertEquals(1,denied.calls); assertEquals(PersonalPlanSyncStatus.AccessDenied,denied.status) }
        finally { deniedJob.cancelAndJoin() }
    }
    @Test fun activeConflictDoesNotAutomaticallyRetryAnOldChoice() = runBlocking {
        val f=Fixture(); val job=f.run(this,PersonalPlanSyncStatus.Conflict,listOf(5,10,15))
        try { f.settle(); delay(50); assertEquals(1,f.calls) } finally { job.cancelAndJoin() }
    }
}
