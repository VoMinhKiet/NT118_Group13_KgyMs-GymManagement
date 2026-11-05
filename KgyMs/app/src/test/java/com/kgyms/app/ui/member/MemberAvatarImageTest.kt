package com.kgyms.app.ui.member

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberAvatarImageTest {
    @Test fun initialsUseLastTwoNonEmptyNames() {
        assertEquals("TK", memberInitials("  Trương  Tấn Kiệt  "))
        assertEquals("A", memberInitials("Anh"))
        assertEquals("", memberInitials("   "))
    }

    @Test fun samplingBoundsLargePortraitLandscapeAndPanoramaBeforeAllocation() {
        listOf(12000 to 9000, 9000 to 12000, 64000 to 16, 384 to 384, 32 to 24).forEach { (w, h) ->
            val sample = avatarSampleSize(w, h)
            assertTrue(sample > 0 && sample and (sample - 1) == 0)
            assertTrue(maxOf(w, h) / sample <= MEMBER_AVATAR_SIZE * 2)
        }
        assertEquals(1, avatarSampleSize(384, 384))
        assertEquals(16, avatarSampleSize(12000, 9000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unreadableBoundsAreRejectedBeforeDecode() {
        avatarSampleSize(-1, -1)
    }
}
