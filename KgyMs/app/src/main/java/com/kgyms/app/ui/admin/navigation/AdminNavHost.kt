package com.kgyms.app.ui.admin.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminAppearance
import com.kgyms.app.ui.admin.components.AdminBottomBar
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminTopBar
import com.kgyms.app.ui.admin.screens.ActivityLogsScreen
import com.kgyms.app.ui.admin.screens.AssetDetailScreen
import com.kgyms.app.ui.admin.screens.AssetScreen
import com.kgyms.app.ui.admin.screens.ComplaintDetailScreen
import com.kgyms.app.ui.admin.screens.ComplaintScreen
import com.kgyms.app.ui.admin.screens.DashboardScreen
import com.kgyms.app.ui.admin.screens.InvoiceScreen
import com.kgyms.app.ui.admin.screens.MemberDetailScreen
import com.kgyms.app.ui.admin.screens.MemberScreen
import com.kgyms.app.ui.admin.screens.NotificationScreen
import com.kgyms.app.ui.admin.screens.PackageScreen
import com.kgyms.app.ui.admin.screens.ReceiptScreen
import com.kgyms.app.ui.admin.screens.RevenueScreen
import com.kgyms.app.ui.admin.screens.StaffDetailScreen
import com.kgyms.app.ui.admin.screens.StaffScreen
import com.kgyms.app.ui.admin.screens.TrafficScreen
import com.kgyms.app.ui.admin.screens.TransactionsScreen
import com.kgyms.app.viewmodel.admin.BillingViewModel
import com.kgyms.app.viewmodel.admin.NotificationAdminViewModel

/** 4 tab chính có bottom bar; mọi màn khác là màn phụ. */
private val primaryRoutes = listOf(AdminRoute.Dashboard, AdminRoute.Members, AdminRoute.Staff, AdminRoute.Assets)

/** Màn chi tiết quay về màn danh sách cha của nó. */
private val detailParents = mapOf(
    AdminRoute.MemberDetail to AdminRoute.Members,
    AdminRoute.StaffDetail to AdminRoute.Staff,
    AdminRoute.AssetDetail to AdminRoute.Assets,
    AdminRoute.Transactions to AdminRoute.Invoice,
    AdminRoute.ComplaintDetail to AdminRoute.Complaints
)

private fun AdminRoute.title(): String = when (this) {
    AdminRoute.Dashboard -> "KgyMs Admin"
    AdminRoute.Members -> "Quản lý hội viên"
    AdminRoute.MemberDetail -> "Chi tiết hội viên"
    AdminRoute.Staff -> "Nhân sự & PT"
    AdminRoute.StaffDetail -> "Hồ sơ nhân sự"
    AdminRoute.Assets -> "Danh mục tài sản"
    AdminRoute.AssetDetail -> "Chi tiết tài sản"
    AdminRoute.Packages -> "Quản lý gói tập"
    AdminRoute.Invoice -> "Quản lý hóa đơn"
    AdminRoute.Transactions -> "Lịch sử giao dịch"
    AdminRoute.Receipt -> "Biên lai"
    AdminRoute.Revenue -> "Doanh thu"
    AdminRoute.Traffic -> "Lưu lượng ra vào"
    AdminRoute.Complaints -> "Quản lý khiếu nại"
    AdminRoute.ComplaintDetail -> "Xử lý khiếu nại"
    AdminRoute.Notifications -> "Quản lý thông báo"
    AdminRoute.ActivityLogs -> "Nhật ký hoạt động"
}

@Composable
fun AdminNavHost(onLogout: () -> Unit = {}) {
    // Cùng MaterialTheme và màu status bar với Member.
    AdminAppearance {
        AdminNavHostContent(onLogout)
    }
}

