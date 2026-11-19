package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminSection
import com.kgyms.app.ui.admin.components.AdminStatCard
import com.kgyms.app.ui.admin.components.AdminStatRow
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.ReportViewModel
import com.kgyms.app.viewmodel.admin.ShareRowUi

/* ============================ DOANH THU ============================ */

@Composable
fun RevenueScreen(
    onReceiptClick: (String) -> Unit,
    viewModel: ReportViewModel = viewModel()
) {
    val state by viewModel.revenue.collectAsState()

    AdminPage {
        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            else -> {
                AdminCard {
                    AdminLabel(state.monthLabel)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(state.monthTotal, color = AdminColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(state.growth, color = state.growthTone.color, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                AdminStatRow { cell ->
                    AdminStatCard("Hôm nay", state.today, modifier = cell, tone = AdminTone.Positive)
                    AdminStatCard("Hoá đơn đã thu", state.paidCount, modifier = cell, caption = "trong tháng", tone = AdminTone.Info)
                }
                Spacer(modifier = Modifier.height(10.dp))
                AdminBarChartCard(title = "Doanh thu 7 ngày", bars = state.dailyBars, trailing = state.weekTotal)

                AdminSection("Theo nguồn (tháng này)")
                ShareRows(state.bySource)

                AdminSection("Theo phương thức")
                ShareRows(state.byMethod)

                if (state.topPackages.isNotEmpty()) {
                    AdminSection("Gói bán chạy")
                    ShareRows(state.topPackages)
                }

                AdminSection("Thanh toán gần đây")
                if (state.recent.isEmpty()) AdminMessage("Chưa có hoá đơn nào được thanh toán.")
                state.recent.forEach { payment ->
                    AdminListRow(
                        title = payment.title,
                        subtitle = payment.subtitle,
                        tone = AdminTone.Positive,
                        onClick = { onReceiptClick(payment.invoice) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/* ============================ LƯU LƯỢNG ============================ */

@Composable
fun TrafficScreen(
    viewModel: ReportViewModel = viewModel()
) {
    val state by viewModel.traffic.collectAsState()

    AdminPage {
        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            else -> {
                AdminStatRow { cell ->
                    AdminStatCard("Hôm nay", state.today, modifier = cell, caption = "lượt check-in", tone = AdminTone.Positive)
                    AdminStatCard("Đang ở gym", state.inGymNow, modifier = cell, tone = AdminTone.Info)
                }
                Spacer(modifier = Modifier.height(8.dp))
                AdminStatRow { cell ->
                    AdminStatCard("7 ngày qua", state.weekTotal, modifier = cell, caption = "lượt", tone = AdminTone.Brand)
                    AdminStatCard("Thời gian tập TB", state.avgDuration, modifier = cell, caption = "30 ngày qua", tone = AdminTone.Neutral)
                }
                Spacer(modifier = Modifier.height(10.dp))
                AdminBarChartCard(title = "Check-in 7 ngày", bars = state.weeklyBars, trailing = "${state.weekTotal} lượt")

                AdminSection("Đang ở gym (${state.inGym.size})")
                if (state.inGym.isEmpty()) AdminMessage("Hiện không có ai trong phòng tập.")
                state.inGym.forEach { person ->
                    AdminListRow(title = person.name, subtitle = person.since, tone = AdminTone.Positive)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                AdminSection("Khung giờ (30 ngày qua)")
                ShareRows(state.slots)

                AdminSection("Lượt vào hôm nay (${state.todayVisits.size})")
                if (state.todayVisits.isEmpty()) AdminMessage("Hôm nay chưa có lượt check-in nào.")
                ShareRows(state.todayVisits)
            }
        }
    }
}

@Composable
private fun ShareRows(rows: List<ShareRowUi>) {
    rows.forEach { row ->
        AdminKeyValueRow(row.label, row.value, row.tone)
        Spacer(modifier = Modifier.height(8.dp))
    }
}
