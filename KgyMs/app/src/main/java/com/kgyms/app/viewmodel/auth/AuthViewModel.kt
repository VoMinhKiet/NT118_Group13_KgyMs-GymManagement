package com.kgyms.app.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.AuthFailureException
import com.kgyms.app.data.repository.AuthRepository
import com.kgyms.app.utils.isValidEmail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Mã lỗi đăng nhập/đăng ký; View dịch sang ngôn ngữ người dùng đang chọn. */
enum class AuthError {
    // Lỗi nhập liệu
    EmptyCredentials, NameRequired, EmailRequired, InvalidEmail,
    PasswordTooShort, PasswordMismatch, GoogleTokenMissing,

    // Lỗi từ Firebase / hồ sơ
    WrongCredentials, AccountNotFound, EmailAlreadyInUse, WeakPassword,
    AccountDisabled, AccountInactive, ProfileMissing, ProfileSaveFailed,
    TooManyRequests, Network,

    // Lỗi chung theo từng thao tác
    LoginFailed, GoogleLoginFailed, RegisterFailed,
}

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val error: AuthError) : AuthState()
}

class AuthViewModel(
    authRepositoryProvider: () -> AuthRepository = { AuthRepository() }
) : ViewModel() {

    // Chỉ khởi tạo Firebase khi thực sự cần gọi mạng (giúp kiểm thử phần validate không cần Firebase).
    private val authRepository: AuthRepository by lazy(authRepositoryProvider)

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
            _authState.value = AuthState.Error(AuthError.EmptyCredentials)
            return
        }

        launchRequest(fallback = AuthError.LoginFailed) {
            authRepository.login(
                email = email.trim(),
                password = password
            )
        }
    }

    fun loginWithGoogle(
        idToken: String,
        mockEnabled: Boolean = false
    ) {
        if (_authState.value is AuthState.Loading) return

        if (idToken.isBlank()) {
            _authState.value = AuthState.Error(AuthError.GoogleTokenMissing)
            return
        }

        launchRequest(fallback = AuthError.GoogleLoginFailed) {
            authRepository.loginWithGoogle(idToken)
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

        val validationError = when {
            normalizedName.isBlank() -> AuthError.NameRequired
            normalizedEmail.isBlank() -> AuthError.EmailRequired
            !isValidEmail(normalizedEmail) -> AuthError.InvalidEmail
            password.length < 6 -> AuthError.PasswordTooShort
            password != confirmPassword -> AuthError.PasswordMismatch
            else -> null
        }
        if (validationError != null) {
            _authState.value = AuthState.Error(validationError)
            return
        }

        launchRequest(fallback = AuthError.RegisterFailed) {
            authRepository.register(
                profile = User(
                    fullName = normalizedName,
                    email = normalizedEmail,
                    role = "user"
                ),
                password = password
            )
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

    private fun launchRequest(fallback: AuthError, block: suspend () -> User) {
        request?.cancel()
        _authState.value = AuthState.Loading

        request = viewModelScope.launch {
            try {
                _authState.value = AuthState.Success(block())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _authState.value = AuthState.Error(exception.toAuthError(fallback))
            }
        }
    }

    private fun Exception.toAuthError(fallback: AuthError): AuthError = when (this) {
        is AuthFailureException -> when (reason) {
            AuthFailureException.Reason.ProfileMissing -> AuthError.ProfileMissing
            AuthFailureException.Reason.AccountInactive -> AuthError.AccountInactive
            AuthFailureException.Reason.ProfileSaveFailed -> AuthError.ProfileSaveFailed
        }
        is FirebaseNetworkException -> AuthError.Network
        is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
        is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse
        // WeakPassword là lớp con của InvalidCredentials nên phải kiểm tra trước.
        is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AuthError.InvalidEmail else AuthError.WrongCredentials
        is FirebaseAuthInvalidUserException ->
            if (errorCode == "ERROR_USER_DISABLED") AuthError.AccountDisabled else AuthError.AccountNotFound
        else -> fallback
    }
}
