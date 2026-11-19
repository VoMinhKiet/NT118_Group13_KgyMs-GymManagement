package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.PaymentStatus
import com.kgyms.app.data.repository.ReconciliationRecord
import com.kgyms.app.data.repository.ReconciliationRepository
import com.kgyms.app.data.util.AppDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ============================ UI MODEL ============================ */

data class ReconciliationRowUi(
    val id: String,
    /** "Chuyển thiếu tiền" */
    val reasonLabel: String,
    val reasonTone: AdminTone,
    /** "Nhận 999.000đ • cần 1.000.000đ" */
    val amountLine: String,
    val content: String,
    /** "2026-11-19 10:00:00 • GD 100521933" */
    val meta: String,
    /** "Hoá đơn KGAB23CDEF • Chờ thanh toán • Kiệt V"; rỗng nếu không có hoá đơn. */
    val invoiceLine: String,
    val paymentId: String,
    val isOpen: Boolean,
    /** "Đã kích hoạt • 10:05 19/11/2026 • ghi chú"; chỉ có khi đã xử lý. */
    val resolutionLine: String
)

/** Hộp thoại xử lý một giao dịch. */
data class ReconciliationDialogUi(
    val id: String,
    val action: String = ReconciliationRepository.ACTIVATE,
    val paymentId: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class ReconciliationUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val filter: String = ReconciliationViewModel.FILTER_OPEN,
    val filters: List<OptionUi> = emptyList(),
    val rows: List<ReconciliationRowUi> = emptyList(),
    val openCount: Int = 0,
    val dialog: ReconciliationDialogUi? = null,
    val message: String? = null
) {
    val actionOptions: List<OptionUi> = listOf(
        OptionUi(ReconciliationRepository.ACTIVATE, "Kích hoạt hoá đơn"),
        OptionUi(ReconciliationRepository.REFUNDED, "Đã hoàn tiền"),
        OptionUi(ReconciliationRepository.IGNORED, "Bỏ qua")
    )
}

/* ============================ VIEWMODEL ============================ */

