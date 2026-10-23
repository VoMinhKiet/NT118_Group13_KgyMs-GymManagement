
package com.kgyms.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.kgyms.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ForgotPasswordState {
    data object Idle : ForgotPasswordState
    data object Loading : ForgotPasswordState
    data object EmailSent : ForgotPasswordState
    data class Error(val message: String) : ForgotPasswordState
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

        if (
            normalizedEmail.isBlank() ||
            !android.util.Patterns.EMAIL_ADDRESS
                .matcher(normalizedEmail)
                .matches()
        ) {
            _state.value = ForgotPasswordState.Error(
                "Vui lòng nhập địa chỉ email hợp lệ."
            )
            return
        }

        viewModelScope.launch {
            _state.value = ForgotPasswordState.Loading

            try {
                authRepository.sendPasswordResetEmail(normalizedEmail)
                _state.value = ForgotPasswordState.EmailSent
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: FirebaseNetworkException) {
                _state.value = ForgotPasswordState.Error(
                    "Không có kết nối mạng. Vui lòng thử lại."
                )
            } catch (exception: FirebaseAuthException) {
                _state.value = ForgotPasswordState.Error(
                    "Không thể gửi email lúc này. " +
                            "Vui lòng kiểm tra cấu hình Firebase và thử lại."
                )
            } catch (exception: Exception) {
                _state.value = ForgotPasswordState.Error(
                    exception.message
                        ?: "Đã xảy ra lỗi. Vui lòng thử lại."
                )
            }
        }
    }

    fun resetState() {
        _state.value = ForgotPasswordState.Idle
    }
}
