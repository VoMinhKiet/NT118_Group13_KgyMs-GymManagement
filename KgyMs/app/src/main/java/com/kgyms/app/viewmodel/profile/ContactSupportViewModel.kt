package com.kgyms.app.viewmodel.profile

import androidx.lifecycle.ViewModel
import com.kgyms.app.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ContactSupportUiState(
    val hotlinePhone: String = Constants.HOTLINE_PHONE,
    /** Hiển thị popup xác nhận gọi hotline. */
    val showCallConfirm: Boolean = false,
    /** Số điện thoại đang chờ View thực hiện cuộc gọi (sự kiện một lần). */
    val pendingCall: String? = null,
)

class ContactSupportViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ContactSupportUiState())
    val uiState: StateFlow<ContactSupportUiState> = _uiState.asStateFlow()

    fun onHotlineClick() {
        _uiState.update { it.copy(showCallConfirm = true) }
    }

    fun dismissCallConfirm() {
        _uiState.update { it.copy(showCallConfirm = false) }
    }

    fun confirmCall() {
        _uiState.update { it.copy(showCallConfirm = false, pendingCall = it.hotlinePhone) }
    }

    /** View gọi sau khi đã xử lý xong sự kiện gọi điện. */
    fun onCallHandled() {
        _uiState.update { it.copy(pendingCall = null) }
    }
}
