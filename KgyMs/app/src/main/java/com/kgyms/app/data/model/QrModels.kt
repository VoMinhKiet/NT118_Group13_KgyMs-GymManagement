package com.kgyms.app.data.model

/** Giá trị hợp lệ của payments/{id}/status (khớp database.rules.json). */
object PaymentStatus {
    const val PENDING = "pending"
    const val PAID = "paid"
    const val CANCELLED = "cancelled"
}

/** Giá trị hợp lệ của payments/{id}/method. */
object PaymentMethod {
    const val CASH = "cash"
    const val TRANSFER = "transfer"
}

/** Giá trị hợp lệ của memberships/{id}/status. */
object MembershipStatus {
    const val PENDING = "pending"
    const val ACTIVE = "active"
    const val FROZEN = "frozen"
    const val EXPIRED = "expired"
    const val CANCELLED = "cancelled"
}

/** Gói có mã cố định trên Firebase. */
object SpecialPackages {
    /** Vé ngày 5.000đ, gợi ý khi hội viên chưa có gói mà quét QR điểm danh. */
    const val DAY_PASS = "day_pass"
}

/** Tài khoản nhận tiền khi hội viên quét VietQR. */
object PaymentReceiver {
    /** Mã BIN Napas của Vietcombank. */
    const val BANK_BIN = "970436"
    const val BANK_NAME = "Vietcombank"
    const val ACCOUNT_NO = "9793704700"
    const val DISPLAY_NAME = "Kgyms-VoMinhKiet"
    /** Nội dung chuyển khoản = "KGYMS <paymentId>". */
    const val TRANSFER_PREFIX = "KGYMS"

    fun transferContent(paymentId: String) = "$TRANSFER_PREFIX $paymentId"
}

/** Một hoá đơn — node payments/{paymentId}. Mã hoá đơn chính là [id]. */
data class Payment(
    val id: String = "",
    val uid: String = "",
    val membershipId: String = "",
    /** Số tiền phải trả (VNĐ), đã trừ giảm giá. */
    val amount: Long = 0,
    val discount: Long = 0,
    val method: String = PaymentMethod.TRANSFER,
    val status: String = PaymentStatus.PENDING,
    /** HH:mm dd/MM/yyyy */
    val createdAt: String = "",
    /** HH:mm dd/MM/yyyy, rỗng khi chưa trả. */
    val paidAt: String = ""
)

/** Gói hội viên đã đăng ký — node memberships/{membershipId}. */
data class Membership(
    val id: String = "",
    val uid: String = "",
    val packageId: String = "",
    val trainerUid: String = "",
    val status: String = MembershipStatus.PENDING,
    /** dd/MM/yyyy, rỗng khi chưa kích hoạt. */
    val startDate: String = "",
    val endDate: String = "",
    val sessionsUsed: Int = 0,
    val createdAt: String = ""
)

/** Một lượt vào/ra — node checkIns/{checkInId}. [checkOutAt] = 0 nghĩa là còn trong gym. */
data class CheckIn(
    val id: String = "",
    val uid: String = "",
    val checkInAt: Long = 0,
    val checkOutAt: Long = 0
)

/** Hoá đơn kèm dữ liệu liên quan, dùng cho màn Giao dịch của admin. */
data class PaymentRecord(
    val payment: Payment,
    val membership: Membership?,
    val pkg: GymPackage?,
    val memberName: String
)

/** Kết quả xử lý một lần quét QR điểm danh. */
sealed interface CheckInResult {
    data class CheckedIn(val at: Long, val packageName: String) : CheckInResult
    data class CheckedOut(val checkInAt: Long, val checkOutAt: Long) : CheckInResult
    /** Không phải mã điểm danh của KgyMs. */
    data object NotCheckInCode : CheckInResult
    /** Đúng định dạng nhưng sai chữ ký hoặc đã hết hạn (mã đổi mỗi 30 giây). */
    data object InvalidCode : CheckInResult
    /** Admin và PT không cần điểm danh. */
    data object StaffNotRequired : CheckInResult
    /** Chưa có gói đang hoạt động hôm nay → gợi ý mua vé ngày. */
    data object NoActiveMembership : CheckInResult
}
