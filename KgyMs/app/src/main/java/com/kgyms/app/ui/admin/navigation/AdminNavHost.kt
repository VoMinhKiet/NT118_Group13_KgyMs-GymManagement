package com.kgyms.app.ui.admin.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kgyms.app.ui.admin.components.AdminAppearance
import com.kgyms.app.ui.admin.screens.ActivityLogsScreen
import com.kgyms.app.ui.admin.screens.NotificationScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.viewmodel.admin.NotificationAdminViewModel
import com.kgyms.app.ui.admin.screens.AssetDetailScreen
import com.kgyms.app.ui.admin.screens.AssetScreen
import com.kgyms.app.ui.admin.screens.ComplaintDetailScreen
import com.kgyms.app.data.model.ComplaintItem
import com.kgyms.app.ui.admin.screens.ComplaintScreen
import com.kgyms.app.ui.admin.screens.DashboardScreen
import com.kgyms.app.ui.admin.screens.InvoiceScreen
import com.kgyms.app.ui.admin.screens.MemberDetailScreen
import com.kgyms.app.ui.admin.screens.MemberScreen
import com.kgyms.app.ui.admin.screens.PackageScreen
import com.kgyms.app.ui.admin.screens.ReceiptScreen
import com.kgyms.app.ui.admin.screens.RevenueScreen
import com.kgyms.app.ui.admin.screens.StaffDetailScreen
import com.kgyms.app.ui.admin.screens.StaffScreen
import com.kgyms.app.ui.admin.screens.TrafficScreen
import com.kgyms.app.data.model.TransactionItem
import com.kgyms.app.ui.admin.screens.TransactionsScreen
import com.kgyms.app.ui.admin.components.AdminBottomBar
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminTopBar



@Composable
fun AdminNavHost(onLogout: () -> Unit = {}) {
    // Cùng MaterialTheme và màu status bar với Member; luồng dữ liệu Admin giữ nguyên.
    AdminAppearance {
        AdminNavHostContent(onLogout)
    }
}

