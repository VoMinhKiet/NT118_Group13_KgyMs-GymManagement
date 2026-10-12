package com.kgyms.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Email và mật khẩu không được để trống")
            return
        }
        
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            // Giả lập thời gian kết nối mạng / Firebase
            delay(1500)
            
            // TODO: Kết nối với FirebaseAuth thực tế ở đây
            if (email == "test@gmail.com" && password == "123456") {
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error("Sai email hoặc mật khẩu. Hãy thử test@gmail.com / 123456")
            }
        }
    }

    fun register(fullName: String, email: String, password: String, confirmPassword: String) {
        if (fullName.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            _authState.value = AuthState.Error("Vui lòng điền đầy đủ thông tin")
            return
        }
        if (password != confirmPassword) {
            _authState.value = AuthState.Error("Mật khẩu xác nhận không khớp")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            // Giả lập thời gian kết nối mạng / Firebase
            delay(1500)
            
            // TODO: Kết nối với FirebaseAuth thực tế ở đây
            _authState.value = AuthState.Success
        }
    }
    
    fun loginWithGoogle() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            // Giả lập popup đăng nhập Google hiện lên và xử lý
            delay(1500)
            
            // Mặc định cho đăng nhập thành công luôn trong demo
            _authState.value = AuthState.Success
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
