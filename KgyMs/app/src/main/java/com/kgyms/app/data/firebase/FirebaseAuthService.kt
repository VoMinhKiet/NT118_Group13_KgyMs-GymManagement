
package com.kgyms.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
