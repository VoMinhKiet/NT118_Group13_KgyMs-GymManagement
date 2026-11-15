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

    @Test fun practiceStartedBeforeMidnightKeepsDraftIdentityAndFinishesOnceAfterMidnight()=runBlocking {
        val zone="UTC";val before=PlanDates.millis("2026-11-15",zone)+86_390_000L;val after=before+20_000L
        val old=run("practice:2026-11-15:full-body","practice:2026-11-15:full-body","")
        val next=run("practice:2026-11-16:full-body","practice:2026-11-16:full-body","")
        val repo=WorkoutHistoryRepository("A",Store());repo.load();repo.begin(old,before);repo.mark(old.key,"row")

        val resumed=repo.archive.value.activeDraft(next)
        assertEquals(old,resumed?.context);assertEquals(setOf("row"),resumed?.performed)
        val attempt=repo.finish(checkNotNull(resumed).context.key,after)
        assertEquals(old.logicalSessionId,attempt.logicalSessionId)
        assertEquals(before,attempt.startedAt);assertEquals(after,attempt.finishedAt)
        assertEquals(attempt,repo.archive.value.displayedCompletion(next,after,zone))
        assertEquals(1,repo.archive.value.attempts.size)
    }

    @Test fun processRestartRestoresTheSameActiveAttemptAndCheckedExercises()=runBlocking {
        val store=Store();val first=WorkoutHistoryRepository("A",store);first.load()
        val context=run("practice:2026-11-15:full-body","practice:2026-11-15:full-body","")
        val draft=first.begin(context,50);first.mark(context.key,"squat")

        val reopened=WorkoutHistoryRepository("A",store);reopened.load()
        val restored=reopened.archive.value.activeDraft(context)
        assertEquals(draft.attemptId,restored?.attemptId)
        assertEquals(50L,restored?.startedAt)
        assertEquals(setOf("squat"),restored?.performed)
    }

    @Test fun secondDeviceDerivesCompletionFromRemoteAttemptWithoutFinishedMarker()=runBlocking {
        val remote=Remote();val a=WorkoutHistoryRepository("A",Store(),remote);val b=WorkoutHistoryRepository("A",Store(),remote)
        a.load();b.load();val attempt=finish(a,"session:runtime-a","stable-session","plan-1",100);a.synchronize();b.synchronize()

        assertTrue(b.archive.value.finishedRuns.isEmpty())
        assertEquals(attempt,b.archive.value.completed("stable-session",101))
        assertEquals(1,workoutHistorySummary(b.archive.value,3,101,TimeZone.getTimeZone("UTC")).unique)
    }

    @Test fun remoteCompletionNeverDeletesOrOverridesALocalDraftForTheSameLogicalSession()=runBlocking {
        val remote=Remote();val a=WorkoutHistoryRepository("A",Store(),remote);val b=WorkoutHistoryRepository("A",Store(),remote)
        a.load();b.load();val localContext=run("session:revision-b","stable-session","plan-2")
        val draft=b.begin(localContext,90);b.mark(localContext.key,"row")
        val remoteAttempt=finish(a,"session:revision-a","stable-session","plan-1",100);a.synchronize();b.synchronize()

        assertEquals(draft.attemptId,b.archive.value.activeDraft(localContext)?.attemptId)
        assertEquals(setOf("row"),b.archive.value.activeDraft(localContext)?.performed)
        assertEquals(remoteAttempt,b.archive.value.completed("stable-session",101))
        val localAttempt=b.finish(localContext.key,110)
        assertNotEquals(remoteAttempt.id,localAttempt.id)
        assertEquals(1,workoutHistorySummary(b.archive.value,3,111,TimeZone.getTimeZone("UTC")).unique)
    }

    @Test fun planVersionAndActivePlanChangesKeepLogicalCompletionVisible()=runBlocking {
        val repo=WorkoutHistoryRepository("A",Store());repo.load()
        val first=finish(repo,"session:plan-1-runtime","stable-session","plan-1",100)
        val revised=run("session:plan-2-runtime","stable-session","plan-2")
        assertEquals(first,repo.archive.value.completed(revised.logicalSessionId,101))
        val retake=finish(repo,revised.key,revised.logicalSessionId,revised.planVersionId,120)
        assertEquals(retake,repo.archive.value.completed("stable-session",121))
        assertEquals(1,workoutHistorySummary(repo.archive.value,4,121,TimeZone.getTimeZone("UTC")).unique)
    }

    @Test fun mondayStartsANewWeekWithoutDeletingSundayHistory() {
        val zone=TimeZone.getTimeZone("UTC")
        val sunday=PlanDates.millis("2026-11-15","UTC")+12*60*60*1000L
        val monday=PlanDates.millis("2026-11-16","UTC")+12*60*60*1000L
        fun attempt(id:String,logical:String,time:Long)=WorkoutAttempt(id,"A",logical,"plan","full-body","T","T",time-10,time,
            listOf(AttemptExercise("squat",true)))
        val archive=WorkoutHistoryArchive(attempts=listOf(attempt("sun","sun-session",sunday),attempt("mon","mon-session",monday)))

        val summary=workoutHistorySummary(archive,3,monday+1,zone)
        assertEquals(1,summary.unique);assertEquals(1,summary.attempts)
        assertEquals(2,archive.attempts.size)
    }
}
