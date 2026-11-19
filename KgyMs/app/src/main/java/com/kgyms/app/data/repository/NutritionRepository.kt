package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.NutritionRemoteCodec
import com.kgyms.app.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal interface NutritionJournalStore {
    suspend fun read(): NutritionJournal
    suspend fun save(value: NutritionJournal)
}

internal data class NutritionRemoteHead(val record: NutritionRemoteRecord?, val etag: String)

internal interface NutritionRemote {
    val uid: String
    val connected: StateFlow<Boolean>
    val changes: Flow<Unit>
    fun start() {}
    fun stop() {}
    fun retryListener() {}
    suspend fun fetch(): NutritionRemoteHead
    /** False means the server ETag changed. The caller must read again before making any decision. */
    suspend fun write(expectedEtag: String, push: NutritionPush): Boolean
}

internal data class NutritionViewData(
    val plan: NutritionPlanData? = null,
    val status: NutritionSyncStatus = NutritionSyncStatus.Loading,
    val cloudConflict: NutritionRemoteRecord? = null,
    val editable: Boolean = false,
)

internal class NutritionRepository(
    val uid: String,
    private val store: NutritionJournalStore,
    private val remote: NutritionRemote?,
    private val isOwner: () -> Boolean,
    private val writeId: () -> String = { UUID.randomUUID().toString().replace("-", "") },
) {
    private val journalLock = Mutex()
    private val syncLock = Mutex()
    private val requests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    private val mutableView = MutableStateFlow(NutritionViewData())
    val view = mutableView.asStateFlow()
    private var journal: NutritionJournal? = null

    @Volatile private var closed = false

    init { require(uid.isNotBlank() && (remote == null || remote.uid == uid)) }

    fun close() {
        closed = true
        remote?.stop()
        mutableView.value = NutritionViewData(status = NutritionSyncStatus.Offline)
    }

    private fun guard() {
        if (closed || !isOwner()) throw NutritionOwnerChanged()
    }

    private fun publish(status: NutritionSyncStatus) {
        if (closed || !isOwner()) {
            close()
            return
        }
        val current = journal
        mutableView.value = NutritionViewData(current?.plan, status, current?.conflict, editable = current != null)
    }

    private suspend fun persist(next: NutritionJournal) = withContext(NonCancellable) {
        guard()
        if (next != journal) store.save(next)
        guard()
        journal = next
    }

    suspend fun load() = journalLock.withLock {
        if (journal != null || closed) return@withLock
        try {
            guard()
            journal = store.read()
            publish(when {
                journal?.conflict != null -> NutritionSyncStatus.Conflict
                journal?.plan == null && remote != null -> NutritionSyncStatus.Loading
                else -> NutritionSyncStatus.Device
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: NutritionOwnerChanged) {
            close()
        } catch (_: NutritionUnreadable) {
            publish(NutritionSyncStatus.Incompatible)
        } catch (_: Exception) {
            publish(NutritionSyncStatus.Error)
        }
    }

    suspend fun save(change: (NutritionPlanData) -> NutritionPlanData): Boolean = journalLock.withLock {
        if (closed || !isOwner()) return@withLock false
        val current = journal ?: return@withLock false
        val value = change(current.plan ?: NutritionPlanData())
        NutritionRemoteCodec.encodePlan(value)
        if (value == current.plan) return@withLock true
        withContext(NonCancellable) {
            persist(current.copy(plan = value, pending = true))
            publish(if (current.conflict == null) NutritionSyncStatus.Device else NutritionSyncStatus.Conflict)
            requests.tryEmit(Unit)
            true
        }
    }

    suspend fun resolveConflict(expectedCloud: NutritionRemoteRecord, keepDevice: Boolean): Boolean = journalLock.withLock {
        if (closed || !isOwner()) return@withLock false
        val current = journal ?: return@withLock false
        val cloud = current.conflict?.takeIf { it == expectedCloud } ?: return@withLock false
        val next = if (keepDevice) NutritionJournal(current.plan, cloud, pending = true)
            else NutritionJournal(cloud.plan, cloud)
        withContext(NonCancellable) {
            persist(next)
            publish(NutritionSyncStatus.Device)
            requests.tryEmit(Unit)
            true
        }
    }

    suspend fun retry() {
        load()
        remote?.retryListener()
        requests.emit(Unit)
    }

    @OptIn(FlowPreview::class)
    suspend fun run() {
        try {
            load()
            val service = remote ?: return
            if (closed || !isOwner()) return
            service.start()
            val scheduledRetries = flow {
                while (true) {
                    delay(30_000)
                    if (service.connected.value && view.value.status == NutritionSyncStatus.Offline) emit(Unit)
                }
            }
            merge(service.connected.map { Unit }, service.changes, requests, scheduledRetries)
                .debounce(250)
                .takeWhile {
                    val currentOwner = !closed && isOwner()
                    if (!currentOwner) close()
                    currentOwner
                }
                .collect { synchronize() }
        } finally {
            remote?.stop()
        }
    }

    suspend fun synchronize() = syncLock.withLock sync@{
        val service = remote ?: return@sync
        try {
            val loaded = journalLock.withLock {
                guard()
                if (journal == null) return@withLock false
                if (!service.connected.value) {
                    publish(if (journal?.conflict != null) NutritionSyncStatus.Conflict else NutritionSyncStatus.Offline)
                    return@withLock false
                }
                if (journal?.conflict == null) publish(NutritionSyncStatus.Syncing)
                true
            }
            if (!loaded) return@sync
            repeat(4) {
                guard()
                val head = service.fetch()
                val push = journalLock.withLock { reconcile(head.record) } ?: return@sync
                guard()
                service.write(head.etag, push)
                // Even after success, confirm the canonical server document and recover by writeId
                // if the process previously stopped between the PUT and the subsequent GET.
            }
            journalLock.withLock { publish(NutritionSyncStatus.Offline) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: NutritionOwnerChanged) {
            close()
        } catch (_: NutritionUnreadable) {
            journalLock.withLock { publish(NutritionSyncStatus.Incompatible) }
        } catch (_: NutritionOffline) {
            journalLock.withLock {
                publish(if (journal?.conflict == null) NutritionSyncStatus.Offline else NutritionSyncStatus.Conflict)
            }
        } catch (_: Exception) {
            journalLock.withLock { publish(NutritionSyncStatus.Error) }
        }
    }

    /** Runs with the journal lock. Only an authoritative GET can supply an absent record. */
    private suspend fun reconcile(head: NutritionRemoteRecord?): NutritionPush? {
        guard()
        var current = checkNotNull(journal)
        val base = current.base
        if (base != null && (head == null || head.revision < base.revision ||
                (head.revision == base.revision && head != base))) throw NutritionUnreadable()
        val priorConflict = current.conflict
        if (priorConflict != null && (head == null || head.revision < priorConflict.revision ||
                (head.revision == priorConflict.revision && head != priorConflict))) throw NutritionUnreadable()

        val sent = current.sent
        if (head != null && sent != null && head.writeId == sent.writeId) {
            if (sent.revision != head.revision || sent.plan != head.plan) throw NutritionUnreadable()
            current = NutritionJournal(current.plan, head, pending = current.plan != head.plan)
            persist(current)
        }
        if (!current.pending || (head != null && current.plan == head.plan)) {
            persist(NutritionJournal(head?.plan, head))
            publish(if (head == null) NutritionSyncStatus.Empty else NutritionSyncStatus.Synced)
            return null
        }
        if (head != current.base && head != null) {
            persist(current.copy(conflict = head, sent = null))
            publish(NutritionSyncStatus.Conflict)
            return null
        }

        val plan = checkNotNull(current.plan)
        val revision = head?.revision ?: 0
        if (revision == Int.MAX_VALUE) throw NutritionUnreadable()
        val push = current.sent?.takeIf { it.plan == plan && it.revision == revision + 1 }
            ?: NutritionPush(revision + 1, writeId(), plan)
        persist(current.copy(sent = push))
        publish(NutritionSyncStatus.Syncing)
        return push
    }
}
