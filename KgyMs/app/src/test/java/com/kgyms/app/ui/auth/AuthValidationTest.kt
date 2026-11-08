package com.kgyms.app.ui.auth

import org.junit.Assert.*
import org.junit.Test
import com.kgyms.app.viewmodel.auth.AuthState
import com.kgyms.app.viewmodel.auth.AuthViewModel
import com.kgyms.app.utils.convertWeight
import com.kgyms.app.utils.registrationError

class AuthValidationTest {
    @Test fun validMockRegistrationDoesNotNeedARealAccount() {
        assertNull(registrationError("Nguyễn Minh Anh", "minhanh@example.com", "123456", "123456"))
    }
    @Test fun blocksBlankInvalidEmailShortAndMismatchingPasswords() {
        assertNotNull(registrationError(" ", "a@b.com", "123456", "123456"))
        assertNotNull(registrationError("Anh", "not-an-email", "123456", "123456"))
        assertNotNull(registrationError("Anh", "a@b.com", "12345", "12345"))
        assertNotNull(registrationError("Anh", "a@b.com", "123456", "654321"))
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
        vm.login("", "")
        assertTrue(vm.authState.value is AuthState.Error)
        vm.resetState()
        vm.register("Anh", "not-an-email", "123456", "123456")
        assertTrue(vm.authState.value is AuthState.Error)
        vm.resetState()
        vm.register("Anh", "anh@example.com", "123456", "654321")
        assertTrue(vm.authState.value is AuthState.Error)
        vm.resetState()
        vm.loginWithGoogle(idToken = "")
        assertTrue(vm.authState.value is AuthState.Error)
    }
}
