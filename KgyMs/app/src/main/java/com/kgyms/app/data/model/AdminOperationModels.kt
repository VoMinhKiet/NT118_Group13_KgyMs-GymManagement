package com.kgyms.app.data.model

/*
 * ================================================================
 * Model nghiệp vụ cho các màn Admin chưa nối Firebase
 * (Hội viên, Hóa đơn, Doanh thu, Lưu lượng, Khiếu nại).
 * Dữ liệu hiện do AdminMockRepository cung cấp; khi nối Firebase chỉ cần
 * thay repository, ViewModel và màn hình giữ nguyên.
 * ================================================================
 */

enum class MemberStatus { Active, Expiring }

data class AdminMember(
    val id: String,
    val name: String,
    val memberCode: String,
    val planName: String,
    val status: MemberStatus,
    val daysLeft: Int,
    /** dd/MM/yyyy */
    val expiresOn: String,
    val checkInsThisMonth: Int,
    val trainerName: String,
    /** dd/MM */
    val lastInBody: String,
    val debt: Long
)

data class InvoiceDraft(
    val memberLabel: String,
    val service: String,
    val discountPercent: Int,
    val total: Long
)

enum class ComplaintStatus { New, InProgress }

data class AdminComplaint(
    /** Vd "#CS-208". */
    val id: String,
    val title: String,
    val memberName: String,
    /** dd/MM/yyyy */
    val date: String,
    val isHighPriority: Boolean,
    val status: ComplaintStatus,
    val description: String
)

data class RevenueReport(
    val monthTotal: Long,
    val growthPercent: Double,
    /** Doanh thu 7 ngày gần nhất, cũ → mới. */
    val daily: List<Long>,
    val packageRevenue: Long,
    val ptRevenue: Long,
    val otherRevenue: Long
)

data class TrafficSlot(val timeRange: String, val visits: Int)

data class TrafficReport(
    val today: Int,
    val inGymNow: Int,
    /** Lượt check-in 7 ngày gần nhất, cũ → mới. */
    val weekly: List<Int>,
    val busySlots: List<TrafficSlot>
)
