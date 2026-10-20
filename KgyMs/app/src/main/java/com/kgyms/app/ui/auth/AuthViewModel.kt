package com.kgyms.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private var request: Job? = null
    private fun allowMock(enabled: Boolean): Boolean {
        if (!enabled) _authState.value = AuthState.Error("Chưa tích hợp xác thực thật. Chế độ mock chỉ dùng trong bản debug.")
        return enabled
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun login(email: String, password: String, mockEnabled: Boolean = false) {
        if (_authState.value is AuthState.Loading || !allowMock(mockEnabled)) return
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Email và mật khẩu không được để trống")
            return
        }
        
        _authState.value = AuthState.Loading
        request = viewModelScope.launch {
            // Giả lập thời gian kết nối mạng / Firebase
            delay(1500)
            
            // TODO: Kết nối với FirebaseAuth thực tế ở đây
            if (email.trim().equals("test@gmail.com", ignoreCase = true) && password == "123456") {
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error("Sai email hoặc mật khẩu. Hãy thử test@gmail.com / 123456")
            }
        }
    }

    fun register(fullName: String, email: String, password: String, confirmPassword: String, mockEnabled: Boolean = false) {
        if (_authState.value is AuthState.Loading || !allowMock(mockEnabled)) return
        registrationError(fullName, email, password, confirmPassword)?.let {
            _authState.value = AuthState.Error(it)
            return
        }
        _authState.value = AuthState.Loading
        request = viewModelScope.launch {
            delay(1500)
            _authState.value = AuthState.Success
        }
    }

    fun loginWithGoogle(mockEnabled: Boolean = false) {
        if (_authState.value is AuthState.Loading || !allowMock(mockEnabled)) return
        _authState.value = AuthState.Loading
        request = viewModelScope.launch {
            // Giả lập popup đăng nhập Google hiện lên và xử lý
            delay(1500)
            
            // Mặc định cho đăng nhập thành công luôn trong demo
            _authState.value = AuthState.Success
        }
    }

    fun resetState() {
        request?.cancel()
        _authState.value = AuthState.Idle
    }
}
