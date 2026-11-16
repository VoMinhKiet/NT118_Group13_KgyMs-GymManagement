package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.firebase.MemberService
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.MemberProfile
import com.kgyms.app.data.model.PaymentMethod
import com.kgyms.app.data.model.PaymentReceiver
import com.kgyms.app.data.model.PaymentRecord
import com.kgyms.app.data.model.PaymentStatus
import com.kgyms.app.data.repository.PaymentRepository
import com.kgyms.app.data.util.AppDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ============================ UI MODEL ============================ */

data class TransactionRowUi(
    val invoice: String,
    /** "KG7F3K9Q2A • 1.990.000đ" */
    val title: String,
    /** "Nguyễn Minh Anh • Premium 6 tháng" */
    val subtitle: String,
    /** "23:57 13/11/2026 • Chuyển khoản" */
    val meta: String,
    val statusLabel: String,
    val tone: AdminTone,
    val canConfirm: Boolean
)

data class ReceiptUi(
    val invoice: String,
    val memberUid: String,
    val amount: String,
    val statusLabel: String,
    val tone: AdminTone,
    val fields: List<DetailFieldUi>,
    /** Hoá đơn đang chờ → hiện nút xác nhận / huỷ. */
    val canConfirm: Boolean,
    /** Chuyển khoản đang chờ → hiện mã VietQR để khách quét. */
    val qrPayload: String?,
    val transferNote: String,
    /** Nội dung gửi qua bảng chia sẻ của Android. */
    val shareText: String
)

/** Form "Tạo hoá đơn tại quầy". */
data class DeskInvoiceFormUi(
    val memberUid: String = "",
    val memberQuery: String = "",
    val packageId: String = "",
    /** Chữ số; nghĩa là số tiền hoặc % tuỳ [discountPercent]. */
    val discount: String = "",
    val discountPercent: Boolean = false,
    val method: String = PaymentMethod.CASH,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class MemberChoiceUi(val uid: String, val name: String, val detail: String)

/** Phần tính sẵn của form để UI chỉ hiển thị. */
data class DeskInvoiceSummaryUi(
    val memberChoices: List<MemberChoiceUi> = emptyList(),
    val selectedMember: String = "",
    val packageOptions: List<OptionUi> = emptyList(),
    val price: String = "—",
    val discountAmount: String = "0đ",
    val total: String = "—",
    val submitLabel: String = "Thu tiền mặt & kích hoạt gói"
)

data class BillingUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    // Tổng quan
    val pendingCount: String = "–",
    val pendingTotal: String = "",
    val paidThisMonth: String = "–",
    val paidToday: String = "",
    val pendingRows: List<TransactionRowUi> = emptyList(),

    // Lịch sử giao dịch
    val query: String = "",
    val filter: String = BillingViewModel.FILTER_ALL,
    val filters: List<OptionUi> = emptyList(),
    val rows: List<TransactionRowUi> = emptyList(),
    val rowsSummary: String = "",

    // Biên lai
    val receipts: Map<String, ReceiptUi> = emptyMap(),

    // Tạo hoá đơn tại quầy (null = đóng)
    val deskForm: DeskInvoiceFormUi? = null,
    val deskSummary: DeskInvoiceSummaryUi = DeskInvoiceSummaryUi(),

    // Hộp thoại
    val confirmInvoice: String? = null,
    val cancelInvoice: String? = null,
    val isWorking: Boolean = false,

    /** Hoá đơn vừa tạo → màn hình mở Biên lai rồi gọi onOpenedInvoiceHandled(). */
    val openedInvoice: String? = null,
    val message: String? = null
) {
    val methodOptions: List<OptionUi> = listOf(
        OptionUi(PaymentMethod.CASH, "Tiền mặt"),
        OptionUi(PaymentMethod.TRANSFER, "Chuyển khoản (VietQR)")
    )
    val discountModeOptions: List<OptionUi> = listOf(OptionUi("amount", "Số tiền (đ)"), OptionUi("percent", "Phần trăm (%)"))
}

/* ============================ VIEWMODEL ============================ */

