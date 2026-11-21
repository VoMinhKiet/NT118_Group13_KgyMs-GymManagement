package com.kgyms.app.viewmodel.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.kgyms.app.data.firebase.isPasswordPolicyViolation
import com.kgyms.app.data.repository.AccountType
import com.kgyms.app.data.repository.AuthRepository
import com.kgyms.app.utils.Constants
import com.kgyms.app.utils.PasswordPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lỗi của luồng đổi/quên mật khẩu; View dịch sang ngôn ngữ người dùng đang chọn. */
enum class PasswordError {
    NotPasswordAccount, NoEmail, Network, TooManyRequests, ResetEmailFailed,
    WeakPassword, WrongCurrentPassword, RecentLoginRequired, SessionExpired, ChangeFailed,
}

sealed interface ChangePasswordEvent {
    data object PasswordChanged : ChangePasswordEvent

    data class ResetEmailSent(val email: String) : ChangePasswordEvent

    /** View mở trang của Google (khôi phục hoặc đổi mật khẩu Google). */
    data class OpenUrl(val url: String) : ChangePasswordEvent

    data class Error(val error: PasswordError) : ChangePasswordEvent
}

data class ChangePasswordUiState(
    val accountType: AccountType = AccountType.NotSignedIn,
    val email: String = "",
    val isLoading: Boolean = false,
    /** Hiển thị popup xác nhận gửi email đặt lại mật khẩu. */
    val showResetConfirm: Boolean = false,
    val event: ChangePasswordEvent? = null,
)

class ChangePasswordViewModel(
    authRepositoryProvider: () -> AuthRepository = { AuthRepository() }
) : ViewModel() {

    private val authRepository: AuthRepository by lazy(authRepositoryProvider)
    private val owner = authRepository.getCurrentUid()
    private fun ownsSession() = owner != null && authRepository.getCurrentUid() == owner

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                accountType = authRepository.getAccountType(),
                email = authRepository.getCurrentEmail().orEmpty(),
            )
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (_uiState.value.isLoading) return
        if (!ownsSession()) {
            emit(ChangePasswordEvent.Error(PasswordError.SessionExpired))
            return
        }

        when (_uiState.value.accountType) {
            AccountType.EmailPassword -> Unit
            else -> {
                emit(ChangePasswordEvent.Error(PasswordError.NotPasswordAccount))
                return
            }
        }

        // Chặn mật khẩu yếu trước khi gọi Firebase (cùng quy tắc với màn Đăng ký).
        if (!PasswordPolicy.isStrong(newPassword)) {
            emit(ChangePasswordEvent.Error(PasswordError.WeakPassword))
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                authRepository.changePassword(currentPassword, newPassword)
                _uiState.update { it.copy(isLoading = false, event = ChangePasswordEvent.PasswordChanged) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, event = ChangePasswordEvent.Error(changeError(exception))) }
            }
        }
    }

    /** Người dùng bấm "Quên mật khẩu?". */
    fun onForgotPasswordClick() {
        if (!ownsSession()) {
            emit(ChangePasswordEvent.Error(PasswordError.SessionExpired))
            return
        }
        when (_uiState.value.accountType) {
            AccountType.Google -> emit(ChangePasswordEvent.OpenUrl(Constants.GOOGLE_PASSWORD_RECOVERY_URL))
            else -> _uiState.update { it.copy(showResetConfirm = true) }
        }
    }

    fun onChangeGooglePasswordClick() {
        emit(ChangePasswordEvent.OpenUrl(Constants.GOOGLE_CHANGE_PASSWORD_URL))
    }

    fun dismissResetConfirm() {
        _uiState.update { it.copy(showResetConfirm = false) }
    }

    /** Gửi email đặt lại mật khẩu, cùng cơ chế với màn Quên mật khẩu ở module auth. */
    fun confirmSendResetEmail() {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(showResetConfirm = false) }

        if (!ownsSession()) {
            emit(ChangePasswordEvent.Error(PasswordError.SessionExpired))
            return
        }
        if (state.email.isBlank()) {
            emit(ChangePasswordEvent.Error(PasswordError.NoEmail))
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordResetEmail(state.email)
                _uiState.update { it.copy(isLoading = false, event = ChangePasswordEvent.ResetEmailSent(state.email)) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val error = when (exception) {
                    is FirebaseNetworkException -> PasswordError.Network
                    is FirebaseTooManyRequestsException -> PasswordError.TooManyRequests
                    else -> PasswordError.ResetEmailFailed
                }
                _uiState.update { it.copy(isLoading = false, event = ChangePasswordEvent.Error(error)) }
            }
        }
    }

    /** View gọi sau khi đã xử lý sự kiện. */
    fun consumeEvent() {
        _uiState.update { it.copy(event = null) }
    }

    private fun emit(event: ChangePasswordEvent) {
        _uiState.update { it.copy(event = event) }
    }

    private fun changeError(exception: Exception): PasswordError = when {
        exception.isPasswordPolicyViolation() -> PasswordError.WeakPassword
        else -> changeErrorByType(exception)
    }

    private fun changeErrorByType(exception: Exception): PasswordError = when (exception) {
        // WeakPassword là lớp con của InvalidCredentials nên phải kiểm tra trước.
        is FirebaseAuthWeakPasswordException -> PasswordError.WeakPassword
        is FirebaseAuthInvalidCredentialsException -> PasswordError.WrongCurrentPassword
        is FirebaseAuthRecentLoginRequiredException -> PasswordError.RecentLoginRequired
        is FirebaseTooManyRequestsException -> PasswordError.TooManyRequests
        is FirebaseNetworkException -> PasswordError.Network
        // FirebaseAuthService ném IllegalStateException khi mất phiên hoặc thiếu email.
        is IllegalStateException -> PasswordError.SessionExpired
        else -> PasswordError.ChangeFailed
    }
}
