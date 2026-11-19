package com.kgyms.app.personalplan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.kgyms.app.data.firebase.PersonalPlanProductionConnection
import com.kgyms.app.data.firebase.PersonalPlanEmulatorConnection
import com.kgyms.app.data.firebase.PersonalPlanFirebaseService
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.utils.PersonalPlanGenerator
import com.kgyms.app.data.repository.*
import com.kgyms.app.ui.member.plan.PersonalPlanState
import com.kgyms.app.utils.ExerciseEditScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone
import java.util.UUID

class PersonalPlanProductionIntegrationTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun demoApkCannotOpenProductionAdapterOrInitializeTheDefaultApp() {
        assertEquals("com.kgyms.app.personalplanemu",context.packageName)
        assertNull(PersonalPlanProductionConnection.open(context,"A"))
        assertTrue(runCatching { FirebaseApp.getInstance() }.isFailure)
        assertTrue(FirebaseApp.getApps(context).all { it.options.projectId=="demo-kgyms-personal-plans" })
    }
    @Test fun productionMetadataIsSeparateAndExistingArchiveSurvivesReopenWithoutMigration() = runBlocking {
        val uid="metadata-${UUID.randomUUID()}"
        val catalog=WorkoutReferenceCatalog.snapshot(); val now=System.currentTimeMillis(); val zone=TimeZone.getDefault().id
        val plan=PersonalPlanGenerator.seal(PersonalPlanGenerator.generate(uid,PersonalPlanConfig(WorkoutLevel.Beginner,1,PlanDates.date(now,zone),zone),catalog,now))
        val legacy=LocalPersonalPlanRepository(context,uid); legacy.save(plan,null)
        val demo=legacy.snapshot(); legacy.compareAndSet(demo,demo.copy(metadata=PersonalPlanSyncMetadata(revision=444)))
        val production=LocalPersonalPlanRepository(context,uid,"firebase_sync_v1")
        val first=production.snapshot()
        assertEquals(listOf(plan),first.archive.plans); assertNull(first.metadata.revision)
        production.compareAndSet(first,first.copy(metadata=PersonalPlanSyncMetadata(2,mapOf(plan.id to 1),acknowledgedActiveId=plan.id)))
        val reopened=LocalPersonalPlanRepository(context,uid,"firebase_sync_v1").snapshot()
        assertEquals(first.archive,reopened.archive); assertEquals(2L,reopened.metadata.revision)
        assertEquals(444L,legacy.snapshot().metadata.revision)
        assertEquals(plan,LocalPersonalPlanRepository(context,uid).load().active)
        assertTrue(LocalPersonalPlanRepository(context,"other-$uid","firebase_sync_v1").load().plans.isEmpty())
    }
    @Test fun realAdapterResumesPendingEditsAndUidSwitchStopsOnlyItsOwnListeners() = runBlocking<Unit> {
        val demo=PersonalPlanEmulatorConnection.create(context)
        val group="lifecycle-${UUID.randomUUID()}"
        var adapter:PersonalPlanProductionConnection?=null
        var job:Job?=null
        try {
            val uid=demo.signIn("A",group)
            adapter=PersonalPlanProductionConnection.observeIsolatedEmulator(context,demo,uid)
            val connection=adapter
            connection.start(); withTimeout(15_000){connection.available.first{it}}
            val repo=SyncedPersonalPlanRepository(LocalPersonalPlanRepository(context,uid,"firebase_sync_v1"),PersonalPlanFirebaseService(connection,uid))
            val state=PersonalPlanState(repo); state.load()
            suspend fun waitSynced(count:Int) { withTimeout(15_000){while(state.syncStatus!=PersonalPlanSyncStatus.Synced || state.archive.plans.size!=count)delay(25)} }
            fun observe()=launch { PersonalPlanSyncCoordinator().run(connection.signals,state.syncRequests,{repo.metadata.revision},{state.syncStatus},{state.synchronize(automatic=true)}) }
            job=observe(); waitSynced(0)
            val now=System.currentTimeMillis(); val zone=TimeZone.getDefault().id; val catalog=WorkoutReferenceCatalog.snapshot()
            val plan=PersonalPlanGenerator.generate(uid,PersonalPlanConfig(WorkoutLevel.Beginner,1,PlanDates.date(now,zone),zone),catalog,now)
            assertTrue(state.save(plan,catalog,now)); waitSynced(1)
            val first=state.active!!
            job.cancelAndJoin(); connection.stop()
            assertFalse(connection.available.value)
            // Stopping this observer must not sign out or disconnect the existing shared SDK.
            assertEquals(uid,demo.auth.currentUser!!.uid)
            assertEquals(first,PersonalPlanFirebaseService(demo,uid).read().records.single().plan)
            assertTrue(state.saveExercises(first,first.sessions.first().id,listOf("bridge"),ExerciseEditScope.Session,catalog,first.id))
            assertEquals(PersonalPlanSyncStatus.Pending,state.syncStatus)
            assertEquals(1,PersonalPlanFirebaseService(demo,uid).read().records.size)
            connection.start(); job=observe(); waitSynced(2)
            assertEquals(listOf("bridge"),PersonalPlanFirebaseService(demo,uid).read().records.last().plan.sessions.first().exerciseIds)
            job.cancelAndJoin(); job=null
            val other=demo.signIn("B",group)
            withTimeout(5_000){while(connection.available.value)delay(25)}
            assertTrue(runCatching { PersonalPlanFirebaseService(connection,uid).read() }.isFailure)
            assertTrue(PersonalPlanFirebaseService(demo,other).read().records.isEmpty())
            connection.close(); assertEquals(other,demo.auth.currentUser!!.uid)
            assertEquals(2,LocalPersonalPlanRepository(context,uid,"firebase_sync_v1").load().plans.size)
        } finally { job?.cancelAndJoin(); adapter?.close(); demo.close() }
    }
}
