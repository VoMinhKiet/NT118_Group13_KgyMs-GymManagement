package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import com.kgyms.app.data.repository.AdminMockRepository
import java.util.Locale

data class RevenueUiState(
    val monthTotal: String,
    val growth: String,
    val growthTone: AdminTone,
    val dailyBars: List<BarUi>,
    val breakdown: List<Pair<String, String>>
)

data class TrafficUiState(
    val today: String,
    val inGymNow: String,
    val weeklyBars: List<BarUi>,
    val busySlots: List<Pair<String, String>>
)

/** Báo cáo Doanh thu và Lưu lượng ra vào. */
class ReportViewModel(
    repository: AdminMockRepository = AdminMockRepository()
) : ViewModel() {

    val revenue: RevenueUiState = repository.revenueReport().let { r ->
        RevenueUiState(
            monthTotal = AdminFormat.vnd(r.monthTotal),
            growth = String.format(Locale.ROOT, "%+.1f%% so với tháng trước", r.growthPercent),
            growthTone = if (r.growthPercent >= 0) AdminTone.Positive else AdminTone.Critical,
            dailyBars = AdminFormat.bars(AdminFormat.lastSevenDayLabels(), r.daily),
            breakdown = listOf(
                "Gói tập" to AdminFormat.compact(r.packageRevenue),
                "PT" to AdminFormat.compact(r.ptRevenue),
                "Khác" to AdminFormat.compact(r.otherRevenue)
            )
        )
    }

    val traffic: TrafficUiState = repository.trafficReport().let { t ->
        TrafficUiState(
            today = t.today.toString(),
            inGymNow = t.inGymNow.toString(),
            weeklyBars = AdminFormat.bars(AdminFormat.lastSevenDayLabels(), t.weekly.map { it.toLong() }),
            busySlots = t.busySlots.map { it.timeRange to "${it.visits} lượt" }
        )
    }
}
