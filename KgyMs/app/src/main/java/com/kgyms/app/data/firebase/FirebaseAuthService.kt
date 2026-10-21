
package com.kgyms.app.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
