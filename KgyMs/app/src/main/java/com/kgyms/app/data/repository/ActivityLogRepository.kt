package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.ActivityLogService
import com.kgyms.app.data.firebase.FirebaseAuthService
import com.kgyms.app.data.firebase.FirestoreService
import com.kgyms.app.data.model.ActivityLog
import com.kgyms.app.data.model.ActivityLogAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Nhật ký hoạt động dùng chung cho toàn app (node activityLogs).
 *
 * Cách dùng ở bất kỳ repository nào:
 *     activityLogRepository.log(ActivityLogAction.PAYMENT, "thanh toán gói Premium 6 tháng")
 * → title lưu lên Firebase: "<Tên người đang đăng nhập> thanh toán gói Premium 6 tháng".
 * Admin được ghi là "Admin <tên>".
 *
 * Ghi log không bao giờ làm hỏng thao tác chính: lỗi ghi log bị bỏ qua.
 */
class ActivityLogRepository(
    private val authService: FirebaseAuthService = FirebaseAuthService(),
    private val userService: FirestoreService = FirestoreService(),
    private val logService: ActivityLogService = ActivityLogService()
) {
    fun observeLogs(): Flow<List<ActivityLog>> = logService.observeLogs()

    /**
     * @param action một giá trị trong [ActivityLogAction].
     * @param description phần mô tả phía sau tên người thực hiện, vd "thêm tài sản \"Máy Smith\"".
     * @param actorName truyền vào khi đã biết tên (vd lúc vừa đăng ký); để null thì tự đọc users/{uid}.
     */
    suspend fun log(action: String, description: String, actorName: String? = null) {
        try {
            require(action in ActivityLogAction.all) { "action không hợp lệ: $action" }
            val uid = authService.getCurrentUid() ?: return

            val name = actorName?.takeIf { it.isNotBlank() } ?: resolveActorName(uid)

            val log = ActivityLog(
                title = "$name $description",
                actorUid = uid,
                action = action,
                createdAt = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT).format(Date())
            )

            // Mất mạng thì Firebase giữ bản ghi trong hàng đợi và tự gửi sau;
            // không chờ quá lâu để khỏi treo thao tác chính.
            withTimeoutOrNull(LOG_TIMEOUT_MS) { logService.addLog(log) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Không chặn thao tác chính nếu ghi log thất bại.
        }
    }

    private companion object {
        const val LOG_TIMEOUT_MS = 4_000L
    }

    private suspend fun resolveActorName(uid: String): String {
        val user = runCatching { userService.getUser(uid) }.getOrNull()
        val name = user?.fullName?.takeIf { it.isNotBlank() }
            ?: authService.currentUser?.email?.substringBefore("@")
            ?: "Người dùng"

        val isAdmin = user?.role == "admin"
        return when {
            !isAdmin -> name
            name.startsWith("Admin", ignoreCase = true) -> name
            else -> "Admin $name"
        }
    }
}
