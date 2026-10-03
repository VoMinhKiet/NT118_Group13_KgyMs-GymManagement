package com.kgyms.app.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kgyms.app.ui.admin.screens.ActivityLogsScreen
import com.kgyms.app.ui.admin.screens.AssetDetailScreen
import com.kgyms.app.ui.admin.screens.AssetScreen
import com.kgyms.app.ui.admin.screens.ComplaintDetailScreen
import com.kgyms.app.ui.admin.screens.ComplaintItem
import com.kgyms.app.ui.admin.screens.ComplaintScreen
import com.kgyms.app.ui.admin.screens.DashboardScreen
import com.kgyms.app.ui.admin.screens.HotlineScreen
import com.kgyms.app.ui.admin.screens.InvoiceScreen
import com.kgyms.app.ui.admin.screens.MemberDetailScreen
import com.kgyms.app.ui.admin.screens.MemberScreen
import com.kgyms.app.ui.admin.screens.PackageScreen
import com.kgyms.app.ui.admin.screens.ReceiptScreen
import com.kgyms.app.ui.admin.screens.RevenueScreen
import com.kgyms.app.ui.admin.screens.StaffDetailScreen
import com.kgyms.app.ui.admin.screens.StaffScreen
import com.kgyms.app.ui.admin.screens.TrafficScreen
import com.kgyms.app.ui.admin.screens.TransactionItem
import com.kgyms.app.ui.admin.screens.TransactionsScreen

