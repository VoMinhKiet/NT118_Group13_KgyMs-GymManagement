package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.PaymentMethod
import com.kgyms.app.data.model.PaymentReceiver
import com.kgyms.app.data.model.PaymentRecord
import com.kgyms.app.data.model.PaymentStatus
import com.kgyms.app.data.repository.AdminMockRepository
import com.kgyms.app.data.repository.PaymentRepository
import com.kgyms.app.data.util.AppDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvoiceDraftUi(
    val fields: List<DetailFieldUi>,
    val total: String
)

data class TransactionRowUi(
    val invoice: String,
    /** "KG7F3K9Q2A • 1.990.000đ" */
    val title: String,
    /** "Nguyễn Minh Anh • Premium 6 tháng" */
    val subtitle: String,
    val date: String,
    val statusLabel: String,
    val tone: AdminTone
)

data class ReceiptUi(
    val invoice: String,
    val amount: String,
    val statusLabel: String,
    val tone: AdminTone,
    val fields: List<DetailFieldUi>,
    /** Hoá đơn đang chờ → hiện nút "Xác nhận đã nhận tiền". */
    val canConfirm: Boolean
)

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val filter: String = BillingViewModel.FILTER_ALL,
    val filters: List<OptionUi> = listOf(
        OptionUi(BillingViewModel.FILTER_ALL, "Tất cả"),
        OptionUi(BillingViewModel.FILTER_PAID, "Thành công"),
        OptionUi(BillingViewModel.FILTER_PENDING, "Chờ"),
        OptionUi(BillingViewModel.FILTER_CANCELLED, "Đã huỷ")
    ),
    val rows: List<TransactionRowUi> = emptyList(),
    /** invoice → biên lai, để màn Biên lai đọc theo state realtime. */
    val receipts: Map<String, ReceiptUi> = emptyMap(),
    /** Hoá đơn đang chờ admin bấm xác nhận trong hộp thoại. */
    val confirmInvoice: String? = null,
    val isConfirming: Boolean = false,
    val message: String? = null
)

/** Hóa đơn mới (mẫu), Lịch sử giao dịch và Biên lai (Firebase node payments). */
class BillingViewModel(
    mockRepository: AdminMockRepository = AdminMockRepository(),
    private val repository: PaymentRepository = PaymentRepository()
) : ViewModel() {

    val invoiceDraft: InvoiceDraftUi = mockRepository.invoiceDraft().let { draft ->
        InvoiceDraftUi(
            fields = listOf(
                DetailFieldUi("Hội viên", draft.memberLabel),
                DetailFieldUi("Dịch vụ", draft.service),
                DetailFieldUi("Giảm giá", "${draft.discountPercent}%")
            ),
            total = AdminFormat.vnd(draft.total)
        )
    }

    private val _transactions = MutableStateFlow(TransactionsUiState())
    val transactionsState: StateFlow<TransactionsUiState> = _transactions.asStateFlow()

    private var records: List<PaymentRecord> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeAdminRecords()
                .catch { e -> _transactions.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { list ->
                    records = list.sortedByDescending { AppDates.parseTimestamp(it.payment.createdAt) ?: 0L }
                    _transactions.update { it.recompute() }
                }
        }
    }

    fun onFilterSelected(filter: String) = _transactions.update { it.copy(filter = filter).recompute() }

    fun onMessageShown() = _transactions.update { it.copy(message = null) }

    /* ---------- Xác nhận đã nhận tiền ---------- */

    fun requestConfirm(invoice: String) = _transactions.update {
        if (it.receipts[invoice]?.canConfirm == true) it.copy(confirmInvoice = invoice) else it
    }

    fun dismissConfirm() = _transactions.update { if (it.isConfirming) it else it.copy(confirmInvoice = null) }

    fun confirmPaid() {
        val invoice = _transactions.value.confirmInvoice ?: return
        _transactions.update { it.copy(isConfirming = true) }
        viewModelScope.launch {
            val result = try {
                Result.success(repository.confirmPaid(invoice))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            _transactions.update {
                it.copy(
                    isConfirming = false,
                    confirmInvoice = null,
                    message = result.exceptionOrNull()?.toUserMessage()
                        ?: "Đã xác nhận hoá đơn $invoice. Gói của hội viên đã được kích hoạt."
                )
            }
        }
    }

    /* ============================ MAPPING ============================ */

    private fun TransactionsUiState.recompute() = copy(
        isLoading = false,
        errorMessage = null,
        rows = records
            .filter { filter == FILTER_ALL || it.payment.status == filter }
            .map { r ->
                TransactionRowUi(
                    invoice = r.payment.id,
                    title = "${r.payment.id} • ${AdminFormat.vnd(r.payment.amount)}",
                    subtitle = "${r.memberName} • ${service(r)}",
                    date = r.payment.createdAt,
                    statusLabel = statusLabel(r.payment.status),
                    tone = tone(r.payment.status)
                )
            },
        receipts = records.associate { r -> r.payment.id to r.toReceipt() }
    )

    private fun PaymentRecord.toReceipt(): ReceiptUi {
        val p = payment
        val fields = buildList {
            add(DetailFieldUi("Hội viên", memberName))
            add(DetailFieldUi("Dịch vụ", service(this@toReceipt)))
            add(DetailFieldUi("Phương thức", if (p.method == PaymentMethod.CASH) "Tiền mặt" else "Chuyển khoản"))
            if (p.method == PaymentMethod.TRANSFER) add(DetailFieldUi("Nội dung CK", PaymentReceiver.transferContent(p.id)))
            if (p.discount > 0) add(DetailFieldUi("Giảm giá", AdminFormat.vnd(p.discount)))
            add(DetailFieldUi("Tạo lúc", p.createdAt.ifBlank { "—" }))
            if (p.paidAt.isNotBlank()) add(DetailFieldUi("Thanh toán lúc", p.paidAt, AdminTone.Positive))
            membership?.takeIf { it.startDate.isNotBlank() && it.endDate.isNotBlank() }?.let {
                add(DetailFieldUi("Thời hạn", "${it.startDate} – ${it.endDate}"))
            }
        }
        return ReceiptUi(
            invoice = p.id,
            amount = AdminFormat.vnd(p.amount),
            statusLabel = statusLabel(p.status),
            tone = tone(p.status),
            fields = fields,
            canConfirm = p.status == PaymentStatus.PENDING
        )
    }

    private fun service(r: PaymentRecord) = r.pkg?.name ?: "—"

    private fun statusLabel(status: String) = when (status) {
        PaymentStatus.PAID -> "Đã thanh toán"
        PaymentStatus.CANCELLED -> "Đã huỷ"
        else -> "Chờ thanh toán"
    }

    private fun tone(status: String) = when (status) {
        PaymentStatus.PAID -> AdminTone.Positive
        PaymentStatus.CANCELLED -> AdminTone.Neutral
        else -> AdminTone.Warning
    }

    private fun Throwable.toUserMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("permission", ignoreCase = true) -> "Bạn không có quyền xem/sửa giao dịch (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }

    companion object {
        const val FILTER_ALL = "all"
        const val FILTER_PAID = PaymentStatus.PAID
        const val FILTER_PENDING = PaymentStatus.PENDING
        const val FILTER_CANCELLED = PaymentStatus.CANCELLED
    }
}
