package com.kgyms.app.data.model

/*
 * ================================================================
 * DATA
 * ================================================================
 */

data class ActivityLogItem(
    val title: String,
    val time: String
)

/*
 * ============================
 * ASSET DATA
 * ============================
 */

data class AssetItem(
    val name: String,
    val quantity: String,
    val description: String
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
