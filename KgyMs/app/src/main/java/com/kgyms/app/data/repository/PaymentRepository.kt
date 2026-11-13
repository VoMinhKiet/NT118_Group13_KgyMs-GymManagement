package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.FirebaseAuthService
import com.kgyms.app.data.firebase.PackageService
import com.kgyms.app.data.firebase.PaymentService
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.Membership
import com.kgyms.app.data.model.MembershipStatus
import com.kgyms.app.data.model.NotificationType
import com.kgyms.app.data.model.Payment
import com.kgyms.app.data.model.PaymentMethod
import com.kgyms.app.data.model.PaymentReceiver
import com.kgyms.app.data.model.PaymentRecord
import com.kgyms.app.data.model.PaymentStatus
import com.kgyms.app.data.qr.VietQr
import com.kgyms.app.data.util.AppDates
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Thanh toán gói tập bằng VietQR (giai đoạn bán tự động):
 * 1. Hội viên chọn gói → tạo memberships + payments `pending` trong 1 lần ghi.
 * 2. Hội viên chuyển khoản với nội dung "KGYMS <paymentId>".
 * 3. Admin thấy tiền vào → [confirmPaid]: hoá đơn `paid`, gói `active`, gửi thông báo.
 *
 * Giai đoạn tự động sau này chỉ cần một Cloud Function làm đúng bước 3.
 */
