package com.kgyms.app.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.AuthFailureException
import com.kgyms.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lỗi của màn xác nhận email; View dịch sang ngôn ngữ đang chọn. */
enum class VerifyEmailError { Network, TooManyRequests, SessionExpired, AccountInactive, ProfileSaveFailed, Failed }

sealed interface VerifyEmailEvent {
    /** Email đã xác nhận và hồ sơ đã sẵn sàng. */
    data class Verified(val user: User) : VerifyEmailEvent

    /** Người dùng bấm kiểm tra nhưng chưa mở link trong email. */
    data object NotVerifiedYet : VerifyEmailEvent

    data object EmailResent : VerifyEmailEvent

    /** Lần gửi email ngay sau khi đăng ký bị lỗi: nhắc người dùng bấm "Gửi lại". */
    data object InitialSendFailed : VerifyEmailEvent

    /** Đã thoát khỏi tài khoản đang chờ xác nhận (đổi email hoặc quay lại đăng nhập). */
    data object SignedOut : VerifyEmailEvent

    data class Error(val error: VerifyEmailError) : VerifyEmailEvent
}

data class VerifyEmailUiState(
    val email: String = "",
    val isChecking: Boolean = false,
    val isResending: Boolean = false,
    val isLeaving: Boolean = false,
    /** Số giây còn lại trước khi được gửi lại email. */
    val resendCooldown: Int = 0,
    val event: VerifyEmailEvent? = null,
) {
    val isBusy: Boolean get() = isChecking || isResending || isLeaving
}

class VerifyEmailViewModel(
    authRepositoryProvider: () -> AuthRepository = { AuthRepository() }
) : ViewModel() {

    private val authRepository: AuthRepository by lazy(authRepositoryProvider)

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    private var started = false
    private var cooldownJob: Job? = null

    /**
     * Gọi một lần khi màn hình mở. [emailSent]: true = vừa gửi email (bắt đầu đếm ngược gửi lại),
     * false = gửi thất bại (nhắc bấm gửi lại), null = lần này không gửi email (đăng nhập / mở lại app).
     */
    fun start(email: String, emailSent: Boolean?) {
        if (started) return
        started = true
        _uiState.update {
            it.copy(email = email.ifBlank { authRepository.getCurrentEmail().orEmpty() })
        }
        when (emailSent) {
            true -> startCooldown()
            false -> emit(VerifyEmailEvent.InitialSendFailed)
            null -> Unit
        }
    }

    /**
     * Kiểm tra người dùng đã bấm link chưa. [silent] = true khi app tự kiểm tra lúc quay lại
     * từ ứng dụng email: không báo "chưa xác nhận" để tránh làm phiền.
     */
    fun checkVerified(silent: Boolean = false) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            try {
                val user = authRepository.confirmEmailVerified()
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        event = when {
                            user != null -> VerifyEmailEvent.Verified(user)
                            silent -> it.event
                            else -> VerifyEmailEvent.NotVerifiedYet
                        }
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        event = if (silent) it.event else VerifyEmailEvent.Error(exception.toVerifyError())
                    )
                }
            }
        }
    }

    fun resend(languageCode: String) {
        val state = _uiState.value
        if (state.isBusy || state.resendCooldown > 0) return
        _uiState.update { it.copy(isResending = true) }
        viewModelScope.launch {
            try {
                authRepository.resendVerificationEmail(languageCode)
                _uiState.update { it.copy(isResending = false, event = VerifyEmailEvent.EmailResent) }
                startCooldown()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(isResending = false, event = VerifyEmailEvent.Error(exception.toVerifyError()))
                }
            }
        }
    }

    /** Đổi email đăng ký: xóa tài khoản chưa xác nhận để có thể đăng ký lại. */
    fun useAnotherEmail() = leave { authRepository.cancelPendingRegistration() }

    /** Quay lại đăng nhập: chỉ đăng xuất, giữ tài khoản để xác nhận sau. */
    fun backToLogin() = leave { authRepository.logout() }

    fun consumeEvent() {
        _uiState.update { it.copy(event = null) }
    }

    private fun leave(action: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            try {
                action()
            } finally {
                cooldownJob?.cancel()
                _uiState.update { it.copy(isLeaving = false, event = VerifyEmailEvent.SignedOut) }
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in RESEND_COOLDOWN_SECONDS downTo 1) {
                _uiState.update { it.copy(resendCooldown = remaining) }
                delay(1_000)
            }
            _uiState.update { it.copy(resendCooldown = 0) }
        }
    }

    private fun emit(event: VerifyEmailEvent) {
        _uiState.update { it.copy(event = event) }
    }

    private fun Exception.toVerifyError(): VerifyEmailError = when (this) {
        is AuthFailureException -> when (reason) {
            AuthFailureException.Reason.AccountInactive -> VerifyEmailError.AccountInactive
            AuthFailureException.Reason.ProfileSaveFailed -> VerifyEmailError.ProfileSaveFailed
            AuthFailureException.Reason.ProfileMissing -> VerifyEmailError.Failed
        }
        is FirebaseNetworkException -> VerifyEmailError.Network
        is FirebaseTooManyRequestsException -> VerifyEmailError.TooManyRequests
        is IllegalStateException -> VerifyEmailError.SessionExpired
        else -> VerifyEmailError.Failed
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
