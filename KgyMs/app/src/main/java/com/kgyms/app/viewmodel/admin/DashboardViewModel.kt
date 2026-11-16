package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.repository.DashboardRepository
import com.kgyms.app.data.repository.DashboardSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Nơi một mục trên Dashboard dẫn tới (UI tự đổi sang route điều hướng). */
enum class AdminDestination {
    Revenue, Members, Traffic, Staff, Assets, Packages, Invoice, Complaints, Notifications, ActivityLogs
}

data class KpiUi(
    val title: String,
    val value: String,
    val caption: String,
    val tone: AdminTone,
    val target: AdminDestination
)

data class AlertUi(val text: String, val tone: AdminTone, val target: AdminDestination)

data class ModuleUi(
    val name: String,
    val value: String,
    val caption: String,
    val tone: AdminTone,
    val target: AdminDestination
)

data class RecentLogUi(val title: String, val time: String)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val monthLabel: String = "",
    /** Cảnh báo khi có node không đọc được. */
    val warning: String? = null,
    val kpis: List<KpiUi> = emptyList(),
    val revenueTotal: String = "",
    val revenueBars: List<BarUi> = emptyList(),
    val alerts: List<AlertUi> = emptyList(),
    val modules: List<ModuleUi> = emptyList(),
    val recentLogs: List<RecentLogUi> = emptyList()
)

/** Tổng quan Admin: mọi số liệu tính realtime từ Firebase. */
class DashboardViewModel(
    private val repository: DashboardRepository = DashboardRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(
        DashboardUiState(monthLabel = "Tháng " + SimpleDateFormat("MM/yyyy", Locale.ROOT).format(Date()))
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSummary().collect { summary ->
                _state.value = summary.toUiState(_state.value.monthLabel)
            }
        }
    }

    private fun DashboardSummary.toUiState(monthLabel: String) = DashboardUiState(
        isLoading = false,
        monthLabel = monthLabel,
        warning = failedNodes.takeIf { it.isNotEmpty() }?.let { "Không đọc được dữ liệu: ${it.joinToString(", ")}" },
        kpis = listOf(
            KpiUi("Doanh thu", AdminFormat.compact(revenueThisMonth),
                "Tháng này • $paidThisMonthCount hóa đơn", AdminTone.Brand, AdminDestination.Revenue),
            KpiUi("Hội viên có gói", "$activeMemberUsers/$totalUsers",
                "$activeMemberPercent% tài khoản", AdminTone.Info, AdminDestination.Members),
            KpiUi("Check-in", checkInsToday.toString(),
                "Hôm nay • $inGymNow đang ở gym", AdminTone.Positive, AdminDestination.Traffic),
            KpiUi("Cần xử lý", needsAttention.toString(),
                if (needsAttention == 0) "Không có việc tồn đọng" else "Xem danh sách bên dưới",
                if (needsAttention == 0) AdminTone.Positive else AdminTone.Critical, AdminDestination.Complaints)
        ),
        revenueTotal = AdminFormat.compact(revenueLast7Days.sumOf { it.amount }),
        revenueBars = AdminFormat.bars(revenueLast7Days.map { it.label }, revenueLast7Days.map { it.amount }),
        alerts = buildList {
            if (openComplaints + inProgressComplaints > 0) add(AlertUi(
                "$openComplaints khiếu nại mới, $inProgressComplaints đang xử lý", AdminTone.Critical, AdminDestination.Complaints))
            if (pendingPayments > 0) add(AlertUi(
                "$pendingPayments hóa đơn chờ thanh toán", AdminTone.Warning, AdminDestination.Invoice))
            if (assetNeedFixTypes > 0) add(AlertUi(
                "$assetNeedFixTypes loại máy cần sửa/bảo trì ($assetNeedFixQuantity thiết bị)", AdminTone.Warning, AdminDestination.Assets))
            if (expiringSoon > 0) add(AlertUi(
                "$expiringSoon gói hội viên hết hạn trong 7 ngày", AdminTone.Info, AdminDestination.Notifications))
        },
        modules = listOf(
            ModuleUi("Hội viên", "$totalUsers tài khoản", "$expiringSoon gói sắp hết hạn", AdminTone.Info, AdminDestination.Members),
            ModuleUi("Nhân sự & PT", "$staffActive đang làm", "Tổng $staffTotal nhân sự", AdminTone.Brand, AdminDestination.Staff),
            ModuleUi("Tài sản", "$assetQuantity thiết bị", "$assetNeedFixQuantity cần sửa/bảo trì",
                if (assetNeedFixQuantity > 0) AdminTone.Warning else AdminTone.Positive, AdminDestination.Assets),
            ModuleUi("Gói tập", "$packagesActive đang bán", "Tổng $packagesTotal gói", AdminTone.Brand, AdminDestination.Packages),
            ModuleUi("Hóa đơn", "$pendingPayments chờ thanh toán", "$paidThisMonthCount đã thu tháng này",
                if (pendingPayments > 0) AdminTone.Warning else AdminTone.Positive, AdminDestination.Invoice),
            ModuleUi("Khiếu nại", "$openComplaints mới", "$inProgressComplaints đang xử lý",
                if (openComplaints > 0) AdminTone.Critical else AdminTone.Neutral, AdminDestination.Complaints),
            ModuleUi("Thông báo", "$broadcastsCount lần đã gửi",
                if (reminderEnabled) "Nhắc hạn: bật (${reminderDays.replace(",", ", ")} ngày)" else "Nhắc hạn: tắt",
                AdminTone.Positive, AdminDestination.Notifications),
            ModuleUi("Nhật ký", "$logsToday hôm nay", "Xem toàn bộ hoạt động", AdminTone.Neutral, AdminDestination.ActivityLogs)
        ),
        recentLogs = recentLogs.map { RecentLogUi(it.title, it.createdAt) }
    )
}
