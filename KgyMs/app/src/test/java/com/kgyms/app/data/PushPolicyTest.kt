package com.kgyms.app.data

import com.kgyms.app.data.local.PushCategory
import com.kgyms.app.data.local.PushPolicy
import com.kgyms.app.data.local.PushPreferences
import com.kgyms.app.data.push.AdminPushRelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPolicyTest {
    private val noon = 12
    private val midnight = 0

    @Test fun categoryFollowsServerWireValues() {
        assertEquals(PushCategory.GYM, PushCategory.fromWire("gym"))
        assertEquals(PushCategory.REMINDERS, PushCategory.fromWire("reminders"))
        assertEquals(PushCategory.TRANSACTIONS, PushCategory.fromWire("transactions"))
        // Thiếu/lạ ⇒ giao dịch: không bao giờ làm mất thông báo thanh toán.
        assertEquals(PushCategory.TRANSACTIONS, PushCategory.fromWire(null))
        assertEquals(PushCategory.TRANSACTIONS, PushCategory.fromWire("khac"))
    }

    @Test fun defaultsShowEverythingWithSoundAndVibration() {
        val result = PushPolicy.decide(PushPreferences(), PushCategory.TRANSACTIONS, noon)
        assertTrue(result.show && result.sound && result.vibration && result.showOnLockScreen)
    }

    @Test fun eachCategorySwitchOnlyAffectsItsOwnCategory() {
        val prefs = PushPreferences(gym = false)
        assertFalse(PushPolicy.decide(prefs, PushCategory.GYM, noon).show)
        assertTrue(PushPolicy.decide(prefs, PushCategory.REMINDERS, noon).show)
        assertTrue(PushPolicy.decide(prefs, PushCategory.TRANSACTIONS, noon).show)

        assertFalse(PushPolicy.decide(PushPreferences(reminders = false), PushCategory.REMINDERS, noon).show)
        assertFalse(PushPolicy.decide(PushPreferences(transactions = false), PushCategory.TRANSACTIONS, noon).show)
    }

    @Test fun soundVibrationAndLockScreenSwitchesAreIndependent() {
        val silent = PushPolicy.decide(PushPreferences(sound = false), PushCategory.GYM, noon)
        assertTrue(silent.show && !silent.sound && silent.vibration)

        val still = PushPolicy.decide(PushPreferences(vibration = false), PushCategory.GYM, noon)
        assertTrue(still.sound && !still.vibration)

        assertFalse(PushPolicy.decide(PushPreferences(lockScreen = false), PushCategory.GYM, noon).showOnLockScreen)
    }

    @Test fun quietHoursMuteSoundAndVibrationButStillShow() {
        listOf(22, 23, 0, 3, 6).forEach { hour ->
            val result = PushPolicy.decide(PushPreferences(), PushCategory.TRANSACTIONS, hour)
            assertTrue("giờ $hour vẫn hiện", result.show)
            assertFalse("giờ $hour không kêu", result.sound)
            assertFalse("giờ $hour không rung", result.vibration)
        }
        listOf(7, 12, 21).forEach { hour ->
            val result = PushPolicy.decide(PushPreferences(), PushCategory.TRANSACTIONS, hour)
            assertTrue("giờ $hour có âm thanh", result.sound && result.vibration)
        }
    }

    @Test fun quietHoursOffKeepsSoundAtNight() {
        val result = PushPolicy.decide(PushPreferences(quietHours = false), PushCategory.TRANSACTIONS, midnight)
        assertTrue(result.sound && result.vibration)
    }

    @Test fun defaultsMapCoversEverySwitchOnScreen() {
        assertEquals(setOf("general", "reminders", "transactions", "sound", "vibration", "lock", "dnd"), PushPreferences.DEFAULTS.keys)
        assertTrue(PushPreferences.DEFAULTS.values.all { it })
    }

    @Test fun relayExtractsOnlyNotificationNodesFromMultiPathUpdates() {
        val updates = mapOf<String, Any?>(
            "payments/KG1/status" to "paid",
            "notifications/u1/n1" to mapOf("title" to "Thanh toán thành công"),
            "notifications/u2/n2" to mapOf("title" to "x"),
            "notifications/u3/n3/isRead" to true,
            "notifications/u4/n4" to null,
            "notificationBroadcasts/b1" to mapOf("title" to "y"),
        )
        assertEquals(listOf("u1" to "n1", "u2" to "n2"), AdminPushRelay.targetsFrom(updates))
    }
}
