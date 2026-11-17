package com.kgyms.app.data.repository

import com.kgyms.app.data.model.*
import com.kgyms.app.data.firebase.WorkoutHistoryCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal interface WorkoutHistoryStore {
    suspend fun read(): WorkoutHistoryArchive
    suspend fun update(change: (WorkoutHistoryArchive) -> WorkoutHistoryArchive): WorkoutHistoryArchive
}
internal interface WorkoutHistoryRemote {
    val uid: String
    val available: StateFlow<Boolean>
    val changes: StateFlow<Long>
    suspend fun fetch(): RemoteWorkoutHistory
    suspend fun append(attempt: WorkoutAttempt)
    fun start() {}
    fun stop() {}
    fun close() {}
}

/** All local business transitions commit before success. Remote reads only merge immutable objects. */
internal class WorkoutHistoryRepository(val uid: String, private val store: WorkoutHistoryStore, val remote: WorkoutHistoryRemote? = null,
    private val newId: () -> String = { "attempt-${UUID.randomUUID()}" },
) {
    val archive = MutableStateFlow(WorkoutHistoryArchive())
    val status = MutableStateFlow(HistorySyncState.LocalOnly)
    val requests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    private val localMutex = Mutex()
    private val syncMutex = Mutex()
    @Volatile private var closed = false
    init { require(uid.isNotBlank()); require(remote == null || remote.uid == uid) }
    private fun live() = check(!closed) { "Workout owner session closed" }
    fun close() { closed = true; remote?.close() }
    suspend fun load() = localMutex.withLock {
        live(); archive.value = store.read()
        status.value = if(archive.value.pending.isNotEmpty())HistorySyncState.Pending else HistorySyncState.LocalOnly
    }
    private suspend fun change(block: (WorkoutHistoryArchive) -> WorkoutHistoryArchive) = localMutex.withLock {
        live(); store.update { current -> live(); block(current) }.also { archive.value = it }
    }
    suspend fun begin(context: WorkoutRunContext, now: Long): WorkoutDraft {
        require(context.exerciseIds.isNotEmpty() && context.exerciseIds.distinct().size == context.exerciseIds.size)
        val result = change { current ->
            if (context.key in current.drafts) current else current.copy(drafts = current.drafts +
                (context.key to WorkoutDraft(context, newId(), now)))
        }
        return result.drafts.getValue(context.key)
    }
    suspend fun mark(key: String, id: String) = change { current ->
        val draft = current.drafts[key] ?: return@change current
        require(id in draft.context.exerciseIds)
        current.copy(drafts = current.drafts + (key to draft.copy(performed =
            if (id in draft.performed) draft.performed - id else draft.performed + id)))
    }
    suspend fun finish(key: String, now: Long): WorkoutAttempt {
        val result = change { current ->
            val draft = current.drafts[key]
            if (draft == null) { require(current.finished(key) != null) { "No active workout" }; current }
            else {
                val c = draft.context
                val attempt = WorkoutAttempt(draft.attemptId, uid, c.logicalSessionId, c.planVersionId, c.workoutId,
                    c.titleVi, c.titleEn, draft.startedAt, now.coerceAtLeast(draft.startedAt),
                    c.exerciseIds.map { AttemptExercise(it, it in draft.performed) })
                WorkoutHistoryCodec.decode(WorkoutHistoryCodec.encode(attempt),uid,attempt.id)
                require(current.attempts.none { it.id == attempt.id })
                current.copy(attempts = current.attempts + attempt, drafts = current.drafts - key,
                    finishedRuns = current.finishedRuns + (key to attempt.id))
            }
        }
        status.value = HistorySyncState.Pending; requests.tryEmit(Unit)
        return checkNotNull(result.finished(key))
    }
    private fun merge(current: WorkoutHistoryArchive, server: RemoteWorkoutHistory): WorkoutHistoryArchive {
        require(server.attempts.all { it.uid == uid } && server.confirmations.all { it.uid == uid })
        val existing = current.attempts.associateBy { it.id }
        server.attempts.forEach { incoming -> if (existing[incoming.id]?.let { it != incoming } == true) throw WorkoutHistoryConflict() }
        val remoteIds = server.attempts.map { it.id }.toSet()
        require(server.confirmations.all { it.attemptId in remoteIds })
        val confirmations = current.confirmations.associateBy { it.attemptId }.toMutableMap()
        server.confirmations.forEach { c ->
            if (confirmations[c.attemptId]?.let { it != c } == true) throw WorkoutHistoryConflict()
            confirmations[c.attemptId] = c
        }
        return current.copy(attempts = current.attempts + server.attempts.filter { it.id !in existing },
            acknowledged = current.acknowledged + remoteIds, confirmations = confirmations.values.toList(), remoteKnown = true)
    }
    suspend fun synchronize() = syncMutex.withLock {
        live()
        val server = remote
        if (server == null || !server.available.value) {
            status.value = if (archive.value.pending.isNotEmpty()) HistorySyncState.Pending else HistorySyncState.LocalOnly
            return@withLock
        }
        status.value = HistorySyncState.Syncing
        try {
            var cloud = server.fetch(); live()
            change { merge(it, cloud) }
            val pending = archive.value.pending.toList()
            for (attempt in pending) {
                live()
                try { server.append(attempt) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    // A lost acknowledgement or another client may already have created this exact ID.
                    cloud = server.fetch(); live()
                    val found = cloud.attempts.firstOrNull { it.id == attempt.id }
                    if (found != null && found != attempt) throw WorkoutHistoryConflict()
                    if (found == null) throw error
                }
                live()
                change { it.copy(acknowledged = it.acknowledged + attempt.id) }
            }
            if(pending.isNotEmpty()) {
                cloud = server.fetch(); live()
                change { merge(it, cloud) }
            }
            status.value = if (archive.value.pending.isEmpty()) HistorySyncState.Synced else HistorySyncState.Pending
        } catch (cancelled: CancellationException) { status.value = HistorySyncState.Pending; throw cancelled }
        catch (_: WorkoutHistoryDenied) { status.value = HistorySyncState.AccessDenied }
        catch (_: WorkoutHistoryOffline) { status.value = HistorySyncState.Pending }
        catch (_: WorkoutHistoryConflict) { status.value = HistorySyncState.Conflict }
        catch (_: Exception) { status.value = HistorySyncState.Error }
    }
}
