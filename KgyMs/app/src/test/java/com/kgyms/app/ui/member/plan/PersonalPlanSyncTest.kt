package com.kgyms.app.ui.member.plan

import com.kgyms.app.data.firebase.PersonalPlanRemoteCodec as Codec
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.*
import com.kgyms.app.utils.ExerciseEditScope
import com.kgyms.app.utils.PersonalPlanEditor
import com.kgyms.app.utils.PersonalPlanGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PersonalPlanSyncTest {
    private val catalog = WorkoutReferenceCatalog.snapshot()
    private fun plan(seed: String = "one", uid: String = "A"): PersonalPlan {
        val date = "2020-12-29"; val zone = "Asia/Ho_Chi_Minh"
        val base = PersonalPlanGenerator.generate(uid, PersonalPlanConfig(WorkoutLevel.Beginner, 3, date, zone), catalog, PlanDates.millis(date, zone))
        val id = "plan-$seed"
        return base.copy(id = id, sessions = base.sessions.mapIndexed { i, s -> s.copy(id = "$id-$i") })
    }
    private class Store(override val uid: String = "A", var data: LocalPersonalPlanSnapshot = LocalPersonalPlanSnapshot()) : PersonalPlanSyncStore {
        override suspend fun snapshot() = data
        override suspend fun compareAndSet(expected: LocalPersonalPlanSnapshot, next: LocalPersonalPlanSnapshot) {
            check(data == expected); check(next.archive.plans.all { it.uid == uid }); data = next
        }
    }
    private class Remote(override val uid: String = "A") : PersonalPlanRemote {
        override val available = MutableStateFlow(true)
        var data = RemotePersonalPlanArchive()
        var appendCalls = 0; var activateCalls = 0
        var denied = false
        var beforeAppend: (suspend () -> Unit)? = null
        var beforeRead: (suspend () -> Unit)? = null
        override suspend fun read(): RemotePersonalPlanArchive { beforeRead?.invoke(); if (denied) throw PersonalPlanAccessDenied(); if (!available.value) throw PersonalPlanOffline(); return data }
        override suspend fun append(base: RemotePersonalPlanArchive, plan: PersonalPlan) {
            beforeAppend?.invoke(); if (!available.value) throw PersonalPlanOffline()
            check(base.revision == data.revision && base.nextOrdinal == data.nextOrdinal)
            check(data.records.none { it.plan.id == plan.id }); appendCalls++
            data = data.copy(revision = data.revision + 1, nextOrdinal = data.nextOrdinal + 1,
                records = data.records + RemotePersonalPlan(plan, data.nextOrdinal, 1000))
        }
        override suspend fun activate(base: RemotePersonalPlanArchive, id: String) {
            check(base.revision == data.revision); check(data.records.any { it.plan.id == id }); activateCalls++
            data = data.copy(revision = data.revision + 1, activeId = id)
        }
    }
    private fun archive(vararg plans: PersonalPlan) = LocalPersonalPlanSnapshot(PersonalPlanArchive(plans.lastOrNull()?.id, plans.toList()))

    @Test fun deniedRulesRetainLocalVersionsAndDoNotPretendToBeSyncedOrEmpty() = runBlocking {
        val old = plan(); val store = Store(data = archive(old)); val remote=Remote().apply { denied=true }
        val repo=SyncedPersonalPlanRepository(store,remote); repo.load()
        assertEquals(PersonalPlanSyncStatus.AccessDenied,repo.synchronize())
        assertEquals(listOf(old),store.data.archive.plans); assertEquals(old.id,store.data.archive.activeId)
        assertEquals(0,remote.appendCalls); assertEquals(0,remote.activateCalls)
        remote.denied=false
        assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize()); assertEquals(listOf(old),store.data.archive.plans)
    }

    @Test fun legacyLocalUploadPreservesOrderIdsEditedExercisesAndActive() = runBlocking {
        val first = plan(); val next = PersonalPlanEditor.revision(first, PersonalPlanEditor.apply(first, first.sessions.first().id, listOf("bridge"), ExerciseEditScope.Session, catalog), catalog)
        val store = Store(data = archive(first, next)); val remote = Remote(); val repo = SyncedPersonalPlanRepository(store, remote)
        repo.load(); assertEquals(PersonalPlanSyncStatus.Synced, repo.synchronize())
        assertEquals(listOf(first, next), remote.data.records.map { it.plan }); assertEquals(next.id, remote.data.activeId)
        assertEquals(listOf(1,2), remote.data.records.map { it.ordinal }); assertNull(store.data.metadata.activation)
    }
    @Test fun emptyLocalDownloadsAllVersionsAndConfirmedActive() = runBlocking {
        val one = plan(); val two = plan("two"); val remote = Remote()
        remote.data = RemotePersonalPlanArchive(3, 3, two.id, listOf(RemotePersonalPlan(one,1,1), RemotePersonalPlan(two,2,2)))
        val store = Store(); val repo = SyncedPersonalPlanRepository(store, remote)
        assertEquals(PersonalPlanSyncStatus.Synced, repo.synchronize())
        assertEquals(PersonalPlanArchive(two.id, listOf(one,two)), repo.load()); assertEquals(0, remote.appendCalls)
    }
    @Test fun mergeBothSidesPreservesAllVersionsAndAsksBeforeChangingActive() = runBlocking {
        val local = plan(); val cloud = plan("cloud"); val store = Store(data = archive(local)); val remote = Remote()
        remote.data = RemotePersonalPlanArchive(1,2,cloud.id,listOf(RemotePersonalPlan(cloud,1,1)))
        val repo = SyncedPersonalPlanRepository(store, remote); repo.load()
        assertEquals(PersonalPlanSyncStatus.Conflict, repo.synchronize()); assertTrue(repo.activeConflict)
        assertEquals(local.id, store.data.archive.activeId); assertEquals(cloud.id, remote.data.activeId)
        assertEquals(setOf(local,cloud), store.data.archive.plans.toSet()); assertEquals(2, store.data.metadata.ordinals[local.id])
        repo.selectActive(local.id)
        assertEquals(PersonalPlanSyncStatus.Synced, repo.synchronize()); assertEquals(local.id, remote.data.activeId)
    }
    @Test fun choosingRemoteActiveResolvesWithoutDeletingLocalVersion() = runBlocking {
        val local = plan(); val cloud = plan("cloud"); val store = Store(data = archive(local)); val remote = Remote()
        remote.data = RemotePersonalPlanArchive(1,2,cloud.id,listOf(RemotePersonalPlan(cloud,1,1)))
        val repo = SyncedPersonalPlanRepository(store, remote); repo.load(); repo.synchronize()
        repo.selectActive(cloud.id); assertEquals(PersonalPlanSyncStatus.Synced, repo.synchronize())
        assertEquals(cloud.id, store.data.archive.activeId); assertEquals(2, store.data.archive.plans.size)
    }
    @Test fun sameIdDifferentPayloadDoesNotOverwriteEitherSide() = runBlocking {
        val local = plan(); val cloud = local.copy(sessions = local.sessions.map { it.copy(exerciseIds = listOf("bridge")) })
        val store = Store(data = archive(local)); val remote = Remote(); remote.data = RemotePersonalPlanArchive(1,2,cloud.id,listOf(RemotePersonalPlan(cloud,1,1)))
        val repo = SyncedPersonalPlanRepository(store, remote); repo.load()
        assertEquals(PersonalPlanSyncStatus.Conflict, repo.synchronize()); assertFalse(repo.activeConflict)
        assertEquals(local, store.data.archive.active); assertEquals(cloud, remote.data.records.first().plan); assertEquals(0, remote.appendCalls)
    }
    @Test fun retryAfterAcknowledgementNeverCreatesDuplicateOrReactivatesOldVersion() = runBlocking {
        val one = plan(); val store = Store(data = archive(one)); val remote = Remote(); val repo = SyncedPersonalPlanRepository(store, remote)
        repo.load(); repo.synchronize(); val before = remote.data
        assertEquals(PersonalPlanSyncStatus.Synced, repo.synchronize()); assertEquals(before, remote.data)
        assertEquals(1, remote.appendCalls); assertEquals(1, remote.activateCalls)
    }
    @Test fun offlineSavesAndRestartKeepAllLocalDataUntilServerAcknowledges() = runBlocking {
        val store = Store(); val remote = Remote(); remote.available.value = false
        val repo = SyncedPersonalPlanRepository(store, remote); repo.load()
        val one = plan(); repo.save(one, null)
        assertEquals(PersonalPlanSyncStatus.Pending, repo.synchronize()); assertEquals(one, store.data.archive.active); assertEquals(0, remote.appendCalls)
        repo.close()
        val reopened = SyncedPersonalPlanRepository(store, remote); assertEquals(one, reopened.load().active)
        remote.available.value = true
        assertEquals(PersonalPlanSyncStatus.Synced, reopened.synchronize()); assertEquals(one, remote.data.records.first().plan)
    }
    @Test fun externalRevisionChangeRetriesAppendButDoesNotRetryStaleActivation() = runBlocking {
        val base = plan("base"); val next = plan("next"); val remote = Remote()
        remote.data = RemotePersonalPlanArchive(1,2,base.id,listOf(RemotePersonalPlan(base,1,1)))
        val store = Store(data = LocalPersonalPlanSnapshot(PersonalPlanArchive(next.id,listOf(base,next)),
            PersonalPlanSyncMetadata(1, mapOf(base.id to 1), PersonalPlanActivation(next.id,1),base.id)))
        remote.beforeAppend = { remote.beforeAppend = null; remote.data = remote.data.copy(revision = 2) }
        val repo = SyncedPersonalPlanRepository(store, remote)
        assertEquals(PersonalPlanSyncStatus.Conflict, repo.synchronize()); assertEquals(1, remote.appendCalls); assertEquals(0, remote.activateCalls)
        assertEquals(base.id, remote.data.activeId); assertEquals(2, store.data.archive.plans.size)
    }
    @Test fun editsMadeDuringUploadAreNotLostOrMarkedSynced() = runBlocking {
        val one = plan(); val two = plan("new-during-network"); val store = Store(data = archive(one)); val remote = Remote()
        val repo = SyncedPersonalPlanRepository(store, remote); repo.load()
        remote.beforeAppend = { remote.beforeAppend = null; repo.save(two, one.id) }
        assertEquals(PersonalPlanSyncStatus.Pending, repo.synchronize())
        assertEquals(two.id, store.data.archive.activeId); assertEquals(listOf(one,two), store.data.archive.plans)
        assertEquals(listOf(one), remote.data.records.map { it.plan })
    }
    @Test fun closeDuringReadCannotMergeOrWriteToAnotherOwner() = runBlocking {
        val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>(); val store = Store(data = archive(plan())); val remote = Remote()
        remote.beforeRead = { started.complete(Unit); gate.await() }
        val repo = SyncedPersonalPlanRepository(store, remote)
        val task = launch { repo.synchronize() }; started.await(); val before = store.data
        repo.close(); gate.complete(Unit); task.join()
        assertEquals(before, store.data); assertEquals(0, remote.appendCalls)
        assertTrue(runCatching { SyncedPersonalPlanRepository(Store("B"), remote) }.isFailure)
    }
    @Test fun remoteCodecRoundTripsEditedPayloadAndDenseMapListsWithoutRegenerating() {
        val original = plan(); val edited = PersonalPlanEditor.revision(original, PersonalPlanEditor.apply(original, original.sessions.first().id, listOf("bridge","row"), ExerciseEditScope.Session, catalog), catalog)
        val encoded = Codec.encode(edited)
        assertEquals(edited, Codec.decodePlan(encoded, "A", edited.id))
        val mapped = encoded + ("sessions" to (encoded.getValue("sessions") as List<*>).mapIndexed { i, value -> i.toString() to value }.toMap())
        assertEquals(edited, Codec.decodePlan(mapped, "A", edited.id))
        assertEquals(original.sessions.map { it.id }, edited.sessions.map { it.id })
    }
    @Test fun remoteCodecRejectsOwnerHolesDuplicatesBadDatesAndUnknownSchema() {
        val p = plan(); val encoded = Codec.encode(p)
        assertTrue(runCatching { Codec.decodePlan(encoded,"B",p.id) }.isFailure)
        listOf(encoded + ("schemaVersion" to 2), encoded + ("sessionsPerWeek" to 4_294_967_299L),
            encoded + ("startDate" to "2020-02-31"), encoded + ("zoneId" to "No/Zone"), encoded + ("confirmedAt" to 5),
            encoded + ("sessions" to mapOf("1" to (encoded["sessions"] as List<*>).first())))
            .forEach { assertTrue(runCatching { Codec.decodePlan(it,"A",p.id) }.isFailure) }
        assertTrue(runCatching { Codec.validate(p.copy(sessions = p.sessions.map { it.copy(exerciseIds = listOf("row","row")) })) }.isFailure)
    }
    @Test fun remoteCodecRejectsBrokenOrderAndInvalidActiveWithoutTreatingThemAsEmpty() {
        val p = plan()
        val raw = mapOf("remoteSchemaVersion" to 1, "state" to mapOf("revision" to 1,"nextOrdinal" to 2,"activeId" to p.id),
            "plans" to mapOf(p.id to mapOf("ordinal" to 1,"uploadedAt" to 1,"payload" to Codec.encode(p))), "versionOrder" to mapOf("v1" to p.id))
        assertEquals(p, Codec.decode(raw,"A").records.first().plan)
        assertTrue(runCatching { Codec.decode(raw + ("versionOrder" to mapOf("v2" to p.id)),"A") }.isFailure)
        assertTrue(runCatching { Codec.decode(raw + ("state" to mapOf("revision" to 1,"nextOrdinal" to 2,"activeId" to "missing")),"A") }.isFailure)
        assertEquals(RemotePersonalPlanArchive(), Codec.decode(null,"A"))
    }
    @Test fun legacyLocalWriteAfterConfirmedEmptyReadDoesNotLoseItsActiveSelection() = runBlocking {
        val store=Store(); val remote=Remote(); val repo=SyncedPersonalPlanRepository(store,remote)
        assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize())
        val p=plan()
        store.data=store.data.copy(archive=PersonalPlanArchive(p.id,listOf(p)))
        repo.load()
        assertEquals(PersonalPlanSyncStatus.Synced,repo.synchronize())
        assertEquals(p.id,store.data.archive.activeId); assertEquals(p.id,remote.data.activeId)
    }
}
