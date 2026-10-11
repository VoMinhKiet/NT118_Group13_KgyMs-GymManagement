package com.kgyms.app.ui.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBirthdayTest {
    @Test fun acceptsRealCalendarDates() {
        assertTrue(validProfileBirthday("01/04/1998"))
        assertTrue(validProfileBirthday("29/02/2000"))
    }
    @Test fun rejectsInvalidCalendarDatesAndWrongFormats() {
        listOf("31/04/1998", "29/02/2001", "01/13/1998", "1/4/1998", "1998-04-01", "", "01/04/1998extra")
            .forEach { assertFalse(it, validProfileBirthday(it)) }
    }
}
