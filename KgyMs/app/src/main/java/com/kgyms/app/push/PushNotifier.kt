package com.kgyms.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.kgyms.app.MainActivity
import com.kgyms.app.R
import com.kgyms.app.data.local.ProfileDevicePreferences
import com.kgyms.app.data.local.PushCategory
import com.kgyms.app.data.local.PushPolicy
import com.kgyms.app.data.local.PushPresentation
import com.kgyms.app.data.repository.LanguageRepository
import java.util.Calendar

/**
 * Hiện thông báo push ra thanh trạng thái, áp dụng đúng các công tắc trong Cài đặt → Thông báo:
 * - Nhóm (tin phòng tập / nhắc nhở / giao dịch) tắt → không hiện.
 * - Âm thanh, Rung → chọn kênh thông báo tương ứng (Android cố định âm/rung theo kênh).
 * - Không làm phiền → trong 22:00–07:00 hiện nhưng không kêu, không rung.
 * - Màn hình khóa → tắt thì ẩn thông báo khỏi màn hình khóa.
 */
internal object PushNotifier {

    /** Trả true nếu đã hiện thông báo. */
    fun show(context: Context, data: Map<String, String>, now: Calendar = Calendar.getInstance()): Boolean {
        val uid = data["uid"].orEmpty()
        val title = data["title"].orEmpty()
        val message = data["message"].orEmpty()
        if (uid.isBlank() || title.isBlank() || message.isBlank()) return false

        // Máy dùng chung nhiều tài khoản: chỉ hiện thông báo của tài khoản đang đăng nhập.
        val currentUid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (currentUid != uid) return false

        val storage = context.getSharedPreferences(LanguageRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val prefs = ProfileDevicePreferences(storage, uid).pushPreferences()
        val presentation = PushPolicy.decide(
            prefs = prefs,
            category = PushCategory.fromWire(data["category"]),
            hourOfDay = now.get(Calendar.HOUR_OF_DAY),
        )
        if (!presentation.show) return false

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        val channelId = ensureChannel(context, presentation)
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_kgyms)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(
                if (presentation.showOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_SECRET
            )
        if (presentation.showOnLockScreen) {
            // Màn hình khóa chỉ hiện tiêu đề; nội dung hiện sau khi mở khóa (giữ riêng tư số tiền, gói tập).
            builder.setPublicVersion(
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_stat_kgyms)
                    .setContentTitle(title)
                    .build()
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 7: âm/rung đặt trên từng thông báo thay vì theo kênh.
            var defaults = 0
            if (presentation.sound) defaults = defaults or NotificationCompat.DEFAULT_SOUND
            if (presentation.vibration) defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
            builder.setDefaults(defaults)
        }

        val notificationId = (data["notificationId"].orEmpty().ifBlank { title + message }).hashCode()
        return try {
            manager.notify(notificationId, builder.build())
            true
        } catch (_: SecurityException) {
            false // quyền thông báo bị thu hồi giữa chừng
        }
    }

    /** Một kênh cho mỗi tổ hợp (âm thanh × rung); người dùng vẫn chỉnh thêm được trong cài đặt hệ thống. */
    internal fun channelId(sound: Boolean, vibration: Boolean): String =
        "kgyms_${if (sound) "s" else "q"}${if (vibration) "v" else "q"}"

    private fun ensureChannel(context: Context, presentation: PushPresentation): String {
        val id = channelId(presentation.sound, presentation.vibration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(id) == null) {
                val name = context.getString(R.string.push_channel_name)
                val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.push_channel_description)
                    enableVibration(presentation.vibration)
                    if (!presentation.sound) setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }
        return id
    }
}
