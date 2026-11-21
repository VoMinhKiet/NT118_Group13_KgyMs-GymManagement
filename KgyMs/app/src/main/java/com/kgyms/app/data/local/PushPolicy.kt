package com.kgyms.app.data.local

/** Nhóm thông báo push; khớp với `category` mà server gửi kèm (server/src/domain/push.ts). */
internal enum class PushCategory {
    /** Tin phòng tập, ưu đãi (admin gửi). */
    GYM,

    /** Nhắc hết hạn gói... */
    REMINDERS,

    /** Thanh toán thành công và các thông báo giao dịch/tài khoản. */
    TRANSACTIONS;

    companion object {
        /** Giá trị lạ/thiếu được coi là giao dịch (nhóm quan trọng nhất, mặc định bật). */
        fun fromWire(value: String?): PushCategory = when (value) {
            "gym" -> GYM
            "reminders" -> REMINDERS
            else -> TRANSACTIONS
        }
    }
}

/**
 * Các công tắc trong Cài đặt → Thông báo. Mỗi công tắc lưu trong SharedPreferences với khóa
 * `notification_<key>` (xem [ProfileDevicePreferences]); mặc định ở [DEFAULTS].
 */
internal data class PushPreferences(
    val gym: Boolean = true,
    val reminders: Boolean = true,
    val transactions: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val lockScreen: Boolean = true,
    /** Không làm phiền: trong khung giờ yên tĩnh vẫn hiện thông báo nhưng không kêu, không rung. */
    val quietHours: Boolean = true,
) {
    companion object {
        const val QUIET_START_HOUR = 22
        const val QUIET_END_HOUR = 7

        /** Mặc định của từng công tắc theo khóa; dùng chung cho màn cài đặt và lúc nhận push. */
        val DEFAULTS: Map<String, Boolean> = PushPreferences().let {
            mapOf(
                "general" to it.gym,
                "reminders" to it.reminders,
                "transactions" to it.transactions,
                "sound" to it.sound,
                "vibration" to it.vibration,
                "lock" to it.lockScreen,
                "dnd" to it.quietHours,
            )
        }

        fun isQuietHour(hourOfDay: Int): Boolean = hourOfDay >= QUIET_START_HOUR || hourOfDay < QUIET_END_HOUR
    }
}

/** Cách một thông báo push sẽ được hiện (hoặc bị bỏ qua). */
internal data class PushPresentation(
    val show: Boolean,
    val sound: Boolean,
    val vibration: Boolean,
    /** true = hiện nội dung trên màn hình khóa; false = ẩn hoàn toàn khỏi màn hình khóa. */
    val showOnLockScreen: Boolean,
)

internal object PushPolicy {
    fun decide(prefs: PushPreferences, category: PushCategory, hourOfDay: Int): PushPresentation {
        val allowed = when (category) {
            PushCategory.GYM -> prefs.gym
            PushCategory.REMINDERS -> prefs.reminders
            PushCategory.TRANSACTIONS -> prefs.transactions
        }
        val quiet = prefs.quietHours && PushPreferences.isQuietHour(hourOfDay)
        return PushPresentation(
            show = allowed,
            sound = prefs.sound && !quiet,
            vibration = prefs.vibration && !quiet,
            showOnLockScreen = prefs.lockScreen,
        )
    }
}
