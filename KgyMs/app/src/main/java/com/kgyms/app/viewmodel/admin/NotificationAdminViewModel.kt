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

/* ============================ UI MODEL ============================ */

data class ReminderUi(
    val enabled: Boolean = false,
    val dayOptions: List<OptionUi> = ExpiryReminderSettings.AVAILABLE_DAYS.map { OptionUi(it.toString(), "$it ngày") },
    val selectedDays: Set<String> = setOf("3"),
    val lastRunText: String = "Chưa quét lần nào. App tự quét mỗi khi admin mở app.",
    val isScanning: Boolean = false
)

data class BroadcastRowUi(
    val id: String,
    val title: String,
    /** "Ưu đãi • Tất cả user • 10 người" */
    val subtitle: String,
    val time: String,
    val tone: AdminTone
)

data class BroadcastDetailUi(
    val id: String,
    val title: String,
    val message: String,
    val info: String,
    val recipientCount: Int,
    val isEditing: Boolean = false,
    val editTitle: String = "",
    val editMessage: String = "",
    val isBusy: Boolean = false,
    val confirmingDelete: Boolean = false,
    val error: String? = null
)

data class RecipientChoiceUi(val uid: String, val name: String, val email: String)

/** Form "Soạn thông báo". */
data class ComposeFormUi(
    val type: String = NotificationType.PROMOTION,
    val target: String = NotificationTarget.ALL_USERS,
    val packageId: String = "",
    val targetUid: String = "",
    val userQuery: String = "",
    val title: String = "",
    val message: String = "",
    val isSending: Boolean = false,
    val error: String? = null
)

data class NotificationAdminUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val reminder: ReminderUi = ReminderUi(),
    val broadcasts: List<BroadcastRowUi> = emptyList(),
    /** null = sheet soạn đang đóng. */
    val compose: ComposeFormUi? = null,
    val recipientCount: Int = 0,
    val packageOptions: List<OptionUi> = emptyList(),
    val recipientChoices: List<RecipientChoiceUi> = emptyList(),
    /** Lần gửi đang mở xem chi tiết. */
    val detail: BroadcastDetailUi? = null,
    val message: String? = null
) {
    val typeOptions: List<OptionUi> = NotificationType.all.map { OptionUi(it, NotificationLabels.type(it)) }
    val targetOptions: List<OptionUi> = NotificationTarget.all.map { OptionUi(it, NotificationLabels.target(it)) }
}

object NotificationLabels {
    fun type(type: String) = when (type) {
        NotificationType.PROMOTION -> "Ưu đãi"
        NotificationType.REMINDER -> "Nhắc nhở"
        else -> "Thông báo chung"
    }

    fun tone(type: String) = when (type) {
        NotificationType.PROMOTION -> AdminTone.Warning
        NotificationType.REMINDER -> AdminTone.Info
        else -> AdminTone.Positive
    }

    fun target(target: String) = when (target) {
        NotificationTarget.ALL_USERS -> "Tất cả user"
        NotificationTarget.ACTIVE_MEMBERS -> "Hội viên có gói"
        else -> "1 cá nhân"
    }
}

/* ============================ VIEWMODEL ============================ */

class NotificationAdminViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationAdminUiState())
    val state: StateFlow<NotificationAdminUiState> = _state.asStateFlow()

    private var broadcasts: List<NotificationBroadcast> = emptyList()
    private var settings = ExpiryReminderSettings()
    private var users: List<RecipientUser> = emptyList()
    private var packages: List<PackageOption> = emptyList()
    private var memberships: List<MembershipSummary> = emptyList()

    /** Nhắc hạn tự động chỉ chạy 1 lần mỗi phiên admin. */
    private var autoReminderStarted = false

    init {
        viewModelScope.launch {
            repository.observeBroadcasts()
                .catch { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { list ->
                    val parser = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.ROOT)
                    broadcasts = list.sortedByDescending {
                        runCatching { parser.parse(it.createdAt)?.time }.getOrNull() ?: Long.MIN_VALUE
                    }
                    _state.update { it.copy(isLoading = false, broadcasts = broadcasts.map(::toRow)).refreshDetail() }
                }
        }
        viewModelScope.launch {
            repository.observeExpirySettings()
                .catch { /* lỗi quyền đã hiện ở danh sách gửi */ }
                .collect { value ->
                    settings = value
                    _state.update { it.copy(reminder = reminderUi(it.reminder.isScanning)) }
                }
        }
        refreshReferenceData()
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /* ---------- Nhắc hết hạn ---------- */

    /** Gọi khi admin vào app: tự quét và gửi nhắc hết hạn nếu đang bật. */
    fun runAutoRemindersOnce() {
        if (autoReminderStarted) return
        autoReminderStarted = true
        viewModelScope.launch { runSafely { repository.runExpiryReminders() } }
    }

    fun onReminderToggle(enabled: Boolean) = saveSettings(enabled, settings.days)

    fun onReminderDayToggle(key: String) {
        val day = key.toIntOrNull() ?: return
        saveSettings(settings.enabled, if (day in settings.days) settings.days - day else settings.days + day)
    }

    fun scanNow() {
        if (_state.value.reminder.isScanning) return
        _state.update { it.copy(reminder = it.reminder.copy(isScanning = true)) }
        viewModelScope.launch {
            val result = runSafely { repository.runExpiryReminders() }
            val text = result.fold(
                onSuccess = { count ->
                    when {
                        count < 0 -> "Nhắc hết hạn đang tắt. Bật công tắc trước khi quét."
                        count == 0 -> "Không có hội viên nào cần nhắc lúc này."
                        else -> "Đã gửi nhắc hết hạn cho $count hội viên."
                    }
                },
                onFailure = { it.toUserMessage() }
            )
            _state.update { it.copy(reminder = it.reminder.copy(isScanning = false), message = text) }
        }
    }

    private fun saveSettings(enabled: Boolean, days: Set<Int>) {
        if (enabled && days.isEmpty()) {
            _state.update { it.copy(message = "Chọn ít nhất 1 mốc ngày để nhắc.") }
            return
        }
        // Hiện ngay trên giao diện, Firebase sẽ đẩy lại giá trị thật.
        settings = settings.copy(enabled = enabled, days = days)
        _state.update { it.copy(reminder = reminderUi(it.reminder.isScanning)) }
        viewModelScope.launch {
            runSafely { repository.saveExpirySettings(enabled, days) }
                .onFailure { e -> _state.update { it.copy(message = e.toUserMessage()) } }
        }
    }

    private fun reminderUi(isScanning: Boolean) = ReminderUi(
        enabled = settings.enabled,
        selectedDays = settings.days.map { it.toString() }.toSet(),
        lastRunText = if (settings.lastRunAt.isBlank()) "Chưa quét lần nào. App tự quét mỗi khi admin mở app."
        else "Lần quét gần nhất: ${settings.lastRunAt} • đã gửi ${settings.lastRunCount}",
        isScanning = isScanning
    )

    /* ---------- Soạn thông báo ---------- */

    fun openCompose() {
        refreshReferenceData()
        _state.update { it.withCompose(ComposeFormUi()) }
    }

    /** Mở form soạn thông báo riêng cho 1 người (vd từ màn chi tiết hội viên). */
    fun openComposeFor(uid: String) {
        refreshReferenceData()
        _state.update {
            it.withCompose(ComposeFormUi(type = NotificationType.GENERAL, target = NotificationTarget.USER, targetUid = uid))
        }
    }

    fun closeCompose() = _state.update { if (it.compose?.isSending == true) it else it.copy(compose = null) }

    fun onComposeChange(form: ComposeFormUi) = _state.update { it.withCompose(form.copy(error = null)) }

    fun send() {
        val form = _state.value.compose ?: return
        validate(form.title, form.message)?.let { return setComposeError(form, it) }
        if (form.target == NotificationTarget.USER && form.targetUid.isBlank()) return setComposeError(form, "Vui lòng chọn người nhận.")

        val draft = form.toDraft()
        val recipients = repository.resolveRecipients(draft, users, memberships)
        if (recipients.isEmpty()) return setComposeError(form, "Không có người nhận nào phù hợp với lựa chọn này.")

        _state.update { it.copy(compose = form.copy(isSending = true)) }
        viewModelScope.launch {
            val result = runSafely { repository.sendBroadcast(draft, recipients, targetLabel(form)) }
            _state.update {
                result.fold(
                    onSuccess = { count -> it.copy(compose = null, message = "Đã gửi thông báo tới $count người") },
                    onFailure = { e -> it.copy(compose = form.copy(isSending = false, error = e.toUserMessage())) }
                )
            }
        }
    }

    private fun setComposeError(form: ComposeFormUi, error: String) =
        _state.update { it.copy(compose = form.copy(error = error)) }

    private fun NotificationAdminUiState.withCompose(form: ComposeFormUi): NotificationAdminUiState {
        val keyword = form.userQuery.trim()
        return copy(
            compose = form,
            recipientCount = repository.resolveRecipients(form.toDraft(), users, memberships).size,
            packageOptions = listOf(OptionUi("", "Mọi gói")) + packages.map { OptionUi(it.id, it.name) },
            recipientChoices = users
                .filter {
                    keyword.isEmpty() || it.fullName.contains(keyword, ignoreCase = true) ||
                        it.email.contains(keyword, ignoreCase = true)
                }
                .map { RecipientChoiceUi(it.uid, it.fullName.ifBlank { "(Chưa có tên)" }, it.email) }
        )
    }

    private fun refreshReferenceData() {
        viewModelScope.launch {
            runSafely {
                users = repository.getRecipientUsers()
                packages = repository.getPackages()
                memberships = repository.getMemberships()
            }
            _state.update { state -> state.compose?.let { state.withCompose(it) } ?: state }
        }
    }

    /* ---------- Chi tiết / sửa / thu hồi ---------- */

    fun openDetail(id: String) = _state.update { state ->
        state.copy(detail = broadcasts.firstOrNull { it.id == id }?.let(::toDetail))
    }

    fun closeDetail() = _state.update { if (it.detail?.isBusy == true) it else it.copy(detail = null) }

    fun startEdit() = updateDetail { it.copy(isEditing = true, editTitle = it.title, editMessage = it.message, error = null) }

    fun cancelEdit() = updateDetail { it.copy(isEditing = false, error = null) }

    fun onEditChange(title: String, message: String) = updateDetail { it.copy(editTitle = title, editMessage = message, error = null) }

    fun saveEdit() {
        val detail = _state.value.detail ?: return
        val broadcast = broadcasts.firstOrNull { it.id == detail.id } ?: return
        validate(detail.editTitle, detail.editMessage)?.let { e -> return updateDetail { it.copy(error = e) } }

        updateDetail { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = runSafely { repository.editBroadcast(broadcast, detail.editTitle.trim(), detail.editMessage.trim()) }
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(detail = null, message = "Đã cập nhật thông báo cho ${broadcast.recipients.size} người") },
                    onFailure = { e -> it.copy(detail = detail.copy(isBusy = false, error = e.toUserMessage())) }
                )
            }
        }
    }

    fun askDelete() = updateDetail { it.copy(confirmingDelete = true) }

    fun cancelDelete() = updateDetail { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        val detail = _state.value.detail ?: return
        val broadcast = broadcasts.firstOrNull { it.id == detail.id } ?: return
        updateDetail { it.copy(confirmingDelete = false, isBusy = true) }
        viewModelScope.launch {
            val result = runSafely { repository.deleteBroadcast(broadcast) }
            _state.update {
                it.copy(detail = null, message = result.exceptionOrNull()?.toUserMessage() ?: "Đã thu hồi thông báo")
            }
        }
    }

    private fun updateDetail(block: (BroadcastDetailUi) -> BroadcastDetailUi) =
        _state.update { state -> state.detail?.let { state.copy(detail = block(it)) } ?: state }

    /** Giữ dialog chi tiết đồng bộ khi dữ liệu Firebase đổi (vd lần gửi đã bị thu hồi ở nơi khác). */
    private fun NotificationAdminUiState.refreshDetail(): NotificationAdminUiState {
        val current = detail ?: return this
        if (current.isEditing || current.isBusy) return this
        val fresh = this@NotificationAdminViewModel.broadcasts.firstOrNull { it.id == current.id }
            ?: return copy(detail = null)
        return copy(detail = toDetail(fresh).copy(confirmingDelete = current.confirmingDelete))
    }

    /* ============================ MAPPING ============================ */

    private fun toRow(b: NotificationBroadcast) = BroadcastRowUi(
        id = b.id,
        title = b.title,
        subtitle = "${NotificationLabels.type(b.type)} • ${b.targetLabel.ifBlank { NotificationLabels.target(b.target) }} • ${b.recipients.size} người",
        time = b.createdAt,
        tone = NotificationLabels.tone(b.type)
    )

    private fun toDetail(b: NotificationBroadcast) = BroadcastDetailUi(
        id = b.id,
        title = b.title,
        message = b.message,
        info = "${NotificationLabels.type(b.type)} • ${b.targetLabel} • ${b.recipients.size} người\n${b.createdAt}",
        recipientCount = b.recipients.size
    )

    private fun targetLabel(form: ComposeFormUi): String = when (form.target) {
        NotificationTarget.ALL_USERS -> "Tất cả user"
        NotificationTarget.ACTIVE_MEMBERS ->
            packages.firstOrNull { it.id == form.packageId }?.name?.let { "Hội viên gói $it" } ?: "Hội viên đang có gói"
        NotificationTarget.USER ->
            users.firstOrNull { it.uid == form.targetUid }?.let { it.fullName.ifBlank { it.email } } ?: "1 người"
        else -> form.target
    }

    private fun validate(title: String, message: String): String? = when {
        title.isBlank() -> "Vui lòng nhập tiêu đề."
        message.isBlank() -> "Vui lòng nhập nội dung."
        title.trim().length > MAX_TITLE -> "Tiêu đề tối đa $MAX_TITLE ký tự."
        message.trim().length > MAX_MESSAGE -> "Nội dung tối đa $MAX_MESSAGE ký tự."
        else -> null
    }

    private fun ComposeFormUi.toDraft() = NotificationDraft(
        title = title.trim(),
        message = message.trim(),
        type = type,
        target = target,
        packageId = if (target == NotificationTarget.ACTIVE_MEMBERS) packageId else "",
        targetUid = if (target == NotificationTarget.USER) targetUid else ""
    )

    private inline fun <T> runSafely(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Throwable.toUserMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("permission", ignoreCase = true) -> "Bạn không có quyền quản lý thông báo (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }

    private companion object {
        /** Khớp giới hạn trong database.rules.json (notifications). */
        const val MAX_TITLE = 200
        const val MAX_MESSAGE = 2000
    }
}
