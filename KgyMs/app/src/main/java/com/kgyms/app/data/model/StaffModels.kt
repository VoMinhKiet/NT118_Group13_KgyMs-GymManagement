package com.kgyms.app.data.model

/** Giá trị hợp lệ của staff/{uid}/position (khớp database.rules.json). */
object StaffPosition {
    const val PT = "pt"
    const val RECEPTIONIST = "receptionist"
    const val SUPPORT = "support"
    const val MANAGER = "manager"

    val all = listOf(PT, RECEPTIONIST, SUPPORT, MANAGER)
}

object StaffStatus {
    const val ACTIVE = "active"
    const val INACTIVE = "inactive"
}

/**
 * Hồ sơ nhân sự — node staff/{uid} (key = uid của user).
 * Node này mọi user đăng nhập đọc được (hội viên xem PT), nên KHÔNG chứa SĐT/email.
 */
data class StaffMember(
    val uid: String = "",
    val fullName: String = "",
    val avatarUrl: String = "",
    val position: String = StaffPosition.PT,
    val specialty: String = "",
    val bio: String = "",
    /** "HH:mm–HH:mm" */
    val workShift: String = "",
    /** dd/MM/yyyy */
    val startDate: String = "",
    val status: String = StaffStatus.ACTIVE
) {
    val isActive: Boolean get() = status == StaffStatus.ACTIVE
    val isTrainer: Boolean get() = position == StaffPosition.PT
}

/** User có thể được tuyển làm nhân sự, và thông tin liên hệ chỉ admin xem. */
data class StaffCandidate(
    val uid: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val avatarUrl: String
)
