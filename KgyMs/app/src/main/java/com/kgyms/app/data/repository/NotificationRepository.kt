package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.FirebaseAuthService
import com.kgyms.app.data.firebase.NotificationService
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.AppNotification
import com.kgyms.app.data.model.ExpiryReminderSettings
import com.kgyms.app.data.model.MembershipSummary
import com.kgyms.app.data.model.NotificationBroadcast
import com.kgyms.app.data.model.NotificationTarget
import com.kgyms.app.data.model.NotificationType
import com.kgyms.app.data.model.PackageOption
import com.kgyms.app.data.model.RecipientUser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow

/** Nội dung admin soạn ở form "Soạn thông báo". */
data class NotificationDraft(
    val title: String,
    val message: String,
    val type: String,
    val target: String,
    /** Rỗng = mọi gói (chỉ dùng với ACTIVE_MEMBERS). */
    val packageId: String = "",
    /** Chỉ dùng với USER. */
    val targetUid: String = ""
)

/**
 * Điều phối thông báo: gửi hàng loạt / gửi riêng, sửa, thu hồi, nhắc hết hạn gói.
 * Mọi thao tác ghi nhiều node đều dùng 1 lần updateChildren để không bị ghi dở.
 */
class NotificationRepository(
    private val service: NotificationService = NotificationService(),
    private val authService: FirebaseAuthService = FirebaseAuthService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    /* ============================ PHÍA HỘI VIÊN ============================ */

    /** null nếu chưa đăng nhập. */
    fun currentUid(): String? = authService.getCurrentUid()

    fun observeMyNotifications(uid: String): Flow<List<AppNotification>> =
        service.observeUserNotifications(uid)

    suspend fun markRead(uid: String, ids: Collection<String>) = service.markRead(uid, ids)

    /* ============================ PHÍA ADMIN ============================ */

    fun observeBroadcasts(): Flow<List<NotificationBroadcast>> = service.observeBroadcasts()

    fun observeExpirySettings(): Flow<ExpiryReminderSettings> = service.observeExpirySettings()

    suspend fun getRecipientUsers(): List<RecipientUser> = service.getRecipientUsers()

    suspend fun getPackages(): List<PackageOption> = service.getPackages()

    suspend fun getMemberships(): List<MembershipSummary> = service.getMemberships()

    suspend fun saveExpirySettings(enabled: Boolean, days: Set<Int>) {
        service.saveExpirySettings(enabled, days)
        activityLogRepository.log(
            ActivityLogAction.NOTIFICATION,
            if (enabled) "bật nhắc hết hạn gói (mốc ${days.sorted().joinToString(", ")} ngày)"
            else "tắt nhắc hết hạn gói"
        )
    }

    /** Danh sách uid sẽ nhận theo đối tượng đã chọn. */
    fun resolveRecipients(
        draft: NotificationDraft,
        users: List<RecipientUser>,
        memberships: List<MembershipSummary>
    ): List<String> {
        val activeUids = users.map { it.uid }.toSet()
        return when (draft.target) {
            NotificationTarget.ALL_USERS -> users.map { it.uid }
            NotificationTarget.ACTIVE_MEMBERS -> memberships
                .filter { it.status == "active" }
                .filter { draft.packageId.isBlank() || it.packageId == draft.packageId }
                .map { it.uid }
                .filter { it in activeUids }
                .distinct()
            NotificationTarget.USER -> listOf(draft.targetUid).filter { it in activeUids }
            else -> emptyList()
        }
    }

    /** Gửi thông báo, trả về số người nhận. */
    suspend fun sendBroadcast(draft: NotificationDraft, recipients: List<String>, targetLabel: String): Int {
        require(recipients.isNotEmpty()) { "Không có người nhận nào phù hợp." }

        val now = nowText()
        val broadcastId = service.newKey("notificationBroadcasts")
        val updates = mutableMapOf<String, Any?>()
        val recipientMap = mutableMapOf<String, String>()

        recipients.forEach { uid ->
            val notifId = service.newKey("notifications/$uid")
            recipientMap[uid] = notifId
            updates["notifications/$uid/$notifId"] = mapOf(
                "title" to draft.title,
                "message" to draft.message,
                "type" to draft.type,
                "isRead" to false,
                "createdAt" to now,
                "broadcastId" to broadcastId
            )
        }

        updates["notificationBroadcasts/$broadcastId"] = mapOf(
            "title" to draft.title,
            "message" to draft.message,
            "type" to draft.type,
            "target" to draft.target,
            "packageId" to draft.packageId,
            "targetUid" to draft.targetUid,
            "targetLabel" to targetLabel,
            "recipients" to recipientMap,
            "recipientCount" to recipientMap.size,
            "createdAt" to now,
            "createdBy" to authService.getCurrentUid().orEmpty()
        )

        service.updatePaths(updates)

        activityLogRepository.log(
            ActivityLogAction.NOTIFICATION,
            "gửi thông báo \"${draft.title}\" tới $targetLabel (${recipients.size} người)"
        )
        return recipients.size
    }

    /** Sửa tiêu đề/nội dung của lần gửi và của mọi thông báo đã gửi đi. */
    suspend fun editBroadcast(broadcast: NotificationBroadcast, title: String, message: String) {
        val updates = mutableMapOf<String, Any?>(
            "notificationBroadcasts/${broadcast.id}/title" to title,
            "notificationBroadcasts/${broadcast.id}/message" to message
        )
        broadcast.recipients.forEach { (uid, notifId) ->
            updates["notifications/$uid/$notifId/title"] = title
            updates["notifications/$uid/$notifId/message"] = message
        }
        service.updatePaths(updates)

        activityLogRepository.log(
            ActivityLogAction.NOTIFICATION,
            "sửa thông báo \"$title\" (${broadcast.recipients.size} người nhận)"
        )
    }

    /** Thu hồi: xóa lần gửi và xóa thông báo khỏi hộp thư của mọi người nhận. */
    suspend fun deleteBroadcast(broadcast: NotificationBroadcast) {
        val updates = mutableMapOf<String, Any?>("notificationBroadcasts/${broadcast.id}" to null)
        broadcast.recipients.forEach { (uid, notifId) ->
            updates["notifications/$uid/$notifId"] = null
        }
        service.updatePaths(updates)

        activityLogRepository.log(
            ActivityLogAction.NOTIFICATION,
            "thu hồi thông báo \"${broadcast.title}\" (${broadcast.recipients.size} người nhận)"
        )
    }

    /**
     * Quét memberships active và gửi nhắc "còn N ngày" theo cấu hình.
     * Mỗi gói chỉ nhận 1 nhắc cho mỗi mốc (nhớ theo endDate, gia hạn xong sẽ nhắc lại từ đầu).
     *
     * @param force chạy kể cả khi đang tắt (nút "Quét & gửi ngay" vẫn tôn trọng cờ bật/tắt; force chỉ dùng cho test).
     * @return số thông báo đã gửi; -1 nếu tính năng đang tắt.
     */
    suspend fun runExpiryReminders(force: Boolean = false): Int {
        val settings = service.getExpirySettings()
        if (!settings.enabled && !force) return -1
        if (settings.days.isEmpty()) return 0

        val memberships = service.getMemberships()
        val packages = service.getPackages().associate { it.id to it.name }
        val activeUsers = service.getRecipientUsers().map { it.uid }.toSet()
        val sent = service.getSentReminders()
        val today = startOfToday()
        val now = nowText()

        val updates = mutableMapOf<String, Any?>()
        var count = 0

        memberships
            .filter { it.status == "active" && it.uid in activeUsers }
            .forEach { membership ->
                val end = parseDay(membership.endDate) ?: return@forEach
                val daysLeft = ((end - today) / DAY_MS.toDouble()).roundToLong().toInt()
                if (daysLeft < 0) return@forEach

                // Các mốc đã "tới": còn ≤ N ngày. Chỉ gửi 1 nhắc cho mốc nhỏ nhất chưa nhắc.
                val reached = settings.days.filter { daysLeft <= it }
                if (reached.isEmpty()) return@forEach

                val alreadySent = sent[membership.id].orEmpty()
                val nearest = reached.min()
                if (alreadySent[nearest] == membership.endDate) return@forEach

                val packageName = packages[membership.packageId] ?: "của bạn"
                val notifId = service.newKey("notifications/${membership.uid}")
                updates["notifications/${membership.uid}/$notifId"] = mapOf(
                    "title" to "Gói tập sắp hết hạn",
                    "message" to expiryMessage(packageName, daysLeft, membership.endDate),
                    "type" to NotificationType.REMINDER,
                    "isRead" to false,
                    "createdAt" to now,
                    "broadcastId" to ""
                )
                // Đánh dấu mọi mốc đã tới để lần sau không nhắc lại mốc lớn hơn.
                reached.forEach { day ->
                    updates["sentReminders/${membership.id}/d$day"] = membership.endDate
                }
                count++
            }

        updates["notificationSettings/expiryReminder/lastRunAt"] = now
        updates["notificationSettings/expiryReminder/lastRunCount"] = count
        service.updatePaths(updates)

        if (count > 0) {
            activityLogRepository.log(
                ActivityLogAction.NOTIFICATION,
                "gửi nhắc hết hạn gói cho $count hội viên"
            )
        }
        return count
    }

    private fun expiryMessage(packageName: String, daysLeft: Int, endDate: String): String =
        if (daysLeft == 0) {
            "Gói $packageName hết hạn hôm nay ($endDate). Gia hạn ngay để không bị gián đoạn nhé!"
        } else {
            "Gói $packageName còn $daysLeft ngày nữa hết hạn ($endDate). Gia hạn sớm để không bị gián đoạn nhé!"
        }

    private fun nowText(): String = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT).format(Date())

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parseDay(text: String): Long? = runCatching {
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }.parse(text)?.time
    }.getOrNull()

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