@Composable
private fun AdminNavHostContent(onLogout: () -> Unit) {
    // Mỗi lần admin vào app: tự quét và gửi nhắc hết hạn gói (nếu đang bật).
    val notificationViewModel: NotificationAdminViewModel = viewModel()
    LaunchedEffect(Unit) { notificationViewModel.runAutoRemindersOnce() }

    val screenState = rememberSaveableStateHolder()
    var logoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }

    var currentRoute by rememberSaveable(stateSaver = AdminRouteSaver) { mutableStateOf<AdminRoute>(AdminRoute.Dashboard) }
    // Tab chính đã mở màn phụ hiện tại (để nút back quay đúng tab).
    var primaryParent by rememberSaveable(stateSaver = AdminRouteSaver) { mutableStateOf<AdminRoute>(AdminRoute.Dashboard) }

    // Id bản ghi đang mở ở các màn chi tiết.
    var memberId by rememberSaveable { mutableStateOf("") }
    var staffId by rememberSaveable { mutableStateOf("") }
    var assetId by rememberSaveable { mutableStateOf("") }
    var invoiceId by rememberSaveable { mutableStateOf("") }
    var complaintId by rememberSaveable { mutableStateOf("") }

    // Màn mở được từ nhiều nơi (Biên lai, Hoá đơn, Thông báo) nhớ màn đã mở nó để back quay đúng chỗ.
    // Chuỗi rỗng = dùng quy tắc mặc định.
    var receiptReturn by rememberSaveable { mutableStateOf("") }
    var invoiceReturn by rememberSaveable { mutableStateOf("") }
    var notificationReturn by rememberSaveable { mutableStateOf("") }

    val billingViewModel: BillingViewModel = viewModel()

    fun navigate(target: AdminRoute) {
        if (target in primaryRoutes) primaryParent = target
        else if (currentRoute in primaryRoutes) primaryParent = currentRoute
        when (target) {
            AdminRoute.Receipt -> receiptReturn = currentRoute.route
            AdminRoute.Invoice -> invoiceReturn = ""
            AdminRoute.Notifications -> notificationReturn = ""
            else -> Unit
        }
        currentRoute = target
    }

    /** Mở màn [target] từ chi tiết hội viên; back sẽ quay lại chi tiết hội viên. */
    fun openFromMember(target: AdminRoute) {
        navigate(target)
        when (target) {
            AdminRoute.Invoice -> invoiceReturn = AdminRoute.MemberDetail.route
            AdminRoute.Notifications -> notificationReturn = AdminRoute.MemberDetail.route
            else -> Unit
        }
    }

    fun goBack() {
        val override = when (currentRoute) {
            AdminRoute.Receipt -> adminRouteOf(receiptReturn) ?: AdminRoute.Transactions
            AdminRoute.Invoice -> adminRouteOf(invoiceReturn)
            AdminRoute.Notifications -> adminRouteOf(notificationReturn)
            else -> null
        }
        currentRoute = override
            ?: detailParents[currentRoute]
            ?: primaryParent.takeIf { currentRoute != it }
            ?: AdminRoute.Dashboard
    }

    BackHandler(enabled = currentRoute != AdminRoute.Dashboard) { goBack() }

    val showBottomBar = currentRoute in primaryRoutes

    CompositionLocalProvider(LocalAdminFeedback provides { feedback = it }) {
        Column(modifier = Modifier.fillMaxSize().background(AdminColors.Background)) {
            AdminTopBar(
                title = currentRoute.title(),
                subtitle = if (currentRoute == AdminRoute.Dashboard) "Tổng quan hệ thống" else null,
                showBackButton = currentRoute != AdminRoute.Dashboard,
                onBack = ::goBack,
                onProfileClick = { feedback = "Thông tin tài khoản Admin • Bản thử nghiệm tích hợp, chưa có tài khoản backend." },
                onLogoutClick = { logoutConfirmation = true }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    // Màn phụ không có bottom bar nên tự chừa chỗ cho thanh điều hướng hệ thống.
                    .then(if (showBottomBar) Modifier else Modifier.navigationBarsPadding())
                    .testTag("admin_route_${currentRoute.route}")
            ) {
                val recordKey = when (currentRoute) {
                    AdminRoute.MemberDetail -> memberId
                    AdminRoute.StaffDetail -> staffId
                    AdminRoute.AssetDetail -> assetId
                    AdminRoute.Receipt -> invoiceId
                    AdminRoute.ComplaintDetail -> complaintId
                    else -> ""
                }
                screenState.SaveableStateProvider("${currentRoute.route}:$recordKey") {
                    when (currentRoute) {
                        AdminRoute.Dashboard -> DashboardScreen(onNavigate = ::navigate)
                        AdminRoute.Members -> MemberScreen(onMemberClick = { memberId = it; navigate(AdminRoute.MemberDetail) })
                        AdminRoute.MemberDetail -> MemberDetailScreen(
                            memberId = memberId,
                            onCreateInvoice = { uid ->
                                billingViewModel.openDeskForm(uid)
                                openFromMember(AdminRoute.Invoice)
                            },
                            onSendNotification = { uid ->
                                notificationViewModel.openComposeFor(uid)
                                openFromMember(AdminRoute.Notifications)
                            },
                            onReceiptClick = { invoiceId = it; navigate(AdminRoute.Receipt) }
                        )
                        AdminRoute.Staff -> StaffScreen(onStaffClick = { staffId = it; navigate(AdminRoute.StaffDetail) })
                        AdminRoute.StaffDetail -> StaffDetailScreen(staffId)
                        AdminRoute.Assets -> AssetScreen(onNavigate = ::navigate, onAssetClick = { assetId = it; navigate(AdminRoute.AssetDetail) })
                        AdminRoute.AssetDetail -> AssetDetailScreen(assetId, onBack = ::goBack)
                        AdminRoute.Packages -> PackageScreen()
                        AdminRoute.Invoice -> InvoiceScreen(
                            onTransactionClick = { navigate(AdminRoute.Transactions) },
                            onReceiptClick = { invoiceId = it; navigate(AdminRoute.Receipt) },
                            viewModel = billingViewModel
                        )
                        AdminRoute.Transactions -> TransactionsScreen(onReceiptClick = { invoiceId = it; navigate(AdminRoute.Receipt) }, viewModel = billingViewModel)
                        AdminRoute.Receipt -> ReceiptScreen(invoiceId, onMissing = ::goBack, viewModel = billingViewModel)
                        AdminRoute.Revenue -> RevenueScreen(onReceiptClick = { invoiceId = it; navigate(AdminRoute.Receipt) })
                        AdminRoute.Traffic -> TrafficScreen()
                        AdminRoute.Complaints -> ComplaintScreen(onComplaintClick = { complaintId = it; navigate(AdminRoute.ComplaintDetail) })
                        AdminRoute.ComplaintDetail -> ComplaintDetailScreen(complaintId, onMissing = { currentRoute = AdminRoute.Complaints })
                        AdminRoute.Notifications -> NotificationScreen(notificationViewModel)
                        AdminRoute.ActivityLogs -> ActivityLogsScreen()
                    }
                }
            }

            if (showBottomBar) {
                AdminBottomBar(currentRoute = currentRoute, onNavigate = ::navigate)
            }
        }
    }

    if (logoutConfirmation) {
        AlertDialog(
            onDismissRequest = { logoutConfirmation = false },
            containerColor = AdminColors.Surface,
            titleContentColor = AdminColors.TextPrimary,
            textContentColor = AdminColors.TextSecondary,
            title = { Text("Đăng xuất khỏi KgyMs?") },
            text = { Text("Bạn sẽ quay về màn hình đăng nhập.") },
            confirmButton = {
                TextButton(
                    onClick = { logoutConfirmation = false; onLogout() },
                    modifier = Modifier.testTag("admin_confirm_logout")
                ) { Text("Đăng xuất", color = AdminColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { logoutConfirmation = false }) { Text("Hủy", color = AdminColors.TextSecondary) }
            }
        )
    }

    feedback?.let { message ->
        AlertDialog(
            onDismissRequest = { feedback = null },
            containerColor = AdminColors.Surface,
            titleContentColor = AdminColors.TextPrimary,
            textContentColor = AdminColors.TextSecondary,
            title = { Text("Chức năng đang phát triển") },
            text = { Text("$message\n\nThao tác này chưa ghi dữ liệu, tạo chứng từ hay xuất tệp thật.") },
            confirmButton = {
                TextButton(onClick = { feedback = null }, modifier = Modifier.testTag("admin_feedback_ok")) {
                    Text("Đã hiểu", color = AdminColors.Primary)
                }
            }
        )
    }
}
