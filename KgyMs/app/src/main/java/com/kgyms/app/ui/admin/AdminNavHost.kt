package com.kgyms.app.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kgyms.app.ui.admin.screens.DashboardScreen
import com.kgyms.app.ui.admin.screens.MemberScreen


@Composable
fun AdminNavHost() {

    var currentRoute by remember {
        mutableStateOf<AdminRoute>(
            AdminRoute.Dashboard
        )
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {

        // =================================================
        // TOP BAR
        // =================================================

        AdminTopBar(
            title = when (currentRoute) {

                AdminRoute.Dashboard ->
                    "KgyMs Admin"

                AdminRoute.Members ->
                    "Quản lý hội viên"

                AdminRoute.Staff ->
                    "Nhân sự & PT"

                AdminRoute.Assets ->
                    "Danh mục tài sản"

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

                AdminRoute.Hotline ->
                    "Hotline CSKH"

                AdminRoute.ActivityLogs ->
                    "Nhật ký hoạt động"

                else ->
                    "KgyMs Admin"
            },

            subtitle =
                if (currentRoute == AdminRoute.Dashboard)
                    "Tổng quan hệ thống"
                else
                    null,

            showBackButton =
                currentRoute != AdminRoute.Dashboard,

            onBack = {
                currentRoute =
                    AdminRoute.Dashboard
            },

            onProfileClick = {
                // TODO:
                // Chuyển sang AdminProfileScreen
            },

            onLogoutClick = {
                // TODO:
                // FirebaseAuth.getInstance().signOut()
            }
        )


        // =================================================
        // MAIN CONTENT
        // =================================================

        Box(
            modifier = Modifier
                .weight(1f)
        ) {

            when (currentRoute) {

                // -----------------------------------------
                // DASHBOARD
                // -----------------------------------------

                AdminRoute.Dashboard -> {

                    DashboardScreen(
                        onNavigate = {
                            currentRoute = it
                        }
                    )
                }


                // -----------------------------------------
                // MEMBERS
                // -----------------------------------------

                AdminRoute.Members -> {

                    MemberScreen(
                        onNavigate = {
                            currentRoute = it
                        }
                    )
                }


                // -----------------------------------------
                // TEMPORARY
                // -----------------------------------------

                else -> {

                    DashboardScreen(
                        onNavigate = {
                            currentRoute = it
                        }
                    )
                }
            }
        }


        // =================================================
        // BOTTOM NAVIGATION
        // =================================================

        AdminBottomBar(

            currentRoute =
                currentRoute,

            onNavigate = {
                currentRoute = it
            }
        )
    }
}