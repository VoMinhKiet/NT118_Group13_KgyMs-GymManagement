package com.kgyms.app.ui.admin.navigation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.staticCompositionLocalOf

/** Báo một thao tác chỉ có ở bản thử nghiệm (chưa tạo chứng từ, gọi điện hay xuất tệp thật). */
internal val LocalAdminFeedback = staticCompositionLocalOf<(String) -> Unit> { {} }

private val routes = listOf(
    AdminRoute.Dashboard, AdminRoute.Members, AdminRoute.MemberDetail,
    AdminRoute.Staff, AdminRoute.StaffDetail, AdminRoute.Assets, AdminRoute.AssetDetail,
    AdminRoute.Packages, AdminRoute.Invoice, AdminRoute.Transactions, AdminRoute.Receipt,
    AdminRoute.Revenue, AdminRoute.Traffic, AdminRoute.Complaints, AdminRoute.ComplaintDetail,
    AdminRoute.Notifications, AdminRoute.ActivityLogs,
)

internal val AdminRouteSaver = Saver<AdminRoute, String>(
    save = { it.route },
    restore = { key -> routes.firstOrNull { it.route == key } ?: AdminRoute.Dashboard },
)