/** Đối soát ngân hàng: giao dịch SePay mà server không tự khớp được với hoá đơn. */
class ReconciliationViewModel(
    private val repository: ReconciliationRepository = ReconciliationRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ReconciliationUiState())
    val state: StateFlow<ReconciliationUiState> = _state.asStateFlow()

    private var records: List<ReconciliationRecord> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeRecords()
                .catch { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { list ->
                    records = list.sortedByDescending { AppDates.parseTimestamp(it.item.createdAt) ?: 0L }
                    _state.update { it.copy(isLoading = false, errorMessage = null).recompute() }
                }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onFilterSelected(filter: String) = _state.update { it.copy(filter = filter).recompute() }

    /* ---------- Xử lý ---------- */

    fun openDialog(id: String) {
        val record = records.firstOrNull { it.item.id == id && it.item.status == STATUS_OPEN } ?: return
        // Không có hoá đơn trong nội dung CK thì gợi ý "Đã hoàn tiền" thay vì kích hoạt.
        val action = if (record.item.paymentId.isBlank()) ReconciliationRepository.REFUNDED else ReconciliationRepository.ACTIVATE
        _state.update { it.copy(dialog = ReconciliationDialogUi(id = id, action = action, paymentId = record.item.paymentId)) }
    }

    fun onDialogChange(dialog: ReconciliationDialogUi) =
        _state.update { if (it.dialog?.isSaving == true) it else it.copy(dialog = dialog.copy(error = null)) }

    fun dismissDialog() = _state.update { if (it.dialog?.isSaving == true) it else it.copy(dialog = null) }

    fun submitDialog() {
        val dialog = _state.value.dialog ?: return
        if (dialog.action == ReconciliationRepository.ACTIVATE && dialog.paymentId.isBlank()) {
            return _state.update { it.copy(dialog = dialog.copy(error = "Nhập mã hoá đơn cần kích hoạt (KG...).")) }
        }
        _state.update { it.copy(dialog = dialog.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            val result = try {
                repository.resolve(
                    id = dialog.id,
                    action = dialog.action,
                    paymentId = dialog.paymentId.takeIf { dialog.action == ReconciliationRepository.ACTIVATE },
                    note = dialog.note
                )
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(dialog = null, message = successMessage(dialog)) },
                    onFailure = { e -> it.copy(dialog = dialog.copy(isSaving = false, error = e.toUserMessage())) }
                )
            }
        }
    }

    /* ============================ MAPPING ============================ */

    private fun ReconciliationUiState.recompute(): ReconciliationUiState {
        val open = records.count { it.item.status == STATUS_OPEN }
        val visible = when (filter) {
            FILTER_OPEN -> records.filter { it.item.status == STATUS_OPEN }
            FILTER_RESOLVED -> records.filter { it.item.status != STATUS_OPEN }
            else -> records
        }
        return copy(
            openCount = open,
            filters = listOf(
                OptionUi(FILTER_OPEN, "Cần xử lý ($open)"),
                OptionUi(FILTER_RESOLVED, "Đã xử lý (${records.size - open})"),
                OptionUi(FILTER_ALL, "Tất cả (${records.size})")
            ),
            rows = visible.map { it.toRow() }
        )
    }

    private fun ReconciliationRecord.toRow(): ReconciliationRowUi {
        val r = item
        val isOpen = r.status == STATUS_OPEN
        return ReconciliationRowUi(
            id = r.id,
            reasonLabel = reasonLabel(r.reason),
            reasonTone = if (!isOpen) AdminTone.Neutral else if (r.reason == "activation_error") AdminTone.Critical else AdminTone.Warning,
            amountLine = buildString {
                append("Nhận ${AdminFormat.vnd(r.amount)}")
                if (r.expectedAmount > 0 && r.expectedAmount != r.amount) append(" • cần ${AdminFormat.vnd(r.expectedAmount)}")
            },
            content = r.content.ifBlank { "(không có nội dung)" },
            meta = listOf(r.transactionDate, "GD ${r.id}").filter { it.isNotBlank() }.joinToString(" • "),
            invoiceLine = if (r.paymentId.isBlank()) "" else listOf(
                "Hoá đơn ${r.paymentId}",
                payment?.let { paymentStatusLabel(it.status, it.isExpired()) } ?: "không tồn tại",
                memberName
            ).filter { it.isNotBlank() }.joinToString(" • "),
            paymentId = r.paymentId,
            isOpen = isOpen,
            resolutionLine = if (isOpen) "" else listOf(resolutionLabel(r.resolution), r.resolvedAt, r.note)
                .filter { it.isNotBlank() }.joinToString(" • ")
        )
    }

    private fun successMessage(dialog: ReconciliationDialogUi) = when (dialog.action) {
        ReconciliationRepository.ACTIVATE -> "Đã kích hoạt hoá đơn ${dialog.paymentId.trim().uppercase()}. Hội viên đã nhận thông báo."
        ReconciliationRepository.REFUNDED -> "Đã ghi nhận hoàn tiền giao dịch ${dialog.id}."
        else -> "Đã bỏ qua giao dịch ${dialog.id}."
    }

    private fun reasonLabel(reason: String) = when (reason) {
        "no_code" -> "Không có mã hoá đơn"
        "invoice_not_found" -> "Không tìm thấy hoá đơn"
        "invoice_not_pending" -> "Hoá đơn không còn chờ"
        "invoice_expired" -> "Hoá đơn đã hết hạn"
        "underpaid" -> "Chuyển thiếu tiền"
        "overpaid" -> "Chuyển dư tiền"
        "activation_error" -> "Lỗi khi kích hoạt gói"
        else -> reason.ifBlank { "Không rõ" }
    }

    private fun resolutionLabel(resolution: String) = when (resolution) {
        ReconciliationRepository.ACTIVATE -> "Đã kích hoạt"
        ReconciliationRepository.REFUNDED -> "Đã hoàn tiền"
        ReconciliationRepository.IGNORED -> "Đã bỏ qua"
        else -> "Đã xử lý"
    }

    private fun paymentStatusLabel(status: String, expired: Boolean) = when {
        status == PaymentStatus.PAID -> "Đã thanh toán"
        status == PaymentStatus.CANCELLED -> "Đã huỷ"
        expired -> "Chờ thanh toán (quá hạn)"
        else -> "Chờ thanh toán"
    }

    private fun Throwable.toUserMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("permission", ignoreCase = true) -> "Bạn không có quyền xem đối soát (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }

    companion object {
        const val FILTER_OPEN = "open"
        const val FILTER_RESOLVED = "resolved"
        const val FILTER_ALL = "all"
        private const val STATUS_OPEN = "open"
    }
}
