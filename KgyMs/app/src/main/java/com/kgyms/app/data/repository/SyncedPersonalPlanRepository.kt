package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.PersonalPlanRemoteCodec
import com.kgyms.app.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface PersonalPlanSyncStore {
    val uid: String
    suspend fun snapshot(): LocalPersonalPlanSnapshot
    suspend fun compareAndSet(expected: LocalPersonalPlanSnapshot, next: LocalPersonalPlanSnapshot)
}
internal interface PersonalPlanRemote {
    val uid: String
    val available: StateFlow<Boolean>
    val target: PersonalPlanSyncTarget get() = PersonalPlanSyncTarget.Emulator
    fun prepareRetry() {}
    suspend fun read(): RemotePersonalPlanArchive
    suspend fun append(base: RemotePersonalPlanArchive, plan: PersonalPlan)
    suspend fun activate(base: RemotePersonalPlanArchive, id: String)
}

/** Narrow capability used by UI state so activation errors remain testable without a Firebase adapter. */
internal interface ActivePersonalPlanRepository {
    suspend fun selectActive(id: String): PersonalPlanArchive
}

/** Separate from Firebase errors: no field from a different owner is ever adopted. */
internal fun mergePersonalPlanSnapshots(local: PersonalPlanArchive, remote: RemotePersonalPlanArchive, uid: String, preserveActive: Boolean): PersonalPlanArchive {
    require(local.plans.all { it.uid == uid } && remote.records.all { it.plan.uid == uid })
    val byId = local.plans.associateBy { it.id }
    remote.records.forEach { record ->
        byId[record.plan.id]?.let { if (it != record.plan) throw PersonalPlanSyncConflict("Different payload for ${record.plan.id}") }
    }
    val known = remote.records.map { it.plan.id }.toSet()
    return PersonalPlanArchive(if (preserveActive) local.activeId else remote.activeId,
        remote.records.map { it.plan } + local.plans.filter { it.id !in known })
}

