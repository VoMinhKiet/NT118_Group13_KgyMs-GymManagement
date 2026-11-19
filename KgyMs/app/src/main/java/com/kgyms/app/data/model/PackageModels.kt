package com.kgyms.app.data.model

/** Giá trị hợp lệ của packages/{id}/type (khớp database.rules.json). */
object PackageType {
    const val MEMBERSHIP = "membership"
    const val PT = "pt"

    val all = listOf(MEMBERSHIP, PT)
}

/** Một gói tập — node packages/{packageId} trên Firebase Realtime Database. */
data class GymPackage(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = PackageType.MEMBERSHIP,
    /** VNĐ. */
    val price: Long = 0,
    val durationDays: Int = 0,
    /** Chỉ dùng với gói PT. */
    val sessions: Int = 0,
    /** Chỉ dùng với gói PT: PT do admin phân công. */
    val trainerUid: String = "",
    val isActive: Boolean = true,
    val benefits: List<String> = emptyList()
)

/** PT có thể phân công cho gói (user có isStaff = true). */
data class TrainerOption(val uid: String, val name: String)
