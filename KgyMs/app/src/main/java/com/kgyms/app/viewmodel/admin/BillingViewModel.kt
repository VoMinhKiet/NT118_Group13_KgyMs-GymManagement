package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import com.kgyms.app.data.model.AdminTransaction
import com.kgyms.app.data.repository.AdminMockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class InvoiceDraftUi(
    val fields: List<DetailFieldUi>,
    val total: String
)

data class TransactionRowUi(
    val invoice: String,
    /** "#INV-1092 • 1.990.000đ" */
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
    val fields: List<DetailFieldUi>
)

data class TransactionsUiState(
    val filter: String = BillingViewModel.FILTER_ALL,
    val filters: List<OptionUi> = listOf(
        OptionUi(BillingViewModel.FILTER_ALL, "Tất cả"),
        OptionUi(BillingViewModel.FILTER_PAID, "Thành công"),
        OptionUi(BillingViewModel.FILTER_PENDING, "Chờ")
    ),
    val rows: List<TransactionRowUi> = emptyList()
)

/** Hóa đơn mới, Lịch sử giao dịch và Biên lai. */
class BillingViewModel(
    repository: AdminMockRepository = AdminMockRepository()
) : ViewModel() {

    private val transactions = repository.transactions()

    val invoiceDraft: InvoiceDraftUi = repository.invoiceDraft().let { draft ->
        InvoiceDraftUi(
            fields = listOf(
                DetailFieldUi("Hội viên", draft.memberLabel),
                DetailFieldUi("Dịch vụ", draft.service),
                DetailFieldUi("Giảm giá", "${draft.discountPercent}%")
            ),
            total = AdminFormat.vnd(draft.total)
        )
    }

    private val _transactions = MutableStateFlow(TransactionsUiState().recompute())
    val transactionsState: StateFlow<TransactionsUiState> = _transactions.asStateFlow()

    fun onFilterSelected(filter: String) = _transactions.update { it.copy(filter = filter).recompute() }

    fun receipt(invoice: String): ReceiptUi? = transactions.firstOrNull { it.invoice == invoice }?.let { t ->
        ReceiptUi(
            invoice = t.invoice,
            amount = AdminFormat.vnd(t.amount),
            statusLabel = statusLabel(t),
            tone = tone(t),
            fields = listOf(
                DetailFieldUi("Hội viên", t.memberName),
                DetailFieldUi("Dịch vụ", t.service),
                DetailFieldUi("Thời gian", t.date)
            )
        )
    }

    private fun TransactionsUiState.recompute() = copy(
        rows = transactions
            .filter {
                when (filter) {
                    FILTER_PAID -> it.isPaid
                    FILTER_PENDING -> !it.isPaid
                    else -> true
                }
            }
            .map {
                TransactionRowUi(
                    invoice = it.invoice,
                    title = "${it.invoice} • ${AdminFormat.vnd(it.amount)}",
                    subtitle = "${it.memberName} • ${it.service}",
                    date = it.date,
                    statusLabel = statusLabel(it),
                    tone = tone(it)
                )
            }
    )

    private fun statusLabel(t: AdminTransaction) = if (t.isPaid) "Đã thanh toán" else "Chờ thanh toán"

    private fun tone(t: AdminTransaction) = if (t.isPaid) AdminTone.Positive else AdminTone.Warning

    companion object {
        const val FILTER_ALL = "all"
        const val FILTER_PAID = "paid"
        const val FILTER_PENDING = "pending"
    }
}
