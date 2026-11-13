package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminBarChartCard
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminSection
import com.kgyms.app.ui.admin.components.AdminStatCard
import com.kgyms.app.ui.admin.components.AdminStatRow
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.ui.admin.navigation.AdminRoute
import com.kgyms.app.viewmodel.admin.AdminDestination
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.DashboardViewModel
import com.kgyms.app.viewmodel.admin.ModuleUi

/** Đổi đích đến ngữ nghĩa của ViewModel thành route điều hướng. */
internal fun AdminDestination.toRoute(): AdminRoute = when (this) {
    AdminDestination.Revenue -> AdminRoute.Revenue
    AdminDestination.Members -> AdminRoute.Members
    AdminDestination.Traffic -> AdminRoute.Traffic
    AdminDestination.Staff -> AdminRoute.Staff
    AdminDestination.Assets -> AdminRoute.Assets
    AdminDestination.Packages -> AdminRoute.Packages
    AdminDestination.Invoice -> AdminRoute.Invoice
    AdminDestination.Complaints -> AdminRoute.Complaints
    AdminDestination.Notifications -> AdminRoute.Notifications
    AdminDestination.ActivityLogs -> AdminRoute.ActivityLogs
}

@Composable
fun DashboardScreen(
    onNavigate: (AdminRoute) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    AdminPage {
        Text(
            text = "Tổng quan hệ thống",
            color = AdminColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = state.monthLabel, color = AdminColors.TextSecondary, fontSize = 12.sp)

        state.warning?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, color = AdminTone.Critical.color, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoading) {
            AdminLoading()
            return@AdminPage
        }

        // KPI: 2 hàng x 2 thẻ.
        state.kpis.chunked(2).forEach { row ->
            AdminStatRow { cell ->
                row.forEach { kpi ->
                    AdminStatCard(
                        title = kpi.title,
                        value = kpi.value,
                        caption = kpi.caption,
                        tone = kpi.tone,
                        modifier = cell,
                        onClick = { onNavigate(kpi.target.toRoute()) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        AdminBarChartCard(
            title = "Doanh thu 7 ngày",
            bars = state.revenueBars,
            trailing = state.revenueTotal,
            onClick = { onNavigate(AdminRoute.Revenue) },
            modifier = Modifier.testTag("dashboard_revenue_chart")
        )

        if (state.alerts.isNotEmpty()) {
            AdminSection("Cần xử lý")
            state.alerts.forEach { alert ->
                AdminListRow(
                    title = alert.text,
                    tone = alert.tone,
                    onClick = { onNavigate(alert.target.toRoute()) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        AdminSection("Chức năng quản lý")
        state.modules.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { module ->
                    ModuleCard(module, Modifier.weight(1f)) { onNavigate(module.target.toRoute()) }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        AdminSection("Hoạt động gần đây")
        if (state.recentLogs.isEmpty()) {
            AdminMessage("Chưa có hoạt động nào được ghi lại.")
        } else {
            state.recentLogs.forEach { log ->
                AdminListRow(
                    title = log.title,
                    meta = log.time,
                    onClick = { onNavigate(AdminRoute.ActivityLogs) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleUi, modifier: Modifier, onClick: () -> Unit) {
    AdminCard(
        modifier = modifier
            .heightIn(min = 96.dp)
            .testTag("dashboard_module_${module.target.name}"),
        onClick = onClick
    ) {
        Text(text = module.name, color = module.tone.color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = module.value,
            color = AdminColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = module.caption,
            color = AdminColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
