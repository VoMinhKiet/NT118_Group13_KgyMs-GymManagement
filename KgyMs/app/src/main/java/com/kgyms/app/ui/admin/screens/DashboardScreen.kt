package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.kgyms.app.R
import com.kgyms.app.ui.admin.AdminColors
import com.kgyms.app.ui.admin.AdminSectionTitle
import com.kgyms.app.ui.admin.ActivityItem
import com.kgyms.app.ui.admin.AdminViewModel
import com.kgyms.app.ui.admin.AdminRoute
import com.kgyms.app.ui.admin.MetricCard
import com.kgyms.app.ui.admin.SimpleBarChart


@Composable
fun DashboardScreen(
    onNavigate: (AdminRoute) -> Unit,
    viewModel: AdminViewModel = viewModel()
) {

    val state by viewModel.dashboardState.collectAsState()

    // =====================================================
    // ADMIN BACKGROUND
    // =====================================================

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



            Spacer(
                modifier = Modifier.height(10.dp)
            )


            // =================================================
            // KPI ROW 1
            // =================================================

            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                MetricCard(
                    title = "Doanh thu",
                    value = state.revenue,
                    accentColor = AdminColors.Primary,

                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Hội viên",
                    value = state.members,
                    accentColor = AdminColors.Cyan,

                    modifier = Modifier.weight(1f)
                )
            }


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            // =================================================
            // KPI ROW 2
            // =================================================

            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                MetricCard(
                    title = "Check-in",
                    value = state.checkIn,
                    accentColor = AdminColors.Green,

                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Duy trì",
                    value = state.retention,
                    accentColor = AdminColors.Cyan,

                    modifier = Modifier.weight(1f)
                )
            }


            Spacer(
                modifier = Modifier.height(16.dp)
            )


            // =================================================
            // REVENUE
            // =================================================

            AdminSectionTitle(
                title = "Doanh thu 30 ngày"
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )


            Card(
                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(
                    14.dp
                ),

                colors = CardDefaults.cardColors(
                    containerColor =
                        AdminColors.Surface
                )
            ) {

                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 12.dp
                    )
                ) {

                    SimpleBarChart(
                        values = listOf(
                            48f,
                            70f,
                            82f,
                            62f,
                            92f,
                            76f,
                            100f
                        )
                    )
                }
            }


            Spacer(
                modifier = Modifier.height(16.dp)
            )


            // =================================================
            // RECENT ACTIVITIES
            // =================================================

            AdminSectionTitle(
                title = "Hoạt động gần đây"
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )


            ActivityItem(
                title =
                    "Nguyễn Văn Long • Gia hạn Premium",

                subtitle =
                    "Vừa xong"
            )


            Spacer(
                modifier = Modifier.height(7.dp)
            )


            ActivityItem(
                title =
                    "Trần Thị Mai • Check-in",

                subtitle =
                    "Vừa xong"
            )


            Spacer(
                modifier = Modifier.height(7.dp)
            )


            ActivityItem(
                title =
                    "Lê Hoàng • Đăng ký PT",

                subtitle =
                    "Vừa xong"
            )


            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }
    }
}