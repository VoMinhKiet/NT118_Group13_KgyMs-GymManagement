package com.kgyms.app.viewmodel.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProfileSaveEvent {
    /**
     * @param user hồ sơ sau khi lưu.
     * @param persisted true nếu đã lưu lên Firebase; false khi chưa đăng nhập (chế độ thử nghiệm).
     * @param emailVerificationSentTo email mới đã được gửi thư xác nhận (nếu có đổi email).
     * @param emailError lỗi khi yêu cầu đổi email (thông tin khác vẫn đã được lưu).
     */
    data class Saved(
        val user: User,
        val persisted: Boolean,
        val emailVerificationSentTo: String? = null,
        val emailError: String? = null,
    ) : ProfileSaveEvent

    data class Error(val message: String) : ProfileSaveEvent
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Hồ sơ trên Firebase; null nếu chưa đăng nhập hoặc chưa tải xong. */
    val user: User? = null,
    val saveEvent: ProfileSaveEvent? = null,
)

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        if (userRepository.getCurrentUid() == null) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                _uiState.update { it.copy(isLoading = false, user = user) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun savePersonalInfo(
        fullName: String,
        email: String,
        phone: String,
        birthday: String,
    ) {
        if (_uiState.value.isSaving) return

        val name = fullName.trim()
        val newEmail = email.trim()
        val newPhone = phone.trim()
        val current = _uiState.value.user

        // Chưa đăng nhập (bản thử nghiệm / UI test): chỉ cập nhật cục bộ.
        if (userRepository.getCurrentUid() == null) {
            val local = (current ?: User()).copy(
                fullName = name, email = newEmail, phone = newPhone, birthday = birthday
            )
            _uiState.update { it.copy(saveEvent = ProfileSaveEvent.Saved(local, persisted = false)) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                userRepository.updatePersonalInfo(name, newPhone, birthday)

                val currentEmail = userRepository.getCurrentAuthEmail() ?: current?.email.orEmpty()
                val emailChanged = newEmail.isNotBlank() && !newEmail.equals(currentEmail, ignoreCase = true)
                var verificationSentTo: String? = null
                var emailError: String? = null
                if (emailChanged) {
                    try {
                        userRepository.requestEmailChange(newEmail)
                        verificationSentTo = newEmail
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        emailError = emailErrorMessage(exception)
                    }
                }

                // Email trong hồ sơ giữ nguyên cho tới khi người dùng xác nhận email mới.
                val updated = (current ?: User()).copy(
                    fullName = name, phone = newPhone, birthday = birthday,
                    email = currentEmail,
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        user = updated,
                        saveEvent = ProfileSaveEvent.Saved(updated, true, verificationSentTo, emailError),
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, saveEvent = ProfileSaveEvent.Error(saveErrorMessage(exception)))
                }
            }
        }
    }

    /** View gọi sau khi đã hiển thị kết quả lưu. */
    fun consumeSaveEvent() {
        _uiState.update { it.copy(saveEvent = null) }
    }

    private fun saveErrorMessage(exception: Exception): String = when (exception) {
        is FirebaseNetworkException -> "Không có kết nối mạng. Vui lòng thử lại."
        is IllegalStateException -> exception.message ?: "Không thể lưu hồ sơ."
        else -> "Không thể lưu hồ sơ. Vui lòng kiểm tra kết nối và Database Rules."
    }

    private fun emailErrorMessage(exception: Exception): String = when (exception) {
        is FirebaseAuthRecentLoginRequiredException ->
            "Để đổi email, vui lòng đăng xuất và đăng nhập lại rồi thử lại."
        is FirebaseAuthUserCollisionException -> "Email này đã được tài khoản khác sử dụng."
        is FirebaseNetworkException -> "Không có kết nối mạng nên chưa đổi được email."
        is FirebaseAuthException -> "Không thể đổi email lúc này."
        else -> exception.message ?: "Không thể đổi email lúc này."
    }
}
