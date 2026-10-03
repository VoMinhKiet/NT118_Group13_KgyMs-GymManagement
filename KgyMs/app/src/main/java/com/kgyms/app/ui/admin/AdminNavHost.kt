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
import com.kgyms.app.ui.admin.screens.DashboardScreen
import com.kgyms.app.ui.admin.screens.MemberDetailScreen
import com.kgyms.app.ui.admin.screens.MemberScreen
import com.kgyms.app.ui.admin.screens.StaffDetailScreen
import com.kgyms.app.ui.admin.screens.StaffScreen

@Composable
fun AdminNavHost() {

    /*
     * Route hiện tại
     */
    var currentRoute by remember {
        mutableStateOf<AdminRoute>(
            AdminRoute.Dashboard
        )
    }


    /*
     * Hội viên đang chọn
     */
    var selectedMemberName by remember {
        mutableStateOf(
            "Nguyễn Minh Anh"
        )
    }


    /*
     * Nhân sự đang chọn
     */
    var selectedStaffName by remember {
        mutableStateOf(
            "Trần Hoàng"
        )
    }


    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * ============================
         * TOP BAR
         * ============================
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
                    "Chi tiết khiếu nại"

                AdminRoute.Hotline ->
                    "Hotline CSKH"

                AdminRoute.ActivityLogs ->
                    "Nhật ký hoạt động"

                else ->
                    "KgyMs Admin"
            },


            subtitle = when (currentRoute) {

                AdminRoute.Dashboard ->
                    "Tổng quan hệ thống"

                else ->
                    null
            },


            /*
             * Hiện nút back ở tất cả
             * màn hình ngoài Dashboard
             */
            showBackButton =
                currentRoute != AdminRoute.Dashboard,


            /*
             * Xử lý nút back
             */
            onBack = {

                when (currentRoute) {

                    AdminRoute.MemberDetail -> {
                        currentRoute =
                            AdminRoute.Members
                    }

                    AdminRoute.StaffDetail -> {
                        currentRoute =
                            AdminRoute.Staff
                    }

                    else -> {
                        currentRoute =
                            AdminRoute.Dashboard
                    }
                }
            },


            /*
             * Profile Admin
             */
            onProfileClick = {
                // TODO
            },


            /*
             * Logout
             */
            onLogoutClick = {
                // TODO
                // FirebaseAuth.getInstance().signOut()
            }
        )


        /*
         * ============================
         * CONTENT
         * ============================
         */

        Box(
            modifier = Modifier.weight(1f)
        ) {

            when (currentRoute) {

                /*
                 * ============================
                 * DASHBOARD
                 * ============================
                 */

                AdminRoute.Dashboard -> {

                    DashboardScreen(
                        onNavigate = {
                            currentRoute = it
                        }
                    )
                }


                /*
                 * ============================
                 * MEMBERS
                 * ============================
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
                 * ============================
                 * MEMBER DETAIL
                 * ============================
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
                 * ============================
                 * STAFF
                 * ============================
                 */

                AdminRoute.Staff -> {

                    StaffScreen(

                        onNavigate = {
                            currentRoute = it
                        },

                        onStaffClick = { staffName ->

                            /*
                             * Lưu nhân sự được chọn
                             */
                            selectedStaffName =
                                staffName

                            /*
                             * Chuyển sang
                             * hồ sơ nhân sự
                             */
                            currentRoute =
                                AdminRoute.StaffDetail
                        }
                    )
                }


                /*
                 * ============================
                 * STAFF DETAIL
                 * ============================
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
                 * ============================
                 * CÁC SCREEN CHƯA LÀM
                 * ============================
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
         * ============================
         * BOTTOM BAR
         * ============================
         *
         * Không hiện BottomBar ở
         * MemberDetail / StaffDetail.
         */

        if (
            currentRoute !=
            AdminRoute.MemberDetail &&
            currentRoute !=
            AdminRoute.StaffDetail
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