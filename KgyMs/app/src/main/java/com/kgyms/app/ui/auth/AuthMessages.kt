package com.kgyms.app.ui.auth

import com.kgyms.app.ui.localization.pickText
import com.kgyms.app.viewmodel.auth.AuthError
import com.kgyms.app.viewmodel.auth.ForgotPasswordError
import com.kgyms.app.viewmodel.auth.OnboardingError

/*
 * Dịch mã lỗi từ các ViewModel auth sang ngôn ngữ đang chọn ("en" / "vi").
 * Không phải @Composable để gọi được trong LaunchedEffect.
 */

internal fun AuthError.message(language: String): String {
    fun t(vi: String, en: String) = pickText(language, vi, en)
    return when (this) {
        AuthError.EmptyCredentials -> t("Email và mật khẩu không được để trống.", "Email and password cannot be empty.")
        AuthError.NameRequired -> t("Vui lòng nhập họ tên.", "Please enter your full name.")
        AuthError.EmailRequired -> t("Vui lòng nhập email.", "Please enter your email.")
        AuthError.InvalidEmail -> t("Email không đúng định dạng.", "Please enter a valid email address.")
        AuthError.PasswordTooShort -> t("Mật khẩu phải có ít nhất 6 ký tự.", "Password must be at least 6 characters.")
        AuthError.PasswordMismatch -> t("Mật khẩu xác nhận không khớp.", "Passwords do not match.")
        AuthError.GoogleTokenMissing -> t("Không lấy được thông tin xác thực Google. Vui lòng thử lại.", "Could not get Google sign-in credentials. Please try again.")
        AuthError.WrongCredentials -> t("Email hoặc mật khẩu không đúng.", "Incorrect email or password.")
        AuthError.AccountNotFound -> t("Không tìm thấy tài khoản với email này.", "No account found with this email.")
        AuthError.EmailAlreadyInUse -> t("Email này đã được sử dụng cho tài khoản khác.", "This email is already in use by another account.")
        AuthError.WeakPassword -> t("Mật khẩu quá yếu. Vui lòng chọn mật khẩu mạnh hơn.", "Password is too weak. Please choose a stronger one.")
        AuthError.AccountDisabled -> t("Tài khoản đã bị vô hiệu hóa.", "This account has been disabled.")
        AuthError.AccountInactive -> t("Tài khoản hiện không hoạt động. Vui lòng liên hệ quản trị viên.", "This account is inactive. Please contact an administrator.")
        AuthError.ProfileMissing -> t("Tài khoản chưa có hồ sơ trên hệ thống. Vui lòng liên hệ quản trị viên.", "This account has no profile in the system. Please contact an administrator.")
        AuthError.ProfileSaveFailed -> t("Không thể lưu hồ sơ. Vui lòng kiểm tra kết nối và thử lại.", "Could not save your profile. Please check your connection and try again.")
        AuthError.TooManyRequests -> t("Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.", "Too many attempts. Please try again later.")
        AuthError.Network -> t("Không có kết nối mạng. Vui lòng thử lại.", "No network connection. Please try again.")
        AuthError.LoginFailed -> t("Đăng nhập thất bại. Vui lòng thử lại.", "Sign in failed. Please try again.")
        AuthError.GoogleLoginFailed -> t("Đăng nhập Google thất bại. Vui lòng thử lại.", "Google sign-in failed. Please try again.")
        AuthError.RegisterFailed -> t("Đăng ký thất bại. Vui lòng thử lại.", "Sign up failed. Please try again.")
    }
}

internal fun ForgotPasswordError.message(language: String): String = when (this) {
    ForgotPasswordError.InvalidEmail -> pickText(language, "Vui lòng nhập địa chỉ email hợp lệ.", "Please enter a valid email address.")
    ForgotPasswordError.Network -> pickText(language, "Không có kết nối mạng. Vui lòng thử lại.", "No network connection. Please try again.")
    ForgotPasswordError.TooManyRequests -> pickText(language, "Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau.", "Too many requests. Please try again later.")
    ForgotPasswordError.Failed -> pickText(language, "Không thể gửi email lúc này. Vui lòng thử lại.", "Could not send the email right now. Please try again.")
}

internal fun OnboardingError.message(language: String): String = when (this) {
    OnboardingError.InvalidData -> pickText(language, "Vui lòng kiểm tra lại thông tin hồ sơ.", "Please check your profile information.")
    OnboardingError.SaveFailed -> pickText(language, "Không thể lưu hồ sơ. Vui lòng kiểm tra kết nối và thử lại.", "Could not save your profile. Please check your connection and try again.")
}
