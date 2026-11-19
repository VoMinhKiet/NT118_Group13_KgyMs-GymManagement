package com.kgyms.app.data.model

internal enum class PersonalPlanSyncStatus { Device, Pending, Syncing, Synced, Failed, Conflict, AccessDenied }
internal enum class PersonalPlanSyncTarget { Emulator, Firebase }
internal data class PersonalPlanSyncSignal(val connected: Boolean = false, val revision: Long? = null, val epoch: Long = 0)
internal data class PersonalPlanActivation(val planId: String, val baseRevision: Long?)
internal data class PersonalPlanSyncMetadata(
    val revision: Long? = null,
    val ordinals: Map<String, Int> = emptyMap(),
    val activation: PersonalPlanActivation? = null,
    val acknowledgedActiveId: String? = null,
)
internal data class LocalPersonalPlanSnapshot(val archive: PersonalPlanArchive = PersonalPlanArchive(), val metadata: PersonalPlanSyncMetadata = PersonalPlanSyncMetadata())
internal data class RemotePersonalPlan(val plan: PersonalPlan, val ordinal: Int, val uploadedAt: Long)
internal data class RemotePersonalPlanArchive(
    val revision: Long = 0, val nextOrdinal: Int = 1, val activeId: String? = null,
    val records: List<RemotePersonalPlan> = emptyList(),
)
internal class PersonalPlanSyncConflict(message: String) : IllegalStateException(message)
internal class PersonalPlanOffline : IllegalStateException("Personal Plan connection is offline")
internal class PersonalPlanAccessDenied : IllegalStateException("Personal Plan sync permission denied")
