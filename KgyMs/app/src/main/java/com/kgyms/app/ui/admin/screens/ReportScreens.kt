package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminBarChartCard
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminKeyValueRow
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminSection
import com.kgyms.app.ui.admin.components.AdminStatCard
import com.kgyms.app.ui.admin.components.AdminStatRow
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.ReportViewModel

@Composable
fun RevenueScreen(
    viewModel: ReportViewModel = viewModel()
) {
    val revenue = viewModel.revenue

    AdminPage {
        AdminCard {
            AdminLabel("Doanh thu tháng")
            Spacer(modifier = Modifier.height(4.dp))
            Text(revenue.monthTotal, color = AdminColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(revenue.growth, color = revenue.growthTone.color, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        AdminBarChartCard(title = "Doanh thu theo ngày", bars = revenue.dailyBars)

        AdminSection("Theo nguồn")
        revenue.breakdown.forEach { (label, value) ->
            AdminKeyValueRow(label, value)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun TrafficScreen(
    viewModel: ReportViewModel = viewModel()
) {
    val traffic = viewModel.traffic

    AdminPage {
        AdminStatRow { cell ->
            AdminStatCard("Hôm nay", traffic.today, modifier = cell, tone = AdminTone.Positive)
            AdminStatCard("Đang ở gym", traffic.inGymNow, modifier = cell, tone = AdminTone.Info)
        }
        Spacer(modifier = Modifier.height(10.dp))
        AdminBarChartCard(title = "Check-in 7 ngày", bars = traffic.weeklyBars)

        AdminSection("Khung giờ đông nhất")
        traffic.busySlots.forEach { (time, count) ->
            AdminKeyValueRow(time, count)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
