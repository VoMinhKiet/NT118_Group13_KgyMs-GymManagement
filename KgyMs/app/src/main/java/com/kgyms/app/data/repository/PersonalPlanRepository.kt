package com.kgyms.app.data.repository

import com.kgyms.app.data.model.PersonalPlan
import com.kgyms.app.data.model.PersonalPlanArchive

/** Local storage in phase 1. A future remote source belongs only under memberWorkoutPlans/{uid}. */
internal enum class PersonalPlanStorage { Memory, Device }

internal interface PersonalPlanRepository {
    val uid: String
    val storage: PersonalPlanStorage get() = PersonalPlanStorage.Memory
    suspend fun load(): PersonalPlanArchive
    suspend fun save(plan: PersonalPlan, expectedActiveId: String?): PersonalPlanArchive
}

internal fun appendPersonalPlan(archive: PersonalPlanArchive, plan: PersonalPlan, uid: String, expectedActiveId: String?): PersonalPlanArchive {
    require(plan.uid == uid && uid.isNotBlank()) { "Account changed" }
    require(archive.plans.all { it.uid == uid }) { "Wrong owner" }
    val previous = archive.plans.firstOrNull { it.id == plan.id }
    require(previous == null || previous == plan) { "Existing plan cannot be overwritten" }
    if (archive.activeId == plan.id && previous == plan) return archive
    check(archive.activeId == expectedActiveId) { "Plan changed; reload first" }
    return PersonalPlanArchive(plan.id, if (previous == null) archive.plans + plan else archive.plans)
}

internal class MemoryPersonalPlanRepository(override val uid: String) : PersonalPlanRepository {
    private var archive = PersonalPlanArchive()
    override suspend fun load() = archive
    override suspend fun save(plan: PersonalPlan, expectedActiveId: String?): PersonalPlanArchive {
        archive = appendPersonalPlan(archive, plan, uid, expectedActiveId)
        return archive
    }
}
