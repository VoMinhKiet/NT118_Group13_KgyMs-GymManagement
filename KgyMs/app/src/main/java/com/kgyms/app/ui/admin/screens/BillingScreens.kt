package com.kgyms.app.ui.admin.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.model.PaymentReceiver
import com.kgyms.app.ui.admin.components.AdminBadge
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminChipGroup
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminMessageEffect
import com.kgyms.app.ui.admin.components.AdminOutlinedButton
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.components.AdminSearchBox
import com.kgyms.app.ui.admin.components.AdminSection
import com.kgyms.app.ui.admin.components.AdminStatCard
import com.kgyms.app.ui.admin.components.AdminStatRow
import com.kgyms.app.ui.admin.components.AdminTextField
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.ui.member.qr.QrCodeImage
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.BillingUiState
import com.kgyms.app.viewmodel.admin.BillingViewModel
import com.kgyms.app.viewmodel.admin.DeskInvoiceFormUi
import com.kgyms.app.viewmodel.admin.TransactionRowUi

/* ============================ TỔNG QUAN HOÁ ĐƠN ============================ */

@Composable
fun InvoiceScreen(
    onTransactionClick: () -> Unit,
    onReceiptClick: (String) -> Unit,
    viewModel: BillingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    AdminMessageEffect(state.message, viewModel::onMessageShown)
    OpenInvoiceEffect(state.openedInvoice, viewModel::onOpenedInvoiceHandled, onReceiptClick)

    AdminPage {
        AdminStatRow { cell ->
            AdminStatCard("Chờ xác nhận", state.pendingCount, modifier = cell, caption = state.pendingTotal, tone = AdminTone.Warning)
            AdminStatCard("Đã thu tháng này", state.paidThisMonth, modifier = cell, caption = state.paidToday, tone = AdminTone.Positive)
        }
        Spacer(modifier = Modifier.height(12.dp))
        AdminPrimaryButton("Tạo hóa đơn tại quầy", onClick = { viewModel.openDeskForm() }, tag = "invoice_desk_button")
        Spacer(modifier = Modifier.height(8.dp))
        AdminOutlinedButton("Lịch sử giao dịch", onClick = onTransactionClick)

        AdminSection("Cần xác nhận")
        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.pendingRows.isEmpty() -> AdminMessage("Không có hoá đơn nào đang chờ xác nhận.")
            else -> state.pendingRows.forEach { row ->
                TransactionCard(
                    row = row,
                    onOpen = { onReceiptClick(row.invoice) },
                    onConfirm = { viewModel.requestConfirm(row.invoice) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    BillingDialogs(state, viewModel)
}

/* ============================ LỊCH SỬ GIAO DỊCH ============================ */

@Composable
fun TransactionsScreen(
    onReceiptClick: (String) -> Unit,
    viewModel: BillingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        AdminSearchBox(state.query, viewModel::onQueryChange, "Mã hoá đơn, tên hội viên, gói", "admin_transaction_search")
        Spacer(modifier = Modifier.height(10.dp))
        AdminChipGroup(state.filters, { it == state.filter }, viewModel::onFilterSelected, tagPrefix = "admin_transaction_filter")
        if (state.rowsSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.rowsSummary, color = AdminColors.TextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))

        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.rows.isEmpty() -> AdminMessage("Không có giao dịch phù hợp.")
            else -> state.rows.forEach { row ->
                TransactionCard(row = row, onOpen = { onReceiptClick(row.invoice) }, onConfirm = null)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    BillingDialogs(state, viewModel)
}

/** Thẻ hoá đơn: bấm để xem biên lai; [onConfirm] != null thì có nút xác nhận nhanh. */
@Composable
private fun TransactionCard(row: TransactionRowUi, onOpen: () -> Unit, onConfirm: (() -> Unit)?) {
    AdminCard(onClick = onOpen, modifier = Modifier.testTag("transaction_${row.invoice}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, color = AdminColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(row.subtitle, color = AdminColors.TextSecondary, fontSize = 12.sp)
                Text(row.meta, color = AdminColors.TextDark, fontSize = 11.sp)
            }
            AdminBadge(row.statusLabel, row.tone)
        }
        if (onConfirm != null && row.canConfirm) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("transaction_confirm_${row.invoice}")) {
                Text("Đã nhận tiền", color = AdminColors.Primary, fontWeight = FontWeight.SemiBold)
            }
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
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
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

        receipt.qrPayload?.let { payload ->
            Spacer(modifier = Modifier.height(10.dp))
            AdminCard {
                AdminLabel("Cho khách quét mã để chuyển khoản")
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCodeImage(payload, "Mã VietQR hoá đơn ${receipt.invoice}", modifier = Modifier.size(220.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(receipt.transferNote, color = AdminColors.TextSecondary, fontSize = 12.sp)
                    Text(
                        "Nội dung: ${PaymentReceiver.transferContent(receipt.invoice)}",
                        color = AdminColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        DetailFields(receipt.fields)

        Spacer(modifier = Modifier.height(4.dp))
        if (receipt.canConfirm) {
            AdminPrimaryButton(
                "Xác nhận đã nhận tiền",
                onClick = { viewModel.requestConfirm(receipt.invoice) },
                loading = state.isWorking && state.confirmInvoice == receipt.invoice,
                tag = "admin_receipt_confirm"
            )
            Spacer(modifier = Modifier.height(8.dp))
            AdminOutlinedButton(
                "Huỷ hoá đơn",
                onClick = { viewModel.requestCancel(receipt.invoice) },
                tone = AdminTone.Critical,
                tag = "admin_receipt_cancel"
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        AdminOutlinedButton(
            "Chia sẻ biên lai",
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Biên lai ${receipt.invoice}")
                    putExtra(Intent.EXTRA_TEXT, receipt.shareText)
                }
                context.startActivity(Intent.createChooser(send, "Chia sẻ biên lai"))
            },
            tag = "admin_receipt_share"
        )
    }

    BillingDialogs(state, viewModel)
}

/* ============================ HỘP THOẠI + SHEET ============================ */

/** Mở biên lai của hoá đơn vừa tạo tại quầy (chỉ 1 lần). */
@Composable
private fun OpenInvoiceEffect(invoice: String?, onHandled: () -> Unit, onOpen: (String) -> Unit) {
    LaunchedEffect(invoice) {
        if (invoice != null) {
            onHandled()
            onOpen(invoice)
        }
    }
}

@Composable
private fun BillingDialogs(state: BillingUiState, viewModel: BillingViewModel) {
    state.confirmInvoice?.let { invoice ->
        val receipt = state.receipts[invoice]
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            containerColor = AdminColors.Surface,
            title = { Text("Xác nhận đã nhận tiền?", color = AdminColors.TextPrimary) },
            text = {
                Text(
                    "Chỉ xác nhận khi đã nhận đủ ${receipt?.amount.orEmpty()} cho hoá đơn $invoice " +
                        "(chuyển khoản vào ${PaymentReceiver.BANK_NAME} ${PaymentReceiver.ACCOUNT_NO} với nội dung " +
                        "\"${PaymentReceiver.transferContent(invoice)}\", hoặc tiền mặt tại quầy). " +
                        "Gói của hội viên sẽ được kích hoạt và hội viên nhận thông báo.",
                    color = AdminColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPaid, enabled = !state.isWorking, modifier = Modifier.testTag("billing_confirm_ok")) {
                    Text(if (state.isWorking) "Đang xác nhận…" else "Xác nhận", color = AdminColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm, enabled = !state.isWorking) {
                    Text("Hủy", color = AdminColors.TextSecondary)
                }
            }
        )
    }

    state.cancelInvoice?.let { invoice ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCancel,
            containerColor = AdminColors.Surface,
            title = { Text("Huỷ hoá đơn $invoice?", color = AdminColors.TextPrimary) },
            text = { Text("Hoá đơn và gói đăng ký kèm theo sẽ chuyển sang \"Đã huỷ\".", color = AdminColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = viewModel::cancelInvoice, enabled = !state.isWorking, modifier = Modifier.testTag("billing_cancel_ok")) {
                    Text(if (state.isWorking) "Đang huỷ…" else "Huỷ hoá đơn", color = AdminTone.Critical.color, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCancel, enabled = !state.isWorking) {
                    Text("Giữ lại", color = AdminColors.TextSecondary)
                }
            }
        )
    }

    state.deskForm?.let { form -> DeskInvoiceSheet(state, form, viewModel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeskInvoiceSheet(state: BillingUiState, form: DeskInvoiceFormUi, viewModel: BillingViewModel) {
    val change: (DeskInvoiceFormUi) -> Unit = viewModel::onDeskFormChange
    val summary = state.deskSummary

    ModalBottomSheet(
        onDismissRequest = viewModel::closeDeskForm,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AdminColors.Background
    ) {
        SheetColumn(title = "Tạo hóa đơn tại quầy") {
            // 1. Hội viên
            AdminLabel("1. Hội viên")
            if (form.memberUid.isNotBlank()) {
                AdminListRow(
                    title = summary.selectedMember.ifBlank { "Đã chọn" },
                    subtitle = "Bấm để chọn người khác",
                    tone = AdminTone.Brand,
                    onClick = { change(form.copy(memberUid = "")) }
                )
            } else {
                AdminTextField(form.memberQuery, { change(form.copy(memberQuery = it)) }, "Tìm tên, mã HV, email, SĐT", tag = "desk_member_search")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (summary.memberChoices.isEmpty()) AdminMessage("Không tìm thấy hội viên.")
                    summary.memberChoices.forEach { m ->
                        AdminListRow(
                            title = m.name,
                            subtitle = m.detail,
                            modifier = Modifier.testTag("desk_member_${m.uid}"),
                            onClick = { change(form.copy(memberUid = m.uid)) }
                        )
                    }
                }
            }

            // 2. Gói
            AdminLabel("2. Gói tập")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.packageOptions.forEach { option ->
                    AdminListRow(
                        title = option.label,
                        tone = if (option.key == form.packageId) AdminTone.Brand else null,
                        trailingText = if (option.key == form.packageId) "Đã chọn" else null,
                        showChevron = false,
                        modifier = Modifier.testTag("desk_package_${option.key}"),
                        onClick = { change(form.copy(packageId = option.key)) }
                    )
                }
            }

            // 3. Giảm giá
            AdminLabel("3. Giảm giá")
            AdminChipGroup(
                state.discountModeOptions,
                { (it == "percent") == form.discountPercent },
                { change(form.copy(discountPercent = it == "percent", discount = "")) }
            )
            AdminTextField(
                form.discount, { change(form.copy(discount = it)) },
                if (form.discountPercent) "Giảm (%)" else "Giảm (đ)",
                keyboardType = KeyboardType.Number,
                tag = "desk_discount"
            )

            // 4. Thanh toán
            AdminLabel("4. Phương thức")
            AdminChipGroup(state.methodOptions, { it == form.method }, { change(form.copy(method = it)) }, tagPrefix = "desk_method")

            AdminCard {
                Row { Text("Giá gói", color = AdminColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(summary.price, color = AdminColors.TextPrimary, fontSize = 13.sp) }
                Row { Text("Giảm giá", color = AdminColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("- ${summary.discountAmount}", color = AdminColors.TextPrimary, fontSize = 13.sp) }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tổng thanh toán", color = AdminColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(summary.total, color = AdminColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            form.error?.let { Text(it, color = AdminTone.Critical.color, fontSize = 12.sp) }
            AdminPrimaryButton(summary.submitLabel, onClick = viewModel::submitDeskForm, loading = form.isSaving, tag = "desk_submit")
        }
    }
}
