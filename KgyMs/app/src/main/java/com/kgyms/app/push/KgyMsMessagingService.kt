package com.kgyms.app.push

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kgyms.app.data.push.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Nhận push từ server KgyMs. Server gửi "data message" (không có phần notification) nên hàm
 * [onMessageReceived] luôn chạy — kể cả khi app đóng — và [PushNotifier] quyết định hiện hay không
 * theo công tắc người dùng chọn.
 */
class KgyMsMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifier.show(applicationContext, message.data)
    }

    /** FCM đổi token (cài lại, xóa dữ liệu...): đăng ký lại nếu đang có người đăng nhập. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val signedIn = runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)
        if (!signedIn) return
        scope.launch { PushTokenRepository(tokenProvider = { token }).register() }
    }
}
