package com.kgyms.app.ui.admin

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.kgyms.app.ui.admin.screens.ComplaintItem
import com.kgyms.app.ui.admin.screens.TransactionItem

// Reports a UI-only action without claiming to create invoices, call or export.
internal val LocalAdminFeedback = staticCompositionLocalOf<(String) -> Unit> { {} }

private val routes = listOf(
    AdminRoute.Dashboard, AdminRoute.Members, AdminRoute.MemberDetail,
    AdminRoute.Staff, AdminRoute.StaffDetail, AdminRoute.Assets, AdminRoute.AssetDetail,
    AdminRoute.Packages, AdminRoute.Invoice, AdminRoute.Transactions, AdminRoute.Receipt,
    AdminRoute.Revenue, AdminRoute.Traffic, AdminRoute.Complaints, AdminRoute.ComplaintDetail,
    AdminRoute.Hotline, AdminRoute.ActivityLogs,
)
internal val AdminRouteSaver = Saver<AdminRoute, String>(
    save = { it.route }, restore = { key -> routes.firstOrNull { it.route == key } ?: AdminRoute.Dashboard },
)
internal val AdminTransactionSaver = listSaver<TransactionItem?, String>(
    save = { t -> t?.let { listOf(it.invoice, it.memberName, it.service, it.amount, it.status, it.date) } ?: emptyList() },
    restore = { if (it.size == 6) TransactionItem(it[0], it[1], it[2], it[3], it[4], it[5]) else null },
)
internal val AdminComplaintSaver = listSaver<ComplaintItem?, String>(
    save = { c -> c?.let { listOf(it.id, it.title, it.memberName, it.date, it.priority, it.status, it.description) } ?: emptyList() },
    restore = { if (it.size == 7) ComplaintItem(it[0], it[1], it[2], it[3], it[4], it[5], it[6]) else null },
)
