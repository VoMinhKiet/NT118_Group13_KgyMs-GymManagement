
package com.kgyms.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.kgyms.app.utils.PasswordPolicy
import kotlinx.coroutines.tasks.await

/** Lỗi Firebase trả về khi mật khẩu không đạt Password Policy đang bật trên server. */
fun Throwable.isPasswordPolicyViolation(): Boolean {
    val code = (this as? FirebaseAuthException)?.errorCode.orEmpty()
    return PASSWORD_POLICY_ERROR in code || PASSWORD_POLICY_ERROR in message.orEmpty()
}

private const val PASSWORD_POLICY_ERROR = "PASSWORD_DOES_NOT_MEET_REQUIREMENTS"

class FirebaseAuthService(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    suspend fun login(
        email: String,
        password: String
    ): FirebaseUser {
        val normalizedEmail = email.trim()

        require(normalizedEmail.isNotBlank()) {
            "Vui lòng nhập email."
        }

        require(password.isNotBlank()) {
            "Vui lòng nhập mật khẩu."
        }

        return firebaseAuth
            .signInWithEmailAndPassword(normalizedEmail, password)
            .await()
            .user ?: throw IllegalStateException(
            "Không lấy được thông tin tài khoản sau khi đăng nhập."
        )
    }

    suspend fun register(
        email: String,
        password: String
    ): FirebaseUser {
        val normalizedEmail = email.trim()

        require(normalizedEmail.isNotBlank()) {
            "Vui lòng nhập email."
        }

        require(PasswordPolicy.isStrong(password)) {
            "Mật khẩu chưa đáp ứng đủ yêu cầu."
        }

        return firebaseAuth
            .createUserWithEmailAndPassword(normalizedEmail, password)
            .await()
            .user ?: throw IllegalStateException(
            "Không lấy được thông tin tài khoản sau khi đăng ký."
        )
    }

    /** Lưu họ tên vào tài khoản Auth để dùng khi tạo hồ sơ sau bước xác nhận email. */
    suspend fun updateDisplayName(user: FirebaseUser, displayName: String) {
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        ).await()
    }

    /** Gửi email chứa link xác nhận địa chỉ email tới tài khoản đang đăng nhập. */
    suspend fun sendEmailVerification(languageCode: String) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        firebaseAuth.setLanguageCode(languageCode)
        user.sendEmailVerification().await()
    }

    /**
     * Tải lại tài khoản hiện tại từ server để đọc trạng thái xác nhận email mới nhất
     * (người dùng bấm link trong trình duyệt, app không tự biết).
     */
    suspend fun reloadCurrentUser(): FirebaseUser? {
        val user = firebaseAuth.currentUser ?: return null
        user.reload().await()
        val refreshed = firebaseAuth.currentUser ?: return null
        if (refreshed.uid != user.uid) return null
        // Làm mới token để claim email_verified cũng được cập nhật.
        if (refreshed.isEmailVerified) refreshed.getIdToken(true).await()
        return refreshed
    }

    /** Xóa tài khoản Auth đang đăng nhập (chỉ dùng cho tài khoản đăng ký dở, chưa có hồ sơ). */
    suspend fun deleteCurrentUser() {
        firebaseAuth.currentUser?.delete()?.await()
    }

    // Google ID token -> Firebase credential -> Firebase user.
    suspend fun loginWithGoogleIdToken(
        idToken: String
    ): FirebaseUser {
        require(idToken.isNotBlank()) {
            "Google ID token không hợp lệ."
        }

        val credential = GoogleAuthProvider.getCredential(
            idToken,
            null
        )

        return firebaseAuth
            .signInWithCredential(credential)
            .await()
            .user ?: throw IllegalStateException(
            "Không lấy được tài khoản sau khi xác thực Google."
        )
    }

    // Gửi email đặt lại mật khẩu.
    suspend fun sendPasswordResetEmail(email: String) {
        val normalizedEmail = email.trim()

        require(normalizedEmail.isNotBlank()) {
            "Vui lòng nhập email."
        }

        require(
            android.util.Patterns.EMAIL_ADDRESS
                .matcher(normalizedEmail)
                .matches()
        ) {
            "Email không đúng định dạng."
        }

        firebaseAuth
            .sendPasswordResetEmail(normalizedEmail)
            .await()
    }

    /**
     * Gửi email xác nhận tới địa chỉ mới. Email đăng nhập chỉ đổi sau khi
     * người dùng bấm vào liên kết trong email đó.
     */
    suspend fun requestEmailChange(newEmail: String) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")

        user.verifyBeforeUpdateEmail(newEmail.trim()).await()
    }

    /** Danh sách phương thức đăng nhập của tài khoản hiện tại, ví dụ "password", "google.com". */
    fun getSignInProviders(): List<String> =
        firebaseAuth.currentUser?.providerData?.map { it.providerId }.orEmpty()

    /**
     * Đổi mật khẩu cho tài khoản email/password: xác thực lại bằng mật khẩu hiện tại
     * (Firebase yêu cầu đăng nhập gần đây) rồi cập nhật mật khẩu mới.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        val email = user.email
            ?: throw IllegalStateException("Tài khoản không có email để xác thực.")

        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential).await()
        check(firebaseAuth.currentUser?.uid == user.uid) { "Account changed" }
        user.updatePassword(newPassword).await()
        check(firebaseAuth.currentUser?.uid == user.uid) { "Account changed" }
    }

    fun logout() {
        firebaseAuth.signOut()
    }

    fun isLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    fun getCurrentUid(): String? {
        return firebaseAuth.currentUser?.uid
    }
}
