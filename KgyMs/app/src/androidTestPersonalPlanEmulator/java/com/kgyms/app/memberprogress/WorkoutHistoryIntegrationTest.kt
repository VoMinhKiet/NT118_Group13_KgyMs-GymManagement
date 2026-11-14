package com.kgyms.app.memberprogress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ServerValue
import com.kgyms.app.data.firebase.*
import com.kgyms.app.data.local.LocalWorkoutHistoryStore
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.*
import com.kgyms.app.ui.member.progress.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Demo project only; all permission assertions use ordinary authenticated SDK clients. */
class WorkoutHistoryIntegrationTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val group="m2c-${UUID.randomUUID()}"
    private val actors=mutableListOf<Actor>()
    private val jobs=mutableListOf<Job>()
    private val liveScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private class Memory:WorkoutHistoryStore {
        var value=WorkoutHistoryArchive()
        override suspend fun read()=value
        override suspend fun update(change:(WorkoutHistoryArchive)->WorkoutHistoryArchive):WorkoutHistoryArchive {value=change(value);return value}
    }
    private data class Actor(val demo:PersonalPlanEmulatorConnection,val remote:WorkoutHistoryFirebaseRemote,val repo:WorkoutHistoryRepository)
    private suspend fun actor(label:String="A",namespace:String=group,memory:Boolean=false):Actor {
        assertEquals(PersonalPlanEmulatorConnection.APPLICATION_ID,context.packageName)
        val demo=PersonalPlanEmulatorConnection.create(context);val uid=demo.signIn(label,namespace)
        val remote=WorkoutHistoryFirebaseRemote.emulator(context,demo,uid);remote.start()
        withTimeout(15_000){remote.available.first{it}}
        return Actor(demo,remote,WorkoutHistoryRepository(uid,if(memory)Memory() else LocalWorkoutHistoryStore(context,uid),remote)).also{actors+=it;it.repo.load()}
    }
    private fun run(key:String="session",logical:String="logical",version:String="plan-1")=WorkoutRunContext(key,logical,version,"full-body","Toàn thân","Full body",listOf("squat","row"))
    private suspend fun finish(repo:WorkoutHistoryRepository,key:String="session",logical:String="logical",version:String="plan-1"):WorkoutAttempt {
        repo.begin(run(key,logical,version),System.currentTimeMillis());repo.mark(key,"squat");return repo.finish(key,System.currentTimeMillis())
    }
    private suspend fun eventually(check:()->Boolean){withTimeout(20_000){while(!check())delay(25)}}
    @After fun cleanup()=runBlocking {jobs.forEach{it.cancelAndJoin()};liveScope.cancel();actors.forEach{it.repo.close();it.demo.close()}}
    @Test fun isolatedEnvironmentAndOwnerBoundariesNeverOpenProduction()=runBlocking<Unit> {
        assertNull(WorkoutHistoryFirebaseRemote.production(context,"A"));assertTrue(runCatching{FirebaseApp.getInstance()}.isFailure)
        val a=actor();val saved=finish(a.repo);a.repo.synchronize();assertEquals(AttemptState.SelfReported,a.repo.archive.value.state(saved))
        val b=actor("B");assertTrue(b.remote.fetch().attempts.isEmpty())
        assertTrue(runCatching{b.demo.database.getReference("memberWorkoutAttempts/${a.repo.uid}").get().await()}.isFailure)
        assertTrue(FirebaseApp.getApps(context).all{it.options.projectId==PersonalPlanEmulatorConnection.PROJECT})
    }
    @Test fun retryRetakeAndChangedPlanVersionKeepAllAttemptsAndOneUniqueSession()=runBlocking<Unit> {
        val a=actor();val first=finish(a.repo);a.repo.finish("session",System.currentTimeMillis());a.repo.synchronize();a.repo.synchronize()
        finish(a.repo);finish(a.repo,"version-2","logical","plan-2");a.repo.synchronize()
        val server=a.remote.fetch();assertEquals(3,server.attempts.size);assertEquals(first,server.attempts.first{it.id==first.id})
        assertEquals(1,workoutHistorySummary(a.repo.archive.value,3,System.currentTimeMillis()).unique)
        assertEquals(0,workoutHistorySummary(a.repo.archive.value,3,System.currentTimeMillis()).confirmed)
    }
    @Test fun twoLiveClientsRefreshAndPreservePendingLocalEditsAndActiveDrafts()=runBlocking<Unit> {
        val a=actor();val b=actor(memory=true);assertEquals(a.repo.uid,b.repo.uid)
        val stateA=WorkoutHistoryState(a.repo);val stateB=WorkoutHistoryState(b.repo);stateA.load();stateB.load()
        jobs+=liveScope.launch{stateA.observeArchive()};jobs+=liveScope.launch{stateA.observeStatus()};jobs+=liveScope.launch{stateA.runSyncLoop()}
        jobs+=liveScope.launch{stateB.observeArchive()};jobs+=liveScope.launch{stateB.observeStatus()};val liveB=liveScope.launch{stateB.runSyncLoop()};jobs+=liveB
        eventually{stateA.archive.remoteKnown&&stateB.archive.remoteKnown}
        val first=finish(a.repo,"a","a");eventually{stateB.archive.attempts.any{it.id==first.id}}
        liveB.cancelAndJoin();b.remote.stop()
        val pending=finish(b.repo,"pending","pending");b.repo.begin(run("active","active"),System.currentTimeMillis())
        val newer=finish(a.repo,"new","new");eventually{stateA.archive.acknowledged.contains(newer.id)}
        jobs+=liveScope.launch{stateB.runSyncLoop()}
        eventually{stateB.archive.acknowledged.contains(pending.id)&&stateB.archive.attempts.any{it.id==newer.id}}
        assertEquals(3,stateB.archive.attempts.size);assertTrue("active" in stateB.archive.drafts)
    }
    @Test fun permissionDeniedRetainsPendingAndAnotherAccountCannotAdoptIt()=runBlocking<Unit> {
        val a=actor();val local=LocalWorkoutHistoryStore(context,a.repo.uid)
        val denied=object:WorkoutHistoryRemote by a.remote {override suspend fun append(attempt:WorkoutAttempt){throw WorkoutHistoryDenied()}}
        val repo=WorkoutHistoryRepository(a.repo.uid,local,denied);repo.load();val saved=finish(repo)
        repo.synchronize();assertEquals(HistorySyncState.AccessDenied,repo.status.value);assertEquals(AttemptState.LocalPending,repo.archive.value.state(saved))
        assertTrue(a.remote.fetch().attempts.isEmpty())
        val reopened=WorkoutHistoryRepository(a.repo.uid,local,a.remote);reopened.load();reopened.synchronize()
        assertEquals(saved,reopened.archive.value.attempts.single());assertEquals(AttemptState.SelfReported,reopened.archive.value.state(saved))
        val b=actor("B");assertTrue(b.repo.archive.value.attempts.isEmpty());assertTrue(b.remote.fetch().attempts.isEmpty())
    }
    @Test fun actualAdminConfirmationIsRequiredAndMemberCannotForgeIt()=runBlocking<Unit> {
        val a=actor();val attempt=finish(a.repo);a.repo.synchronize()
        val path="memberWorkoutConfirmations/${a.repo.uid}/${attempt.id}"
        val payload=mapOf("schemaVersion" to 1,"uid" to a.repo.uid,"attemptId" to attempt.id,"confirmedBy" to a.repo.uid,"confirmedAt" to ServerValue.TIMESTAMP)
        assertTrue(runCatching{a.demo.database.getReference(path).setValue(payload).await()}.isFailure)
        assertTrue(a.remote.fetch().confirmations.isEmpty())
        val admin=actor("B");seedDemoAdminRole(admin.repo.uid)
        // Ordinary signed-in SDK write below is checked by Rules; owner bypass is only fixture setup.
        admin.demo.database.getReference(path).setValue(payload+("confirmedBy" to admin.repo.uid)).await()
        a.repo.synchronize();assertEquals(AttemptState.Confirmed,a.repo.archive.value.state(attempt))
        assertEquals(1,workoutHistorySummary(a.repo.archive.value,3,System.currentTimeMillis()).confirmed)
    }
    private fun seedDemoAdminRole(uid:String) {
        check(context.packageName==PersonalPlanEmulatorConnection.APPLICATION_ID)
        val url=URL("http://10.0.2.2:9000/users/$uid/role.json?ns=${PersonalPlanEmulatorConnection.PROJECT}-default-rtdb")
        val call=url.openConnection() as HttpURLConnection
        call.connectTimeout=5_000;call.readTimeout=5_000;call.instanceFollowRedirects=false
        call.requestMethod="PUT";call.doOutput=true;call.setRequestProperty("Authorization","Bearer owner");call.setRequestProperty("Content-Type","application/json")
        try{call.outputStream.use{it.write("\"admin\"".toByteArray())};check(call.responseCode==200)}finally{call.disconnect()}
    }
    fun seedRestart()=runBlocking<Unit> {
        val namespace="m2c-restart-${UUID.randomUUID()}";val a=actor(namespace=namespace)
        a.demo.setOnline(false);val result=finish(a.repo);a.repo.begin(run("active","active"),System.currentTimeMillis())
        a.repo.synchronize();assertEquals(HistorySyncState.Pending,a.repo.status.value)
        check(context.getSharedPreferences("m2c_restart",Context.MODE_PRIVATE).edit().putString("namespace",namespace).putString("uid",a.repo.uid)
            .putString("id",result.id).putInt("pid",android.os.Process.myPid()).commit())
    }
    fun verifyRestart()=runBlocking<Unit> {
        val prefs=context.getSharedPreferences("m2c_restart",Context.MODE_PRIVATE)
        assertNotEquals(prefs.getInt("pid",-1),android.os.Process.myPid())
        val uid=checkNotNull(prefs.getString("uid",null));val before=LocalWorkoutHistoryStore(context,uid).read()
        assertEquals(prefs.getString("id",null),before.attempts.single().id);assertEquals(AttemptState.LocalPending,before.state(before.attempts.single()))
        assertTrue("active" in before.drafts)
        val a=actor(namespace=checkNotNull(prefs.getString("namespace",null)));assertEquals(uid,a.repo.uid)
        a.repo.synchronize();assertEquals(HistorySyncState.Synced,a.repo.status.value);assertEquals(1,a.remote.fetch().attempts.size)
        assertEquals(AttemptState.SelfReported,a.repo.archive.value.state(a.repo.archive.value.attempts.single()));assertTrue("active" in a.repo.archive.value.drafts)
    }
}
class WorkoutHistoryRestartTest {
    @Test fun seed(){val fixture=WorkoutHistoryIntegrationTest();try{fixture.seedRestart()}finally{fixture.cleanup()}}
    @Test fun verify(){val fixture=WorkoutHistoryIntegrationTest();try{fixture.verifyRestart()}finally{fixture.cleanup()}}
}
