package com.kgyms.app.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Mã lỗi khi lưu onboarding; View dịch theo ngôn ngữ đang chọn. */
enum class OnboardingError { InvalidData, SaveFailed }

sealed interface OnboardingState {
    data object Idle : OnboardingState
    data object Saving : OnboardingState
    data object Completed : OnboardingState
    data class Error(val error: OnboardingError) : OnboardingState
}

/**
 * Lưu giới tính, tuổi chọn khi tạo tài khoản vào users/{uid}.
 * Cân nặng/chiều cao chỉ dùng trên màn hình, không lưu lên Firebase (dữ liệu nhạy cảm).
 */
class OnboardingViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun completeOnboarding(
        gender: String?,
        age: Int,
    ) {
        if (_state.value is OnboardingState.Saving) return

        if (gender.isNullOrBlank() || age !in 10..100) {
            _state.value = OnboardingState.Error(OnboardingError.InvalidData)
            return
        }

        _state.value = OnboardingState.Saving
        viewModelScope.launch {
            try {
                userRepository.saveOnboarding(gender, age)
                _state.value = OnboardingState.Completed
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _state.value = OnboardingState.Error(OnboardingError.SaveFailed)
            }
        }
    }

    fun resetState() {
        _state.value = OnboardingState.Idle
    }
}
