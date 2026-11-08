package com.kgyms.app.ui.member.profile

import com.kgyms.app.viewmodel.profile.EmailChangeError
import com.kgyms.app.viewmodel.profile.PasswordError
import com.kgyms.app.viewmodel.profile.ProfileSaveError

/*
 * Dịch mã lỗi từ ViewModel sang ngôn ngữ người dùng đang chọn ("vi" / "en").
 * Không phải @Composable để có thể gọi trong LaunchedEffect; lấy ngôn ngữ từ
 * LocalProfileLanguage.current rồi truyền vào.
 */

private fun pick(language: String, vi: String, en: String) = if (language == "vi") vi else en

internal fun ProfileSaveError.message(language: String): String = when (this) {
    ProfileSaveError.Network -> pick(language,
        "Không có kết nối mạng. Vui lòng thử lại.",
        "No network connection. Please try again.")
    ProfileSaveError.SessionExpired -> pick(language,
        "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "Your session has expired. Please sign in again.")
    ProfileSaveError.Failed -> pick(language,
        "Không thể lưu hồ sơ. Vui lòng kiểm tra kết nối và thử lại.",
        "Could not save your profile. Please check your connection and try again.")
}

internal fun EmailChangeError.message(language: String): String = when (this) {
    EmailChangeError.RecentLoginRequired -> pick(language,
        "Để đổi email, vui lòng đăng xuất và đăng nhập lại rồi thử lại.",
        "To change your email, please sign out, sign in again and retry.")
    EmailChangeError.AlreadyInUse -> pick(language,
        "Email này đã được tài khoản khác sử dụng.",
        "This email is already used by another account.")
    EmailChangeError.Network -> pick(language,
        "Không có kết nối mạng nên chưa đổi được email.",
        "No network connection, so your email was not changed.")
    EmailChangeError.Failed -> pick(language,
        "Không thể đổi email lúc này.",
        "Could not change your email right now.")
}

internal fun PasswordError.message(language: String): String = when (this) {
    PasswordError.NotPasswordAccount -> pick(language,
        "Tài khoản này không đăng nhập bằng mật khẩu nên không thể đổi mật khẩu tại đây.",
        "This account does not sign in with a password, so it cannot be changed here.")
    PasswordError.NoEmail -> pick(language,
        "Tài khoản không có email để gửi liên kết đặt lại mật khẩu.",
        "This account has no email to send a password reset link to.")
    PasswordError.Network -> pick(language,
        "Không có kết nối mạng. Vui lòng thử lại.",
        "No network connection. Please try again.")
    PasswordError.TooManyRequests -> pick(language,
        "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.",
        "Too many attempts. Please try again later.")
    PasswordError.ResetEmailFailed -> pick(language,
        "Không thể gửi email lúc này. Vui lòng thử lại.",
        "Could not send the email right now. Please try again.")
    PasswordError.WeakPassword -> pick(language,
        "Mật khẩu mới quá yếu. Vui lòng chọn mật khẩu mạnh hơn.",
        "The new password is too weak. Please choose a stronger one.")
    PasswordError.WrongCurrentPassword -> pick(language,
        "Mật khẩu hiện tại không đúng.",
        "Your current password is incorrect.")
    PasswordError.RecentLoginRequired -> pick(language,
        "Vui lòng đăng xuất và đăng nhập lại rồi thử đổi mật khẩu.",
        "Please sign out, sign in again and retry changing your password.")
    PasswordError.SessionExpired -> pick(language,
        "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "Your session has expired. Please sign in again.")
    PasswordError.ChangeFailed -> pick(language,
        "Không thể đổi mật khẩu lúc này. Vui lòng thử lại.",
        "Could not change your password right now. Please try again.")
}
