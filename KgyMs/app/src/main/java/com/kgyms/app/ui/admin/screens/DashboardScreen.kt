package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.AdminColors
import com.kgyms.app.ui.admin.AdminRoute
import com.kgyms.app.ui.admin.AdminViewModel

@Composable
fun DashboardScreen(
    onNavigate: (AdminRoute) -> Unit,
    viewModel: AdminViewModel = viewModel()
) {

    /*
     * ============================
     * UI STATE
     * ============================
     */

    val state by viewModel.dashboardState.collectAsState()


    /*
     * ============================
     * SCREEN
     * ============================
     */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                AdminColors.Background
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {

            /*
             * ============================
             * DASHBOARD TITLE
             * ============================
             */

            Text(
                text = "Tổng quan hệ thống",

                color =
                    AdminColors.TextPrimary,

                fontSize = 24.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier = Modifier.height(4.dp)
            )


            Text(
                text = "Tháng 09/2026",

                color =
                    AdminColors.TextSecondary,

                fontSize = 9.sp
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * KPI ROW 1
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                /*
                 * DOANH THU
                 *
                 * Click -> RevenueScreen
                 */

                DashboardMetricCard(
                    title = "Doanh thu",
                    value = state.revenue,
                    accentColor = AdminColors.Primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigate(
                            AdminRoute.Revenue
                        )
                    }
                )


                /*
                 * HỘI VIÊN
                 *
                 * Click -> MemberScreen
                 */

                DashboardMetricCard(
                    title = "Hội viên",
                    value = state.members,
                    accentColor = AdminColors.Cyan,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigate(
                            AdminRoute.Members
                        )
                    }
                )
            }


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            /*
             * ============================
             * KPI ROW 2
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                /*
                 * CHECK-IN
                 *
                 * Click -> TrafficScreen
                 */

                DashboardMetricCard(
                    title = "Check-in",
                    value = state.checkIn,
                    accentColor = AdminColors.Green,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigate(
                            AdminRoute.Traffic
                        )
                    }
                )


                /*
                 * RETENTION
                 *
                 * Hiện tại chưa điều hướng.
                 */

                DashboardMetricCard(
                    title = "Duy trì",
                    value = state.retention,
                    accentColor = AdminColors.Cyan,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * REVENUE CHART
             * ============================
             */

            RevenueChartCard(
                onClick = {
                    onNavigate(
                        AdminRoute.Revenue
                    )
                }
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * RECENT ACTIVITIES
             * ============================
             */

            DashboardActivityItem(
                title =
                    "Nguyễn Văn Long • Gia hạn Premium",

                subtitle =
                    "Vừa xong"
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            DashboardActivityItem(
                title =
                    "Trần Thị Mai • Check-in",

                subtitle =
                    "Vừa xong"
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            DashboardActivityItem(
                title =
                    "Lê Hoàng • Đăng ký PT",

                subtitle =
                    "Vừa xong"
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }
    }
}


/*
 * ============================================================
 * DASHBOARD METRIC CARD
 * ============================================================
 */

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Column(
        modifier = modifier
            .height(64.dp)
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                AdminColors.Surface
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
    ) {

        Text(
            text = title,

            color =
                AdminColors.TextSecondary,

            fontSize = 8.sp
        )


        Spacer(
            modifier = Modifier.height(1.dp)
        )


        Text(
            text = value,

            color =
                AdminColors.TextPrimary,

            fontSize = 17.sp,

            fontWeight =
                FontWeight.Bold
        )


        Spacer(
            modifier = Modifier.height(3.dp)
        )


        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(
                    RoundedCornerShape(3.dp)
                )
                .background(
                    accentColor
                )
        )
    }
}


/*
 * ============================================================
 * REVENUE CHART
 * ============================================================
 */

@Composable
private fun RevenueChartCard(
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(
                RoundedCornerShape(16.dp)
            )
            .background(
                AdminColors.Surface
            )
            .clickable {
                onClick()
            }
            .padding(12.dp)
    ) {

        Text(
            text = "Doanh thu 30 ngày",

            color =
                AdminColors.TextPrimary,

            fontSize = 10.sp,

            fontWeight =
                FontWeight.SemiBold
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        /*
         * BAR CHART
         */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),

            horizontalArrangement =
                Arrangement.SpaceEvenly,

            verticalAlignment =
                Alignment.Bottom
        ) {

            val bars = listOf(
                0.45f,
                0.58f,
                0.78f,
                0.68f,
                0.92f,
                0.82f,
                1.0f
            )

            bars.forEach { heightRatio ->

                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight(
                            heightRatio
                        )
                        .clip(
                            RoundedCornerShape(
                                topStart = 5.dp,
                                topEnd = 5.dp
                            )
                        )
                        .background(
                            AdminColors.Primary
                        )
                )
            }
        }


        Spacer(
            modifier = Modifier.height(4.dp)
        )


        Text(
            text = "T1   T2   T3   T4   T5   T6   T7",

            color =
                AdminColors.TextDark,

            fontSize = 6.sp
        )
    }
}


/*
 * ============================================================
 * ACTIVITY ITEM
 * ============================================================
 */

@Composable
private fun DashboardActivityItem(
    title: String,
    subtitle: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(
                RoundedCornerShape(12.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 8.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(
                    RoundedCornerShape(8.dp)
                )
                .background(
                    AdminColors.SurfaceLight
                )
        )


        Spacer(
            modifier = Modifier.width(8.dp)
        )


        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,

                color =
                    AdminColors.TextPrimary,

                fontSize = 9.sp
            )


            Spacer(
                modifier = Modifier.height(2.dp)
            )


            Text(
                text = subtitle,

                color =
                    AdminColors.TextSecondary,

                fontSize = 7.sp
            )
        }


        Text(
            text = "›",

            color =
                AdminColors.TextPrimary,

            fontSize = 18.sp
        )
    }
}