/** Quản lý hoá đơn (node payments): tổng quan, tạo tại quầy, lịch sử, biên lai. */
class BillingViewModel(
    private val repository: PaymentRepository = PaymentRepository(),
    private val memberService: MemberService = MemberService()
) : ViewModel() {

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private var records: List<PaymentRecord> = emptyList()
    private var members: List<MemberProfile> = emptyList()
    private var packages: List<GymPackage> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeAdminRecords()
                .catch { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { list ->
                    records = list.sortedByDescending { AppDates.parseTimestamp(it.payment.createdAt) ?: 0L }
                    _state.update { it.copy(isLoading = false, errorMessage = null).recompute() }
                }
        }
        viewModelScope.launch {
            memberService.observeMembers().catch { }.collect { list ->
                members = list.sortedBy { it.displayName.lowercase() }
                _state.update { it.recomputeDesk() }
            }
        }
        viewModelScope.launch {
            repository.observePackages().catch { }.collect { list ->
                packages = list.filter { it.isActive }.sortedWith(compareBy({ it.type }, { it.price }))
                _state.update { it.recomputeDesk() }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onOpenedInvoiceHandled() = _state.update { it.copy(openedInvoice = null) }

    /* ---------- Lịch sử ---------- */

    fun onQueryChange(query: String) = _state.update { it.copy(query = query).recompute() }

    fun onFilterSelected(filter: String) = _state.update { it.copy(filter = filter).recompute() }

    /* ---------- Xác nhận đã nhận tiền ---------- */

    fun requestConfirm(invoice: String) = _state.update {
        if (it.receipts[invoice]?.canConfirm == true) it.copy(confirmInvoice = invoice) else it
    }

    fun dismissConfirm() = _state.update { if (it.isWorking) it else it.copy(confirmInvoice = null) }

    fun confirmPaid() {
        val invoice = _state.value.confirmInvoice ?: return
        runAction(
            action = { repository.confirmPaid(invoice) },
            success = "Đã xác nhận hoá đơn $invoice. Gói của hội viên đã được kích hoạt."
        ) { it.copy(confirmInvoice = null) }
    }

    /* ---------- Huỷ hoá đơn ---------- */

    fun requestCancel(invoice: String) = _state.update {
        if (it.receipts[invoice]?.canConfirm == true) it.copy(cancelInvoice = invoice) else it
    }

    fun dismissCancel() = _state.update { if (it.isWorking) it else it.copy(cancelInvoice = null) }

    fun cancelInvoice() {
        val invoice = _state.value.cancelInvoice ?: return
        runAction(action = { repository.cancelByAdmin(invoice) }, success = "Đã huỷ hoá đơn $invoice") {
            it.copy(cancelInvoice = null)
        }
    }

    /* ---------- Tạo hoá đơn tại quầy ---------- */

    /** [memberUid] khác rỗng khi mở từ màn chi tiết hội viên (chọn sẵn người). */
    fun openDeskForm(memberUid: String = "") =
        _state.update { it.copy(deskForm = DeskInvoiceFormUi(memberUid = memberUid)).recomputeDesk() }

    fun closeDeskForm() = _state.update { if (it.deskForm?.isSaving == true) it else it.copy(deskForm = null) }

    fun onDeskFormChange(form: DeskInvoiceFormUi) = _state.update {
        it.copy(deskForm = form.copy(discount = form.discount.filter(Char::isDigit).take(10), error = null)).recomputeDesk()
    }

    fun submitDeskForm() {
        val form = _state.value.deskForm ?: return
        val pkg = packages.firstOrNull { it.id == form.packageId }
        val discount = pkg?.let { discountAmount(form, it) } ?: 0L
        val error = when {
            form.memberUid.isBlank() -> "Vui lòng chọn hội viên."
            pkg == null -> "Vui lòng chọn gói."
            form.discountPercent && (form.discount.toIntOrNull() ?: 0) > 100 -> "Giảm giá tối đa 100%."
            discount > pkg.price -> "Giảm giá không được lớn hơn giá gói."
            else -> null
        }
        if (error != null) return _state.update { it.copy(deskForm = form.copy(error = error)) }

        _state.update { it.copy(deskForm = form.copy(isSaving = true)) }
        viewModelScope.launch {
            val result = runSafely { repository.createDeskInvoice(form.memberUid, pkg!!, discount, form.method) }
            _state.update {
                result.fold(
                    onSuccess = { id ->
                        it.copy(
                            deskForm = null,
                            openedInvoice = id,
                            message = if (form.method == PaymentMethod.CASH) "Đã thu tiền hoá đơn $id, gói đã được kích hoạt."
                            else "Đã tạo hoá đơn $id. Cho khách quét mã VietQR để chuyển khoản."
                        )
                    },
                    onFailure = { e -> it.copy(deskForm = form.copy(isSaving = false, error = e.toUserMessage())) }
                )
            }
        }
    }

    /* ============================ MAPPING ============================ */

    private fun BillingUiState.recompute(): BillingUiState {
        val today = AppDates.startOfDay()
        val monthStart = java.util.Calendar.getInstance().apply {
            timeInMillis = today
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        val pending = records.filter { it.payment.status == PaymentStatus.PENDING }
        val paid = records.filter { it.payment.status == PaymentStatus.PAID }
            .map { it to (AppDates.parseTimestamp(it.payment.paidAt) ?: 0L) }
        val paidMonth = paid.filter { it.second >= monthStart }
        val paidToday = paid.filter { it.second >= today }

        val keyword = query.trim()
        val visible = records
            .filter { filter == FILTER_ALL || it.payment.status == filter }
            .filter { keyword.isEmpty() || "${it.payment.id} ${it.memberName} ${service(it)}".contains(keyword, ignoreCase = true) }
        val visiblePaid = visible.filter { it.payment.status == PaymentStatus.PAID }.sumOf { it.payment.amount }

        fun count(status: String) = records.count { it.payment.status == status }

        return copy(
            pendingCount = pending.size.toString(),
            pendingTotal = if (pending.isEmpty()) "Không có hoá đơn chờ" else "Tổng ${AdminFormat.vnd(pending.sumOf { it.payment.amount })}",
            paidThisMonth = AdminFormat.compact(paidMonth.sumOf { it.first.payment.amount }),
            paidToday = "Hôm nay ${AdminFormat.vnd(paidToday.sumOf { it.first.payment.amount })} • ${paidMonth.size} hoá đơn",
            pendingRows = pending.map { it.toRow() },
            filters = listOf(
                OptionUi(FILTER_ALL, "Tất cả (${records.size})"),
                OptionUi(FILTER_PENDING, "Chờ (${count(PaymentStatus.PENDING)})"),
                OptionUi(FILTER_PAID, "Thành công (${count(PaymentStatus.PAID)})"),
                OptionUi(FILTER_CANCELLED, "Đã huỷ (${count(PaymentStatus.CANCELLED)})")
            ),
            rows = visible.map { it.toRow() },
            rowsSummary = if (visible.isEmpty()) "" else "${visible.size} hoá đơn • đã thu ${AdminFormat.vnd(visiblePaid)}",
            receipts = records.associate { r -> r.payment.id to r.toReceipt() }
        )
    }

    private fun BillingUiState.recomputeDesk(): BillingUiState {
        val form = deskForm ?: return this
        val keyword = form.memberQuery.trim()
        val pkg = packages.firstOrNull { it.id == form.packageId }
        val discount = pkg?.let { discountAmount(form, it) } ?: 0L
        val selected = members.firstOrNull { it.uid == form.memberUid }
        return copy(
            deskSummary = DeskInvoiceSummaryUi(
                memberChoices = members
                    .filter { !it.isLocked }
                    .filter {
                        keyword.isEmpty() || "${it.displayName} ${it.email} ${it.phone} ${it.memberCode}".contains(keyword, ignoreCase = true)
                    }
                    .take(30)
                    .map { m -> MemberChoiceUi(m.uid, m.displayName, listOf(m.memberCode, m.email, m.phone).filter { it.isNotBlank() }.joinToString(" • ")) },
                selectedMember = selected?.let { m -> listOf(m.displayName, m.memberCode).filter { it.isNotBlank() }.joinToString(" • ") }.orEmpty(),
                packageOptions = packages.map { OptionUi(it.id, "${it.name} • ${AdminFormat.vnd(it.price)}") },
                price = pkg?.let { AdminFormat.vnd(it.price) } ?: "—",
                discountAmount = AdminFormat.vnd(discount),
                total = pkg?.let { AdminFormat.vnd((it.price - discount).coerceAtLeast(0)) } ?: "—",
                submitLabel = if (form.method == PaymentMethod.CASH) "Thu tiền mặt & kích hoạt gói" else "Tạo hoá đơn & hiện mã VietQR"
            )
        )
    }

    private fun discountAmount(form: DeskInvoiceFormUi, pkg: GymPackage): Long {
        val value = form.discount.toLongOrNull() ?: 0L
        return if (form.discountPercent) pkg.price * value.coerceAtMost(100) / 100 else value
    }

    private fun PaymentRecord.toRow() = TransactionRowUi(
        invoice = payment.id,
        title = "${payment.id} • ${AdminFormat.vnd(payment.amount)}",
        subtitle = "$memberName • ${service(this)}",
        meta = "${payment.createdAt} • ${methodLabel(payment.method)}",
        statusLabel = statusLabel(payment.status),
        tone = tone(payment.status),
        canConfirm = payment.status == PaymentStatus.PENDING
    )

    private fun PaymentRecord.toReceipt(): ReceiptUi {
        val p = payment
        val fields = buildList {
            add(DetailFieldUi("Hội viên", memberName))
            add(DetailFieldUi("Dịch vụ", service(this@toReceipt)))
            add(DetailFieldUi("Phương thức", methodLabel(p.method)))
            if (p.method == PaymentMethod.TRANSFER) add(DetailFieldUi("Nội dung CK", PaymentReceiver.transferContent(p.id)))
            if (p.discount > 0) add(DetailFieldUi("Giảm giá", AdminFormat.vnd(p.discount)))
            add(DetailFieldUi("Tạo lúc", p.createdAt.ifBlank { "—" }))
            if (p.paidAt.isNotBlank()) add(DetailFieldUi("Thanh toán lúc", p.paidAt, AdminTone.Positive))
            membership?.takeIf { it.startDate.isNotBlank() && it.endDate.isNotBlank() }?.let {
                add(DetailFieldUi("Thời hạn", "${it.startDate} – ${it.endDate}"))
            }
        }
        val pendingTransfer = p.status == PaymentStatus.PENDING && p.method == PaymentMethod.TRANSFER
        return ReceiptUi(
            invoice = p.id,
            memberUid = p.uid,
            amount = AdminFormat.vnd(p.amount),
            statusLabel = statusLabel(p.status),
            tone = tone(p.status),
            fields = fields,
            canConfirm = p.status == PaymentStatus.PENDING,
            qrPayload = if (pendingTransfer) repository.qrPayload(p) else null,
            transferNote = "${PaymentReceiver.BANK_NAME} • ${PaymentReceiver.ACCOUNT_NO} • ${PaymentReceiver.DISPLAY_NAME}",
            shareText = buildString {
                appendLine("KgyMs – Biên lai ${p.id}")
                appendLine("Trạng thái: ${statusLabel(p.status)}")
                appendLine("Số tiền: ${AdminFormat.vnd(p.amount)}")
                fields.forEach { appendLine("${it.label}: ${it.value}") }
            }.trim()
        )
    }

    private fun service(r: PaymentRecord) = r.pkg?.name ?: "—"

    private fun methodLabel(method: String) = if (method == PaymentMethod.CASH) "Tiền mặt" else "Chuyển khoản"

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

    /** Chạy 1 thao tác ghi, hiện thông báo kết quả; [close] đóng hộp thoại liên quan. */
    private fun runAction(action: suspend () -> Unit, success: String, close: (BillingUiState) -> BillingUiState) {
        _state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val result = runSafely { action() }
            _state.update { close(it).copy(isWorking = false, message = result.exceptionOrNull()?.toUserMessage() ?: success) }
        }
    }

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
