package com.kgyms.app.viewmodel.admin

/**
 * Sắc thái ngữ nghĩa của một trạng thái/số liệu.
 * ViewModel chỉ nói "tốt / cần chú ý / nghiêm trọng…", còn màu cụ thể do lớp UI quyết định.
 */
enum class AdminTone {
    /** Màu nhận diện chính của app. */
    Brand,

    /** Tốt, hoạt động bình thường. */
    Positive,

    /** Cần chú ý (đang bảo trì, chờ thanh toán…). */
    Warning,

    /** Nghiêm trọng (hỏng, khiếu nại mới…). */
    Critical,

    /** Thông tin trung tính có nhấn. */
    Info,

    /** Không nhấn. */
    Neutral
}
