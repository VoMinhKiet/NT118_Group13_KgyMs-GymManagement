package com.kgyms.app.data.qr

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mã QR điểm danh xoay vòng mỗi [PERIOD_SECONDS] giây (kiểu TOTP).
 *
 * Nội dung mã: "KGYMS-CHECKIN:<window>:<sig>"
 * - window = epochSeconds / 30
 * - sig    = 8 ký tự hex đầu của HMAC-SHA256(key = secret, message = window dạng chuỗi)
 *
 * Phải khớp với tool/checkin_qr.html. secret lưu ở gymInfo/checkInSecret.
 */
object CheckInCode {

    const val PREFIX = "KGYMS-CHECKIN"
    const val PERIOD_SECONDS = 30L

    /** Chấp nhận lệch ±1 chu kỳ để bù giờ giữa điện thoại và máy ở quầy. */
    private const val ALLOWED_DRIFT = 1L
    private const val SIGNATURE_LENGTH = 8

    fun isCheckInCode(raw: String): Boolean = raw.trim().startsWith("$PREFIX:")

    fun window(nowMillis: Long): Long = nowMillis / 1000 / PERIOD_SECONDS

    fun build(secret: String, window: Long): String = "$PREFIX:$window:${sign(secret, window)}"

    fun verify(raw: String, secret: String, nowMillis: Long): Boolean {
        val parts = raw.trim().split(':')
        if (parts.size != 3 || parts[0] != PREFIX || secret.isBlank()) return false
        val window = parts[1].toLongOrNull() ?: return false
        if (kotlin.math.abs(window - window(nowMillis)) > ALLOWED_DRIFT) return false
        return MessageDigest.isEqual(
            sign(secret, window).toByteArray(Charsets.UTF_8),
            parts[2].lowercase().toByteArray(Charsets.UTF_8)
        )
    }

    private fun sign(secret: String, window: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(window.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            .take(SIGNATURE_LENGTH)
    }
}
