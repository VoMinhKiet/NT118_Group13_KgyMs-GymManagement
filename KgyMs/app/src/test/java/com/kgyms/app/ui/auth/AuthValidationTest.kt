package com.kgyms.app.ui.auth

import org.junit.Assert.*
import org.junit.Test
import com.kgyms.app.viewmodel.auth.AuthError
import com.kgyms.app.viewmodel.auth.AuthState
import com.kgyms.app.viewmodel.auth.AuthViewModel
import com.kgyms.app.utils.convertWeight
import com.kgyms.app.utils.registrationError

class AuthValidationTest {
    @Test fun validRegistrationInput() {
        assertNull(registrationError("Nguyễn Minh Anh", "minhanh@example.com", "Abc12_", "Abc12_"))
    }
    @Test fun blocksBlankInvalidEmailShortAndMismatchingPasswords() {
        assertNotNull(registrationError(" ", "a@b.com", "Abc12_", "Abc12_"))
        assertNotNull(registrationError("Anh", "not-an-email", "Abc12_", "Abc12_"))
        assertNotNull(registrationError("Anh", "a@b.com", "Ab1_", "Ab1_"))
        assertNotNull(registrationError("Anh", "a@b.com", "123456", "123456"))
        assertNotNull(registrationError("Anh", "a@b.com", "Abc12_", "Abc12."))
    }
    @Test fun weightUnitChangesKeepEquivalentWeightAndRespectRulerLimits() {
        assertEquals(165, convertWeight(75, "KG", "LB"))
        assertEquals(75, convertWeight(165, "LB", "KG"))
        assertEquals(440, convertWeight(200, "KG", "LB"))
        assertEquals(30, convertWeight(66, "LB", "KG"))
        assertEquals(75, convertWeight(75, "KG", "KG"))
    }
    @Test fun invalidInputIsRejectedBeforeAnyNetworkRequest() {
        // Repository không bao giờ được tạo: lỗi validate phải trả về trước khi gọi Firebase.
        val vm = AuthViewModel(authRepositoryProvider = { error("Không được gọi mạng khi dữ liệu không hợp lệ") })
        fun lastError() = (vm.authState.value as AuthState.Error).error

        vm.login("", "")
        assertEquals(AuthError.EmptyCredentials, lastError())
        vm.resetState()
        vm.register("Anh", "not-an-email", "Abc12_", "Abc12_")
        assertEquals(AuthError.InvalidEmail, lastError())
        vm.resetState()
        vm.register("Anh", "anh@example.com", "Ab1_", "Ab1_")
        assertEquals(AuthError.PasswordTooWeak, lastError())
        vm.resetState()
        vm.register("Anh", "anh@example.com", "abcdef1!", "abcdef1!")
        assertEquals(AuthError.PasswordTooWeak, lastError())
        vm.resetState()
        vm.register("Anh", "anh@example.com", "Abc12_", "Abc12.")
        assertEquals(AuthError.PasswordMismatch, lastError())
        vm.resetState()
        vm.loginWithGoogle(idToken = "")
        assertEquals(AuthError.GoogleTokenMissing, lastError())
    }

    @Test fun errorMessagesFollowSelectedLanguage() {
        assertEquals("Passwords do not match.", AuthError.PasswordMismatch.message("en"))
        assertEquals("Mật khẩu xác nhận không khớp.", AuthError.PasswordMismatch.message("vi"))
        // Mọi mã lỗi đều có bản dịch khác nhau cho hai ngôn ngữ.
        AuthError.values().forEach { assertNotEquals(it.message("en"), it.message("vi")) }
    }
}
