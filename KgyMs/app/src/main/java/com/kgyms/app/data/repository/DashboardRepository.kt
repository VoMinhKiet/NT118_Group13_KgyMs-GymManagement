package com.kgyms.app.data.repository

import com.google.firebase.database.DataSnapshot
import com.kgyms.app.data.firebase.DashboardService
import com.kgyms.app.data.model.ActivityLog
import com.kgyms.app.data.model.AssetStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Một cột của biểu đồ doanh thu 7 ngày. */
data class RevenueDay(
    /** "T2".."CN". */
    val label: String,
    val amount: Long
)

/** Toàn bộ số liệu tổng quan, tính từ Firebase. */
data class DashboardSummary(
    // Kinh doanh
    val revenueThisMonth: Long = 0,
    val revenueLast7Days: List<RevenueDay> = emptyList(),
    val paidThisMonthCount: Int = 0,
    val pendingPayments: Int = 0,
    // Hội viên
    val totalUsers: Int = 0,
    val activeMemberUsers: Int = 0,
    val expiringSoon: Int = 0,
    // Check-in
    val checkInsToday: Int = 0,
    val inGymNow: Int = 0,
    // Vận hành
    val openComplaints: Int = 0,
    val inProgressComplaints: Int = 0,
    val assetQuantity: Int = 0,
    val assetNeedFixQuantity: Int = 0,
    val assetNeedFixTypes: Int = 0,
    val packagesActive: Int = 0,
    val packagesTotal: Int = 0,
    val staffActive: Int = 0,
    val staffTotal: Int = 0,
    // Hệ thống
    val broadcastsCount: Int = 0,
    val reminderEnabled: Boolean = false,
    val reminderDays: String = "",
    val logsToday: Int = 0,
    val recentLogs: List<ActivityLog> = emptyList(),
    /** Node nào không đọc được (thiếu quyền/mất mạng). */
    val failedNodes: Set<String> = emptySet()
) {
    /** % user đang có gói active. */
    val activeMemberPercent: Int
        get() = if (totalUsers == 0) 0 else activeMemberUsers * 100 / totalUsers

    /** Việc cần admin xử lý: khiếu nại chưa xong + hóa đơn chờ + loại máy hỏng/bảo trì + gói sắp hết hạn. */
    val needsAttention: Int
        get() = openComplaints + inProgressComplaints + pendingPayments + assetNeedFixTypes + expiringSoon
}

/**
 * Tổng hợp số liệu Dashboard từ nhiều node. Mỗi node lắng nghe realtime;
 * node nào lỗi thì coi như rỗng để phần còn lại vẫn hiển thị.
 */
