
package com.kgyms.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authState =
        MutableStateFlow<AuthState>(AuthState.Idle)

    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var request: Job? = null

    fun login(
        email: String,
        password: String,
        mockEnabled: Boolean = false
    ) {
        if (_authState.value is AuthState.Loading) return

        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error(
                "Email và mật khẩu không được để trống."
            )
            return
        }

        request?.cancel()
        _authState.value = AuthState.Loading

        request = viewModelScope.launch {
            try {
                val user = authRepository.login(
                    email = email.trim(),
                    password = password
                )

                _authState.value = AuthState.Success(user)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _authState.value = AuthState.Error(
                    exception.message
                        ?: "Đăng nhập thất bại. Vui lòng thử lại."
                )
            }
        }
    }

    fun loginWithGoogle(
        idToken: String,
        mockEnabled: Boolean = false
    ) {
        if (_authState.value is AuthState.Loading) return

        request?.cancel()
        _authState.value = AuthState.Loading

        request = viewModelScope.launch {
            try {
                val user = authRepository.loginWithGoogle(idToken)

                _authState.value = AuthState.Success(user)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _authState.value = AuthState.Error(
                    exception.message
                        ?: "Đăng nhập Google thất bại. Vui lòng thử lại."
                )
            }
        }
    }

    fun register(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String,
        mockEnabled: Boolean = false
    ) {
        if (_authState.value is AuthState.Loading) return

        val normalizedName = fullName.trim()
        val normalizedEmail = email.trim()

        when {
            normalizedName.isBlank() -> {
                _authState.value =
                    AuthState.Error("Vui lòng nhập họ tên.")
                return
            }

            normalizedEmail.isBlank() -> {
                _authState.value =
                    AuthState.Error("Vui lòng nhập email.")
                return
            }

            !android.util.Patterns.EMAIL_ADDRESS
                .matcher(normalizedEmail).matches() -> {
                _authState.value =
                    AuthState.Error("Email không đúng định dạng.")
                return
            }

            password.length < 6 -> {
                _authState.value = AuthState.Error(
                    "Mật khẩu phải có ít nhất 6 ký tự."
                )
                return
            }

            password != confirmPassword -> {
                _authState.value = AuthState.Error(
                    "Mật khẩu xác nhận không khớp."
                )
                return
            }
        }

        request?.cancel()
        _authState.value = AuthState.Loading

        request = viewModelScope.launch {
            try {
                val user = authRepository.register(
                    profile = User(
                        fullName = normalizedName,
                        email = normalizedEmail,
                        role = "user"
                    ),
                    password = password
                )

                _authState.value = AuthState.Success(user)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _authState.value = AuthState.Error(
                    exception.message
                        ?: "Đăng ký thất bại. Vui lòng thử lại."
                )
            }
        }
    }

    fun logout() {
        request?.cancel()
        request = null
        authRepository.logout()
        _authState.value = AuthState.Idle
    }

    fun resetState() {
        request?.cancel()
        request = null
        _authState.value = AuthState.Idle
    }
}
