
package com.kgyms.app.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.kgyms.app.data.repository.AuthRepository
import com.kgyms.app.utils.isValidEmail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Mã lỗi gửi email đặt lại mật khẩu; View dịch theo ngôn ngữ đang chọn. */
enum class ForgotPasswordError { InvalidEmail, Network, TooManyRequests, Failed }

sealed interface ForgotPasswordState {
    data object Idle : ForgotPasswordState
    data object Loading : ForgotPasswordState
    data object EmailSent : ForgotPasswordState
    data class Error(val error: ForgotPasswordError) : ForgotPasswordState
}

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state =
        MutableStateFlow<ForgotPasswordState>(ForgotPasswordState.Idle)

    val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()

    fun sendResetEmail(email: String) {
        if (_state.value is ForgotPasswordState.Loading) return

        val normalizedEmail = email.trim()

        if (normalizedEmail.isBlank() || !isValidEmail(normalizedEmail)) {
            _state.value = ForgotPasswordState.Error(ForgotPasswordError.InvalidEmail)
            return
        }

        viewModelScope.launch {
            _state.value = ForgotPasswordState.Loading

            try {
                authRepository.sendPasswordResetEmail(normalizedEmail)
                _state.value = ForgotPasswordState.EmailSent
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.value = ForgotPasswordState.Error(
                    when (exception) {
                        is FirebaseNetworkException -> ForgotPasswordError.Network
                        is FirebaseTooManyRequestsException -> ForgotPasswordError.TooManyRequests
                        // Email sai định dạng theo Firebase.
                        is FirebaseAuthInvalidCredentialsException -> ForgotPasswordError.InvalidEmail
                        else -> ForgotPasswordError.Failed
                    }
                )
            }
        }
    }

    fun resetState() {
        _state.value = ForgotPasswordState.Idle
    }
}
