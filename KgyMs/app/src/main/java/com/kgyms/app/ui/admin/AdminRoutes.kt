package com.kgyms.app.ui.admin

sealed class AdminRoute(val route: String) {

    data object Dashboard : AdminRoute("admin_dashboard")

    // Sau này thêm các màn hình Admin ở đây
    data object Members : AdminRoute("admin_members")
    data object MemberDetail : AdminRoute("admin_member_detail")

    data object Staff : AdminRoute("admin_staff")
    data object StaffDetail : AdminRoute("admin_staff_detail")

    data object Assets : AdminRoute("admin_assets")
    data object AssetDetail : AdminRoute("admin_asset_detail")

    data object Packages : AdminRoute("admin_packages")
    data object Invoice : AdminRoute("admin_invoice")
    data object Transactions : AdminRoute("admin_transactions")

    data object Receipt : AdminRoute("admin_receipt")
    data object Revenue : AdminRoute("admin_revenue")
    data object Traffic : AdminRoute("admin_traffic")

    data object Complaints : AdminRoute("admin_complaints")
    data object ComplaintDetail : AdminRoute("admin_complaint_detail")

    data object Hotline : AdminRoute("admin_hotline")
    data object ActivityLogs : AdminRoute("admin_activity_logs")
}