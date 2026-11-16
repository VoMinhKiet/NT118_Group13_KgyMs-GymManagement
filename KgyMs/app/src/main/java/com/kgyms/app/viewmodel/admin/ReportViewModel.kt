package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.PackageType
import com.kgyms.app.data.model.Payment
import com.kgyms.app.data.model.PaymentMethod
import com.kgyms.app.data.model.PaymentStatus
import com.kgyms.app.data.model.SpecialPackages
import com.kgyms.app.data.repository.ReportRepository
import com.kgyms.app.data.repository.RevenueData
import com.kgyms.app.data.repository.TrafficData
import com.kgyms.app.data.util.AppDates
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ============================ UI MODEL ============================ */

/** Một dòng tỉ trọng: "Gói hội viên — 404.000đ • 98%". */
data class ShareRowUi(val label: String, val value: String, val tone: AdminTone? = null)

data class RecentPaymentUi(val invoice: String, val title: String, val subtitle: String)

data class RevenueUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val monthLabel: String = "",
    val monthTotal: String = "–",
    val growth: String = "",
    val growthTone: AdminTone = AdminTone.Neutral,
    val today: String = "–",
    val paidCount: String = "–",
    val weekTotal: String = "",
    val dailyBars: List<BarUi> = emptyList(),
    val bySource: List<ShareRowUi> = emptyList(),
    val byMethod: List<ShareRowUi> = emptyList(),
    val topPackages: List<ShareRowUi> = emptyList(),
    val recent: List<RecentPaymentUi> = emptyList()
)

data class InGymUi(val name: String, val since: String)

data class TrafficUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val today: String = "–",
    val inGymNow: String = "–",
    val weekTotal: String = "–",
    val avgDuration: String = "–",
    val weeklyBars: List<BarUi> = emptyList(),
    /** Khung giờ trong 30 ngày gần nhất, khung đông nhất được nhấn màu. */
    val slots: List<ShareRowUi> = emptyList(),
    val inGym: List<InGymUi> = emptyList(),
    val todayVisits: List<ShareRowUi> = emptyList()
)

/* ============================ VIEWMODEL ============================ */

