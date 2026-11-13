package com.kgyms.app.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.HomeNotification
import com.kgyms.app.data.repository.NotificationRepository
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Hộp thư thông báo của hội viên đang đăng nhập (notifications/{uid}), realtime.
 * [isLive] = false khi chưa đăng nhập (preview/test) → màn hình dùng dữ liệu mẫu.
 */
data class MemberNotificationUiState(
    val isLive: Boolean = false,
    val notifications: List<HomeNotification> = emptyList()
) {
    val hasUnread: Boolean get() = notifications.any { !it.isRead }
}

class MemberNotificationViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val uid: String? = runCatching { repository.currentUid() }.getOrNull()

    private val _state = MutableStateFlow(MemberNotificationUiState(isLive = uid != null))
    val state: StateFlow<MemberNotificationUiState> = _state.asStateFlow()

    init {
        uid?.let { id ->
            viewModelScope.launch {
                val parser = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT)
                repository.observeMyNotifications(id)
                    .catch { /* mất quyền/mạng: giữ danh sách hiện tại */ }
                    .collect { list ->
                        val rows = list
                            .sortedByDescending {
                                runCatching { parser.parse(it.createdAt)?.time }.getOrNull() ?: Long.MIN_VALUE
                            }
                            .map {
                                HomeNotification(
                                    title = it.title,
                                    message = it.message,
                                    isRead = it.isRead,
                                    time = it.createdAt,
                                    id = it.id
                                )
                            }
                        _state.value = MemberNotificationUiState(isLive = true, notifications = rows)
                    }
            }
        }
    }

    /** Gọi khi hội viên mở danh sách thông báo. */
    fun markAllRead() {
        val id = uid ?: return
        val unread = _state.value.notifications.filter { !it.isRead && it.id.isNotBlank() }.map { it.id }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.markRead(id, unread)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Lần mở sau sẽ thử lại.
            }
        }
    }
}
