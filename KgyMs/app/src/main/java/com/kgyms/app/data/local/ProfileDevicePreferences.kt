package com.kgyms.app.data.local

import android.content.SharedPreferences
import com.kgyms.app.data.model.HomeNotification
import com.kgyms.app.data.model.NotificationType
import com.kgyms.app.data.model.memberAccountKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class FitnessGoalPreferences(
    val goal: String = "strength",
    val weight: String = "",
    val activity: String = "moderate",
)

internal data class NotificationInboxPreferences(val gym: Boolean = true, val reminders: Boolean = true) {
    fun shows(notification: HomeNotification): Boolean = when {
        notification.type == NotificationType.REMINDER -> reminders
        notification.type == NotificationType.PROMOTION -> gym
        notification.type == NotificationType.GENERAL && notification.broadcastId.isNotBlank() -> gym
        // Direct and unclassified notices include payment and account messages.
        else -> true
    }
}

internal class ProfileDevicePreferences(private val storage: SharedPreferences, uid: String) {
    private val accountKey = memberAccountKey(uid)
    private val prefix = "member_profile_${accountKey}_"

    fun goals() = FitnessGoalPreferences(
        goal = storage.getString(prefix + "goal", null) ?: "strength",
        weight = storage.getString(prefix + "weight", null).orEmpty(),
        activity = storage.getString(prefix + "activity", null) ?: "moderate",
    )

    suspend fun saveGoals(target: Int, goals: FitnessGoalPreferences): Boolean = persist {
        storage.edit()
            .putInt("weekly_target_$accountKey", target)
            .putString(prefix + "goal", goals.goal)
            .putString(prefix + "weight", goals.weight)
            .putString(prefix + "activity", goals.activity)
            .commit()
    }

    fun notification(key: String, default: Boolean): Boolean =
        storage.getBoolean(prefix + "notification_" + key, default)

    /** Toàn bộ công tắc thông báo push (đọc cả trong [com.kgyms.app.push.KgyMsMessagingService]). */
    fun pushPreferences() = PushPreferences(
        gym = notification("general", PushPreferences.DEFAULTS.getValue("general")),
        reminders = notification("reminders", PushPreferences.DEFAULTS.getValue("reminders")),
        transactions = notification("transactions", PushPreferences.DEFAULTS.getValue("transactions")),
        sound = notification("sound", PushPreferences.DEFAULTS.getValue("sound")),
        vibration = notification("vibration", PushPreferences.DEFAULTS.getValue("vibration")),
        lockScreen = notification("lock", PushPreferences.DEFAULTS.getValue("lock")),
        quietHours = notification("dnd", PushPreferences.DEFAULTS.getValue("dnd")),
    )

    fun notificationInbox() = NotificationInboxPreferences(
        gym = notification("general", true),
        reminders = notification("reminders", true),
    )

    suspend fun saveNotification(key: String, enabled: Boolean): Boolean = persist {
        storage.edit().putBoolean(prefix + "notification_" + key, enabled).commit()
    }

    private suspend fun persist(write: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            write()
        } catch (_: SecurityException) {
            false
        }
    }
}