@Composable
private fun AdminNavHostContent(onLogout: () -> Unit) {
    // Mỗi lần admin vào app: tự quét và gửi nhắc hết hạn gói (nếu đang bật).
    val notificationViewModel: NotificationAdminViewModel = viewModel()
    LaunchedEffect(Unit) { notificationViewModel.runAutoRemindersOnce() }

    var logoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    val screenState = rememberSaveableStateHolder()

    /*
     * ============================================================
     * CURRENT ROUTE
     * ============================================================
     */

    var currentRoute by rememberSaveable(stateSaver = AdminRouteSaver) {
        mutableStateOf<AdminRoute>(
            AdminRoute.Dashboard
        )
    }


    /*
     * ============================================================
     * SELECTED MEMBER
     * ============================================================
     */

    var selectedMemberName by rememberSaveable {
        mutableStateOf(
            "Nguyễn Minh Anh"
        )
    }


    /*
     * ============================================================
     * SELECTED STAFF
     * ============================================================
     */

    var selectedStaffName by rememberSaveable {
        mutableStateOf(
            "Trần Hoàng"
        )
    }


    /*
     * ============================================================
     * SELECTED ASSET
     * ============================================================
     */

    // Id của máy trong assets/{assetId} trên Firebase.
    var selectedAssetId by rememberSaveable {
        mutableStateOf("")
    }


    /*
     * ============================================================
     * SELECTED TRANSACTION
     * ============================================================
     */

    var selectedTransaction by rememberSaveable(stateSaver = AdminTransactionSaver) {
        mutableStateOf<TransactionItem?>(null)
    }


    /*
     * ============================================================
     * SELECTED COMPLAINT
     * ============================================================
     */

    var selectedComplaint by rememberSaveable(stateSaver = AdminComplaintSaver) {
        mutableStateOf<ComplaintItem?>(null)
    }

    var primaryParent by rememberSaveable(stateSaver = AdminRouteSaver) { mutableStateOf<AdminRoute>(AdminRoute.Dashboard) }
    val primaryRoutes = listOf(AdminRoute.Dashboard, AdminRoute.Members, AdminRoute.Staff, AdminRoute.Assets)
    fun navigateAdmin(target: AdminRoute) {
        if (target in primaryRoutes) primaryParent = target
        else if (currentRoute in primaryRoutes) primaryParent = currentRoute
        currentRoute = target
    }



    /*
     * ============================================================
     * BACK NAVIGATION
     * ============================================================
     *
     * Hàm này được dùng chung cho:
     *
     * 1. Nút Back trên TopBar
     * 2. Nút Back vật lý của Android
     *
     */

    fun goBack() {

        when (currentRoute) {

            /*
             * ====================================================
             * MEMBER DETAIL
             * ====================================================
             */

            AdminRoute.MemberDetail -> {

                currentRoute =
                    AdminRoute.Members
            }


            /*
             * ====================================================
             * STAFF DETAIL
             * ====================================================
             */

            AdminRoute.StaffDetail -> {

                currentRoute =
                    AdminRoute.Staff
            }


            /*
             * ====================================================
             * ASSET DETAIL
             * ====================================================
             */

            AdminRoute.AssetDetail -> {

                currentRoute =
                    AdminRoute.Assets
            }


            /*
             * ====================================================
             * TRANSACTIONS
             * ====================================================
             */

            AdminRoute.Transactions -> {

                currentRoute =
                    AdminRoute.Invoice
            }


            /*
             * ====================================================
             * RECEIPT
             * ====================================================
             */

            AdminRoute.Receipt -> {

                currentRoute =
                    AdminRoute.Transactions
            }


            /*
             * ====================================================
             * COMPLAINT DETAIL
             * ====================================================
             */

            AdminRoute.ComplaintDetail -> {

                currentRoute =
                    AdminRoute.Complaints
            }


            /*
             * ====================================================
             * REVENUE
             * ====================================================
             */

            AdminRoute.Revenue -> {

                currentRoute =
                    primaryParent
            }


            /*
             * ====================================================
             * TRAFFIC
             * ====================================================
             */

            AdminRoute.Traffic -> {

                currentRoute =
                    primaryParent
            }


            /*
             * ====================================================
             * INVOICE
             * ====================================================
             */

            AdminRoute.Invoice -> {

                currentRoute =
                    primaryParent
            }


            /*
             * ====================================================
             * PACKAGE
             * ====================================================
             */

            AdminRoute.Packages -> {

                currentRoute =
                    primaryParent
            }


            /*
             * ====================================================
             * COMPLAINT LIST
             * ====================================================
             */

            AdminRoute.Complaints -> {

                currentRoute =
                    primaryParent
            }


            /*
             * ====================================================
             * ACTIVITY LOGS
             * ====================================================
             */

            AdminRoute.Notifications,
            AdminRoute.ActivityLogs -> {

                currentRoute =
                    primaryParent
            }


            /*
             * ====================================================
             * DASHBOARD
             * ====================================================
             *
             * Không xử lý ở đây.
             *
             * Khi đang ở Dashboard và BackHandler không active,
             * Android sẽ tự xử lý nút Back và Activity sẽ đóng.
             */

            AdminRoute.Dashboard -> {

                // Để Android xử lý mặc định.
            }


            /*
             * ====================================================
             * DEFAULT
             * ====================================================
             */

            else -> {

                currentRoute =
                    AdminRoute.Dashboard
            }
        }
    }


    /*
     * ============================================================
     * ANDROID SYSTEM BACK
     * ============================================================
     *
     * Chỉ intercept nút Back Android khi KHÔNG ở Dashboard.
     *
     * Nếu đang ở Dashboard:
     *
     * BackHandler = disabled
     *        ↓
     * Android xử lý Back mặc định
     *        ↓
     * Activity đóng
     *
     */

    BackHandler(
        enabled =
            currentRoute !=
                    AdminRoute.Dashboard
    ) {

        goBack()
    }


    /*
     * ============================================================
     * ADMIN SHELL
     * ============================================================
     */

    // BottomBar chỉ xuất hiện ở các màn hình chính; ở màn phụ thì nội dung tự chừa chỗ cho thanh điều hướng của hệ thống.
    val showBottomBar = currentRoute !in listOf(
        AdminRoute.MemberDetail,
        AdminRoute.StaffDetail,
        AdminRoute.AssetDetail,
        AdminRoute.Packages,
        AdminRoute.Invoice,
        AdminRoute.Transactions,
        AdminRoute.Receipt,
        AdminRoute.Revenue,
        AdminRoute.Traffic,
        AdminRoute.Complaints,
        AdminRoute.ComplaintDetail,
        AdminRoute.Notifications,
        AdminRoute.ActivityLogs
    )

    CompositionLocalProvider(LocalAdminFeedback provides { feedback = it }) {
    Column(
        modifier =
            Modifier.fillMaxSize().background(AdminColors.Background)
    ) {


        /*
         * ========================================================
         * TOP BAR
         * ========================================================
         */

        AdminTopBar(

            title = when (currentRoute) {

                AdminRoute.Dashboard ->
                    "KgyMs Admin"

                AdminRoute.Members ->
                    "Quản lý hội viên"

                AdminRoute.MemberDetail ->
                    "Chi tiết hội viên"

                AdminRoute.Staff ->
                    "Nhân sự & PT"

                AdminRoute.StaffDetail ->
                    "Hồ sơ nhân sự"

                AdminRoute.Assets ->
                    "Danh mục tài sản"

                AdminRoute.AssetDetail ->
                    "Chi tiết tài sản"

                AdminRoute.Packages ->
                    "Quản lý gói tập"

                AdminRoute.Invoice ->
                    "Tạo hóa đơn"

                AdminRoute.Transactions ->
                    "Lịch sử giao dịch"

                AdminRoute.Receipt ->
                    "Chia sẻ biên lai"

                AdminRoute.Revenue ->
                    "Dashboard doanh thu"

                AdminRoute.Traffic ->
                    "Lưu lượng ra vào"

                AdminRoute.Complaints ->
                    "Quản lý khiếu nại"

                AdminRoute.ComplaintDetail ->
                    "Xử lý khiếu nại"

                AdminRoute.Notifications ->
                    "Quản lý thông báo"

                AdminRoute.ActivityLogs ->
                    "Nhật ký hoạt động"

                else ->
                    "KgyMs Admin"
            },


            /*
             * ====================================================
             * SUBTITLE
             * ====================================================
             */

            subtitle =
                if (
                    currentRoute ==
                    AdminRoute.Dashboard
                ) {

                    "Tổng quan hệ thống"

                } else {

                    null
                },


            /*
             * ====================================================
             * SHOW BACK BUTTON
             * ====================================================
             */

            showBackButton =
                currentRoute !=
                        AdminRoute.Dashboard,


            /*
             * ====================================================
             * APP BACK BUTTON
             * ====================================================
             */

            onBack = {

                goBack()
            },


            /*
             * ====================================================
             * PROFILE
             * ====================================================
             */

            onProfileClick = { feedback = "Thông tin tài khoản Admin • Bản thử nghiệm tích hợp, chưa có tài khoản backend." },


            /*
             * ====================================================
             * LOGOUT
             * ====================================================
             */

            onLogoutClick = { logoutConfirmation = true }
        )


        /*
         * ========================================================
         * SCREEN CONTENT
         * ========================================================
         */

        Box(
            modifier =
                Modifier.weight(1f)
                    .then(if (showBottomBar) Modifier else Modifier.navigationBarsPadding())
                    .testTag("admin_route_${currentRoute.route}")
        ) {

            val recordKey = when (currentRoute) {
                AdminRoute.MemberDetail -> selectedMemberName
                AdminRoute.StaffDetail -> selectedStaffName
                AdminRoute.AssetDetail -> selectedAssetId
                AdminRoute.Receipt -> selectedTransaction?.invoice.orEmpty()
                AdminRoute.ComplaintDetail -> selectedComplaint?.id.orEmpty()
                else -> ""
            }
            screenState.SaveableStateProvider("${currentRoute.route}:$recordKey") {
            when (currentRoute) {


                /*
                 * ==================================================
                 * DASHBOARD
                 * ==================================================
                 */

                AdminRoute.Dashboard -> {

                    DashboardScreen(

                        onNavigate = {

                            navigateAdmin(it)
                        }
                    )
                }


                /*
                 * ==================================================
                 * MEMBERS
                 * ==================================================
                 */

                AdminRoute.Members -> {

                    MemberScreen(

                        onNavigate = {

                            navigateAdmin(it)
                        },

                        onMemberClick = {
                                memberName ->

                            selectedMemberName =
                                memberName

                            currentRoute =
                                AdminRoute.MemberDetail
                        }
                    )
                }


                /*
                 * ==================================================
                 * MEMBER DETAIL
                 * ==================================================
                 */

                AdminRoute.MemberDetail -> {

                    MemberDetailScreen(

                        memberName =
                            selectedMemberName,

                        onBack = {

                            goBack()
                        }
                    )
                }


                /*
                 * ==================================================
                 * STAFF
                 * ==================================================
                 */

                AdminRoute.Staff -> {

                    StaffScreen(

                        onNavigate = {

                            navigateAdmin(it)
                        },

                        onStaffClick = {
                                staffName ->

                            selectedStaffName =
                                staffName

                            currentRoute =
                                AdminRoute.StaffDetail
                        }
                    )
                }


                /*
                 * ==================================================
                 * STAFF DETAIL
                 * ==================================================
                 */

                AdminRoute.StaffDetail -> {

                    StaffDetailScreen(

                        staffName =
                            selectedStaffName,

                        onBack = {

                            goBack()
                        }
                    )
                }


                /*
                 * ==================================================
                 * ASSETS
                 * ==================================================
                 */

                AdminRoute.Assets -> {

                    AssetScreen(

                        onNavigate = {

                            navigateAdmin(it)
                        },

                        onAssetClick = {
                                assetId ->

                            selectedAssetId =
                                assetId

                            currentRoute =
                                AdminRoute.AssetDetail
                        }
                    )
                }


                /*
                 * ==================================================
                 * ASSET DETAIL
                 * ==================================================
                 */

                AdminRoute.AssetDetail -> {

                    AssetDetailScreen(

                        assetId =
                            selectedAssetId,

                        onBack = {

                            goBack()
                        }
                    )
                }


                /*
                 * ==================================================
                 * PACKAGES
                 * ==================================================
                 */

                AdminRoute.Packages -> {

                    PackageScreen(

                        onNavigate = {

                            currentRoute =
                                AdminRoute.Dashboard
                        }
                    )
                }


                /*
                 * ==================================================
                 * INVOICE
                 * ==================================================
                 */

                AdminRoute.Invoice -> {

                    InvoiceScreen(

                        onTransactionClick = {

                            currentRoute =
                                AdminRoute.Transactions
                        }
                    )
                }


                /*
                 * ==================================================
                 * TRANSACTIONS
                 * ==================================================
                 */

                AdminRoute.Transactions -> {

                    TransactionsScreen(

                        onReceiptClick = {
                                transaction ->

                            selectedTransaction =
                                transaction

                            currentRoute =
                                AdminRoute.Receipt
                        }
                    )
                }


                /*
                 * ==================================================
                 * RECEIPT
                 * ==================================================
                 */

                AdminRoute.Receipt -> {

                    if (selectedTransaction == null) TextButton(onClick = { currentRoute = AdminRoute.Transactions }) { Text("Không có giao dịch được chọn. Quay lại danh sách.") }
                    selectedTransaction?.let {
                            transaction ->

                        ReceiptScreen(
                            transaction =
                                transaction
                        )
                    }
                }


                /*
                 * ==================================================
                 * REVENUE
                 * ==================================================
                 */

                AdminRoute.Revenue -> {

                    RevenueScreen()
                }


                /*
                 * ==================================================
                 * TRAFFIC
                 * ==================================================
                 */

                AdminRoute.Traffic -> {

                    TrafficScreen()
                }


                /*
                 * ==================================================
                 * COMPLAINTS
                 * ==================================================
                 */

                AdminRoute.Complaints -> {

                    ComplaintScreen(

                        onComplaintClick = {
                                complaint ->

                            selectedComplaint =
                                complaint

                            currentRoute =
                                AdminRoute.ComplaintDetail
                        }
                    )
                }


                /*
                 * ==================================================
                 * COMPLAINT DETAIL
                 * ==================================================
                 */

                AdminRoute.ComplaintDetail -> {

                    if (selectedComplaint == null) TextButton(onClick = { currentRoute = AdminRoute.Complaints }) { Text("Không có khiếu nại được chọn. Quay lại danh sách.") }
                    selectedComplaint?.let {
                            complaint ->

                        ComplaintDetailScreen(
                            complaint =
                                complaint
                        )
                    }
                }


                /*
                 * ==================================================
                 * ACTIVITY LOGS
                 * ==================================================
                 */

                AdminRoute.Notifications -> {

                    NotificationScreen(notificationViewModel)
                }

                AdminRoute.ActivityLogs -> {

                    ActivityLogsScreen()
                }


                /*
                 * ==================================================
                 * FALLBACK
                 * ==================================================
                 */

                else -> {

                    DashboardScreen(

                        onNavigate = {

                            navigateAdmin(it)
                        }
                    )
                }
            }
            }
        }


        /*
         * ========================================================
         * BOTTOM BAR
         * ========================================================
         *
         * BottomBar chỉ xuất hiện ở các màn hình chính.
         */

        if (showBottomBar) {

            AdminBottomBar(

                currentRoute =
                    currentRoute,

                onNavigate = {

                    navigateAdmin(it)
                }
            )
        }
    }
    }
    if (logoutConfirmation) {
        AlertDialog(onDismissRequest = { logoutConfirmation = false },
            containerColor = AdminColors.Surface, titleContentColor = AdminColors.TextPrimary, textContentColor = AdminColors.TextSecondary,
            title = { Text("Đăng xuất khỏi KgyMs?") },
            text = { Text("Phiên thử nghiệm sẽ kết thúc và quay về đăng nhập.") },
            confirmButton = { TextButton(onClick = { logoutConfirmation = false; onLogout() }, modifier = Modifier.testTag("admin_confirm_logout")) { Text("Đăng xuất", color = AdminColors.Primary) } },
            dismissButton = { TextButton(onClick = { logoutConfirmation = false }) { Text("Hủy", color = AdminColors.TextSecondary) } })
    }
    feedback?.let { message ->
        AlertDialog(onDismissRequest = { feedback = null },
            containerColor = AdminColors.Surface, titleContentColor = AdminColors.TextPrimary, textContentColor = AdminColors.TextSecondary, title = { Text("KgyMs • Bản thử nghiệm") },
            text = { Text(message + "\n\nChỉ xem thử thao tác. Chưa ghi dữ liệu, tạo chứng từ, xuất tệp hoặc liên hệ thật.") },
            confirmButton = { TextButton(onClick = { feedback = null }, modifier = Modifier.testTag("admin_feedback_ok")) { Text("OK", color = AdminColors.Primary) } })
    }

}