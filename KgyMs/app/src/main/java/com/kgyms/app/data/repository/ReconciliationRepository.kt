package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.PaymentService
import com.kgyms.app.data.firebase.ReconciliationService
import com.kgyms.app.data.model.Payment
import com.kgyms.app.data.model.Reconciliation
import com.kgyms.app.data.server.KgyMsServerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/** Một giao dịch đối soát kèm hoá đơn liên quan (nếu có) và tên hội viên. */
data class ReconciliationRecord(
    val item: Reconciliation,
    val payment: Payment?,
    val memberName: String
)

/** Đối soát ngân hàng (admin): xem các giao dịch server không tự khớp được, rồi xử lý qua server. */
class ReconciliationRepository(
    private val service: ReconciliationService = ReconciliationService(),
    private val paymentService: PaymentService = PaymentService(),
    private val serverService: KgyMsServerService = KgyMsServerService()
) {
    fun observeRecords(): Flow<List<ReconciliationRecord>> {
        val names = flow { emit(runCatching { paymentService.getUserNames() }.getOrDefault(emptyMap())) }
        return combine(service.observeAll(), paymentService.observeAllPayments(), names) { items, payments, userNames ->
            val paymentById = payments.associateBy { it.id }
            items.map { item ->
                val payment = paymentById[item.paymentId]
                ReconciliationRecord(
                    item = item,
                    payment = payment,
                    memberName = payment?.uid?.let { userNames[it].orEmpty().ifBlank { it } }.orEmpty()
                )
            }
        }
    }

    /**
     * [action]: [ACTIVATE] (kích hoạt hoá đơn [paymentId], mặc định hoá đơn trong nội dung CK),
     * [REFUNDED] (đã hoàn tiền cho khách), [IGNORED] (bỏ qua). Server ghi activityLogs.
     */
    suspend fun resolve(id: String, action: String, paymentId: String?, note: String) {
        require(action in listOf(ACTIVATE, REFUNDED, IGNORED)) { "Thao tác không hợp lệ." }
        serverService.resolveReconciliation(id, action, paymentId?.trim()?.uppercase(), note.trim())
    }

    companion object {
        const val ACTIVATE = "activate"
        const val REFUNDED = "refunded"
        const val IGNORED = "ignored"
    }
}
