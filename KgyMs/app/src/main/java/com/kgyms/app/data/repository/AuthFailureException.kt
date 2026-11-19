package com.kgyms.app.data.repository

/**
 * Lỗi nghiệp vụ của luồng xác thực. Mang mã lỗi (không mang câu chữ) để
 * ViewModel/View tự hiển thị theo ngôn ngữ người dùng đang chọn.
 */
class AuthFailureException(
    val reason: Reason,
    cause: Throwable? = null
) : Exception(reason.name, cause) {

    enum class Reason {
        /** Đã xác thực nhưng chưa có node users/{uid}. */
        ProfileMissing,
        /** Tài khoản bị khóa (status khác "active"). */
        AccountInactive,
        /** Tạo tài khoản/đăng nhập Google xong nhưng không lưu được hồ sơ. */
        ProfileSaveFailed,
    }
}
