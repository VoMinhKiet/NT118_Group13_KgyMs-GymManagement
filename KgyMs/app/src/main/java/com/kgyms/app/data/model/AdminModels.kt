package com.kgyms.app.data.model

/*
 * ================================================================
 * DATA
 * ================================================================
 */

/** Giá trị hợp lệ của activityLogs/{id}/action (khớp database.rules.json). */
object ActivityLogAction {
    const val ACCOUNT = "account"
    const val ASSET = "asset"
    const val MEMBERSHIP = "membership"
    const val PACKAGE = "package"
    const val PAYMENT = "payment"
    const val CHECK_IN = "check_in"
    const val WORKOUT = "workout"
    const val NOTIFICATION = "notification"

    val all = listOf(ACCOUNT, ASSET, MEMBERSHIP, PACKAGE, PAYMENT, CHECK_IN, WORKOUT, NOTIFICATION)
}

/** Một dòng nhật ký — node activityLogs/{logId} trên Firebase Realtime Database. */
data class ActivityLog(
    val id: String = "",
    val title: String = "",
    val actorUid: String = "",
    val action: String = "",
    /** Định dạng "HH:mm dd/MM/yyyy". */
    val createdAt: String = ""
)

/*
 * ============================
 * ASSET DATA
 * ============================
 */

/** Giá trị hợp lệ của assets/{id}/category (khớp database.rules.json). */
object AssetCategory {
    const val CARDIO = "cardio"
    const val STRENGTH = "strength"
    const val OTHER = "other"

    val all = listOf(CARDIO, STRENGTH, OTHER)
}

/** Giá trị hợp lệ của assets/{id}/status (khớp database.rules.json). */
object AssetStatus {
    const val WORKING = "working"
    const val MAINTENANCE = "maintenance"
    const val BROKEN = "broken"

    val all = listOf(WORKING, MAINTENANCE, BROKEN)
}

/** Một loại máy/thiết bị — node assets/{assetId} trên Firebase Realtime Database. */
data class Asset(
    val id: String = "",
    val name: String = "",
    val category: String = AssetCategory.OTHER,
    val brand: String = "",
    val quantity: Int = 0,
    val status: String = AssetStatus.WORKING,
    val location: String = "",
    val purchaseDate: String = "",
    val lastMaintenanceDate: String = "",
    val nextMaintenanceDate: String = "",
    val warrantyMonths: Int = 0,
    val imageUrl: String = "",
    val description: String = ""
)

data class ComplaintItem(
    val id: String,
    val title: String,
    val memberName: String,
    val date: String,
    val priority: String,
    val status: String,
    val description: String
)

/*
 * ============================
 * MEMBER DATA
 * ============================
 */

data class MemberItem(
    val name: String,
    val description: String
)

/*
 * ============================================================
 * PACKAGE DATA
 * ============================================================
 */

data class PackageItem(
    val name: String,
    val price: String
)

/*
 * ============================
 * STAFF DATA
 * ============================
 */

data class StaffItem(
    val name: String,
    val role: String,
    val status: String
)

/*
 * ============================================================
 * TRANSACTION DATA
 * ============================================================
 */

data class TransactionItem(
    val invoice: String,
    val memberName: String,
    val service: String,
    val amount: String,
    val status: String,
    val date: String
)
