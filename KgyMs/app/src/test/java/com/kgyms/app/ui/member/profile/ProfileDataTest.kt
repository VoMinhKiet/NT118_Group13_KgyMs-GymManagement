package com.kgyms.app.ui.member.profile

import com.kgyms.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ProfileDataTest {
    @Test fun profileShowsTheActualOnboardingAgeAndMeasurements() {
        val member = User(uid = "member-a", gender = "female", age = 21,
            birthday = "13/10/2006", weight = 71.5, weightUnit = "KG", height = 175.0)
        val profile = member.memberProfile("vi")
        assertEquals("Nữ", profile.gender)
        assertEquals("21", profile.age)
        assertEquals("71,5 kg", profile.weight)
        assertEquals("175 cm", profile.height)
        assertEquals("150 lb", member.copy(weight = 150.0, weightUnit = "LB").memberProfile("en").weight)
    }

    @Test fun missingMeasurementsAreNotInferredFromTheBirthdayOrSliderDefaults() {
        val profile = User(uid = "member-b", birthday = "13/10/2006",
            weight = 0.0, height = Double.NaN).memberProfile("en")
        assertEquals("Not updated", profile.age)
        assertEquals("Not updated", profile.gender)
        assertEquals("Not updated", profile.weight)
        assertEquals("Not updated", profile.height)
        assertEquals("75", User(weight = 75.0).memberProfile("en").weight)
    }

    @Test fun measurementsNormalizeDecimalInputAndPoundsWithoutInventingMissingValues() {
        assertEquals(MemberBodyMeasurements("65.25", "172"), MemberBodyMeasurements.parse(" 065,25 ", "172.00"))
        assertEquals(MemberBodyMeasurements("68.04", "175"), MemberBodyMeasurements.fromOnboarding(150, "LB", 175))
        assertEquals(MemberBodyMeasurements(), MemberBodyMeasurements.parse("", "  "))
        for (invalid in listOf("-1", "0", "1e2", "NaN", "Infinity", "65.123", "1,2.3", "501")) {
            assertNull(MemberBodyMeasurements.parse(invalid, "175"))
        }
        assertNull(MemberBodyMeasurements.parse("70", "0"))
    }
}
