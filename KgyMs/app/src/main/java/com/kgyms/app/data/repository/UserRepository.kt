package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.FirebaseAuthService
import com.kgyms.app.data.firebase.FirestoreService
import com.kgyms.app.data.model.User

/**
 * Nguồn dữ liệu hồ sơ người dùng (node users/{uid} trên Firebase Realtime Database).
 */
class UserRepository(
    private val authService: FirebaseAuthService = FirebaseAuthService(),
    private val userService: FirestoreService = FirestoreService()
) {
    fun getCurrentUid(): String? = authService.getCurrentUid()

    /** Email đăng nhập hiện tại trong Firebase Auth. */
    fun getCurrentAuthEmail(): String? = authService.currentUser?.email

    suspend fun getCurrentUser(): User? {
        val uid = authService.getCurrentUid() ?: return null
        return userService.getUser(uid)
    }

    suspend fun updatePersonalInfo(
        fullName: String,
        phone: String,
        birthday: String
    ) {
        userService.updatePersonalInfo(
            uid = requireUid(),
            fullName = fullName,
            phone = phone,
            birthday = birthday
        )
    }

    /** Gửi email xác nhận đổi email; email chỉ được cập nhật sau khi xác nhận. */
    suspend fun requestEmailChange(newEmail: String) {
        requireUid()
        authService.requestEmailChange(newEmail)
    }

    /**
     * Lưu giới tính/tuổi chọn ở luồng tạo tài khoản.
     * Chiều cao/cân nặng là dữ liệu nhạy cảm nên không lưu lên Firebase.
     */
    suspend fun saveOnboarding(
        gender: String,
        age: Int
    ) {
        userService.updateOnboarding(
            uid = requireUid(),
            gender = gender,
            age = age
        )
    }

    private fun requireUid(): String =
        authService.getCurrentUid()
            ?: throw IllegalStateException(
                "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
            )
}
