package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.repository.ReconciliationRepository
import com.kgyms.app.ui.admin.components.AdminBadge
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminChipGroup
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminMessageEffect
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.components.AdminTextField
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.ReconciliationDialogUi
import com.kgyms.app.viewmodel.admin.ReconciliationRowUi
import com.kgyms.app.viewmodel.admin.ReconciliationUiState
import com.kgyms.app.viewmodel.admin.ReconciliationViewModel

/** Đối soát ngân hàng: giao dịch SePay không tự khớp được hoá đơn (thiếu/dư tiền, không có mã, hết hạn...). */
@Composable
fun ReconciliationScreen(
    onReceiptClick: (String) -> Unit,
    viewModel: ReconciliationViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        Text(
            "Tiền đã vào tài khoản ACB nhưng hệ thống không tự kích hoạt được. Kiểm tra từng giao dịch rồi chọn cách xử lý.",
            color = AdminColors.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        AdminChipGroup(state.filters, { it == state.filter }, viewModel::onFilterSelected, tagPrefix = "reconciliation_filter")
        Spacer(modifier = Modifier.height(10.dp))

        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.rows.isEmpty() -> AdminMessage(
                if (state.filter == ReconciliationViewModel.FILTER_OPEN) "Không có giao dịch nào cần xử lý." else "Không có giao dịch."
            )
            else -> state.rows.forEach { row ->
                ReconciliationCard(
                    row = row,
                    onResolve = { viewModel.openDialog(row.id) },
                    onOpenInvoice = { onReceiptClick(row.paymentId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    state.dialog?.let { dialog -> ResolveSheet(state, dialog, viewModel) }
}

@Composable
private fun ReconciliationCard(row: ReconciliationRowUi, onResolve: () -> Unit, onOpenInvoice: () -> Unit) {
    AdminCard(modifier = Modifier.testTag("reconciliation_${row.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.amountLine,
                color = AdminColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            AdminBadge(row.reasonLabel, row.reasonTone)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Nội dung: ${row.content}", color = AdminColors.TextSecondary, fontSize = 12.sp)
            if (row.invoiceLine.isNotBlank()) Text(row.invoiceLine, color = AdminColors.TextSecondary, fontSize = 12.sp)
            Text(row.meta, color = AdminColors.TextDark, fontSize = 11.sp)
            if (row.resolutionLine.isNotBlank()) Text(row.resolutionLine, color = AdminTone.Positive.color, fontSize = 12.sp)
        }
        Row {
            if (row.isOpen) {
                TextButton(onClick = onResolve, modifier = Modifier.testTag("reconciliation_resolve_${row.id}")) {
                    Text("Xử lý", color = AdminColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
            if (row.paymentId.isNotBlank() && row.invoiceLine.isNotBlank() && !row.invoiceLine.contains("không tồn tại")) {
                TextButton(onClick = onOpenInvoice) {
                    Text("Xem hoá đơn", color = AdminColors.TextSecondary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolveSheet(state: ReconciliationUiState, dialog: ReconciliationDialogUi, viewModel: ReconciliationViewModel) {
    val change: (ReconciliationDialogUi) -> Unit = viewModel::onDialogChange
    val row = state.rows.firstOrNull { it.id == dialog.id }

    ModalBottomSheet(
        onDismissRequest = viewModel::dismissDialog,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AdminColors.Background
    ) {
        SheetColumn(title = "Xử lý giao dịch ${dialog.id}") {
            row?.let {
                AdminCard {
                    Text(it.amountLine, color = AdminColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Nội dung: ${it.content}", color = AdminColors.TextSecondary, fontSize = 12.sp)
                    Text("Lý do: ${it.reasonLabel}", color = AdminColors.TextSecondary, fontSize = 12.sp)
                }
            }

            AdminLabel("Cách xử lý")
            AdminChipGroup(state.actionOptions, { it == dialog.action }, { change(dialog.copy(action = it)) }, tagPrefix = "reconciliation_action")

            when (dialog.action) {
                ReconciliationRepository.ACTIVATE -> {
                    Text(
                        "Hoá đơn sẽ chuyển sang \"Đã thanh toán\", gói của hội viên được kích hoạt và hội viên nhận thông báo. " +
                            "Chỉ chọn khi đã chắc khoản tiền này là của hoá đơn đó (kể cả khi khách chuyển thiếu/dư hoặc quá hạn).",
                        color = AdminColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    AdminTextField(
                        dialog.paymentId,
                        { change(dialog.copy(paymentId = it.uppercase())) },
                        "Mã hoá đơn (KG...)",
                        tag = "reconciliation_payment_id"
                    )
                }
                ReconciliationRepository.REFUNDED -> Text(
                    "Dùng khi đã chuyển trả lại tiền cho khách. Hoá đơn (nếu có) giữ nguyên.",
                    color = AdminColors.TextSecondary,
                    fontSize = 12.sp
                )
                else -> Text(
                    "Dùng cho giao dịch không liên quan đến hội viên (vd tiền chuyển nhầm, giao dịch thử).",
                    color = AdminColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            AdminTextField(dialog.note, { change(dialog.copy(note = it.take(500))) }, "Ghi chú (không bắt buộc)", singleLine = false, tag = "reconciliation_note")

            dialog.error?.let { Text(it, color = AdminTone.Critical.color, fontSize = 12.sp) }
            AdminPrimaryButton(
                when (dialog.action) {
                    ReconciliationRepository.ACTIVATE -> "Kích hoạt hoá đơn"
                    ReconciliationRepository.REFUNDED -> "Ghi nhận đã hoàn tiền"
                    else -> "Bỏ qua giao dịch"
                },
                onClick = viewModel::submitDialog,
                loading = dialog.isSaving,
                tag = "reconciliation_submit"
            )
        }
    }
}
