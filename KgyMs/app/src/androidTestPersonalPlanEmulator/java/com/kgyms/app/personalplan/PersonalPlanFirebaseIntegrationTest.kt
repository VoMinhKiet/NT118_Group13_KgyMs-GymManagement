package com.kgyms.app.personalplan

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.kgyms.app.data.firebase.*
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.*
import com.kgyms.app.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.util.TimeZone
import java.util.UUID

/** Real Auth+RTDB Emulator calls only. Never clear data, use admin tokens, or enter production UI. */
class PersonalPlanFirebaseIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "integration-${UUID.randomUUID()}"
    private val connections = mutableListOf<PersonalPlanEmulatorConnection>()
    private val catalog = WorkoutReferenceCatalog.snapshot()
    private val zone = TimeZone.getDefault().id
    private val now = System.currentTimeMillis()
    private fun plan(uid: String, count: Int = 1): PersonalPlan = PersonalPlanGenerator.seal(PersonalPlanGenerator.generate(uid,
        PersonalPlanConfig(WorkoutLevel.Beginner, count, PlanDates.date(now, zone), zone,
            (0 until count).map { (PlanDates.weekday(PlanDates.date(now, zone), zone) + it * 2) % 7 }), catalog, now))
    private suspend fun connection(label: String = "A", group: String = namespace): PersonalPlanEmulatorConnection {
        assertEquals(PersonalPlanEmulatorConnection.APPLICATION_ID, context.packageName)
        val c = PersonalPlanEmulatorConnection.create(context); connections += c
        c.signIn(label, group)
        withTimeout(15_000) { c.available.first { it } }
        return c
    }
    private class Memory(override val uid: String, var value: LocalPersonalPlanSnapshot = LocalPersonalPlanSnapshot()) : PersonalPlanSyncStore {
        override suspend fun snapshot() = value
        override suspend fun compareAndSet(expected: LocalPersonalPlanSnapshot, next: LocalPersonalPlanSnapshot) { check(value == expected); value = next }
    }
    @After fun closeConnections() { connections.forEach { it.close() } }

    @Test fun isolatedManifestOptionsTokensAndEndpointAuditContainNoProductionOrStorage() = runBlocking<Unit> {
        val pkg = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_ACTIVITIES)
        assertTrue(pkg.providers.orEmpty().none { it.name == "com.google.firebase.provider.FirebaseInitProvider" })
        assertTrue(pkg.activities.orEmpty().none { it.name == "com.kgyms.app.MainActivity" })
        assertEquals(0, context.resources.getIdentifier("google_app_id", "string", context.packageName))
        assertTrue(runCatching { FirebaseApp.getInstance() }.isFailure)
        val c = connection(); val uid = c.auth.currentUser!!.uid; c.token(uid)
        val service = PersonalPlanFirebaseService(c,uid)
        service.append(service.read(), plan(uid))
        service.read()
        assertTrue(FirebaseApp.getApps(context).all { it.name != FirebaseApp.DEFAULT_APP_NAME && it.options.projectId == PersonalPlanEmulatorConnection.PROJECT })
        assertTrue(c.database.getReference("memberWorkoutPlans/$uid").toString().startsWith("http://10.0.2.2:9000/"))
        assertTrue(c.audit.isNotEmpty())
        assertTrue(c.audit.all { it.contains("http://10.0.2.2:") && !it.contains("storage", true) && !it.contains("kgyms-72c74") })
    }
    @Test fun uploadEditedLocalHistoryAndDownloadOnFreshClientPreserveIdsVersionsAndNames() = runBlocking<Unit> {
        val firstClient = connection(); val uid = firstClient.auth.currentUser!!.uid
        val original = plan(uid)
        val session = original.sessions.first()
        var ids = PersonalPlanEditor.remove(session.exerciseIds, session.exerciseIds.first())
        ids = PersonalPlanEditor.add(ids, "bridge", catalog, original.config.level)
        val edited = PersonalPlanEditor.revision(original, PersonalPlanEditor.apply(original, session.id, ids, ExerciseEditScope.Session, catalog), catalog)
        val local = LocalPersonalPlanRepository(context,uid); local.save(original,null); local.save(edited,original.id)
        val repo = SyncedPersonalPlanRepository(local, PersonalPlanFirebaseService(firstClient,uid)); repo.load()
        assertEquals(PersonalPlanSyncStatus.Synced, repo.synchronize())
        val other = connection(); assertEquals(uid, other.auth.currentUser!!.uid)
        val empty = Memory(uid); val freshRepo = SyncedPersonalPlanRepository(empty,PersonalPlanFirebaseService(other,uid))
        assertEquals(PersonalPlanSyncStatus.Synced,freshRepo.synchronize())
        assertEquals(listOf(original,edited),empty.value.archive.plans); assertEquals(edited.id,empty.value.archive.activeId)
        val state = com.kgyms.app.ui.member.plan.PersonalPlanState(freshRepo); state.load()
        assertEquals(1,state.versionNumber(original.id)); assertEquals(2,state.versionNumber(edited.id))
        val projection = state.catalog(catalog)
        val route = edited.runtimeSessionId(edited.sessions.first())
        assertEquals(ids,projection.workouts.first { it.id == "personal-workout-$route" }.exerciseIds)
        assertTrue(projection.sessions.all { it.confirmedAt == null && it.completedAt == null })
        assertFalse(projection.historyAvailable)
    }
    @Test fun realAuthAccountsAndUnauthenticatedClientCannotReadOrWriteAnotherUid() = runBlocking<Unit> {
        val ca=connection(); val cb=connection("B"); val uid=ca.auth.currentUser!!.uid
        val sa=PersonalPlanFirebaseService(ca,uid); val p=plan(uid); sa.append(sa.read(),p)
        val uidB=cb.auth.currentUser!!.uid; val sb=PersonalPlanFirebaseService(cb,uidB)
        assertTrue(sb.read().records.isEmpty())
        assertTrue(runCatching { cb.database.getReference("memberWorkoutPlans/$uid").get().await() }.isFailure)
        assertTrue(runCatching { cb.database.getReference("memberWorkoutPlans/$uid/state/activeId").setValue(p.id).await() }.isFailure)
        assertTrue(runCatching { PersonalPlanFirebaseService(cb,uid).read() }.isFailure)
        val guest=PersonalPlanEmulatorConnection.create(context); connections+=guest
        assertTrue(runCatching { guest.database.getReference("memberWorkoutPlans/$uid").get().await() }.isFailure)
        assertTrue(runCatching { ca.database.getReference("memberWorkoutPlans").get().await() }.isFailure)
        assertEquals(p,sa.read().records.first().plan)
    }
    @Test fun concurrentClientsCasRejectsStaleWriteThenAppendRetryKeepsBothWithoutChangingActive() = runBlocking<Unit> {
        val ca=connection(); val cb=connection(); val uid=ca.auth.currentUser!!.uid
        val sa=PersonalPlanFirebaseService(ca,uid); val sb=PersonalPlanFirebaseService(cb,uid)
        val original=plan(uid); sa.append(sa.read(),original); sa.activate(sa.read(),original.id)
        val base=sa.read(); val one=plan(uid,2); val two=plan(uid,3)
        val results=awaitAll(async { runCatching { sa.append(base,one) } },async { runCatching { sb.append(base,two) } })
        assertEquals(1,results.count { it.isSuccess }); assertEquals(1,results.count { it.isFailure })
        val loser=if(results[0].isFailure) one else two
        val store=Memory(uid,LocalPersonalPlanSnapshot(PersonalPlanArchive(null,listOf(loser))))
        val repo=SyncedPersonalPlanRepository(store,sb)
        assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize())
        val cloud=sa.read(); assertEquals(setOf(original,one,two),cloud.records.map { it.plan }.toSet())
        assertEquals(original.id,cloud.activeId); assertEquals(listOf(1,2,3),cloud.records.map { it.ordinal })
        val revision=cloud.revision
        assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize()); assertEquals(revision,sa.read().revision)
    }
    @Test fun offlineEditAndRecreatedConnectionKeepDurableLocalHistoryAndSynchronizeOnReturn() = runBlocking<Unit> {
        val c=connection(); val uid=c.auth.currentUser!!.uid; val local=LocalPersonalPlanRepository(context,uid)
        val first=plan(uid); val repo=SyncedPersonalPlanRepository(local,PersonalPlanFirebaseService(c,uid)); repo.load(); repo.save(first,null)
        assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize())
        c.setOnline(false)
        val edited=PersonalPlanEditor.revision(first,PersonalPlanEditor.apply(first,first.sessions.first().id,listOf("bridge"),ExerciseEditScope.Session,catalog),catalog)
        repo.save(edited,first.id)
        assertEquals(PersonalPlanSyncStatus.Pending,repo.synchronize())
        assertEquals(edited,repo.load().active); repo.close(); c.close()
        val reopenedConnection=connection(); assertEquals(uid,reopenedConnection.auth.currentUser!!.uid)
        val reopened=SyncedPersonalPlanRepository(LocalPersonalPlanRepository(context,uid),PersonalPlanFirebaseService(reopenedConnection,uid))
        assertEquals(edited,reopened.load().active)
        assertEquals(PersonalPlanSyncStatus.Synced,reopened.synchronize())
        assertEquals(listOf(first,edited),PersonalPlanFirebaseService(reopenedConnection,uid).read().records.map { it.plan })
    }
    @Test fun payloadConflictLeavesBothLocalAndServerUnmodified() = runBlocking<Unit> {
        val c=connection(); val uid=c.auth.currentUser!!.uid; val service=PersonalPlanFirebaseService(c,uid)
        val cloud=plan(uid); service.append(service.read(),cloud)
        val localPlan=cloud.copy(sessions=cloud.sessions.map { it.copy(exerciseIds=listOf("bridge")) })
        val store=Memory(uid,LocalPersonalPlanSnapshot(PersonalPlanArchive(localPlan.id,listOf(localPlan))))
        val repo=SyncedPersonalPlanRepository(store,service)
        assertEquals(PersonalPlanSyncStatus.Conflict,repo.synchronize())
        assertEquals(localPlan,store.value.archive.active); assertEquals(cloud,service.read().records.first().plan)
    }
    @Test fun sameConnectionReconnectsWithoutTreatingCachedDataAsServerAcknowledgement() = runBlocking<Unit> {
        val c=connection(); val uid=c.auth.currentUser!!.uid; val service=PersonalPlanFirebaseService(c,uid)
        val p=plan(uid); service.append(service.read(),p)
        c.setOnline(false)
        assertTrue(runCatching { service.read() }.exceptionOrNull() is PersonalPlanOffline)
        c.setOnline(true); withTimeout(15_000){c.available.first{it}}
        assertEquals(p,service.read().records.first().plan)
    }
    @Test fun activeConflictRequiresExplicitSelectionAndStaleActivationIsNeverRetried() = runBlocking<Unit> {
        val c=connection(); val uid=c.auth.currentUser!!.uid; val service=PersonalPlanFirebaseService(c,uid)
        val first=plan(uid); val local=Memory(uid); val repo=SyncedPersonalPlanRepository(local,service)
        repo.load(); repo.save(first,null); repo.synchronize()
        val stale=service.read(); val other=plan(uid,2)
        service.append(stale,other); service.activate(service.read(),other.id)
        assertTrue(runCatching { service.activate(stale,first.id) }.isFailure)
        val next=plan(uid,3); repo.save(next,first.id)
        assertEquals(PersonalPlanSyncStatus.Conflict,repo.synchronize())
        assertTrue(repo.activeConflict); assertEquals(other.id,service.read().activeId)
        assertEquals(next.id,local.value.archive.activeId)
        repo.selectActive(next.id)
        assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize()); assertEquals(next.id,service.read().activeId)
        assertEquals(3,service.read().records.size)
    }
    @Test fun switchingUidDuringSyncCannotCommitToOrDisplayTheNewOwner() = runBlocking<Unit> {
        val ca=connection(); val uid=ca.auth.currentUser!!.uid; val actual=PersonalPlanFirebaseService(ca,uid)
        val entered=CompletableDeferred<Unit>(); val gate=CompletableDeferred<Unit>()
        val delayed=object:PersonalPlanRemote by actual { override suspend fun read():RemotePersonalPlanArchive { entered.complete(Unit); gate.await(); return actual.read() } }
        val local=Memory(uid,LocalPersonalPlanSnapshot(PersonalPlanArchive(null,listOf(plan(uid)))))
        val repo=SyncedPersonalPlanRepository(local,delayed)
        val job=launch { repo.synchronize() }; entered.await(); repo.close(); ca.close()
        val cb=connection("B"); val sb=PersonalPlanFirebaseService(cb,cb.auth.currentUser!!.uid)
        gate.complete(Unit); job.join()
        assertTrue(sb.read().records.isEmpty()); assertEquals(1,local.value.archive.plans.size)
    }
    fun seedProcessRestartFixture() = runBlocking<Unit> {
        val group="restart-${UUID.randomUUID()}"; val c=connection(group=group); val uid=c.auth.currentUser!!.uid
        val repo=SyncedPersonalPlanRepository(LocalPersonalPlanRepository(context,uid),PersonalPlanFirebaseService(c,uid))
        repo.load(); val first=plan(uid); repo.save(first,null); assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize())
        c.setOnline(false)
        val edited=PersonalPlanEditor.revision(first,PersonalPlanEditor.apply(first,first.sessions.first().id,listOf("bridge"),ExerciseEditScope.Session,catalog),catalog)
        repo.save(edited,first.id)
        context.getSharedPreferences("phase2b_restart_fixture",Context.MODE_PRIVATE).edit().putString("group",group).putString("uid",uid).putString("id",edited.id).putInt("pid",android.os.Process.myPid()).commit()
        assertEquals(PersonalPlanSyncStatus.Pending,repo.synchronize())
    }
    fun verifyProcessRestartFixture() = runBlocking<Unit> {
        val prefs=context.getSharedPreferences("phase2b_restart_fixture",Context.MODE_PRIVATE)
        assertNotEquals("Verification must run in a new process",prefs.getInt("pid",-1),android.os.Process.myPid())
        val uid=prefs.getString("uid",null) ?: error("Run seedProcessRestartFixture first")
        val local=LocalPersonalPlanRepository(context,uid)
        val stored=local.load(); assertEquals(prefs.getString("id",null),stored.activeId); assertEquals(2,stored.plans.size)
        assertEquals(listOf("bridge"),stored.active!!.sessions.first().exerciseIds)
        val c=connection(group=checkNotNull(prefs.getString("group",null))); assertEquals(uid,c.auth.currentUser!!.uid)
        val repo=SyncedPersonalPlanRepository(local,PersonalPlanFirebaseService(c,uid)); repo.load()
        assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize())
        assertEquals(2,PersonalPlanFirebaseService(c,uid).read().records.size)
    }
}

/** Seed and verify are deliberately separate instrumentation processes, with an APK-only force-stop between them. */
class PersonalPlanProcessRestartTest {
    @Test fun seed() { val fixture=PersonalPlanFirebaseIntegrationTest(); try { fixture.seedProcessRestartFixture() } finally { fixture.closeConnections() } }
    @Test fun verify() { val fixture=PersonalPlanFirebaseIntegrationTest(); try { fixture.verifyProcessRestartFixture() } finally { fixture.closeConnections() } }
}
