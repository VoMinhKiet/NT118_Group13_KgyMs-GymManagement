
package com.kgyms.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

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

        require(password.length >= 6) {
            "Mật khẩu phải có ít nhất 6 ký tự."
        }

        return firebaseAuth
            .createUserWithEmailAndPassword(normalizedEmail, password)
            .await()
            .user ?: throw IllegalStateException(
            "Không lấy được thông tin tài khoản sau khi đăng ký."
        )
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
        user.updatePassword(newPassword).await()
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
