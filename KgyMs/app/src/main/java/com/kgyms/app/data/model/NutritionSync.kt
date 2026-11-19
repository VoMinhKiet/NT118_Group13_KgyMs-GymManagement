package com.kgyms.app.data.model

internal data class NutritionRemoteRecord(
    val revision: Int,
    val writeId: String,
    val updatedAt: Long,
    val plan: NutritionPlanData,
)

internal data class NutritionPush(val revision: Int, val writeId: String, val plan: NutritionPlanData)

/** Saved atomically before any remote write, including the ID used to recover a lost acknowledgement. */
internal data class NutritionJournal(
    val plan: NutritionPlanData? = null,
    val base: NutritionRemoteRecord? = null,
    val pending: Boolean = false,
    val conflict: NutritionRemoteRecord? = null,
    val sent: NutritionPush? = null,
)

internal enum class NutritionSyncStatus {
    Loading, Device, Syncing, Synced, Empty, Offline, Error, Incompatible, Conflict,
}

internal class NutritionUnreadable : IllegalStateException("Unreadable or unsupported Nutrition data")
internal class NutritionOffline : IllegalStateException("Nutrition connection unavailable")
internal class NutritionDenied : IllegalStateException("Nutrition permission denied")
internal class NutritionOwnerChanged : IllegalStateException("Nutrition owner changed")