/** Local save first. Only explicit server acknowledgement can produce Synced. */
internal class SyncedPersonalPlanRepository(private val store: PersonalPlanSyncStore, private val remote: PersonalPlanRemote) : PersonalPlanRepository, ActivePersonalPlanRepository {
    override val uid = store.uid
    override val storage = PersonalPlanStorage.Device
    val target get() = remote.target
    fun prepareRetry() = remote.prepareRetry()
    val status = MutableStateFlow(PersonalPlanSyncStatus.Pending)
    private val mutex = Mutex()
    @Volatile private var closed = false
    var metadata = PersonalPlanSyncMetadata()
        private set
    var activeConflict = false
        private set
    init { require(uid == remote.uid) }
    private fun live() { check(!closed) { "Owner session closed" } }
    fun close() { closed = true }
    override suspend fun load(): PersonalPlanArchive {
        live()
        var local = store.snapshot()
        // Existing phase-1 archives contain no sync metadata. Retain the active intent; do not guess a cloud revision.
        if (local.metadata.activation == null && local.archive.activeId != null && local.archive.activeId != local.metadata.acknowledgedActiveId) {
            val next = local.copy(metadata = local.metadata.copy(activation = PersonalPlanActivation(checkNotNull(local.archive.activeId), local.metadata.revision)))
            store.compareAndSet(local, next); local = next
        }
        metadata = local.metadata
        return local.archive
    }
    override suspend fun save(plan: PersonalPlan, expectedActiveId: String?): PersonalPlanArchive {
        live(); PersonalPlanRemoteCodec.validate(plan)
        val local = store.snapshot()
        val archive = appendPersonalPlan(local.archive, plan, uid, expectedActiveId)
        val next = local.copy(archive = archive, metadata = local.metadata.copy(activation = PersonalPlanActivation(plan.id, local.metadata.revision)))
        store.compareAndSet(local, next); metadata = next.metadata; status.value = PersonalPlanSyncStatus.Pending
        return archive
    }
    override suspend fun selectActive(id: String): PersonalPlanArchive {
        live()
        val local = store.snapshot(); require(local.archive.plans.any { it.id == id })
        val next = local.copy(archive = local.archive.copy(activeId = id), metadata = local.metadata.copy(activation = PersonalPlanActivation(id, local.metadata.revision)))
        store.compareAndSet(local, next); metadata = next.metadata; status.value = PersonalPlanSyncStatus.Pending
        return next.archive
    }
    suspend fun synchronize(): PersonalPlanSyncStatus = mutex.withLock {
        live(); status.value = PersonalPlanSyncStatus.Syncing; activeConflict = false
        try {
            if (!remote.available.value) throw PersonalPlanOffline()
            load()
            val initial = store.snapshot()
            var cloud = remote.read(); live()
            // Check collisions before any upload or merge.
            mergePersonalPlanSnapshots(initial.archive, cloud, uid, initial.metadata.activation != null)
            val intent = initial.metadata.activation
            var activationBase = intent?.baseRevision ?: if (cloud.revision == 0L) 0L else null
            initial.archive.plans.forEach { plan ->
                PersonalPlanRemoteCodec.validate(plan)
                var attempts = 0
                while (cloud.records.none { it.plan.id == plan.id }) {
                    live(); check(++attempts <= 12) { "Concurrent append retry limit" }
                    val before = cloud
                    try {
                        remote.append(before, plan)
                        cloud = remote.read(); live()
                        check(cloud.records.any { it.plan == plan }) { "Append was not confirmed" }
                        activationBase = if (activationBase == before.revision && cloud.revision == before.revision + 1) cloud.revision else null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (offline: PersonalPlanOffline) { throw offline }
                    catch (error: Exception) {
                        val fresh = remote.read(); live()
                        if (fresh.revision == before.revision) throw error
                        cloud = fresh; activationBase = null
                        mergePersonalPlanSnapshots(initial.archive, cloud, uid, true)
                    }
                }
                if (cloud.records.first { it.plan.id == plan.id }.plan != plan) throw PersonalPlanSyncConflict("Plan ID collision")
            }
            var conflict = false
            val latest = store.snapshot()
            if (intent != null && latest.metadata.activation == intent) {
                if (cloud.activeId != intent.planId) {
                    if (activationBase != cloud.revision) conflict = true
                    else try {
                        live(); remote.activate(cloud, intent.planId)
                        cloud = remote.read(); live()
                        if (cloud.activeId != intent.planId) conflict = true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (offline: PersonalPlanOffline) { throw offline }
                    catch (_: Exception) { cloud = remote.read(); live(); conflict = true }
                }
            }
            // Re-read local immediately before a CAS merge so edits made during network I/O are retained.
            val current = store.snapshot(); live()
            activeConflict = conflict
            val unchangedIntent = current.metadata.activation == intent
            val clearIntent = unchangedIntent && intent != null && !conflict && cloud.activeId == intent.planId
            val pending = if (clearIntent) null else current.metadata.activation
            val merged = mergePersonalPlanSnapshots(current.archive, cloud, uid, pending != null)
            val meta = current.metadata.copy(revision = cloud.revision, ordinals = cloud.records.associate { it.plan.id to it.ordinal },
                acknowledgedActiveId = cloud.activeId, activation = pending)
            store.compareAndSet(current, LocalPersonalPlanSnapshot(merged, meta)); metadata = meta
            val allConfirmed = merged.plans.all { plan -> cloud.records.any { it.plan == plan } }
            status.value = when { conflict -> PersonalPlanSyncStatus.Conflict; pending != null || !allConfirmed -> PersonalPlanSyncStatus.Pending; else -> PersonalPlanSyncStatus.Synced }
        } catch (cancelled: CancellationException) { status.value = PersonalPlanSyncStatus.Pending; throw cancelled }
        catch (_: PersonalPlanOffline) { status.value = PersonalPlanSyncStatus.Pending }
        catch (_: PersonalPlanSyncConflict) { status.value = PersonalPlanSyncStatus.Conflict }
        catch (_: PersonalPlanAccessDenied) { status.value = PersonalPlanSyncStatus.AccessDenied }
        catch (_: Exception) { status.value = PersonalPlanSyncStatus.Failed }
        status.value
    }
}
