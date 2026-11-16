package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.AppNotification
import com.kgyms.app.data.model.ExpiryReminderSettings
import com.kgyms.app.data.model.MembershipSummary
import com.kgyms.app.data.model.NotificationBroadcast
import com.kgyms.app.data.model.NotificationTarget
import com.kgyms.app.data.model.NotificationType
import com.kgyms.app.data.model.PackageOption
import com.kgyms.app.data.model.RecipientUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Realtime Database cho phần thông báo:
 * - notifications/{uid}/{notifId}       hộp thư của từng người
 * - notificationBroadcasts/{id}         lịch sử các lần admin gửi
 * - notificationSettings/expiryReminder cấu hình nhắc hết hạn
 * - sentReminders/{membershipId}/d{N}   đã nhắc mốc N ngày cho endDate nào (chống gửi trùng)
 */
class NotificationService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val root: DatabaseReference = database.reference

    /* ============================ HỘP THƯ NGƯỜI DÙNG ============================ */

    fun observeUserNotifications(uid: String): Flow<List<AppNotification>> =
        root.child("notifications").child(uid).observe { snapshot ->
            snapshot.children.mapNotNull { child ->
                val key = child.key ?: return@mapNotNull null
                if (key.startsWith("_")) null else child.toNotification(key)
            }
        }

    /** Đánh dấu đã đọc (rules chỉ cho chính chủ ghi trường isRead). */
    suspend fun markRead(uid: String, notificationIds: Collection<String>) {
        if (notificationIds.isEmpty()) return
        val updates = notificationIds.associate { "$it/isRead" to true as Any }
        root.child("notifications").child(uid).updateChildren(updates).await()
    }

    /* ============================ LỊCH SỬ GỬI ============================ */

    fun observeBroadcasts(): Flow<List<NotificationBroadcast>> =
        root.child("notificationBroadcasts").observe { snapshot ->
            snapshot.children.mapNotNull { child ->
                val key = child.key ?: return@mapNotNull null
                if (key.startsWith("_")) null else child.toBroadcast(key)
            }
        }

    suspend fun getBroadcast(id: String): NotificationBroadcast? {
        val snapshot = root.child("notificationBroadcasts").child(id).get().await()
        return if (snapshot.exists()) snapshot.toBroadcast(id) else null
    }

    /* ============================ CÀI ĐẶT NHẮC HẠN ============================ */

    fun observeExpirySettings(): Flow<ExpiryReminderSettings> =
        root.child("notificationSettings").child("expiryReminder").observe { it.toSettings() }

    suspend fun getExpirySettings(): ExpiryReminderSettings =
        root.child("notificationSettings").child("expiryReminder").get().await().toSettings()

    suspend fun saveExpirySettings(enabled: Boolean, days: Set<Int>) {
        root.child("notificationSettings").child("expiryReminder").updateChildren(
            mapOf(
                "enabled" to enabled,
                "days" to days.sorted().joinToString(",")
            )
        ).await()
    }

    /** sentReminders: membershipId → (mốc ngày → endDate đã nhắc). */
    suspend fun getSentReminders(): Map<String, Map<Int, String>> {
        val snapshot = root.child("sentReminders").get().await()
        return snapshot.children.associate { membership ->
            membership.key.orEmpty() to membership.children.mapNotNull { mark ->
                val days = mark.key?.removePrefix("d")?.toIntOrNull() ?: return@mapNotNull null
                days to mark.value?.toString().orEmpty()
            }.toMap()
        }
    }

    /* ============================ DỮ LIỆU THAM CHIẾU ============================ */

    /** User đang hoạt động, không gồm admin. */
    suspend fun getRecipientUsers(): List<RecipientUser> {
        val snapshot = root.child("users").get().await()
        return snapshot.children.mapNotNull { user ->
            val uid = user.key ?: return@mapNotNull null
            val role = user.child("role").value?.toString().orEmpty()
            val status = user.child("status").value?.toString().orEmpty().ifBlank { "active" }
            if (uid.startsWith("_") || role == "admin" || status != "active") return@mapNotNull null

            RecipientUser(
                uid = uid,
                fullName = user.child("fullName").value?.toString().orEmpty(),
                email = user.child("email").value?.toString().orEmpty()
            )
        }.sortedBy { it.fullName.lowercase() }
    }

    suspend fun getPackages(): List<PackageOption> {
        val snapshot = root.child("packages").get().await()
        return snapshot.children.mapNotNull { pkg ->
            val id = pkg.key ?: return@mapNotNull null
            if (id.startsWith("_")) return@mapNotNull null
            PackageOption(id, pkg.child("name").value?.toString().orEmpty().ifBlank { id })
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun getMemberships(): List<MembershipSummary> {
        val snapshot = root.child("memberships").get().await()
        return snapshot.children.mapNotNull { m ->
            val id = m.key ?: return@mapNotNull null
            if (id.startsWith("_")) return@mapNotNull null
            MembershipSummary(
                id = id,
                uid = m.child("uid").value?.toString().orEmpty(),
                packageId = m.child("packageId").value?.toString().orEmpty(),
                status = m.child("status").value?.toString().orEmpty(),
                endDate = m.child("endDate").value?.toString().orEmpty()
            )
        }
    }

    /* ============================ GHI NHIỀU NODE CÙNG LÚC ============================ */

    /** Sinh key mới (push id) dưới một đường dẫn mà chưa ghi gì. */
    fun newKey(path: String): String =
        requireNotNull(root.child(path).push().key) { "Không tạo được mã mới." }

    /** Ghi nhiều đường dẫn một lần; hoặc tất cả thành công hoặc không ghi gì. */
    suspend fun updatePaths(updates: Map<String, Any?>) {
        if (updates.isEmpty()) return
        root.updateChildren(updates).await()
    }

    /* ============================ HELPERS ============================ */

    private fun <T> DatabaseReference.observe(map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(map(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        addValueEventListener(listener)
        awaitClose { removeEventListener(listener) }
    }

    private fun DataSnapshot.text(name: String): String = child(name).value?.toString().orEmpty()

    private fun DataSnapshot.toNotification(id: String) = AppNotification(
        id = id,
        title = text("title"),
        message = text("message"),
        type = text("type").ifBlank { NotificationType.GENERAL },
        isRead = child("isRead").value as? Boolean ?: false,
        createdAt = text("createdAt"),
        broadcastId = text("broadcastId")
    )

    private fun DataSnapshot.toBroadcast(id: String) = NotificationBroadcast(
        id = id,
        title = text("title"),
        message = text("message"),
        type = text("type").ifBlank { NotificationType.GENERAL },
        target = text("target").ifBlank { NotificationTarget.ALL_USERS },
        packageId = text("packageId"),
        targetUid = text("targetUid"),
        targetLabel = text("targetLabel"),
        recipients = child("recipients").children.associate {
            it.key.orEmpty() to it.value?.toString().orEmpty()
        },
        createdAt = text("createdAt"),
        createdBy = text("createdBy")
    )

    private fun DataSnapshot.toSettings() = ExpiryReminderSettings(
        enabled = child("enabled").value as? Boolean ?: false,
        days = text("days").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            .ifEmpty { if (exists()) emptySet() else setOf(3) },
        lastRunAt = text("lastRunAt"),
        lastRunCount = (child("lastRunCount").value as? Number)?.toInt() ?: 0
    )
}
