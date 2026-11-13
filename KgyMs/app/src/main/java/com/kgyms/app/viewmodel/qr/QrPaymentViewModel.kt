package com.kgyms.app.viewmodel.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.Membership
import com.kgyms.app.data.model.MembershipStatus
import com.kgyms.app.data.model.PackageType
import com.kgyms.app.data.model.Payment
import com.kgyms.app.data.model.PaymentReceiver
import com.kgyms.app.data.model.PaymentStatus
import com.kgyms.app.data.repository.PaymentRepository
import com.kgyms.app.data.util.AppDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ============================ UI MODEL ============================ */

/** Một gói đang bán. Chữ hiển thị (vi/en) do màn hình ghép. */
data class QrPackageUi(
    val id: String,
    val name: String,
    val description: String,
    val priceLabel: String,
    val isPt: Boolean,
    val durationDays: Int,
    val sessions: Int,
    val benefits: List<String>,
    /** Có hoá đơn đang chờ cho gói này → bấm vào là mở lại hoá đơn đó. */
    val pendingPaymentId: String?
)

/** Hoá đơn đang chờ thanh toán, hiện ở đầu màn danh sách gói. */
data class QrPendingRowUi(
    val paymentId: String,
    val packageName: String,
    val amountLabel: String,
    val createdAt: String
)

/** Màn mã VietQR của một hoá đơn. */
data class QrPaymentDetailUi(
    val paymentId: String,
    val packageName: String,
    val amountLabel: String,
    val transferContent: String,
    /** Chuỗi VietQR để vẽ mã. Rỗng khi hoá đơn không còn chờ. */
    val qrPayload: String,
    val status: String,
    val createdAt: String,
    val paidAt: String,
    /** dd/MM/yyyy – dd/MM/yyyy sau khi admin xác nhận. */
    val period: String
) {
    val bankName: String get() = PaymentReceiver.BANK_NAME
    val accountNo: String get() = PaymentReceiver.ACCOUNT_NO
    val accountName: String get() = PaymentReceiver.DISPLAY_NAME
    val isPending: Boolean get() = status == PaymentStatus.PENDING
    val isPaid: Boolean get() = status == PaymentStatus.PAID
    val isCancelled: Boolean get() = status == PaymentStatus.CANCELLED
}

/** Thông báo một lần; màn hình tự dịch sang vi/en. */
enum class QrPaymentMessage { Cancelled, NotLoggedIn }

data class QrPaymentUiState(
    val isLive: Boolean = false,
    val isLoading: Boolean = true,
    /** Lỗi tải dữ liệu hoặc lỗi thao tác (đã là câu tiếng Việt từ repository/Firebase). */
    val error: String? = null,
    val packages: List<QrPackageUi> = emptyList(),
    val pending: List<QrPendingRowUi> = emptyList(),
    /** Gói đang chờ xác nhận mua. */
    val confirm: QrPackageUi? = null,
    val isSubmitting: Boolean = false,
    /** Hoá đơn đang mở ở màn VietQR. */
    val detail: QrPaymentDetailUi? = null,
    val confirmCancel: Boolean = false,
    val isCancelling: Boolean = false,
    val message: QrPaymentMessage? = null
)

/* ============================ VIEWMODEL ============================ */

