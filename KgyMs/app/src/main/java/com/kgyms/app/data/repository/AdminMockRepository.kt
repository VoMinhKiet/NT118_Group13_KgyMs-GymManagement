package com.kgyms.app.data.repository

import com.kgyms.app.data.model.AdminComplaint
import com.kgyms.app.data.model.ComplaintStatus

/**
 * Nguồn dữ liệu MẪU cho các màn Admin chưa nối Firebase.
 * Đây là nơi duy nhất chứa dữ liệu minh họa; ViewModel chỉ đọc qua interface này.
 */
class AdminMockRepository {

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
}