/** Báo cáo Doanh thu và Lưu lượng ra vào — dữ liệu thật từ Firebase. */
class ReportViewModel(
    private val repository: ReportRepository = ReportRepository()
) : ViewModel() {

    private val _revenue = MutableStateFlow(RevenueUiState())
    val revenue: StateFlow<RevenueUiState> = _revenue.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficUiState())
    val traffic: StateFlow<TrafficUiState> = _traffic.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRevenue()
                .catch { e -> _revenue.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { data -> _revenue.value = data.toRevenueUi() }
        }
        viewModelScope.launch {
            repository.observeTraffic()
                .catch { e -> _traffic.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { data -> _traffic.value = data.toTrafficUi() }
        }
    }

    /* ============================ DOANH THU ============================ */

    private fun RevenueData.toRevenueUi(): RevenueUiState {
        val today = AppDates.startOfDay()
        val monthStart = monthStart(0)
        val lastMonthStart = monthStart(-1)

        val paid = payments
            .filter { it.status == PaymentStatus.PAID }
            .mapNotNull { p -> AppDates.parseTimestamp(p.paidAt)?.let { p to it } }
        val thisMonth = paid.filter { it.second >= monthStart }
        val lastMonth = paid.filter { it.second in lastMonthStart until monthStart }
        val monthTotal = thisMonth.sumOf { it.first.amount }
        val lastTotal = lastMonth.sumOf { it.first.amount }

        val dayStarts = (6 downTo 0).map { AppDates.addDays(today, -it) }
        val daily = dayStarts.map { start -> paid.filter { it.second >= start && it.second < AppDates.addDays(start, 1) }.sumOf { it.first.amount } }

        fun share(amount: Long) = if (monthTotal == 0L) "0%" else "${amount * 100 / monthTotal}%"

        val bySource = thisMonth.groupBy { source(it.first) }
            .map { (label, list) -> label to list.sumOf { it.first.amount } }
            .sortedByDescending { it.second }
            .map { (label, amount) -> ShareRowUi(label, "${AdminFormat.vnd(amount)} • ${share(amount)}") }

        val byMethod = listOf(PaymentMethod.CASH to "Tiền mặt", PaymentMethod.TRANSFER to "Chuyển khoản").map { (method, label) ->
            val list = thisMonth.filter { it.first.method == method }
            val amount = list.sumOf { it.first.amount }
            ShareRowUi(label, "${list.size} HĐ • ${AdminFormat.vnd(amount)}")
        }

        val topPackages = thisMonth.groupBy { packageName(it.first) }
            .map { (name, list) -> Triple(name, list.size, list.sumOf { it.first.amount }) }
            .sortedByDescending { it.third }
            .take(5)
            .map { (name, count, amount) -> ShareRowUi(name, "$count lượt • ${AdminFormat.vnd(amount)}") }

        return RevenueUiState(
            isLoading = false,
            monthLabel = "Doanh thu tháng " + SimpleDateFormat("MM/yyyy", Locale.ROOT).format(Date(monthStart)),
            monthTotal = AdminFormat.vnd(monthTotal),
            growth = when {
                lastTotal == 0L && monthTotal == 0L -> "Chưa có doanh thu"
                lastTotal == 0L -> "Tháng trước chưa có doanh thu"
                else -> String.format(Locale.ROOT, "%+.1f%% so với tháng trước (%s)",
                    (monthTotal - lastTotal) * 100.0 / lastTotal, AdminFormat.compact(lastTotal))
            },
            growthTone = when {
                lastTotal == 0L -> AdminTone.Neutral
                monthTotal >= lastTotal -> AdminTone.Positive
                else -> AdminTone.Critical
            },
            today = AdminFormat.vnd(daily.last()),
            paidCount = thisMonth.size.toString(),
            weekTotal = AdminFormat.compact(daily.sum()),
            dailyBars = AdminFormat.bars(dayStarts.map(::weekday), daily),
            bySource = bySource.ifEmpty { listOf(ShareRowUi("Chưa có hoá đơn nào trong tháng", "—")) },
            byMethod = byMethod,
            topPackages = topPackages,
            recent = paid.sortedByDescending { it.second }.take(5).map { (p, _) ->
                RecentPaymentUi(
                    invoice = p.id,
                    title = "${p.id} • ${AdminFormat.vnd(p.amount)}",
                    subtitle = "${packageName(p)} • ${p.paidAt} • ${if (p.method == PaymentMethod.CASH) "Tiền mặt" else "Chuyển khoản"}"
                )
            }
        )
    }

    private fun RevenueData.source(p: Payment): String {
        val packageId = memberships[p.membershipId]?.packageId
        val pkg = packageId?.let { packages[it] }
        return when {
            packageId == SpecialPackages.DAY_PASS -> "Vé ngày"
            pkg?.type == PackageType.PT -> "Gói PT"
            pkg?.type == PackageType.MEMBERSHIP -> "Gói hội viên"
            else -> "Khác"
        }
    }

    private fun RevenueData.packageName(p: Payment): String {
        val packageId = memberships[p.membershipId]?.packageId
        return packageId?.let { packages[it]?.name ?: it } ?: "Không rõ gói"
    }

    /* ============================ LƯU LƯỢNG ============================ */

    private fun TrafficData.toTrafficUi(): TrafficUiState {
        val today = AppDates.startOfDay()
        val dayStarts = (6 downTo 0).map { AppDates.addDays(today, -it) }
        val visits = checkIns.filter { it.checkInAt > 0 }
        val daily = dayStarts.map { start -> visits.count { it.checkInAt >= start && it.checkInAt < AppDates.addDays(start, 1) }.toLong() }
        val todayVisits = visits.filter { it.checkInAt >= today }.sortedByDescending { it.checkInAt }
        val inGym = todayVisits.filter { it.checkOutAt == 0L }

        val last30 = visits.filter { it.checkInAt >= AppDates.addDays(today, -29) }
        val finished = last30.filter { it.checkOutAt > it.checkInAt }
        val avgMinutes = if (finished.isEmpty()) null else finished.map { (it.checkOutAt - it.checkInAt) / 60_000 }.average().toLong()

        val hourCounts = last30.groupingBy { hourOf(it.checkInAt) }.eachCount()
        val slotCounts = SLOTS.map { (from, to) -> Triple(from, to, (from until to).sumOf { hourCounts[it] ?: 0 }) }
        val busiest = slotCounts.maxOfOrNull { it.third } ?: 0

        val time = SimpleDateFormat("HH:mm", Locale.ROOT)
        fun nameOf(uid: String) = names[uid] ?: uid

        return TrafficUiState(
            isLoading = false,
            today = todayVisits.size.toString(),
            inGymNow = inGym.size.toString(),
            weekTotal = daily.sum().toString(),
            avgDuration = avgMinutes?.let { formatDuration(it) } ?: "—",
            weeklyBars = AdminFormat.bars(dayStarts.map(::weekday), daily),
            slots = slotCounts.map { (from, to, count) ->
                ShareRowUi(
                    label = "%02d:00–%02d:00".format(from, to),
                    value = "$count lượt",
                    tone = if (busiest > 0 && count == busiest) AdminTone.Brand else null
                )
            },
            inGym = inGym.map { InGymUi(nameOf(it.uid), "Vào lúc ${time.format(Date(it.checkInAt))}") },
            todayVisits = todayVisits.map {
                ShareRowUi(
                    label = nameOf(it.uid),
                    value = time.format(Date(it.checkInAt)) + " → " + if (it.checkOutAt > 0) time.format(Date(it.checkOutAt)) else "đang tập",
                    tone = if (it.checkOutAt == 0L) AdminTone.Positive else null
                )
            }
        )
    }

    private fun formatDuration(minutes: Long) = if (minutes < 60) "$minutes phút" else "${minutes / 60} giờ ${minutes % 60} phút"

    /* ============================ HỖ TRỢ ============================ */

    private fun monthStart(offset: Int): Long = Calendar.getInstance().apply {
        timeInMillis = AppDates.startOfDay()
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offset)
    }.timeInMillis

    private fun hourOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    private fun weekday(millis: Long): String =
        when (Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            else -> "CN"
        }

    private fun Throwable.toUserMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("permission", ignoreCase = true) -> "Bạn không có quyền xem báo cáo (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Không tải được dữ liệu."
        }
    }

    private companion object {
        /** Khung giờ thống kê (giờ bắt đầu, giờ kết thúc). */
        val SLOTS = listOf(0 to 5, 5 to 8, 8 to 11, 11 to 14, 14 to 17, 17 to 20, 20 to 24)
    }
}
