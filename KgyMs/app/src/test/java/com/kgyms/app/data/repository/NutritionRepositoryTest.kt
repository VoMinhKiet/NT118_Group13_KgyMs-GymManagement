package com.kgyms.app.data.repository

import com.kgyms.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class NutritionRepositoryTest {
    private val setup = NutritionSetup(NutritionGoal.MAINTAIN, NutritionActivity.LOW, NutritionDiet.REGULAR)
    private val device = NutritionPlanData(setup, mapOf("0:BREAKFAST" to "bf_oats"), NutritionMealCatalog.fingerprint(setup))
    private val cloud = device.copy(meals = mapOf("0:BREAKFAST" to "bf_pho_ga"))

    @Test fun verifiedEmptyCloudMigratesLegacyAndAnotherDeviceCanRead() = runBlocking<Unit> {
        val remote = FakeRemote("A")
        val imported = Store(NutritionJournal(device, pending = true))
        val repo = repository(imported, remote)
        repo.load()
        assertEquals(NutritionSyncStatus.Device, repo.view.value.status)
        assertEquals(0, remote.writes)
        repo.synchronize()
        assertEquals(device, remote.head?.plan)
        assertEquals(1, remote.head?.revision)
        assertEquals(NutritionSyncStatus.Synced, repo.view.value.status)
        assertFalse(imported.durable.pending)
        val other = repository(Store(), remote).also { it.load(); it.synchronize() }
        assertEquals(device, other.view.value.plan)
    }

    @Test fun confirmedAbsentWithoutALocalPlanDoesNotCreateOne() = runBlocking<Unit> {
        val remote = FakeRemote("A")
        val repo = repository(Store(), remote)
        repo.load()
        repo.synchronize()
        assertEquals(NutritionSyncStatus.Empty, repo.view.value.status)
        assertNull(repo.view.value.plan)
        assertEquals(0, remote.writes)
        remote.head = record(cloud)
        repo.synchronize()
        assertEquals(cloud, repo.view.value.plan)
        assertEquals(0, remote.writes)
    }

    @Test fun deniedOfflineAndMalformedReadsNeverBecomeAnEmptyServerRecord() = runBlocking<Unit> {
        val store = Store(NutritionJournal(device, pending = true))
        val remote = FakeRemote("A")
        val repo = repository(store, remote)
        repo.load()
        val cases = listOf(
            NutritionDenied() to NutritionSyncStatus.Error,
            NutritionOffline() to NutritionSyncStatus.Offline,
            NutritionUnreadable() to NutritionSyncStatus.Incompatible,
        )
        for ((error, status) in cases) {
            remote.fetchError = error
            repo.synchronize()
            assertEquals(status, repo.view.value.status)
            assertEquals(device, repo.view.value.plan)
            assertTrue(store.durable.pending)
        }
        assertEquals(0, remote.writes)
        assertNull(remote.head)
    }

    @Test fun savingSetupChangingAndRemovingMealsSurviveReopenAndSyncOnReconnect() = runBlocking<Unit> {
        val remote = FakeRemote("A").apply { connected.value = false }
        val store = Store()
        val repo = repository(store, remote)
        repo.load()
        assertTrue(repo.save { device })
        val updated = setup.copy(diet = NutritionDiet.VEGETARIAN)
        assertTrue(repo.save { it.copy(setup = updated) })
        assertEquals("bf_oats", repo.view.value.plan?.meals?.get("0:BREAKFAST"))
        assertEquals(device.generatedFor, repo.view.value.plan?.generatedFor)
        assertTrue(repo.save { it.copy(meals = it.meals + ("0:BREAKFAST" to "bf_pancake_yen_mach")) })
        assertTrue(repo.save { it.copy(meals = it.meals + ("2:SNACK" to "sn_sua_chua_granola")) })
        assertTrue(repo.save { it.copy(meals = it.meals - "2:SNACK") })
        repo.synchronize()
        assertEquals(NutritionSyncStatus.Offline, repo.view.value.status)
        assertEquals(0, remote.writes)
        repo.close()

        val reopened = repository(store, remote)
        val sync = launch { reopened.run() }
        try {
            withTimeout(5_000) { reopened.view.first { it.status == NutritionSyncStatus.Offline } }
            assertEquals(updated, reopened.view.value.plan?.setup)
            assertEquals(mapOf("0:BREAKFAST" to "bf_pancake_yen_mach"), reopened.view.value.plan?.meals)
            remote.connected.value = true
            withTimeout(5_000) { reopened.view.first { it.status == NutritionSyncStatus.Synced } }
            assertEquals(reopened.view.value.plan, remote.head?.plan)
            assertFalse(store.durable.pending)
        } finally {
            sync.cancelAndJoin()
        }
    }

    @Test fun conflictingLegacyKeepsBothVersionsAcrossReopenUntilExplicitChoice() = runBlocking<Unit> {
        val remote = FakeRemote("A").apply { head = record(cloud) }
        val store = Store(NutritionJournal(device, pending = true))
        val first = repository(store, remote)
        first.load()
        first.synchronize()
        assertEquals(NutritionSyncStatus.Conflict, first.view.value.status)
        assertEquals(device, first.view.value.plan)
        assertEquals(cloud, first.view.value.cloudConflict?.plan)
        assertEquals(0, remote.writes)
        first.close()

        val second = repository(store, remote)
        second.load()
        assertEquals(NutritionSyncStatus.Conflict, second.view.value.status)
        assertTrue(second.resolveConflict(checkNotNull(second.view.value.cloudConflict), keepDevice = false))
        second.synchronize()
        assertEquals(cloud, second.view.value.plan)
        assertEquals(NutritionSyncStatus.Synced, second.view.value.status)
        assertEquals(0, remote.writes)
    }

    @Test fun staleEtagDoesNotOverwriteAndKeepingDeviceRequiresReviewedCloudRevision() = runBlocking<Unit> {
        val initial = record(device)
        val remote = FakeRemote("A").apply { head = initial }
        val store = Store(NutritionJournal(device, initial))
        val repo = repository(store, remote)
        repo.load()
        val edited = device.copy(meals = mapOf("0:BREAKFAST" to "bf_banh_mi_op_la"))
        repo.save { edited }
        val other = record(cloud, 2)
        remote.beforeWrite = { remote.head = other }
        repo.synchronize()
        assertEquals(NutritionSyncStatus.Conflict, repo.view.value.status)
        assertEquals(other, remote.head)
        assertEquals(0, remote.writes)
        assertEquals(edited, repo.view.value.plan)
        assertFalse(repo.resolveConflict(initial, keepDevice = true))
        assertTrue(repo.resolveConflict(other, keepDevice = true))
        repo.synchronize()
        assertEquals(edited, remote.head?.plan)
        assertEquals(3, remote.head?.revision)
        assertEquals(NutritionSyncStatus.Synced, repo.view.value.status)
    }

    @Test fun lostAckRecoversAfterReopenWithoutDuplicatingRevision() = runBlocking<Unit> {
        val store = Store(NutritionJournal(device, pending = true))
        val remote = FakeRemote("A").apply { loseAck = true }
        val repo = repository(store, remote)
        repo.load()
        repo.synchronize()
        assertEquals(NutritionSyncStatus.Offline, repo.view.value.status)
        assertEquals(1, remote.head?.revision)
        assertNotNull(store.durable.sent)
        repo.close()
        val reopened = repository(store, remote)
        reopened.load()
        reopened.synchronize()
        assertEquals(NutritionSyncStatus.Synced, reopened.view.value.status)
        assertEquals(1, remote.writes)
        assertNull(store.durable.sent)
    }

    @Test fun editsDuringUploadAreSavedAgainstTheAcknowledgedRevision() = runBlocking<Unit> {
        val store = Store(NutritionJournal(device, pending = true))
        val remote = FakeRemote("A")
        val repo = repository(store, remote)
        repo.load()
        remote.beforeWrite = { assertTrue(repo.save { cloud }) }
        repo.synchronize()
        assertEquals(2, remote.head?.revision)
        assertEquals(cloud, remote.head?.plan)
        assertEquals(NutritionSyncStatus.Synced, repo.view.value.status)
    }

    @Test fun remoteRollbackAndUnreadableLocalAreProtected() = runBlocking<Unit> {
        val base = record(device, 2)
        val remote = FakeRemote("A").apply { head = record(cloud, 1) }
        val repo = repository(Store(NutritionJournal(device, base)), remote)
        repo.load()
        repo.synchronize()
        assertEquals(NutritionSyncStatus.Incompatible, repo.view.value.status)
        assertEquals(device, repo.view.value.plan)
        val failedStore = Store().apply { readError = NutritionUnreadable() }
        val unreadable = repository(failedStore, remote)
        unreadable.load()
        unreadable.synchronize()
        assertEquals(NutritionSyncStatus.Incompatible, unreadable.view.value.status)
        assertFalse(unreadable.view.value.editable)
        assertFalse(unreadable.save { device })
        assertEquals(0, remote.writes)
    }

    @Test fun changingAccountMidReadClearsOldDataAndCannotWriteUsingTheOldUid() = runBlocking<Unit> {
        var actualUid = "A"
        val remoteA = FakeRemote("A")
        val a = repository(Store(NutritionJournal(device, pending = true)), remoteA) { actualUid == "A" }
        a.load()
        assertEquals(device, a.view.value.plan)
        remoteA.onFetch = { actualUid = "B" }
        a.synchronize()
        assertNull(a.view.value.plan)
        assertFalse(a.save { device })
        assertEquals(0, remoteA.writes)
        val remoteB = FakeRemote("B").apply { head = record(cloud) }
        val b = repository(Store(), remoteB) { actualUid == "B" }
        b.load()
        b.synchronize()
        assertEquals(cloud, b.view.value.plan)
        assertNull(remoteA.head)
    }


    @Test fun acceptedMealWriteSurvivesPickerCancellationWhileWaitingForTheJournal() = runBlocking<Unit> {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        var durable = NutritionJournal(device, pending = true)
        val store = object : NutritionJournalStore {
            override suspend fun read() = durable
            override suspend fun save(value: NutritionJournal) {
                if (!writeStarted.isCompleted) {
                    writeStarted.complete(Unit)
                    releaseWrite.await()
                }
                durable = value
            }
        }
        val repo = NutritionRepository("A", store, null, { true })
        repo.load()
        val otherEdit = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.save { it.copy(setup = setup.copy(activity = NutritionActivity.HIGH)) }
        }
        writeStarted.await()
        val pick = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.saveMeal(NutritionMealTarget(4, MealSlot.DINNER), "di_ca_hap_gung")
        }
        pick.cancel()
        releaseWrite.complete(Unit)
        otherEdit.join()
        pick.join()
        assertEquals("di_ca_hap_gung", durable.plan?.meals?.get("4:DINNER"))
        assertEquals(device.meals["0:BREAKFAST"], durable.plan?.meals?.get("0:BREAKFAST"))
        assertEquals(NutritionActivity.HIGH, durable.plan?.setup?.activity)
        assertEquals(durable.plan, repo.view.value.plan)
    }

    private fun record(plan: NutritionPlanData, revision: Int = 1) =
        NutritionRemoteRecord(revision, (100 + revision).toString(16).padStart(32, '0'), revision.toLong(), plan)

    private fun repository(store: Store, remote: FakeRemote, owner: () -> Boolean = { true }): NutritionRepository {
        var nextId = 0
        return NutritionRepository(remote.uid, store, remote, owner) { (++nextId).toString(16).padStart(32, '0') }
    }

    private class Store(var durable: NutritionJournal = NutritionJournal()) : NutritionJournalStore {
        var readError: Exception? = null
        override suspend fun read(): NutritionJournal {
            readError?.let { throw it }
            return durable
        }

        override suspend fun save(value: NutritionJournal) { durable = value }
    }

    private class FakeRemote(override val uid: String) : NutritionRemote {
        override val connected = MutableStateFlow(true)
        override val changes = MutableSharedFlow<Unit>()
        var head: NutritionRemoteRecord? = null
        var fetchError: Exception? = null
        var writes = 0
        var loseAck = false
        var beforeWrite: (suspend () -> Unit)? = null
        var onFetch: (() -> Unit)? = null
        private fun etag() = head?.writeId ?: "empty"

        override suspend fun fetch(): NutritionRemoteHead {
            fetchError?.let { throw it }
            onFetch?.invoke()
            return NutritionRemoteHead(head, etag())
        }

        override suspend fun write(expectedEtag: String, push: NutritionPush): Boolean {
            val before = beforeWrite
            beforeWrite = null
            before?.invoke()
            if (expectedEtag != etag()) return false
            writes++
            head = NutritionRemoteRecord(push.revision, push.writeId, push.revision.toLong(), push.plan)
            if (loseAck) throw NutritionOffline()
            return true
        }
    }
}
