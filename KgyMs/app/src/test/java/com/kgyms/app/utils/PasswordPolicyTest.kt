package com.kgyms.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test fun strongPasswordMeetsEveryRequirement() {
        listOf("Abc12_", "Abc12.", "Abc12!", "Abc12@", "Abc12#", "Kgyms-2026").forEach {
            assertTrue(it, PasswordPolicy.isStrong(it))
        }
    }

    @Test fun eachMissingRequirementIsReported() {
        assertEquals(listOf(PasswordRequirement.MinLength), PasswordPolicy.unmet("Ab1_"))
        assertEquals(listOf(PasswordRequirement.Uppercase), PasswordPolicy.unmet("abc12_"))
        assertEquals(listOf(PasswordRequirement.Digit), PasswordPolicy.unmet("Abcde_"))
        assertEquals(listOf(PasswordRequirement.Special), PasswordPolicy.unmet("Abcde1"))
        assertEquals(PasswordRequirement.entries, PasswordPolicy.unmet(""))
    }

    @Test fun onlyFirebaseAllowedSymbolsCountAsSpecial() {
        assertFalse(PasswordPolicy.isMet(PasswordRequirement.Special, "Abc 12"))
        // Firebase không tính + và = là ký tự đặc biệt.
        assertFalse(PasswordPolicy.isMet(PasswordRequirement.Special, "Abc12+"))
        assertFalse(PasswordPolicy.isMet(PasswordRequirement.Special, "Abc12="))
        assertTrue(PasswordPolicy.isMet(PasswordRequirement.Special, "Abc12\\"))
        assertTrue(PasswordPolicy.isMet(PasswordRequirement.Special, "Abc12\""))
        assertEquals(30, PasswordPolicy.ALLOWED_SPECIAL_CHARACTERS.length)
    }

    @Test fun onlyAsciiUppercaseAndDigitsCount() {
        assertFalse(PasswordPolicy.isMet(PasswordRequirement.Uppercase, "abcĐ1_"))
        assertTrue(PasswordPolicy.isMet(PasswordRequirement.Uppercase, "abcD"))
    }
}
