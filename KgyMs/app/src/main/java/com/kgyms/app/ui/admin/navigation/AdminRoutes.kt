package com.kgyms.app.ui.admin.navigation

sealed class AdminRoute(
    val route: String
) {

    data object Dashboard :
        AdminRoute("dashboard")

    data object Members :
        AdminRoute("members")

    data object MemberDetail :
        AdminRoute("member_detail")

    data object Staff :
        AdminRoute("staff")

    data object StaffDetail :
        AdminRoute("staff_detail")

    data object Assets :
        AdminRoute("assets")

    data object AssetDetail :
        AdminRoute("asset_detail")

    data object Packages :
        AdminRoute("packages")

    data object Invoice :
        AdminRoute("invoice")

    data object Transactions :
        AdminRoute("transactions")

    data object Receipt :
        AdminRoute("receipt")

    data object Revenue :
        AdminRoute("revenue")

    data object Traffic :
        AdminRoute("traffic")

    data object Complaints :
        AdminRoute("complaints")

    data object ComplaintDetail :
        AdminRoute("complaint_detail")

    data object Notifications :
        AdminRoute("notifications")

    data object ActivityLogs :
        AdminRoute("activity_logs")
}