package com.kgyms.app.data.repository

import com.kgyms.app.data.model.AdminComplaint
import com.kgyms.app.data.model.AdminMember
import com.kgyms.app.data.model.AdminTransaction
import com.kgyms.app.data.model.ComplaintStatus
import com.kgyms.app.data.model.InvoiceDraft
import com.kgyms.app.data.model.MemberStatus
import com.kgyms.app.data.model.RevenueReport
import com.kgyms.app.data.model.TrafficReport
import com.kgyms.app.data.model.TrafficSlot

/**
 * Nguồn dữ liệu MẪU cho các màn Admin chưa nối Firebase.
 * Đây là nơi duy nhất chứa dữ liệu minh họa; ViewModel chỉ đọc qua interface này.
 */
class AdminMockRepository {

    fun members(): List<AdminMember> = listOf(
        AdminMember("m1", "Nguyễn Minh Anh", "KM012345", "Premium", MemberStatus.Active, 248, "30/05/2027", 12, "Trần Hoàng", "18/09", 0),
        AdminMember("m2", "Trần Gia Huy", "KM012346", "Basic", MemberStatus.Expiring, 12, "25/11/2026", 6, "—", "02/09", 0),
        AdminMember("m3", "Lê Phương Thảo", "KM012347", "Premium", MemberStatus.Active, 190, "22/05/2027", 15, "Mai Anh", "10/09", 0),
        AdminMember("m4", "Vũ Hoàng Nam", "KM012348", "Basic", MemberStatus.Expiring, 12, "25/11/2026", 4, "—", "—", 399_000),
        AdminMember("m5", "Mai Ngọc Linh", "KM012349", "Premium", MemberStatus.Active, 75, "27/01/2027", 9, "Trần Hoàng", "21/09", 0)
    )

    fun transactions(): List<AdminTransaction> = listOf(
        AdminTransaction("#INV-1092", "Nguyễn Minh Anh", "Premium 6 tháng", 1_990_000, true, "24/09/2026 09:42"),
        AdminTransaction("#INV-1091", "Trần Gia Huy", "Basic 1 tháng", 399_000, true, "23/09/2026 14:20"),
        AdminTransaction("#INV-1090", "Lê Phương Thảo", "Elite 12 tháng", 2_590_000, false, "22/09/2026 16:10"),
        AdminTransaction("#INV-1089", "Vũ Hoàng Nam", "Premium 6 tháng", 1_990_000, true, "21/09/2026 11:35"),
        AdminTransaction("#INV-1088", "Mai Ngọc Linh", "PT Online 8 buổi", 890_000, true, "20/09/2026 18:45")
    )

    fun invoiceDraft(): InvoiceDraft =
        InvoiceDraft("KM012345 • Nguyễn Minh Anh", "Premium 6 tháng", 0, 1_990_000)

    fun complaints(): List<AdminComplaint> = listOf(
        AdminComplaint("#CS-208", "Thiết bị hỏng", "Nguyễn Minh Anh", "24/09/2026", true, ComplaintStatus.New,
            "Máy chạy bộ gặp lỗi và phát tiếng ồn khi chạy tốc độ > 8km/h."),
        AdminComplaint("#CS-207", "Lịch PT", "Trần Gia Huy", "23/09/2026", true, ComplaintStatus.New,
            "Hội viên phản ánh lịch tập PT chưa được cập nhật đúng."),
        AdminComplaint("#CS-206", "Thanh toán", "Lê Phương Thảo", "22/09/2026", false, ComplaintStatus.InProgress,
            "Giao dịch thanh toán chưa được cập nhật vào hệ thống."),
        AdminComplaint("#CS-205", "Dịch vụ", "Vũ Hoàng Nam", "21/09/2026", false, ComplaintStatus.InProgress,
            "Hội viên phản ánh về chất lượng dịch vụ tại phòng tập.")
    )

    fun revenueReport(): RevenueReport = RevenueReport(
        monthTotal = 128_500_000,
        growthPercent = 12.4,
        daily = listOf(4_500_000, 5_800_000, 7_800_000, 6_800_000, 9_200_000, 8_200_000, 10_000_000),
        packageRevenue = 84_200_000,
        ptRevenue = 32_700_000,
        otherRevenue = 11_600_000
    )

    fun trafficReport(): TrafficReport = TrafficReport(
        today = 352,
        inGymNow = 84,
        weekly = listOf(280, 310, 295, 340, 330, 360, 352),
        busySlots = listOf(
            TrafficSlot("06:00–09:00", 92),
            TrafficSlot("17:00–20:00", 168),
            TrafficSlot("20:00–22:00", 64)
        )
    )
}