@Composable
fun AdminNavHost() {

    /*
     * ============================================================
     * CURRENT ROUTE
     * ============================================================
     */

    var currentRoute by remember {
        mutableStateOf<AdminRoute>(
            AdminRoute.Dashboard
        )
    }


    /*
     * ============================================================
     * SELECTED MEMBER
     * ============================================================
     */

    var selectedMemberName by remember {
        mutableStateOf(
            "Nguyễn Minh Anh"
        )
    }


    /*
     * ============================================================
     * SELECTED STAFF
     * ============================================================
     */

    var selectedStaffName by remember {
        mutableStateOf(
            "Trần Hoàng"
        )
    }


    /*
     * ============================================================
     * SELECTED ASSET
     * ============================================================
     */

    var selectedAssetName by remember {
        mutableStateOf(
            "Máy Smith"
        )
    }


    /*
     * ============================================================
     * SELECTED TRANSACTION
     * ============================================================
     */

    var selectedTransaction by remember {
        mutableStateOf<TransactionItem?>(null)
    }


    /*
     * ============================================================
     * SELECTED COMPLAINT
     * ============================================================
     */

    var selectedComplaint by remember {
        mutableStateOf<ComplaintItem?>(null)
    }


    /*
     * ============================================================
     * ADMIN SHELL
     * ============================================================
     */

    Column(
        modifier = Modifier.fillMaxSize()
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

                AdminRoute.Hotline ->
                    "Hotline CSKH"

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
             * BACK BUTTON
             * ====================================================
             */

            showBackButton =
                currentRoute !=
                        AdminRoute.Dashboard,


            /*
             * ====================================================
             * BACK ACTION
             * ====================================================
             */

            onBack = {

                when (currentRoute) {

                    /*
                     * MEMBER DETAIL
                     */

                    AdminRoute.MemberDetail -> {

                        currentRoute =
                            AdminRoute.Members
                    }


                    /*
                     * STAFF DETAIL
                     */

                    AdminRoute.StaffDetail -> {

                        currentRoute =
                            AdminRoute.Staff
                    }


                    /*
                     * ASSET DETAIL
                     */

                    AdminRoute.AssetDetail -> {

                        currentRoute =
                            AdminRoute.Assets
                    }


                    /*
                     * TRANSACTIONS
                     */

                    AdminRoute.Transactions -> {

                        currentRoute =
                            AdminRoute.Invoice
                    }


                    /*
                     * RECEIPT
                     */

                    AdminRoute.Receipt -> {

                        currentRoute =
                            AdminRoute.Transactions
                    }


                    /*
                     * COMPLAINT DETAIL
                     */

                    AdminRoute.ComplaintDetail -> {

                        currentRoute =
                            AdminRoute.Complaints
                    }


                    /*
                     * REVENUE
                     */

                    AdminRoute.Revenue -> {

                        currentRoute =
                            AdminRoute.Dashboard
                    }


                    /*
                     * TRAFFIC
                     */

                    AdminRoute.Traffic -> {

                        currentRoute =
                            AdminRoute.Dashboard
                    }


                    /*
                     * INVOICE
                     */

                    AdminRoute.Invoice -> {

                        currentRoute =
                            AdminRoute.Dashboard
                    }


                    /*
                     * PACKAGE
                     */

                    AdminRoute.Packages -> {

                        currentRoute =
                            AdminRoute.Dashboard
                    }


                    /*
                     * COMPLAINT LIST
                     */

                    AdminRoute.Complaints -> {

                        currentRoute =
                            AdminRoute.Dashboard
                    }


                    /*
                     * HOTLINE
                     */

                    AdminRoute.Hotline -> {

                        currentRoute =
                            AdminRoute.Dashboard
                    }


                    /*
                     * ACTIVITY LOGS
                     */

                    AdminRoute.ActivityLogs -> {

                        currentRoute =
                            AdminRoute.Dashboard
                    }


                    /*
                     * DEFAULT
                     */

                    else -> {

                        currentRoute =
                            AdminRoute.Dashboard
                    }
                }
            },


            /*
             * ====================================================
             * PROFILE
             * ====================================================
             */

            onProfileClick = {

                // TODO:
                // Mở màn hình thông tin cá nhân
            },


            /*
             * ====================================================
             * LOGOUT
             * ====================================================
             */

            onLogoutClick = {

                // TODO:
                // FirebaseAuth.getInstance().signOut()
            }
        )


        /*
         * ========================================================
         * SCREEN CONTENT
         * ========================================================
         */

        Box(
            modifier = Modifier.weight(1f)
        ) {

            when (currentRoute) {

                /*
                 * ==================================================
                 * DASHBOARD
                 * ==================================================
                 */

                AdminRoute.Dashboard -> {

                    DashboardScreen(
                        onNavigate = {
                            currentRoute = it
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
                            currentRoute = it
                        },

                        onMemberClick = { memberName ->

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

                            currentRoute =
                                AdminRoute.Members
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
                            currentRoute = it
                        },

                        onStaffClick = { staffName ->

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

                            currentRoute =
                                AdminRoute.Staff
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
                            currentRoute = it
                        },

                        onAssetClick = { assetName ->

                            selectedAssetName =
                                assetName

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

                        assetName =
                            selectedAssetName,

                        onBack = {

                            currentRoute =
                                AdminRoute.Assets
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

                        onReceiptClick = { transaction ->

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
                 * COMPLAINT LIST
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
                 * HOTLINE
                 * ==================================================
                 */

                AdminRoute.Hotline -> {

                    HotlineScreen()
                }


                /*
                 * ==================================================
                 * ACTIVITY LOGS
                 * ==================================================
                 */

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
                            currentRoute = it
                        }
                    )
                }
            }
        }


        /*
         * ========================================================
         * BOTTOM BAR
         * ========================================================
         *
         * Không hiển thị BottomBar ở các màn hình:
         *
         * - Detail
         * - Package
         * - Invoice
         * - Transactions
         * - Receipt
         * - Revenue
         * - Traffic
         * - Complaints
         * - Hotline
         * - Activity Logs
         */

        if (
            currentRoute !=
            AdminRoute.MemberDetail &&

            currentRoute !=
            AdminRoute.StaffDetail &&

            currentRoute !=
            AdminRoute.AssetDetail &&

            currentRoute !=
            AdminRoute.Packages &&

            currentRoute !=
            AdminRoute.Invoice &&

            currentRoute !=
            AdminRoute.Transactions &&

            currentRoute !=
            AdminRoute.Receipt &&

            currentRoute !=
            AdminRoute.Revenue &&

            currentRoute !=
            AdminRoute.Traffic &&

            currentRoute !=
            AdminRoute.Complaints &&

            currentRoute !=
            AdminRoute.ComplaintDetail &&

            currentRoute !=
            AdminRoute.Hotline &&

            currentRoute !=
            AdminRoute.ActivityLogs
        ) {

            AdminBottomBar(

                currentRoute =
                    currentRoute,

                onNavigate = {
                    currentRoute = it
                }
            )
        }
    }
}