class QrPaymentViewModel(
    private val repository: PaymentRepository = PaymentRepository()
) : ViewModel() {

    private val uid: String? = runCatching { repository.currentUid() }.getOrNull()

    private val _state = MutableStateFlow(QrPaymentUiState(isLive = uid != null, isLoading = uid != null))
    val state: StateFlow<QrPaymentUiState> = _state.asStateFlow()

    private var allPackages: List<GymPackage> = emptyList()
    private var payments: List<Payment> = emptyList()
    private var memberships: Map<String, Membership> = emptyMap()
    private var openPaymentId: String? = null
    /** Gói được yêu cầu mở sẵn (vd vé ngày từ màn điểm danh) khi danh sách chưa tải xong. */
    private var requestedPackageId: String? = null

    init {
        if (uid == null) {
            _state.update { it.copy(message = QrPaymentMessage.NotLoggedIn) }
        } else {
            viewModelScope.launch {
                combine(
                    repository.observePackages(),
                    repository.observeMyPayments(uid),
                    repository.observeMyMemberships(uid)
                ) { p, pay, m -> Triple(p, pay, m) }
                    .catch { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
                    .collect { (p, pay, m) ->
                        allPackages = p
                        payments = pay
                        memberships = m.associateBy { it.id }
                        _state.update { it.recompute() }
                        requestedPackageId?.let { id ->
                            if (_state.value.packages.any { it.id == id }) {
                                requestedPackageId = null
                                selectPackage(id)
                            }
                        }
                    }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    /* ---------- Chọn gói ---------- */

    /** Mở sẵn một gói (vd vé ngày); nếu danh sách chưa tải thì chờ tải xong mới mở. */
    fun preselectPackage(id: String) {
        if (_state.value.packages.any { it.id == id }) selectPackage(id) else requestedPackageId = id
    }

    fun selectPackage(id: String) {
        val row = _state.value.packages.firstOrNull { it.id == id } ?: return
        val pending = row.pendingPaymentId
        if (pending != null) openPayment(pending) else _state.update { it.copy(confirm = row) }
    }

    fun dismissConfirm() = _state.update { if (it.isSubmitting) it else it.copy(confirm = null) }

    fun confirmPurchase() {
        val row = _state.value.confirm ?: return
        val pkg = allPackages.firstOrNull { it.id == row.id } ?: return dismissConfirm()
        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = runSafely { repository.startPurchase(pkg) }
            result.onSuccess { id -> openPaymentId = id }
            _state.update {
                it.copy(
                    isSubmitting = false,
                    confirm = null,
                    error = result.exceptionOrNull()?.toUserMessage()
                ).recompute()
            }
        }
    }

    /* ---------- Màn VietQR ---------- */

    fun openPayment(paymentId: String) {
        openPaymentId = paymentId
        _state.update { it.recompute() }
    }

    fun closePayment() {
        openPaymentId = null
        _state.update { it.copy(confirmCancel = false).recompute() }
    }

    fun requestCancel() = _state.update { if (it.detail?.isPending == true) it.copy(confirmCancel = true) else it }

    fun dismissCancel() = _state.update { if (it.isCancelling) it else it.copy(confirmCancel = false) }

    fun confirmCancel() {
        val payment = payments.firstOrNull { it.id == openPaymentId } ?: return dismissCancel()
        _state.update { it.copy(isCancelling = true) }
        viewModelScope.launch {
            val result = runSafely { repository.cancelPayment(payment) }
            if (result.isSuccess) openPaymentId = null
            _state.update {
                it.copy(
                    isCancelling = false,
                    confirmCancel = false,
                    message = if (result.isSuccess) QrPaymentMessage.Cancelled else null,
                    error = result.exceptionOrNull()?.toUserMessage()
                ).recompute()
            }
        }
    }

    /* ============================ MAPPING ============================ */

    private fun QrPaymentUiState.recompute(): QrPaymentUiState {
        val packageById = allPackages.associateBy { it.id }
        val pendingPayments = payments
            .filter { it.status == PaymentStatus.PENDING }
            .sortedByDescending { AppDates.parseTimestamp(it.createdAt) ?: 0L }
        val pendingByPackage = pendingPayments
            .mapNotNull { pay -> memberships[pay.membershipId]?.takeIf { it.status == MembershipStatus.PENDING }?.let { it.packageId to pay.id } }
            .toMap()

        return copy(
            isLoading = false,
            packages = allPackages
                .filter { it.isActive }
                .sortedWith(compareBy<GymPackage>({ it.type != PackageType.MEMBERSHIP }, { it.price }))
                .map { it.toUi(pendingByPackage[it.id]) },
            pending = pendingPayments.map { pay ->
                QrPendingRowUi(
                    paymentId = pay.id,
                    packageName = packageName(pay, packageById),
                    amountLabel = vnd(pay.amount),
                    createdAt = pay.createdAt
                )
            },
            detail = openPaymentId?.let { id -> payments.firstOrNull { it.id == id } }?.let { pay ->
                val membership = memberships[pay.membershipId]
                QrPaymentDetailUi(
                    paymentId = pay.id,
                    packageName = packageName(pay, packageById),
                    amountLabel = vnd(pay.amount),
                    transferContent = PaymentReceiver.transferContent(pay.id),
                    qrPayload = if (pay.status == PaymentStatus.PENDING && pay.amount > 0)
                        runCatching { repository.qrPayload(pay) }.getOrDefault("") else "",
                    status = pay.status,
                    createdAt = pay.createdAt,
                    paidAt = pay.paidAt,
                    period = membership?.takeIf { it.startDate.isNotBlank() && it.endDate.isNotBlank() }
                        ?.let { "${it.startDate} – ${it.endDate}" }.orEmpty()
                )
            }
        )
    }

    private fun packageName(pay: Payment, packageById: Map<String, GymPackage>): String =
        memberships[pay.membershipId]?.let { packageById[it.packageId]?.name }.orEmpty()

    private fun GymPackage.toUi(pendingPaymentId: String?) = QrPackageUi(
        id = id,
        name = name,
        description = description,
        priceLabel = vnd(price),
        isPt = type == PackageType.PT,
        durationDays = durationDays,
        sessions = sessions,
        benefits = benefits,
        pendingPaymentId = pendingPaymentId
    )

    private fun vnd(amount: Long): String =
        amount.toString().reversed().chunked(3).joinToString(".").reversed() + "đ"

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
            raw.contains("permission", ignoreCase = true) -> "Không có quyền thực hiện thao tác này."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }
}
