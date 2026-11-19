package com.kgyms.app.ui.member.plan

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.*
import com.kgyms.app.utils.PersonalPlanGenerator
import com.kgyms.app.utils.PersonalPlanEditor
import com.kgyms.app.utils.ExerciseEditScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import com.kgyms.app.data.firebase.PersonalPlanProductionConnection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val LocalPersonalPlans = staticCompositionLocalOf<PersonalPlanState?> { null }

internal enum class PlanActivationFailure { Offline, Conflict, AccessDenied, Invalid, Failed }

@Stable
internal class PersonalPlanState(val repository: PersonalPlanRepository) {
    val syncTarget get() = (repository as? SyncedPersonalPlanRepository)?.target
    val syncRequests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val uid get() = repository.uid
    var archive by mutableStateOf(PersonalPlanArchive())
        private set
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var saveNotice by mutableStateOf(false)
        private set
    var activatingPlanId by mutableStateOf<String?>(null)
        private set
    var activationSucceeded by mutableStateOf(false)
        private set
    var activationFailure by mutableStateOf<PlanActivationFailure?>(null)
        private set
    val busy get() = saving || activatingPlanId != null
    val active get() = archive.active
    val remoteNotChecked get() = repository is SyncedPersonalPlanRepository && archive.plans.isEmpty() && syncStatus != PersonalPlanSyncStatus.Synced
    var syncStatus by mutableStateOf(PersonalPlanSyncStatus.Device)
        private set
    var activeSyncConflict by mutableStateOf(false)
        private set
    var remoteActiveId by mutableStateOf<String?>(null)
        private set
    private var ordinals by mutableStateOf<Map<String, Int>>(emptyMap())
    /** Append order is persisted per UID; display sorting and active selection never renumber plans. */
    fun versionNumber(planId: String): Int? = ordinals[planId] ?: archive.plans.indexOfFirst { it.id == planId && it.uid == uid }
        .takeIf { it >= 0 }?.plus(1)
    suspend fun load() {
        loading = true; failed = false; saveNotice = false
        try { archive = repository.load(); check(archive.plans.all { it.uid == uid }); syncMetadata() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    suspend fun save(plan: PersonalPlan, catalog: WorkoutCatalog, now: Long): Boolean {
        if (loading || failed || saving || plan.uid != uid) return false
        saving = true; saveNotice = false
        return try {
            PersonalPlanGenerator.validate(plan, catalog, now)
            archive = repository.save(PersonalPlanGenerator.seal(plan), archive.activeId)
            syncMetadata()
            syncRequests.tryEmit(Unit)
            saveNotice = true
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        finally { saving = false }
    }

    suspend fun saveExercises(original: PersonalPlan, sessionId: String, ids: List<String>, editScope: ExerciseEditScope,
        catalog: WorkoutCatalog, expectedActiveId: String?): Boolean {
        if (loading || failed || saving || original.uid != uid || archive.activeId != expectedActiveId ||
            archive.plans.none { it == original }) return false
        saving = true; saveNotice = false
        return try {
            val edited = PersonalPlanEditor.apply(original, sessionId, ids, editScope, catalog)
            val revision = PersonalPlanEditor.revision(original, edited, catalog)
            if (revision != original) archive = repository.save(revision, expectedActiveId)
            syncMetadata()
            if (revision != original) syncRequests.tryEmit(Unit)
            saveNotice = true
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        finally { saving = false }
    }

    private fun syncMetadata() {
        (repository as? SyncedPersonalPlanRepository)?.let { repo ->
            syncStatus = repo.status.value; ordinals = repo.metadata.ordinals
            activeSyncConflict = repo.activeConflict; remoteActiveId = repo.metadata.acknowledgedActiveId
        }
    }
    suspend fun synchronize(automatic: Boolean = false) {
        val repo = repository as? SyncedPersonalPlanRepository ?: return
        if (loading || failed || saving) return
        syncStatus = PersonalPlanSyncStatus.Syncing
        try {
            if (!automatic) repo.prepareRetry()
            repo.synchronize()
            archive = repo.load(); syncMetadata()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { syncStatus = PersonalPlanSyncStatus.Failed }
    }
    suspend fun selectActive(id: String): Boolean {
        val repo = repository as? ActivePersonalPlanRepository ?: return false
        if (loading || failed || busy || archive.plans.none { it.id == id && it.uid == uid }) return false
        activatingPlanId = id
        activationSucceeded = false
        activationFailure = null
        return try {
            val updated = repo.selectActive(id)
            check(updated.activeId == id && updated.plans.all { it.uid == uid })
            archive = updated
            syncMetadata()
            activationSucceeded = true
            syncRequests.tryEmit(Unit)
            true
        } catch (e: CancellationException) { throw e }
        catch (_: PersonalPlanOffline) { activationFailure = PlanActivationFailure.Offline; false }
        catch (_: PersonalPlanSyncConflict) { activationFailure = PlanActivationFailure.Conflict; false }
        catch (_: PersonalPlanAccessDenied) { activationFailure = PlanActivationFailure.AccessDenied; false }
        catch (_: IllegalArgumentException) { activationFailure = PlanActivationFailure.Invalid; false }
        catch (_: Exception) { activationFailure = PlanActivationFailure.Failed; false }
        finally { activatingPlanId = null }
    }

    /** Only schedule data is projected; confirmed history is never manufactured. */
    fun catalog(base: WorkoutCatalog): WorkoutCatalog {
        if (loading || failed) return base
        val workouts = archive.plans.flatMap { plan -> plan.sessions.mapNotNull { session ->
            base.workouts.firstOrNull { it.id == session.templateId }?.copy(
                id = "personal-workout-${plan.runtimeSessionId(session)}", exerciseIds = session.exerciseIds,
                title = WorkoutText("Buổi tập cá nhân", "Personal workout"), level = plan.config.level,
            )
        } }
        val sessions = archive.plans.flatMap { plan -> plan.sessions.map { session ->
            val runtimeId = plan.runtimeSessionId(session)
            WorkoutSessionUiModel(runtimeId, "personal-workout-$runtimeId", PlanDates.millis(session.date, plan.config.zoneId), SessionStatus.Planned, PlanSource.Self)
        } }
        // Active plan appears on the weekly overview; archived sessions still resolve via their ID.
        return base.copy(workouts = base.workouts + workouts, sessions = base.sessions + sessions)
    }
}

@Composable
internal fun rememberPersonalPlanState(uid: String?): PersonalPlanState {
    val context = LocalContext.current.applicationContext
    val connection = remember(uid, context) { uid?.let { runCatching { PersonalPlanProductionConnection.open(context, it) }.getOrNull() } }
    val state = remember(uid, context, connection) {
        PersonalPlanState(if (uid == null) MemoryPersonalPlanRepository("preview")
        else if (connection != null) SyncedPersonalPlanRepository(LocalPersonalPlanRepository(context, uid, "firebase_sync_v1"),
            com.kgyms.app.data.firebase.PersonalPlanFirebaseService(connection, uid))
        else LocalPersonalPlanRepository(context, uid))
    }
    LaunchedEffect(state) { state.load() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state, connection, foreground, state.loading, state.failed) {
        if (connection != null && foreground && !state.loading && !state.failed) {
            connection.start()
            try {
                PersonalPlanSyncCoordinator().run(connection.signals, state.syncRequests,
                    { (state.repository as SyncedPersonalPlanRepository).metadata.revision }, { state.syncStatus }, { state.synchronize(automatic = true) })
            } finally { connection.stop() }
        }
    }
    DisposableEffect(state, connection) {
        onDispose { (state.repository as? SyncedPersonalPlanRepository)?.close(); connection?.close() }
    }
    return state
}
