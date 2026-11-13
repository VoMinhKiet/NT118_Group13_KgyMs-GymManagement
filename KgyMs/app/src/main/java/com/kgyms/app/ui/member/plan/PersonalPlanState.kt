package com.kgyms.app.ui.member.plan

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.*
import com.kgyms.app.utils.PersonalPlanGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val LocalPersonalPlans = staticCompositionLocalOf<PersonalPlanState?> { null }

@Stable
internal class PersonalPlanState(val repository: PersonalPlanRepository) {
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
    val active get() = archive.active
    suspend fun load() {
        loading = true; failed = false; saveNotice = false
        try { archive = repository.load(); check(archive.plans.all { it.uid == uid }) }
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
            saveNotice = true
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        finally { saving = false }
    }

    /** Only schedule data is projected; confirmed history is never manufactured. */
    fun catalog(base: WorkoutCatalog): WorkoutCatalog {
        if (loading || failed) return base
        val workouts = archive.plans.flatMap { plan -> plan.sessions.mapNotNull { session ->
            base.workouts.firstOrNull { it.id == session.templateId }?.copy(
                id = "personal-workout-${session.id}", exerciseIds = session.exerciseIds,
                title = WorkoutText("Buổi tập cá nhân", "Personal workout"), level = plan.config.level,
            )
        } }
        val sessions = archive.plans.flatMap { plan -> plan.sessions.map { session ->
            WorkoutSessionUiModel(session.id, "personal-workout-${session.id}", PlanDates.millis(session.date, plan.config.zoneId), SessionStatus.Planned, PlanSource.Self)
        } }
        // Active plan appears on the weekly overview; archived sessions still resolve via their ID.
        return base.copy(workouts = base.workouts + workouts, sessions = base.sessions + sessions)
    }
}

@Composable
internal fun rememberPersonalPlanState(uid: String?): PersonalPlanState {
    val context = LocalContext.current.applicationContext
    val state = remember(uid, context) { PersonalPlanState(if (uid == null) MemoryPersonalPlanRepository("preview") else LocalPersonalPlanRepository(context, uid)) }
    LaunchedEffect(state) { state.load() }
    return state
}