class PaymentRepository(
    private val service: PaymentService = PaymentService(),
    private val packageService: PackageService = PackageService(),
    private val authService: FirebaseAuthService = FirebaseAuthService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    /** null nếu chưa đăng nhập. */
    fun currentUid(): String? = authService.getCurrentUid()

    fun observePackages(): Flow<List<GymPackage>> = packageService.observePackages()

    fun observeMyPayments(uid: String): Flow<List<Payment>> = service.observeUserPayments(uid)

    fun observeMyMemberships(uid: String): Flow<List<Membership>> = service.observeUserMemberships(uid)

    /** Chuỗi VietQR của hoá đơn: chuyển [Payment.amount] vào tài khoản gym, nội dung "KGYMS <id>". */
    fun qrPayload(payment: Payment): String = VietQr.payload(
        bankBin = PaymentReceiver.BANK_BIN,
        accountNo = PaymentReceiver.ACCOUNT_NO,
        amount = payment.amount,
        content = PaymentReceiver.transferContent(payment.id)
    )

    /* ============================ HỘI VIÊN ============================ */

    /**
     * Tạo hoá đơn chuyển khoản cho gói [pkg] và trả về paymentId.
     * Nếu đã có hoá đơn `pending` của đúng gói này thì trả lại hoá đơn đó, không tạo mới.
     */
    suspend fun startPurchase(pkg: GymPackage): String {
        val uid = requireNotNull(currentUid()) { "Bạn cần đăng nhập để thanh toán." }
        require(pkg.isActive) { "Gói này đã ngừng bán." }
        require(pkg.price > 0) { "Gói chưa có giá, vui lòng liên hệ quầy." }

        val pendingMemberships = service.getUserMemberships(uid)
            .filter { it.status == MembershipStatus.PENDING && it.packageId == pkg.id }
            .map { it.id }
            .toSet()
        service.getUserPayments(uid)
            .firstOrNull { it.status == PaymentStatus.PENDING && it.membershipId in pendingMemberships }
            ?.let { return it.id }

        val now = AppDates.timestamp()
        val membershipId = service.newMembershipId()
        val paymentId = newPaymentId()
        service.updatePaths(
            mapOf(
                "memberships/$membershipId" to mapOf(
                    "uid" to uid,
                    "packageId" to pkg.id,
                    "trainerUid" to pkg.trainerUid,
                    "status" to MembershipStatus.PENDING,
                    "startDate" to "",
                    "endDate" to "",
                    "sessionsUsed" to 0,
                    "createdAt" to now
                ),
                "payments/$paymentId" to mapOf(
                    "uid" to uid,
                    "membershipId" to membershipId,
                    "amount" to pkg.price,
                    "discount" to 0,
                    "method" to PaymentMethod.TRANSFER,
                    "status" to PaymentStatus.PENDING,
                    "createdAt" to now,
                    "paidAt" to ""
                )
            )
        )
        activityLogRepository.log(ActivityLogAction.PAYMENT, "tạo hoá đơn $paymentId cho gói \"${pkg.name}\"")
        return paymentId
    }

    /** Hội viên huỷ hoá đơn đang chờ của mình: hoá đơn và gói đều chuyển sang `cancelled`. */
    suspend fun cancelPayment(payment: Payment) {
        require(payment.status == PaymentStatus.PENDING) { "Chỉ huỷ được hoá đơn đang chờ thanh toán." }
        service.updatePaths(
            buildMap {
                put("payments/${payment.id}/status", PaymentStatus.CANCELLED)
                if (payment.membershipId.isNotBlank()) {
                    put("memberships/${payment.membershipId}/status", MembershipStatus.CANCELLED)
                }
            }
        )
        activityLogRepository.log(ActivityLogAction.PAYMENT, "huỷ hoá đơn ${payment.id}")
    }

    /* ============================ ADMIN ============================ */

    /** Mọi hoá đơn kèm gói và tên hội viên, realtime. */
    fun observeAdminRecords(): Flow<List<PaymentRecord>> {
        val names = flow { emit(runCatching { service.getUserNames() }.getOrDefault(emptyMap())) }
        return combine(
            service.observeAllPayments(),
            service.observeAllMemberships(),
            packageService.observePackages(),
            names
        ) { payments, memberships, packages, userNames ->
            val membershipById = memberships.associateBy { it.id }
            val packageById = packages.associateBy { it.id }
            payments.map { payment ->
                val membership = membershipById[payment.membershipId]
                PaymentRecord(
                    payment = payment,
                    membership = membership,
                    pkg = membership?.let { packageById[it.packageId] },
                    memberName = userNames[payment.uid].orEmpty().ifBlank { payment.uid }
                )
            }
        }
    }

    /**
     * Admin xác nhận đã nhận tiền:
     * - payments: `paid` + paidAt
     * - memberships: `active`, startDate/endDate. Nếu hội viên còn gói cùng loại đang chạy
     *   thì gói mới **nối tiếp** sau ngày hết hạn của gói đó.
     * - notifications: báo cho hội viên.
     * Tất cả trong 1 lần ghi.
     */
    suspend fun confirmPaid(paymentId: String) {
        val payment = requireNotNull(service.getPayment(paymentId)) { "Không tìm thấy hoá đơn $paymentId." }
        require(payment.status == PaymentStatus.PENDING) { "Hoá đơn không còn ở trạng thái chờ thanh toán." }

        val membership = payment.membershipId.takeIf { it.isNotBlank() }?.let { service.getMembership(it) }
        val packages = packageService.observePackages().first().associateBy { it.id }
        val pkg = membership?.let { packages[it.packageId] }
        val now = AppDates.timestamp()

        val updates = mutableMapOf<String, Any?>(
            "payments/$paymentId/status" to PaymentStatus.PAID,
            "payments/$paymentId/paidAt" to now
        )

        var period = ""
        if (membership != null && pkg != null) {
            val today = AppDates.startOfDay()
            val latestEnd = service.getUserMemberships(payment.uid)
                .filter { it.id != membership.id && it.status == MembershipStatus.ACTIVE }
                .filter { packages[it.packageId]?.type == pkg.type }
                .mapNotNull { AppDates.parseDay(it.endDate) }
                .filter { it >= today }
                .maxOrNull()
            val start = latestEnd?.let { AppDates.addDays(it, 1) } ?: today
            val end = AppDates.addDays(start, pkg.durationDays.coerceAtLeast(1) - 1)
            period = "${AppDates.day(start)} – ${AppDates.day(end)}"
            updates["memberships/${membership.id}/status"] = MembershipStatus.ACTIVE
            updates["memberships/${membership.id}/startDate"] = AppDates.day(start)
            updates["memberships/${membership.id}/endDate"] = AppDates.day(end)
        }

        val packageName = pkg?.name ?: "gói tập"
        updates["notifications/${payment.uid}/${service.newNotificationId(payment.uid)}"] = mapOf(
            "title" to "Thanh toán thành công",
            "message" to buildString {
                append("KgyMs đã nhận ${formatVnd(payment.amount)} cho hoá đơn $paymentId ($packageName).")
                if (period.isNotBlank()) append(" Thời hạn: $period.")
            },
            "type" to NotificationType.GENERAL,
            "isRead" to false,
            "createdAt" to now
        )

        service.updatePaths(updates)
        activityLogRepository.log(ActivityLogAction.PAYMENT, "xác nhận đã nhận tiền hoá đơn $paymentId ($packageName)")
    }

    /* ============================ HỖ TRỢ ============================ */

    /** Mã hoá đơn ngắn, chỉ chữ in hoa + số để ngân hàng không cắt mất khi chuyển khoản. */
    private fun newPaymentId(): String =
        "KG" + (1..8).map { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] }.joinToString("")

    private fun formatVnd(amount: Long): String =
        amount.toString().reversed().chunked(3).joinToString(".").reversed() + "đ"

    private companion object {
        /** Bỏ 0/O/1/I để đọc không nhầm. */
        const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
    }
}
