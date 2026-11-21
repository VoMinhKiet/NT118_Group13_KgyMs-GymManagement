package com.kgyms.app.utils

/** Các yêu cầu mà mật khẩu mới (đăng ký / đổi mật khẩu) phải thỏa mãn. */
enum class PasswordRequirement { MinLength, Uppercase, Digit, Special }

/**
 * Quy tắc mật khẩu dùng chung cho cả màn hình (checklist) lẫn ViewModel (chặn trước khi gọi mạng).
 * Khớp với Password Policy đang bật (ENFORCE) trên Firebase Authentication của project,
 * để app không báo "đạt" với mật khẩu mà server sẽ từ chối. Kotlin thuần, chạy được trong unit test JVM.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 6

    /** Danh sách ký tự đặc biệt Firebase chấp nhận (lấy từ API passwordPolicy của project). */
    const val ALLOWED_SPECIAL_CHARACTERS = "^\$*.[]{}()?\"!@#%&/\\,><':;|_~`-"

    fun isMet(requirement: PasswordRequirement, password: String): Boolean = when (requirement) {
        PasswordRequirement.MinLength -> password.length >= MIN_LENGTH
        // Firebase chỉ tính chữ hoa/chữ số ASCII.
        PasswordRequirement.Uppercase -> password.any { it in 'A'..'Z' }
        PasswordRequirement.Digit -> password.any { it in '0'..'9' }
        PasswordRequirement.Special -> password.any { it in ALLOWED_SPECIAL_CHARACTERS }
    }

    fun unmet(password: String): List<PasswordRequirement> =
        PasswordRequirement.entries.filterNot { isMet(it, password) }

    fun isStrong(password: String): Boolean = unmet(password).isEmpty()
}
