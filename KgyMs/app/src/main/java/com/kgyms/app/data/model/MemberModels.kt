package com.kgyms.app.data.model

/** Hồ sơ hội viên mà admin xem được (node users/{uid}, không gồm admin). */
data class MemberProfile(
    val uid: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val gender: String,
    val age: Int,
    val birthday: String,
    /** active | inactive | locked */
    val status: String,
    /** dd/MM/yyyy */
    val createdAt: String,
    /** "KM000123", rỗng khi chưa cấp. */
    val memberCode: String,
    val isStaff: Boolean
) {
    val displayName: String get() = fullName.ifBlank { email.substringBefore("@") }.ifBlank { uid }
    val isLocked: Boolean get() = status != "active"
}

/** Toàn bộ dữ liệu cần cho màn Hội viên, đọc realtime cùng lúc. */
data class MemberDirectory(
    val members: List<MemberProfile> = emptyList(),
    val memberships: List<Membership> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val packages: Map<String, GymPackage> = emptyMap(),
    /** uid → tên nhân sự (để hiện PT phụ trách). */
    val staffNames: Map<String, String> = emptyMap()
)
