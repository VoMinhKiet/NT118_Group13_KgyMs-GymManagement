package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminBadge
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminChipGroup
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessageEffect
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminOutlinedButton
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.navigation.LocalAdminFeedback
import com.kgyms.app.data.model.PaymentReceiver
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.BillingViewModel

/* ============================ HÓA ĐƠN MỚI ============================ */

@Composable
fun InvoiceScreen(
    onTransactionClick: () -> Unit,
    viewModel: BillingViewModel = viewModel()
) {
    val draft = viewModel.invoiceDraft
    val showMockAction = LocalAdminFeedback.current

    AdminPage {
        Text("Hóa đơn mới", color = AdminColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Liên kết hội viên và dịch vụ.", color = AdminColors.TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        DetailFields(draft.fields)

        AdminCard {
            AdminLabel("Tổng thanh toán")
            Spacer(modifier = Modifier.height(4.dp))
            Text(draft.total, color = AdminColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        AdminPrimaryButton("Tạo hóa đơn", onClick = { showMockAction("Tạo hóa đơn") })
        Spacer(modifier = Modifier.height(8.dp))
        AdminOutlinedButton("Lịch sử giao dịch", onClick = onTransactionClick)
    }
}

/* ============================ LỊCH SỬ GIAO DỊCH ============================ */

@Composable
fun TransactionsScreen(
    onReceiptClick: (String) -> Unit,
    viewModel: BillingViewModel = viewModel()
) {
    val state by viewModel.transactionsState.collectAsState()

    AdminPage {
        AdminChipGroup(state.filters, { it == state.filter }, viewModel::onFilterSelected, tagPrefix = "admin_transaction_filter")
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage!!, AdminTone.Critical)
            state.rows.isEmpty() -> AdminMessage("Không có giao dịch phù hợp.")
        }
        state.rows.forEach { row ->
            AdminCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.title, color = AdminColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(row.subtitle, color = AdminColors.TextSecondary, fontSize = 12.sp)
                        Text(row.date, color = AdminColors.TextDark, fontSize = 11.sp)
                    }
                    AdminBadge(row.statusLabel, row.tone)
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { onReceiptClick(row.invoice) }) {
                    Text("Chi tiết", color = AdminColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/* ============================ BIÊN LAI ============================ */

@Composable
fun ReceiptScreen(
    invoice: String,
    onMissing: () -> Unit,
    viewModel: BillingViewModel = viewModel()
) {
    val showMockAction = LocalAdminFeedback.current
    val state by viewModel.transactionsState.collectAsState()
    val receipt = state.receipts[invoice]

    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        if (receipt == null && state.isLoading) {
            AdminLoading()
            return@AdminPage
        }
        if (receipt == null) {
            AdminMessage("Không có giao dịch được chọn.")
            AdminOutlinedButton("Quay lại danh sách", onClick = onMissing)
            return@AdminPage
        }

        AdminCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    receipt.invoice,
                    color = AdminColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AdminBadge(receipt.statusLabel, receipt.tone)
            }
            Spacer(modifier = Modifier.height(8.dp))
            AdminLabel("Số tiền")
            Text(receipt.amount, color = AdminColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        DetailFields(receipt.fields)

        Spacer(modifier = Modifier.height(4.dp))
        if (receipt.canConfirm) {
            AdminPrimaryButton(
                "Xác nhận đã nhận tiền",
                onClick = { viewModel.requestConfirm(receipt.invoice) },
                loading = state.isConfirming,
                tag = "admin_receipt_confirm"
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        AdminPrimaryButton("Chia sẻ qua ứng dụng", onClick = { showMockAction("Chia sẻ biên lai qua ứng dụng") })
        Spacer(modifier = Modifier.height(8.dp))
        AdminOutlinedButton("Xuất PDF", onClick = { showMockAction("Xuất biên lai PDF") })
    }

    if (state.confirmInvoice != null && receipt != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            containerColor = AdminColors.Surface,
            title = { Text("Xác nhận đã nhận tiền?", color = AdminColors.TextPrimary) },
            text = {
                Text(
                    "Chỉ xác nhận khi tài khoản ${PaymentReceiver.BANK_NAME} ${PaymentReceiver.ACCOUNT_NO} đã nhận " +
                        "${receipt.amount} với nội dung \"${PaymentReceiver.transferContent(receipt.invoice)}\". " +
                        "Gói của hội viên sẽ được kích hoạt và hội viên nhận thông báo.",
                    color = AdminColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPaid, enabled = !state.isConfirming) {
                    Text(if (state.isConfirming) "Đang xác nhận…" else "Xác nhận", color = AdminColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm, enabled = !state.isConfirming) {
                    Text("Đóng", color = AdminColors.TextSecondary)
                }
            }
        )
    }
}