class DashboardRepository(
    private val service: DashboardService = DashboardService()
) {
    private val nodes = listOf(
        "users", "memberships", "payments", "checkIns", "complaints",
        "assets", "packages", "staff", "notificationBroadcasts",
        "notificationSettings", "activityLogs"
    )

    /** Trạng thái đọc của từng node. */
    private sealed interface NodeState {
        data object Loading : NodeState
        data object Failed : NodeState
        data class Loaded(val snapshot: DataSnapshot) : NodeState
    }

    fun observeSummary(): Flow<DashboardSummary> {
        val flows: List<Flow<Pair<String, NodeState>>> = nodes.map { path ->
            service.observe(path)
                .map<DataSnapshot, Pair<String, NodeState>> { path to NodeState.Loaded(it) }
                .onStart { emit(path to NodeState.Loading) }
                .catch { emit(path to NodeState.Failed) }
        }

        // Chỉ phát khi mọi node đã trả về lần đầu (thành công hoặc lỗi).
        return combine(flows) { pairs -> pairs.toMap() }
            .filter { states -> states.values.none { it == NodeState.Loading } }
            .map { states -> summarize(states) }
    }

    private fun summarize(snap: Map<String, NodeState>): DashboardSummary {
        val failed = snap.filterValues { it == NodeState.Failed }.keys
        fun node(path: String): DataSnapshot? = (snap[path] as? NodeState.Loaded)?.snapshot
        fun children(path: String): List<DataSnapshot> =
            node(path)?.children?.filter { !(it.key ?: "_").startsWith("_") }.orEmpty()

        val now = Calendar.getInstance()
        val todayStart = startOfDay(now.timeInMillis)
        val month = now.get(Calendar.MONTH)
        val year = now.get(Calendar.YEAR)

        /* ---------- Hội viên ---------- */
        val users = children("users")
        val memberUids = users
            .filter { it.text("role") != "admin" }
            .mapNotNull { it.key }
            .toSet()

        val activeMemberships = children("memberships").filter { it.text("status") == "active" }
        val activeMemberUsers = activeMemberships.map { it.text("uid") }.filter { it in memberUids }.toSet().size
        val expiringSoon = activeMemberships.count { m ->
            val end = parseDay(m.text("endDate")) ?: return@count false
            val days = ((end - todayStart) / DAY_MS).toInt()
            days in 0..7
        }

        /* ---------- Doanh thu ---------- */
        val payments = children("payments")
        val paid = payments.filter { it.text("status") == "paid" }
            .mapNotNull { p -> parseDateTime(p.text("paidAt"))?.let { it to p.long("amount") } }

        val paidThisMonth = paid.filter { (time, _) ->
            val c = Calendar.getInstance().apply { timeInMillis = time }
            c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
        }

        val last7 = (6 downTo 0).map { back ->
            val dayStart = todayStart - back * DAY_MS
            val amount = paid.filter { (time, _) -> time >= dayStart && time < dayStart + DAY_MS }.sumOf { it.second }
            RevenueDay(weekdayLabel(dayStart), amount)
        }

        /* ---------- Check-in ---------- */
        val checkIns = children("checkIns")
        val todayCheckIns = checkIns.filter { c -> toMillis(c.long("checkInAt")) >= todayStart }
        val inGymNow = todayCheckIns.count { it.long("checkOutAt") == 0L }

        /* ---------- Khiếu nại ---------- */
        val complaints = children("complaints")

        /* ---------- Tài sản ---------- */
        val assets = children("assets")
        val needFix = assets.filter { it.text("status").let { s -> s.isNotBlank() && s != AssetStatus.WORKING } }

        /* ---------- Gói tập, nhân sự ---------- */
        val packages = children("packages")
        val staff = children("staff")

        /* ---------- Thông báo ---------- */
        val reminder = node("notificationSettings")?.child("expiryReminder")

        /* ---------- Nhật ký ---------- */
        val logs = children("activityLogs").map { log ->
            ActivityLog(
                id = log.key.orEmpty(),
                title = log.text("title"),
                actorUid = log.text("actorUid"),
                action = log.text("action"),
                createdAt = log.text("createdAt")
            )
        }.map { it to (parseDateTime(it.createdAt) ?: Long.MIN_VALUE) }
            .sortedByDescending { it.second }

        return DashboardSummary(
            revenueThisMonth = paidThisMonth.sumOf { it.second },
            revenueLast7Days = last7,
            paidThisMonthCount = paidThisMonth.size,
            pendingPayments = payments.count { it.text("status") == "pending" },
            totalUsers = memberUids.size,
            activeMemberUsers = activeMemberUsers,
            expiringSoon = expiringSoon,
            checkInsToday = todayCheckIns.size,
            inGymNow = inGymNow,
            openComplaints = complaints.count { it.text("status") == "open" },
            inProgressComplaints = complaints.count { it.text("status") == "in_progress" },
            assetQuantity = assets.sumOf { it.long("quantity").toInt() },
            assetNeedFixQuantity = needFix.sumOf { it.long("quantity").toInt() },
            assetNeedFixTypes = needFix.size,
            packagesActive = packages.count { it.child("isActive").value == true },
            packagesTotal = packages.size,
            staffActive = staff.count { it.text("status").ifBlank { "active" } == "active" },
            staffTotal = staff.size,
            broadcastsCount = children("notificationBroadcasts").size,
            reminderEnabled = reminder?.child("enabled")?.value == true,
            reminderDays = reminder?.text("days").orEmpty(),
            logsToday = logs.count { it.second >= todayStart },
            recentLogs = logs.take(5).map { it.first },
            failedNodes = failed
        )
    }

    /* ============================ HELPERS ============================ */

    private fun DataSnapshot.text(name: String) = child(name).value?.toString().orEmpty()

    private fun DataSnapshot.long(name: String): Long = when (val v = child(name).value) {
        is Number -> v.toLong()
        else -> v?.toString()?.toDoubleOrNull()?.toLong() ?: 0L
    }

    /** checkInAt có thể lưu theo giây hoặc mili giây. */
    private fun toMillis(value: Long) = if (value in 1 until 100_000_000_000L) value * 1000 else value

    private fun startOfDay(millis: Long) = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun weekdayLabel(millis: Long): String =
        when (Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            else -> "CN"
        }

    private fun parseDay(text: String): Long? = runCatching {
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }.parse(text)?.time
    }.getOrNull()

    private fun parseDateTime(text: String): Long? = runCatching {
        SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }.parse(text)?.time
    }.getOrNull()

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
