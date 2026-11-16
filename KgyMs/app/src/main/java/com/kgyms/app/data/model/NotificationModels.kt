package com.kgyms.app.data.model

/** Giá trị hợp lệ của notifications/{uid}/{id}/type (khớp database.rules.json). */
object NotificationType {
    const val GENERAL = "general"
    const val REMINDER = "reminder"
    const val PROMOTION = "promotion"

    val all = listOf(PROMOTION, GENERAL, REMINDER)
}

/** Đối tượng nhận của một lần gửi thông báo. */
object NotificationTarget {
    /** Mọi tài khoản user đang hoạt động (không gồm admin). */
    const val ALL_USERS = "all_users"

    /** Hội viên đang có gói active (có thể lọc theo 1 gói). */
    const val ACTIVE_MEMBERS = "active_members"

    /** Một người cụ thể. */
    const val USER = "user"

    val all = listOf(ALL_USERS, ACTIVE_MEMBERS, USER)
}

/** Một thông báo trong hộp thư của người dùng — notifications/{uid}/{id}. */
data class AppNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = NotificationType.GENERAL,
    val isRead: Boolean = false,
    /** "HH:mm dd/MM/yyyy". */
    val createdAt: String = "",
    /** Id lần gửi trong notificationBroadcasts (rỗng nếu là nhắc hạn tự động). */
    val broadcastId: String = ""
)

/** Một lần admin gửi thông báo — notificationBroadcasts/{id}. */
data class NotificationBroadcast(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = NotificationType.GENERAL,
    val target: String = NotificationTarget.ALL_USERS,
    /** Chỉ dùng khi target = active_members và có lọc theo gói. */
    val packageId: String = "",
    /** Chỉ dùng khi target = user. */
    val targetUid: String = "",
    /** Mô tả người nhận để hiển thị, vd "Hội viên gói Premium 6 tháng". */
    val targetLabel: String = "",
    /** uid người nhận → id thông báo trong notifications/{uid}. */
    val recipients: Map<String, String> = emptyMap(),
    val createdAt: String = "",
    val createdBy: String = ""
)

/** Cấu hình nhắc hết hạn gói — notificationSettings/expiryReminder. */
data class ExpiryReminderSettings(
    val enabled: Boolean = false,
    /** Các mốc "còn N ngày" sẽ nhắc. */
    val days: Set<Int> = setOf(3),
    val lastRunAt: String = "",
    val lastRunCount: Int = 0
) {
    companion object {
        /** Các mốc admin có thể bật/tắt. */
        val AVAILABLE_DAYS = listOf(1, 3, 5, 7, 14)
    }
}

/** Người nhận có thể chọn khi gửi riêng. */
data class RecipientUser(
    val uid: String,
    val fullName: String,
    val email: String
)

data class PackageOption(
    val id: String,
    val name: String
)

/** Phần dữ liệu memberships cần cho việc gửi thông báo. */
data class MembershipSummary(
    val id: String,
    val uid: String,
    val packageId: String,
    val status: String,
    /** "dd/MM/yyyy". */
    val endDate: String
)
