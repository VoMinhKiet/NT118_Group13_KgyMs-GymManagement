package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import com.kgyms.app.data.model.AdminComplaint
import com.kgyms.app.data.model.ComplaintStatus
import com.kgyms.app.data.repository.AdminMockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ComplaintRowUi(
    val id: String,
    /** "#CS-208 • Thiết bị hỏng" */
    val title: String,
    val subtitle: String,
    val priorityLabel: String,
    val tone: AdminTone
)

data class ComplaintDetailUi(
    val id: String,
    val title: String,
    val reporter: String,
    val statusLabel: String,
    val priorityLabel: String,
    val tone: AdminTone,
    val description: String
)

data class ComplaintUiState(
    val newCount: String,
    val inProgressCount: String,
    val rows: List<ComplaintRowUi>,
    /** Nội dung phản hồi đang soạn cho từng khiếu nại. */
    val drafts: Map<String, String> = emptyMap(),
    val message: String? = null
)

class ComplaintViewModel(
    repository: AdminMockRepository = AdminMockRepository()
) : ViewModel() {

    private val complaints = repository.complaints()

    private val _state = MutableStateFlow(
        ComplaintUiState(
            newCount = complaints.count { it.status == ComplaintStatus.New }.toString(),
            inProgressCount = complaints.count { it.status == ComplaintStatus.InProgress }.toString(),
            rows = complaints.map { c ->
                ComplaintRowUi(
                    id = c.id,
                    title = "${c.id} • ${c.title}",
                    subtitle = "${c.memberName} • ${c.date} • ${statusLabel(c)}",
                    priorityLabel = priorityLabel(c),
                    tone = tone(c)
                )
            }
        )
    )
    val state: StateFlow<ComplaintUiState> = _state.asStateFlow()

    fun detail(id: String): ComplaintDetailUi? = complaints.firstOrNull { it.id == id }?.let { c ->
        ComplaintDetailUi(
            id = c.id,
            title = c.title,
            reporter = "${c.memberName} • ${c.date}",
            statusLabel = statusLabel(c),
            priorityLabel = priorityLabel(c),
            tone = tone(c),
            description = c.description
        )
    }

    fun onDraftChange(id: String, text: String) = _state.update { it.copy(drafts = it.drafts + (id to text)) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** Chưa nối Firebase: chỉ kiểm tra nội dung và báo lại. */
    fun submitResponse(id: String) = _state.update {
        val text = it.drafts[id].orEmpty().trim()
        it.copy(message = if (text.isEmpty()) "Vui lòng nhập nội dung xử lý." else "Bản thử nghiệm: phản hồi chưa được lưu.")
    }

    private fun statusLabel(c: AdminComplaint) = if (c.status == ComplaintStatus.New) "Mới" else "Đang xử lý"

    private fun priorityLabel(c: AdminComplaint) = if (c.isHighPriority) "Ưu tiên cao" else "Thường"

    private fun tone(c: AdminComplaint) = when {
        c.isHighPriority -> AdminTone.Critical
        c.status == ComplaintStatus.InProgress -> AdminTone.Warning
        else -> AdminTone.Info
    }
}
