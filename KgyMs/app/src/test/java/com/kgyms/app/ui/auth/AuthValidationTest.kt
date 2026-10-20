package com.kgyms.app.ui.auth

import org.junit.Assert.*
import org.junit.Test

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
    @Test fun mockIsOffByDefaultAndDoesNotStartANetworkRequest() {
        val vm = AuthViewModel()
        vm.login("test@gmail.com", "123456")
        assertTrue(vm.authState.value is AuthState.Error)
        vm.resetState()
        vm.register("Anh", "anh@example.com", "123456", "123456")
        assertTrue(vm.authState.value is AuthState.Error)
        vm.resetState()
        vm.loginWithGoogle()
        assertTrue(vm.authState.value is AuthState.Error)
    }
}
