package com.kgyms.app.data.push

import com.google.firebase.messaging.FirebaseMessaging
import com.kgyms.app.data.server.KgyMsServerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Đăng ký token FCM của máy này với tài khoản đang đăng nhập (qua server KgyMs) để nhận push
 * khi app đóng. Mọi thao tác đều "best effort": lỗi mạng/Play Services không được làm hỏng đăng nhập.
 */
class PushTokenRepository(
    private val server: Lazy<KgyMsServerService> = lazy { KgyMsServerService() },
    private val tokenProvider: suspend () -> String? = { currentFcmToken() },
) {
    /** Gọi sau khi hội viên đăng nhập / mở lại app. Trả true nếu server đã nhận token. */
    suspend fun register(): Boolean = guarded {
        val token = tokenProvider() ?: return@guarded false
        server.value.registerPushToken(token)
        true
    }

    /** Gọi TRƯỚC khi đăng xuất (cần phiên đăng nhập để gọi server) để máy không nhận push của tài khoản cũ. */
    suspend fun unregister() {
        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
            guarded {
                val token = tokenProvider() ?: return@guarded false
                server.value.unregisterPushToken(token)
                true
            }
        }
    }

    private suspend fun guarded(block: suspend () -> Boolean): Boolean = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val UNREGISTER_TIMEOUT_MS = 3_000L

        /** null khi thiết bị không có Google Play Services hoặc bản build không cấu hình FCM (test emulator). */
        @Suppress("DEPRECATION")
        suspend fun currentFcmToken(): String? = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Nhờ server push cho các thông báo admin vừa ghi vào Firebase (gửi tin, nhắc hết hạn, thu tiền).
 * Thông báo vẫn nằm trong hộp thư Trang chủ dù push lỗi.
 */
class AdminPushRelay(
    private val server: Lazy<KgyMsServerService> = lazy { KgyMsServerService() },
) {
    suspend fun notify(targets: List<Pair<String, String>>) {
        if (targets.isEmpty()) return
        try {
            withTimeoutOrNull(RELAY_TIMEOUT_MS) { server.value.pushNotifications(targets) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Hộp thư đã có thông báo; push là phần bổ sung.
        }
    }

    companion object {
        private const val RELAY_TIMEOUT_MS = 30_000L

        /**
         * Lấy các cặp (uid, notificationId) từ một multi-path update:
         * khóa dạng "notifications/{uid}/{id}" với giá trị là cả node thông báo.
         */
        fun targetsFrom(updates: Map<String, Any?>): List<Pair<String, String>> =
            updates.entries.mapNotNull { (path, value) ->
                val parts = path.split('/')
                if (parts.size == 3 && parts[0] == "notifications" && value != null) parts[1] to parts[2] else null
            }
    }
}
