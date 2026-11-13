package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminChipGroup
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminDateField
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminMessageEffect
import com.kgyms.app.ui.admin.components.AdminOutlinedButton
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.components.AdminRowShape
import com.kgyms.app.ui.admin.components.AdminStatCard
import com.kgyms.app.ui.admin.components.AdminStatRow
import com.kgyms.app.ui.admin.components.AdminTextField
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.ui.admin.navigation.AdminRoute
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.AssetFormUi
import com.kgyms.app.viewmodel.admin.AssetRowUi
import com.kgyms.app.viewmodel.admin.AssetUiState
import com.kgyms.app.viewmodel.admin.AssetViewModel
import com.kgyms.app.viewmodel.admin.TicketFormUi

@Composable
fun AssetScreen(
    onNavigate: (AdminRoute) -> Unit,
    onAssetClick: (String) -> Unit,
    viewModel: AssetViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        AdminStatRow { cell ->
            AdminStatCard("Thiết bị", state.totalQuantity, modifier = cell, tone = AdminTone.Info)
            AdminStatCard("Cần sửa/bảo trì", state.needFixQuantity, modifier = cell, tone = AdminTone.Critical)
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.rows.isEmpty() -> AdminMessage("Chưa có tài sản nào. Bấm \"Thêm tài sản\" để tạo mới.")
            else -> state.rows.forEach { row ->
                AssetRow(row, onClick = { onAssetClick(row.id) }, onResolve = { viewModel.markResolved(row.id) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        AdminPrimaryButton("Thêm tài sản", onClick = viewModel::openAddForm, tag = "asset_add_button")
        Spacer(modifier = Modifier.height(8.dp))
        AdminOutlinedButton("Tạo phiếu bảo trì", onClick = viewModel::openTicketForm, tag = "asset_ticket_button")
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Nhấn giữ máy đang bảo trì hoặc hỏng để xác nhận đã xử lý xong.",
            color = AdminColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }

    state.addForm?.let { form ->
        AddAssetSheet(state, form, viewModel::onAddFormChange, viewModel::submitAddForm, viewModel::closeAddForm)
    }
    state.ticketForm?.let { form ->
        TicketSheet(state, form, viewModel::onTicketFormChange, viewModel::submitTicketForm, viewModel::closeTicketForm)
    }
}

/** Bấm: mở chi tiết. Nhấn giữ máy cần xử lý: menu xác nhận đã sửa/đã bảo trì. */
@Composable
private fun AssetRow(row: AssetRowUi, onClick: () -> Unit, onResolve: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box {
        AdminListRow(
            title = row.title,
            subtitle = row.subtitle,
            tone = row.tone,
            modifier = Modifier.testTag("asset_card_${row.id}"),
            onClick = onClick,
            onLongClick = row.resolveLabel?.let {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                }
            }
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = AdminColors.Surface
        ) {
            DropdownMenuItem(
                text = { Text(row.resolveLabel.orEmpty(), color = AdminTone.Positive.color, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    menuOpen = false
                    onResolve()
                },
                modifier = Modifier.testTag("asset_resolve_${row.id}")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAssetSheet(
    state: AssetUiState,
    form: AssetFormUi,
    onChange: (AssetFormUi) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AdminColors.Background
    ) {
        SheetColumn(title = "Thêm tài sản") {
            AdminTextField(form.name, { onChange(form.copy(name = it)) }, "Tên máy *", tag = "asset_form_name")

            AdminLabel("Loại")
            AdminChipGroup(state.categoryOptions, { it == form.category }, { onChange(form.copy(category = it)) })

            AdminTextField(form.brand, { onChange(form.copy(brand = it)) }, "Hãng / Model")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminTextField(
                    form.quantity, { onChange(form.copy(quantity = it)) }, "Số lượng *",
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, tag = "asset_form_quantity"
                )
                AdminTextField(
                    form.warrantyMonths, { onChange(form.copy(warrantyMonths = it)) }, "Bảo hành (tháng)",
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number
                )
            }

            AdminLabel("Trạng thái")
            AdminChipGroup(state.statusOptions, { it == form.status }, { onChange(form.copy(status = it)) })

            AdminTextField(form.location, { onChange(form.copy(location = it)) }, "Vị trí (vd: Khu Strength • Tầng 1)")
            AdminDateField(form.purchaseDigits, { onChange(form.copy(purchaseDigits = it)) }, "Ngày mua (dd/MM/yyyy)")
            AdminDateField(form.lastMaintenanceDigits, { onChange(form.copy(lastMaintenanceDigits = it)) }, "Lần bảo trì gần nhất (dd/MM/yyyy)")
            AdminDateField(form.nextMaintenanceDigits, { onChange(form.copy(nextMaintenanceDigits = it)) }, "Lịch bảo trì tiếp theo (dd/MM/yyyy)")
            AdminTextField(form.description, { onChange(form.copy(description = it)) }, "Mô tả", singleLine = false)

            form.error?.let { Text(it, color = AdminTone.Critical.color, fontSize = 12.sp) }
            AdminPrimaryButton("Lưu tài sản", onClick = onSubmit, loading = form.isSaving, tag = "asset_form_submit")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketSheet(
    state: AssetUiState,
    form: TicketFormUi,
    onChange: (TicketFormUi) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AdminColors.Background
    ) {
        SheetColumn(title = "Tạo phiếu bảo trì") {
            AdminLabel("Chọn 1 loại máy")
            state.ticketChoices.forEach { choice ->
                val selected = choice.id == form.selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AdminRowShape)
                        .background(AdminColors.Surface)
                        .border(1.dp, if (selected) AdminColors.Primary else AdminColors.Border, AdminRowShape)
                        .clickable { onChange(form.copy(selectedId = choice.id)) }
                        .testTag("ticket_asset_${choice.id}")
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onChange(form.copy(selectedId = choice.id)) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AdminColors.Primary,
                            unselectedColor = AdminColors.TextSecondary
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(choice.title, color = AdminColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(choice.statusLabel, color = choice.tone.color, fontSize = 11.sp)
                    }
                }
            }

            AdminLabel("Chuyển máy sang trạng thái")
            AdminChipGroup(state.ticketStatusOptions, { it == form.status }, { onChange(form.copy(status = it)) })

            AdminTextField(form.reason, { onChange(form.copy(reason = it)) }, "Lý do cần sửa / bảo trì *", singleLine = false, tag = "ticket_reason")

            form.error?.let { Text(it, color = AdminTone.Critical.color, fontSize = 12.sp) }
            AdminPrimaryButton("Gửi phiếu", onClick = onSubmit, loading = form.isSaving, tag = "ticket_submit")
        }
    }
}

/** Khung nội dung cuộn được dùng chung cho các bottom sheet Admin. */
@Composable
internal fun SheetColumn(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = AdminColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        content()
    }
}
