
package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.FirebaseAuthService
import com.kgyms.app.data.firebase.FirestoreService
import com.kgyms.app.data.firebase.OnboardingMeasurements
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.User
import com.google.firebase.auth.FirebaseUser
import com.kgyms.app.utils.PasswordPolicy
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AccountType { NotSignedIn, EmailPassword, Google, Other }

/** Kết quả đăng nhập/đăng ký bằng email. */
sealed interface AuthOutcome {
    data class SignedIn(val user: User) : AuthOutcome

    /**
     * Email chưa được xác nhận: phải bấm link trong email trước khi vào app.
     * [emailSent]: true/false = vừa gửi email thành công/thất bại; null = lần này không gửi email.
     */
    data class VerificationRequired(val email: String, val emailSent: Boolean?) : AuthOutcome
}

class AuthRepository(
    private val authService: FirebaseAuthService = FirebaseAuthService(),
    private val userService: FirestoreService = FirestoreService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    suspend fun login(
        email: String,
        password: String
    ): AuthOutcome {
        val firebaseUser = authService.login(email, password)
        val profile = userService.getUser(firebaseUser.uid)

        if (profile == null) {
            // Tài khoản email chưa có hồ sơ = đăng ký theo luồng mới nhưng chưa hoàn tất xác nhận.
            // Giữ phiên đăng nhập để màn xác nhận có thể gửi lại email / kiểm tra trạng thái.
            if (!firebaseUser.isEmailVerified) {
                return AuthOutcome.VerificationRequired(
                    email = firebaseUser.email ?: email.trim(),
                    emailSent = null
                )
            }
            return AuthOutcome.SignedIn(createVerifiedEmailProfile(firebaseUser))
        }

        if (!profile.isActive) {
            authService.logout()
            throw AuthFailureException(AuthFailureException.Reason.AccountInactive)
        }

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

        return AuthOutcome.SignedIn(
            profile.copy(
                uid = firebaseUser.uid,
                email = authEmail.ifBlank { profile.email }
            )
        )
    }

    suspend fun loginWithGoogle(idToken: String): User {
        val firebaseUser = authService.loginWithGoogleIdToken(idToken)

        val existingProfile = userService.getUser(firebaseUser.uid)

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

    /**
     * Đăng ký bằng email: chỉ tạo tài khoản Auth và gửi link xác nhận.
     * Hồ sơ users/{uid} chỉ được tạo sau khi email đã được xác nhận
     * (xem [confirmEmailVerified]), nên không có "tài khoản ma" trong hệ thống.
     */
    suspend fun register(
        fullName: String,
        email: String,
        password: String,
        languageCode: String
    ): AuthOutcome.VerificationRequired {
        val normalizedName = fullName.trim()
        require(normalizedName.isNotBlank()) {
            "Vui lòng nhập họ tên."
        }

        require(email.isNotBlank()) {
            "Vui lòng nhập email."
        }

        require(PasswordPolicy.isStrong(password)) {
            "Mật khẩu chưa đáp ứng đủ yêu cầu."
        }

        val firebaseUser = authService.register(
            email = email,
            password = password
        )

        try {
            authService.updateDisplayName(firebaseUser, normalizedName)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Không chặn đăng ký: khi tạo hồ sơ sẽ dùng phần trước @ của email làm tên tạm.
        }

        val emailSent = trySendVerificationEmail(languageCode)

        return AuthOutcome.VerificationRequired(
            email = firebaseUser.email ?: email.trim(),
            emailSent = emailSent
        )
    }

    /** Gửi lại email xác nhận cho tài khoản đang chờ xác nhận. */
    suspend fun resendVerificationEmail(languageCode: String) {
        authService.sendEmailVerification(languageCode)
    }

    /**
     * Kiểm tra lại trạng thái xác nhận email. Trả về null nếu người dùng chưa bấm link;
     * nếu đã xác nhận thì tạo hồ sơ (khi chưa có) và trả về hồ sơ đó.
     */
    suspend fun confirmEmailVerified(): User? {
        val firebaseUser = authService.reloadCurrentUser()
            ?: throw IllegalStateException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        if (!firebaseUser.isEmailVerified) return null

        val existing = userService.getUser(firebaseUser.uid)
        val profile = existing?.copy(uid = firebaseUser.uid) ?: createVerifiedEmailProfile(firebaseUser)
        check(authService.getCurrentUid() == firebaseUser.uid) { "Account changed" }

        if (!profile.isActive) {
            authService.logout()
            throw AuthFailureException(AuthFailureException.Reason.AccountInactive)
        }
        return profile
    }

    /**
     * Tài khoản đang đăng nhập là tài khoản email/password (có thể đang chờ xác nhận email).
     * Dùng khi mở lại app mà tài khoản chưa có hồ sơ.
     */
    fun isPendingEmailRegistration(): Boolean =
        authService.getCurrentUid() != null && "password" in authService.getSignInProviders()

    /**
     * Hủy đăng ký dở (ví dụ gõ nhầm email): xóa tài khoản Auth chưa có hồ sơ để email đó
     * có thể đăng ký lại, rồi đăng xuất. Không bao giờ xóa tài khoản đã có hồ sơ.
     */
    suspend fun cancelPendingRegistration() {
        val uid = authService.getCurrentUid() ?: return
        try {
            if ("password" in authService.getSignInProviders() && userService.getUser(uid) == null) {
                authService.deleteCurrentUser()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Không xóa được (mất mạng, phiên quá cũ...) thì vẫn đăng xuất bình thường.
        } finally {
            authService.logout()
        }
    }

    private suspend fun trySendVerificationEmail(languageCode: String): Boolean = try {
        authService.sendEmailVerification(languageCode)
        true
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        false
    }

    /** Tạo hồ sơ hội viên cho tài khoản email vừa xác nhận xong. */
    private suspend fun createVerifiedEmailProfile(firebaseUser: FirebaseUser): User {
        val email = firebaseUser.email.orEmpty()
        val newProfile = User(
            uid = firebaseUser.uid,
            fullName = firebaseUser.displayName.orEmpty().trim()
                .ifBlank { email.substringBefore("@") },
            email = email,
            role = "user",
            status = "active",
            createdAt = currentDate()
        )

        try {
            userService.saveUser(
                uid = firebaseUser.uid,
                user = newProfile
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
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
        require(PasswordPolicy.isStrong(newPassword)) {
            "Mật khẩu chưa đáp ứng đủ yêu cầu."
        }
        authService.changePassword(currentPassword, newPassword)
        activityLogRepository.log(ActivityLogAction.ACCOUNT, "đổi mật khẩu")
    }

    suspend fun completeOnboarding(
        gender: String,
        age: Int,
        weight: Int,
        weightUnit: String,
        height: Int,
        expectedUid: String? = null,
    ) {
        require(gender.isNotBlank() && age in 10..100 && height in 100..250)
        require((weightUnit == "KG" && weight in 30..200) || (weightUnit == "LB" && weight in 66..440))
        val uid = authService.getCurrentUid()
            ?: throw IllegalStateException(
                "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
            )

        check(expectedUid == null || uid == expectedUid) { "Account changed" }
        userService.updateOnboarding(
            uid = uid,
            gender = gender,
            age = age,
            measurements = OnboardingMeasurements(weight, weightUnit, height),
            isCurrentOwner = { authService.getCurrentUid() == uid },
        )
        check(authService.getCurrentUid() == uid) { "Account changed" }
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
        val profile = userService.getUser(uid)
        check(authService.getCurrentUid() == uid) { "Account changed" }
        return profile
    }

    private fun currentDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())
    }
}
