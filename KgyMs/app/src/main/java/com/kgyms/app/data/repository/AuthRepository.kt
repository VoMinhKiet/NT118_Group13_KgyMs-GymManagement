
package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.FirebaseAuthService
import com.kgyms.app.data.firebase.FirestoreService
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.User
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AccountType { NotSignedIn, EmailPassword, Google, Other }

class AuthRepository(
    private val authService: FirebaseAuthService = FirebaseAuthService(),
    private val userService: FirestoreService = FirestoreService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    suspend fun login(
        email: String,
        password: String
    ): User {
        val firebaseUser = authService.login(email, password)
        val profile = userService.getUser(firebaseUser.uid)

        if (profile == null) {
            authService.logout()
            throw AuthFailureException(AuthFailureException.Reason.ProfileMissing)
        }

        if (!profile.isActive) {
            authService.logout()
            throw AuthFailureException(AuthFailureException.Reason.AccountInactive)
        }

        removeLegacySensitiveData(firebaseUser.uid)

        if (profile.isAdmin) {
            activityLogRepository.log(ActivityLogAction.ACCOUNT, "đăng nhập bằng email")
        }

        // Nếu người dùng đã xác nhận đổi email, email trong Auth sẽ khác hồ sơ:
        // đồng bộ lại để node users/{uid} luôn đúng.
        val authEmail = firebaseUser.email.orEmpty()
        if (authEmail.isNotBlank() && !authEmail.equals(profile.email, ignoreCase = true)) {
            try {
                userService.updateEmail(firebaseUser.uid, authEmail)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // Không chặn đăng nhập nếu đồng bộ email thất bại; lần sau sẽ thử lại.
            }
        }

        return profile.copy(
            uid = firebaseUser.uid,
            email = authEmail.ifBlank { profile.email }
        )
    }

    suspend fun loginWithGoogle(idToken: String): User {
        val firebaseUser = authService.loginWithGoogleIdToken(idToken)

        val existingProfile = userService.getUser(firebaseUser.uid)

        if (existingProfile != null) removeLegacySensitiveData(firebaseUser.uid)

        val profile = if (existingProfile != null) {
            // Giữ nguyên role và status của hồ sơ đã tồn tại.
            existingProfile.copy(
                uid = firebaseUser.uid,
                fullName = existingProfile.fullName.ifBlank {
                    firebaseUser.displayName.orEmpty()
                },
                email = existingProfile.email.ifBlank {
                    firebaseUser.email.orEmpty()
                }
            )
        } else {
            // Hồ sơ chưa tồn tại: chỉ tạo hội viên thông thường.
            val newProfile = User(
                uid = firebaseUser.uid,
                fullName = firebaseUser.displayName.orEmpty()
                    .ifBlank {
                        firebaseUser.email
                            ?.substringBefore("@")
                            .orEmpty()
                    },
                email = firebaseUser.email.orEmpty(),
                role = "user",
                status = "active",
                avatarUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                createdAt = currentDate()
            )

            try {
                userService.saveUser(
                    uid = firebaseUser.uid,
                    user = newProfile
                )
            } catch (exception: Exception) {
                authService.logout()
                throw AuthFailureException(AuthFailureException.Reason.ProfileSaveFailed, exception)
            }

            activityLogRepository.log(
                ActivityLogAction.ACCOUNT,
                "tạo tài khoản mới bằng Google",
                actorName = newProfile.fullName
            )

            newProfile
        }

        if (!profile.isActive) {
            authService.logout()
            throw AuthFailureException(AuthFailureException.Reason.AccountInactive)
        }

        if (existingProfile != null && profile.isAdmin) {
            activityLogRepository.log(ActivityLogAction.ACCOUNT, "đăng nhập bằng Google")
        }

        return profile
    }

    suspend fun register(
        profile: User,
        password: String
    ): User {
        require(profile.fullName.isNotBlank()) {
            "Vui lòng nhập họ tên."
        }

        require(profile.email.isNotBlank()) {
            "Vui lòng nhập email."
        }

        require(password.length >= 6) {
            "Mật khẩu phải có ít nhất 6 ký tự."
        }

        val firebaseUser = authService.register(
            email = profile.email,
            password = password
        )

        val newProfile = profile.copy(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: profile.email.trim(),
            role = "user",
            status = "active",
            createdAt = currentDate()
        )

        try {
            userService.saveUser(
                uid = firebaseUser.uid,
                user = newProfile
            )
        } catch (exception: Exception) {
            authService.logout()
            throw AuthFailureException(AuthFailureException.Reason.ProfileSaveFailed, exception)
        }

        activityLogRepository.log(
            ActivityLogAction.ACCOUNT,
            "tạo tài khoản mới bằng email",
            actorName = newProfile.fullName
        )

        return newProfile
    }

    /** Loại tài khoản đang đăng nhập; quyết định luồng đổi/quên mật khẩu. */
    fun getAccountType(): AccountType {
        if (authService.getCurrentUid() == null) return AccountType.NotSignedIn
        val providers = authService.getSignInProviders()
        return when {
            "password" in providers -> AccountType.EmailPassword
            "google.com" in providers -> AccountType.Google
            else -> AccountType.Other
        }
    }

    fun getCurrentEmail(): String? = authService.currentUser?.email

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        authService.changePassword(currentPassword, newPassword)
        activityLogRepository.log(ActivityLogAction.ACCOUNT, "đổi mật khẩu")
    }

    suspend fun sendPasswordResetEmail(email: String) {
        authService.sendPasswordResetEmail(email)
    }

    fun logout() {
        authService.logout()
    }

    /**
     * Đăng xuất chủ động từ app: nếu là admin thì ghi log "đăng xuất" trước
     * (phải ghi khi còn đăng nhập, vì rules yêu cầu actorUid == auth.uid).
     */
    suspend fun logoutWithActivityLog() {
        val profile = try {
            getCurrentProfile()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

        if (profile?.isAdmin == true) {
            activityLogRepository.log(ActivityLogAction.ACCOUNT, "đăng xuất")
        }

        authService.logout()
    }

    fun isLoggedIn(): Boolean {
        return authService.isLoggedIn()
    }

    fun getCurrentUid(): String? {
        return authService.getCurrentUid()
    }

    suspend fun getCurrentProfile(): User? {
        val uid = authService.getCurrentUid() ?: return null
        return userService.getUser(uid)
    }

    /** Xóa chiều cao/cân nặng từng lưu của tài khoản cũ; không chặn đăng nhập nếu thất bại. */
    private suspend fun removeLegacySensitiveData(uid: String) {
        try {
            userService.removeSensitiveFields(uid)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Lần đăng nhập sau sẽ thử lại.
        }
    }

    private fun currentDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())
    }
}
