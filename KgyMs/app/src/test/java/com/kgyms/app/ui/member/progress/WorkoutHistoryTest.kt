package com.kgyms.app.ui.member.progress

import com.kgyms.app.data.model.*
import com.kgyms.app.data.firebase.WorkoutHistoryCodec
import com.kgyms.app.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class WorkoutHistoryTest {
    private class Store:WorkoutHistoryStore {
        var value=WorkoutHistoryArchive()
        override suspend fun read()=value
        override suspend fun update(change:(WorkoutHistoryArchive)->WorkoutHistoryArchive):WorkoutHistoryArchive { value=change(value);return value }
    }
    private class Remote(override val uid:String="A"):WorkoutHistoryRemote {
        override val available=MutableStateFlow(true);override val changes=MutableStateFlow(0L)
        var attempts=listOf<WorkoutAttempt>();var confirmations=listOf<WorkoutConfirmation>();var denied=false;var calls=0;var lostAck=false
        var beforeFetch:(suspend()->Unit)?=null;var beforeAppend:(suspend()->Unit)?=null
        override suspend fun fetch():RemoteWorkoutHistory {beforeFetch?.invoke();if(denied)throw WorkoutHistoryDenied();if(!available.value)throw WorkoutHistoryOffline();return RemoteWorkoutHistory(attempts,confirmations)}
        override suspend fun append(attempt:WorkoutAttempt){beforeAppend?.invoke();if(denied)throw WorkoutHistoryDenied();check(attempts.none{it.id==attempt.id});attempts=attempts+attempt;calls++;if(lostAck){lostAck=false;throw WorkoutHistoryOffline()}}
    }
    private fun run(key:String="run",logical:String="logical",version:String="plan-1")=WorkoutRunContext(key,logical,version,"full-body","Toàn thân","Full body",listOf("squat","row"))
    private suspend fun finish(repo:WorkoutHistoryRepository,key:String="run",logical:String="logical",version:String="plan-1",time:Long=100):WorkoutAttempt {
        repo.begin(run(key,logical,version),time-10);repo.mark(key,"squat");return repo.finish(key,time)
    }
    @Test fun plannedOrActiveWorkoutDoesNotCreateAResultAndRepeatedFinishIsIdempotent()=runBlocking {
        val store=Store();val repo=WorkoutHistoryRepository("A",store);repo.load()
        assertTrue(repo.archive.value.attempts.isEmpty());repo.begin(run(),1);repo.mark("run","row")
        assertTrue(store.value.attempts.isEmpty());val first=repo.finish("run",2);val retry=repo.finish("run",3)
        assertEquals(first,retry);assertEquals(1,store.value.attempts.size);assertEquals(listOf("row"),first.exercises.filter{it.performed}.map{it.id})
    }
    @Test fun retakeAndPlanRevisionHaveNewAttemptsButOneLogicalSession()=runBlocking {
        val repo=WorkoutHistoryRepository("A",Store());repo.load()
        val a=finish(repo);val b=finish(repo);val c=finish(repo,"revised","logical","plan-2")
        assertEquals(3,setOf(a.id,b.id,c.id).size);assertEquals(setOf("logical"),repo.archive.value.attempts.map{it.logicalSessionId}.toSet())
        val summary=workoutHistorySummary(repo.archive.value,3,101,TimeZone.getTimeZone("UTC"))
        assertEquals(3,summary.attempts);assertEquals(1,summary.unique);assertNull(summary.confirmed)
    }
    @Test fun personalCompletionCountsPendingAndNeverDependsOnGymConfirmation()=runBlocking {
        val repo=WorkoutHistoryRepository("A",Store());repo.load()
        val first=finish(repo);finish(repo,"again","logical","plan-2")
        val pending=repo.archive.value
        val acknowledged=pending.copy(acknowledged=pending.attempts.map{it.id}.toSet(),remoteKnown=true)
        val confirmed=acknowledged.copy(confirmations=listOf(WorkoutConfirmation("A",first.id,"admin",101)))
        for(archive in listOf(pending,acknowledged,confirmed)) {
            val summary=workoutHistorySummary(archive,3,102,TimeZone.getTimeZone("UTC"))
            assertEquals(1,summary.unique);assertEquals(2,summary.attempts);assertEquals(1f/3,summary.progress,0.0001f)
        }
        assertEquals(AttemptState.LocalPending,pending.state(first))
        assertEquals(AttemptState.SelfReported,acknowledged.state(first))
        assertEquals(AttemptState.Confirmed,confirmed.state(first))
    }
    @Test fun offlineRestartThenOnlineRetainsPendingAndNeverPretendsToBeConfirmed()=runBlocking {
        val store=Store();val remote=Remote().apply{available.value=false};val repo=WorkoutHistoryRepository("A",store,remote);repo.load();val attempt=finish(repo)
        repo.synchronize();assertEquals(HistorySyncState.Pending,repo.status.value)
        val reopened=WorkoutHistoryRepository("A",store,remote);reopened.load();assertEquals(attempt,reopened.archive.value.attempts.single())
        remote.available.value=true;reopened.synchronize();assertEquals(AttemptState.SelfReported,reopened.archive.value.state(attempt))
        assertTrue(confirmedHistoryRecords(reopened.archive.value).orEmpty().isEmpty());assertEquals(0,workoutHistorySummary(reopened.archive.value,3,101).confirmed)
    }
    @Test fun permissionDeniedKeepsAllLocalDataAndRetryDoesNotDuplicate()=runBlocking {
        val store=Store();val remote=Remote().apply{denied=true};val repo=WorkoutHistoryRepository("A",store,remote);repo.load();val a=finish(repo)
        repo.synchronize();assertEquals(HistorySyncState.AccessDenied,repo.status.value);assertEquals(AttemptState.LocalPending,repo.archive.value.state(a))
        remote.denied=false;repo.synchronize();repo.synchronize();assertEquals(1,remote.calls);assertEquals(listOf(a),store.value.attempts)
    }
    @Test fun lostAcknowledgementIsResolvedByExactIdAndPayload()=runBlocking {
        val remote=Remote().apply{lostAck=true};val repo=WorkoutHistoryRepository("A",Store(),remote);repo.load();val a=finish(repo)
        repo.synchronize();assertEquals(HistorySyncState.Synced,repo.status.value);assertEquals(1,remote.calls);assertEquals(AttemptState.SelfReported,repo.archive.value.state(a))
    }
    @Test fun remoteMergeRetainsLocalPendingDraftAndASecondClientHistory()=runBlocking {
        val remote=Remote();val a=WorkoutHistoryRepository("A",Store(),remote);val b=WorkoutHistoryRepository("A",Store(),remote);a.load();b.load()
        val aa=finish(a,"a","a");a.synchronize();val bb=finish(b,"b","b");b.begin(run("active"),3);b.synchronize()
        assertEquals(setOf(aa.id,bb.id),b.archive.value.attempts.map{it.id}.toSet());assertTrue("active" in b.archive.value.drafts)
        a.synchronize();assertEquals(setOf(aa.id,bb.id),a.archive.value.attempts.map{it.id}.toSet())
    }
    @Test fun finishDuringNetworkSyncIsNotOverwrittenOrMarkedAcknowledged()=runBlocking {
        val remote=Remote();val repo=WorkoutHistoryRepository("A",Store(),remote);repo.load();finish(repo,"first","first")
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        remote.beforeAppend={entered.complete(Unit);release.await()}
        val sync=launch{repo.synchronize()};entered.await();val next=finish(repo,"new","new");release.complete(Unit);sync.join()
        assertEquals(2,repo.archive.value.attempts.size);assertEquals(AttemptState.LocalPending,repo.archive.value.state(next))
        remote.beforeAppend=null;repo.synchronize();assertEquals(2,remote.calls)
    }
    @Test fun closeDuringFetchDoesNotCommitAnotherOwnersData()=runBlocking {
        val store=Store();val remote=Remote();val repo=WorkoutHistoryRepository("A",store,remote);repo.load()
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();remote.beforeFetch={entered.complete(Unit);release.await()}
        val job=launch{repo.synchronize()};entered.await();repo.close();release.complete(Unit);job.join()
        assertFalse(store.value.remoteKnown);assertTrue(store.value.attempts.isEmpty())
    }
    @Test fun immutableCollisionPreservesBothSidesInsteadOfOverwriting()=runBlocking {
        val remote=Remote();val repo=WorkoutHistoryRepository("A",Store(),remote);repo.load();val local=finish(repo)
        remote.attempts=listOf(local.copy(titleEn="different"));repo.synchronize()
        assertEquals(HistorySyncState.Conflict,repo.status.value);assertEquals(local,repo.archive.value.attempts.single());assertEquals("different",remote.attempts.single().titleEn)
    }
    @Test fun onlyAValidatedConfirmationCanProduceConfirmedStatus()=runBlocking {
        val remote=Remote();val repo=WorkoutHistoryRepository("A",Store(),remote);repo.load();val a=finish(repo);repo.synchronize()
        assertEquals(AttemptState.SelfReported,repo.archive.value.state(a));remote.confirmations=listOf(WorkoutConfirmation("A",a.id,"admin",200));repo.synchronize()
        assertEquals(AttemptState.Confirmed,repo.archive.value.state(a));assertEquals(a.logicalSessionId,confirmedHistoryRecords(repo.archive.value)!!.single().sessionId)
    }
    @Test fun goalPriorityPreservesExplicitLegacyOverrideAndTracksActivePlan() {
        assertEquals(3,resolveWorkoutTarget(null,3));assertEquals(5,resolveWorkoutTarget(null,5));assertEquals(6,resolveWorkoutTarget(6,2));assertEquals(0,resolveWorkoutTarget(null,null))
    }
    @Test fun codecPreservesExercisesAndRejectsForgedConfirmationOrWrongOwner()=runBlocking {
        val repo=WorkoutHistoryRepository("A",Store());repo.load();val a=finish(repo);val encoded=WorkoutHistoryCodec.encode(a)
        assertEquals(a,WorkoutHistoryCodec.decode(encoded,"A",a.id))
        assertTrue(runCatching{WorkoutHistoryCodec.decode(encoded+("confirmed" to true),"A",a.id)}.isFailure)
        assertTrue(runCatching{WorkoutHistoryCodec.decode(encoded,"B",a.id)}.isFailure)
        assertTrue(runCatching{WorkoutHistoryCodec.decode(encoded+("exercises" to listOf(mapOf("id" to "x","performed" to true),mapOf("id" to "x","performed" to false))),"A",a.id)}.isFailure)
    }
}
