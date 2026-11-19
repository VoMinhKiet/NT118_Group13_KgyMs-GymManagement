package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.CheckInService
import com.kgyms.app.data.firebase.FirebaseAuthService
import com.kgyms.app.data.firebase.PackageService
import com.kgyms.app.data.firebase.PaymentService
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.CheckInResult
import com.kgyms.app.data.model.MembershipStatus
import com.kgyms.app.data.qr.CheckInCode
import com.kgyms.app.data.util.AppDates
import kotlinx.coroutines.flow.first

/**
 * Điểm danh bằng QR xoay vòng ở quầy.
 *
 * Quét lần 1 trong ngày → check-in (cần gói `active` còn hạn hôm nay, gói thường hoặc gói PT).
 * Quét khi đang có lượt mở trong hôm nay → check-out.
 * Lượt mở từ hôm trước (quên check-out) tự đóng lúc 23:59 ngày đó.
 */
class CheckInRepository(
    private val service: CheckInService = CheckInService(),
    private val paymentService: PaymentService = PaymentService(),
    private val packageService: PackageService = PackageService(),
    private val authService: FirebaseAuthService = FirebaseAuthService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    suspend fun handleScan(raw: String, nowMillis: Long = System.currentTimeMillis()): CheckInResult {
        if (!CheckInCode.isCheckInCode(raw)) return CheckInResult.NotCheckInCode
        val uid = requireNotNull(authService.getCurrentUid()) { "Bạn cần đăng nhập để điểm danh." }

        val secret = service.getCheckInSecret()
        check(secret.isNotBlank()) { "Phòng gym chưa cấu hình mã điểm danh." }
        if (!CheckInCode.verify(raw, secret, nowMillis)) return CheckInResult.InvalidCode
        if (service.isStaffOrAdmin(uid)) return CheckInResult.StaffNotRequired

        val today = AppDates.startOfDay(nowMillis)
        val open = service.getUserCheckIns(uid).filter { it.checkOutAt == 0L && it.checkInAt > 0 }
        val updates = mutableMapOf<String, Any?>()

        // Quên check-out hôm trước: đóng lúc 23:59 của ngày đó.
        open.filter { it.checkInAt < today }.forEach {
            updates["checkIns/${it.id}/checkOutAt"] = AppDates.endOfDay(it.checkInAt)
        }

        val openToday = open.filter { it.checkInAt >= today }
        if (openToday.isNotEmpty()) {
            openToday.forEach { updates["checkIns/${it.id}/checkOutAt"] = nowMillis }
            service.updatePaths(updates)
            activityLogRepository.log(ActivityLogAction.CHECK_IN, "check-out khỏi phòng gym")
            return CheckInResult.CheckedOut(openToday.minOf { it.checkInAt }, nowMillis)
        }

        val activeMembership = paymentService.getUserMemberships(uid)
            .filter { it.status == MembershipStatus.ACTIVE }
            .filter {
                val start = AppDates.parseDay(it.startDate)
                val end = AppDates.parseDay(it.endDate)
                start != null && end != null && today in start..end
            }
            .maxByOrNull { AppDates.parseDay(it.endDate) ?: 0L }

        if (activeMembership == null) {
            if (updates.isNotEmpty()) service.updatePaths(updates)
            return CheckInResult.NoActiveMembership
        }

        updates["checkIns/${service.newCheckInId()}"] = mapOf(
            "uid" to uid,
            "checkInAt" to nowMillis,
            "checkOutAt" to 0L
        )
        service.updatePaths(updates)
        activityLogRepository.log(ActivityLogAction.CHECK_IN, "check-in tại phòng gym")

        val packageName = runCatching {
            packageService.observePackages().first().firstOrNull { it.id == activeMembership.packageId }?.name
        }.getOrNull().orEmpty()
        return CheckInResult.CheckedIn(nowMillis, packageName)
    }
}
