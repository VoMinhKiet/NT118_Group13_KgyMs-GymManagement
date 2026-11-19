package com.kgyms.app.data.repository

import com.kgyms.app.data.model.CheckInResult
import com.kgyms.app.data.qr.CheckInCode
import com.kgyms.app.data.server.KgyMsServerService
import com.kgyms.app.data.server.ServerException

/**
 * Điểm danh bằng QR xoay vòng ở quầy, xử lý trên server (POST /check-in):
 * server kiểm tra chữ ký HMAC bằng khoá bí mật (app không còn đọc được gymInfo/checkInSecret),
 * rồi check-in / check-out và ghi activityLogs.
 *
 * Quét lần 1 trong ngày → check-in (cần gói `active` còn hạn hôm nay, gói thường hoặc gói PT).
 * Quét khi đang có lượt mở trong hôm nay → check-out.
 * Lượt mở từ hôm trước (quên check-out) tự đóng lúc 23:59 ngày đó.
 */
class CheckInRepository(
    private val serverService: KgyMsServerService = KgyMsServerService()
) {
    suspend fun handleScan(raw: String): CheckInResult {
        // QR lạ (link, mã sản phẩm...) thì khỏi gọi server.
        if (!CheckInCode.isCheckInCode(raw)) return CheckInResult.NotCheckInCode

        val json = serverService.checkIn(raw.trim())
        return when (val result = json.optString("result")) {
            "checked_in" -> CheckInResult.CheckedIn(json.optLong("at"), json.optString("packageName"))
            "checked_out" -> CheckInResult.CheckedOut(json.optLong("checkInAt"), json.optLong("checkOutAt"))
            "not_checkin_code" -> CheckInResult.NotCheckInCode
            "invalid_code" -> CheckInResult.InvalidCode
            "staff_not_required" -> CheckInResult.StaffNotRequired
            "no_active_membership" -> CheckInResult.NoActiveMembership
            else -> throw ServerException("bad_response", "Máy chủ trả kết quả điểm danh không hợp lệ ($result).")
        }
    }
}
