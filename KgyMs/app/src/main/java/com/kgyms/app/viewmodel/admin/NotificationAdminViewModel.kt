package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.ExpiryReminderSettings
import com.kgyms.app.data.model.MembershipSummary
import com.kgyms.app.data.model.NotificationBroadcast
import com.kgyms.app.data.model.NotificationTarget
import com.kgyms.app.data.model.NotificationType
import com.kgyms.app.data.model.PackageOption
import com.kgyms.app.data.model.RecipientUser
import com.kgyms.app.data.repository.NotificationDraft
import com.kgyms.app.data.repository.NotificationRepository
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationAdminUiState(
    val broadcasts: List<NotificationBroadcast> = emptyList(),
    val settings: ExpiryReminderSettings = ExpiryReminderSettings(),
    val users: List<RecipientUser> = emptyList(),
    val packages: List<PackageOption> = emptyList(),
    val memberships: List<MembershipSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val errorMessage: String? = null
)

/** Nội dung form "Soạn thông báo" (chưa kiểm tra). */
data class NotificationForm(
    val title: String = "",
    val message: String = "",
    val type: String = NotificationType.PROMOTION,
    val target: String = NotificationTarget.ALL_USERS,
    val packageId: String = "",
    val targetUid: String = ""
)

class NotificationAdminViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationAdminUiState())
    val state: StateFlow<NotificationAdminUiState> = _state.asStateFlow()

    /** Nhắc hạn tự động chỉ chạy 1 lần mỗi phiên admin. */
    private var autoReminderStarted = false

    init {
        viewModelScope.launch {
            repository.observeBroadcasts()
                .catch { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { list ->
                    val parser = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT)
                    val sorted = list.sortedByDescending {
                        runCatching { parser.parse(it.createdAt)?.time }.getOrNull() ?: Long.MIN_VALUE
                    }
                    _state.update { it.copy(broadcasts = sorted, isLoading = false) }
                }
        }
        viewModelScope.launch {
            repository.observeExpirySettings()
                .catch { /* lỗi quyền đã hiện ở danh sách gửi */ }
                .collect { settings -> _state.update { it.copy(settings = settings) } }
        }
        refreshReferenceData()
    }

    /** Tải lại danh sách user, gói, memberships (dùng khi mở form soạn). */
    fun refreshReferenceData() {
        viewModelScope.launch {
            runCatchingNonCancel {
                val users = repository.getRecipientUsers()
                val packages = repository.getPackages()
                val memberships = repository.getMemberships()
                _state.update { it.copy(users = users, packages = packages, memberships = memberships) }
            }
        }
    }

    /** Gọi khi admin vào app: tự quét nhắc hết hạn nếu đang bật. */
    fun runAutoRemindersOnce() {
        if (autoReminderStarted) return
        autoReminderStarted = true
        viewModelScope.launch {
            runCatchingNonCancel { repository.runExpiryReminders() }
        }
    }

    /** Nút "Quét & gửi ngay". onResult nhận thông báo kết quả để hiển thị. */
    fun scanNow(onResult: (String) -> Unit) {
        if (_state.value.isScanning) return
        _state.update { it.copy(isScanning = true) }
        viewModelScope.launch {
            val result = runCatchingNonCancel { repository.runExpiryReminders() }
            _state.update { it.copy(isScanning = false) }
            onResult(
                result.fold(
                    onSuccess = { count ->
                        when {
                            count < 0 -> "Nhắc hết hạn đang tắt. Bật công tắc trước khi quét."
                            count == 0 -> "Không có hội viên nào cần nhắc lúc này."
                            else -> "Đã gửi nhắc hết hạn cho $count hội viên."
                        }
                    },
                    onFailure = { it.toUserMessage() }
                )
            )
        }
    }

    fun saveExpirySettings(enabled: Boolean, days: Set<Int>, onError: (String) -> Unit) {
        if (enabled && days.isEmpty()) {
            onError("Chọn ít nhất 1 mốc ngày để nhắc.")
            return
        }
        // Cập nhật giao diện ngay, Firebase sẽ đẩy lại giá trị thật.
        _state.update { it.copy(settings = it.settings.copy(enabled = enabled, days = days)) }
        viewModelScope.launch {
            runCatchingNonCancel { repository.saveExpirySettings(enabled, days) }
                .onFailure { onError(it.toUserMessage()) }
        }
    }

    /** Số người sẽ nhận với lựa chọn hiện tại (để hiện trước khi gửi). */
    fun recipientCount(form: NotificationForm): Int {
        val s = _state.value
        return repository.resolveRecipients(form.toDraft(), s.users, s.memberships).size
    }

    /** onResult: null nếu thành công, ngược lại là lỗi. */
    fun send(form: NotificationForm, onResult: (String?) -> Unit) {
        validate(form)?.let { return onResult(it) }

        val s = _state.value
        val draft = form.toDraft()
        val recipients = repository.resolveRecipients(draft, s.users, s.memberships)
        if (recipients.isEmpty()) return onResult("Không có người nhận nào phù hợp với lựa chọn này.")

        viewModelScope.launch {
            val result = runCatchingNonCancel {
                repository.sendBroadcast(draft, recipients, targetLabel(form))
            }
            onResult(result.exceptionOrNull()?.toUserMessage())
        }
    }

    fun edit(broadcast: NotificationBroadcast, title: String, message: String, onResult: (String?) -> Unit) {
        if (title.isBlank()) return onResult("Vui lòng nhập tiêu đề.")
        if (message.isBlank()) return onResult("Vui lòng nhập nội dung.")
        if (title.trim().length > MAX_TITLE) return onResult("Tiêu đề tối đa $MAX_TITLE ký tự.")
        if (message.trim().length > MAX_MESSAGE) return onResult("Nội dung tối đa $MAX_MESSAGE ký tự.")
        viewModelScope.launch {
            val result = runCatchingNonCancel { repository.editBroadcast(broadcast, title.trim(), message.trim()) }
            onResult(result.exceptionOrNull()?.toUserMessage())
        }
    }

    fun delete(broadcast: NotificationBroadcast, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatchingNonCancel { repository.deleteBroadcast(broadcast) }
            onResult(result.exceptionOrNull()?.toUserMessage())
        }
    }

    /** Mô tả người nhận, vd "Hội viên gói Premium 6 tháng", "Kiettest". */
    fun targetLabel(form: NotificationForm): String {
        val s = _state.value
        return when (form.target) {
            NotificationTarget.ALL_USERS -> "Tất cả user"
            NotificationTarget.ACTIVE_MEMBERS -> {
                val pkg = s.packages.firstOrNull { it.id == form.packageId }?.name
                if (pkg == null) "Hội viên đang có gói" else "Hội viên gói $pkg"
            }
            NotificationTarget.USER -> s.users.firstOrNull { it.uid == form.targetUid }
                ?.let { it.fullName.ifBlank { it.email } } ?: "1 người"
            else -> form.target
        }
    }

    private fun validate(form: NotificationForm): String? = when {
        form.title.isBlank() -> "Vui lòng nhập tiêu đề."
        form.message.isBlank() -> "Vui lòng nhập nội dung."
        form.title.trim().length > MAX_TITLE -> "Tiêu đề tối đa $MAX_TITLE ký tự."
        form.message.trim().length > MAX_MESSAGE -> "Nội dung tối đa $MAX_MESSAGE ký tự."
        form.type !in NotificationType.all -> "Loại thông báo không hợp lệ."
        form.target == NotificationTarget.USER && form.targetUid.isBlank() -> "Vui lòng chọn người nhận."
        else -> null
    }

    private fun NotificationForm.toDraft() = NotificationDraft(
        title = title.trim(),
        message = message.trim(),
        type = type,
        target = target,
        packageId = if (target == NotificationTarget.ACTIVE_MEMBERS) packageId else "",
        targetUid = if (target == NotificationTarget.USER) targetUid else ""
    )

    private companion object {
        /** Khớp giới hạn trong database.rules.json (notifications). */
        const val MAX_TITLE = 200
        const val MAX_MESSAGE = 2000
    }

    private inline fun <T> runCatchingNonCancel(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Throwable.toUserMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("permission", ignoreCase = true) ->
                "Bạn không có quyền quản lý thông báo (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }
}
