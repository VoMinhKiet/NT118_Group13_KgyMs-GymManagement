package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminMessageEffect
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.components.AdminTextField
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.PackageDetailUi
import com.kgyms.app.viewmodel.admin.PackageFormUi
import com.kgyms.app.viewmodel.admin.PackageRowUi
import com.kgyms.app.viewmodel.admin.PackageUiState
import com.kgyms.app.viewmodel.admin.PackageViewModel

@Composable
fun PackageScreen(
    viewModel: PackageViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        Text(state.headline, color = AdminColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Bấm để xem chi tiết, nhấn giữ để sửa, ngừng bán hoặc xóa.",
            color = AdminColors.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.rows.isEmpty() -> AdminMessage("Chưa có gói tập nào. Bấm \"Thêm gói tập\" để tạo mới.")
            else -> state.rows.forEach { row ->
                PackageRow(
                    row = row,
                    onClick = { viewModel.openDetail(row.id) },
                    onEdit = { viewModel.openEdit(row.id) },
                    onToggleActive = { viewModel.toggleActive(row.id) },
                    onDelete = { viewModel.requestDelete(row.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        AdminPrimaryButton("Thêm gói tập", onClick = viewModel::openAdd, tag = "package_add_button")
    }

    state.form?.let { form -> PackageFormSheet(state, form, viewModel) }
    state.detail?.let { detail -> PackageDetailDialog(detail, onEdit = { viewModel.openEdit(detail.id) }, onDismiss = viewModel::closeDetail) }
    state.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = AdminColors.Surface,
            title = { Text("Xóa gói tập?", color = AdminColors.TextPrimary) },
            text = { Text("Gói \"${row.title}\" sẽ bị xóa vĩnh viễn.", color = AdminColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("package_delete_confirm")) {
                    Text("Xóa", color = AdminTone.Critical.color)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Hủy", color = AdminColors.TextSecondary) }
            }
        )
    }
}

/** Bấm: xem chi tiết. Nhấn giữ: menu Sửa / Ngừng bán (Mở bán lại) / Xóa. */
@Composable
private fun PackageRow(
    row: PackageRowUi,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box {
        AdminListRow(
            title = row.title,
            subtitle = row.subtitle,
            tone = row.tone,
            trailingText = row.statusLabel,
            modifier = Modifier.testTag("package_row_${row.id}"),
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = AdminColors.Surface) {
            DropdownMenuItem(
                text = { Text("Sửa", color = AdminColors.TextPrimary) },
                onClick = { menuOpen = false; onEdit() },
                modifier = Modifier.testTag("package_edit_${row.id}")
            )
            DropdownMenuItem(
                text = { Text(if (row.isActive) "Ngừng bán" else "Mở bán lại", color = AdminColors.TextPrimary) },
                onClick = { menuOpen = false; onToggleActive() },
                modifier = Modifier.testTag("package_toggle_${row.id}")
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Xóa", color = if (row.inUse) AdminColors.TextSecondary else AdminTone.Critical.color)
                        if (row.inUse) Text("Đang có hội viên dùng", color = AdminColors.TextSecondary, fontSize = 11.sp)
                    }
                },
                onClick = { menuOpen = false; onDelete() },
                modifier = Modifier.testTag("package_delete_${row.id}")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageFormSheet(state: PackageUiState, form: PackageFormUi, viewModel: PackageViewModel) {
    val change: (PackageFormUi) -> Unit = viewModel::onFormChange

    ModalBottomSheet(
        onDismissRequest = viewModel::closeForm,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AdminColors.Background
    ) {
        SheetColumn(title = form.title) {
            AdminTextField(form.name, { change(form.copy(name = it)) }, "Tên gói *", tag = "package_form_name")

            AdminLabel("Loại gói")
            AdminChipGroup(state.typeOptions, { it == form.type }, { change(form.copy(type = it)) }, tagPrefix = "package_form_type")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminTextField(
                    form.price, { change(form.copy(price = it)) }, "Giá (VNĐ) *",
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, tag = "package_form_price"
                )
                AdminTextField(
                    form.durationDays, { change(form.copy(durationDays = it)) }, "Thời hạn (ngày) *",
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, tag = "package_form_days"
                )
            }

            if (form.isPt) {
                AdminTextField(
                    form.sessions, { change(form.copy(sessions = it)) }, "Số buổi PT *",
                    keyboardType = KeyboardType.Number, tag = "package_form_sessions"
                )
                AdminLabel("PT phụ trách")
                AdminChipGroup(state.trainerOptions, { it == form.trainerUid }, { change(form.copy(trainerUid = it)) })
                if (state.trainerOptions.size <= 1) {
                    Text(
                        "Chưa có tài khoản PT (isStaff). Có thể phân công sau.",
                        color = AdminColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            AdminTextField(form.description, { change(form.copy(description = it)) }, "Mô tả", singleLine = false)
            AdminTextField(form.benefits, { change(form.copy(benefits = it)) }, "Quyền lợi (mỗi dòng 1 ý)", singleLine = false, tag = "package_form_benefits")

            AdminLabel("Trạng thái")
            AdminChipGroup(state.activeOptions, { it == form.isActive.toString() }, { change(form.copy(isActive = it == "true")) })

            form.error?.let { Text(it, color = AdminTone.Critical.color, fontSize = 12.sp) }
            AdminPrimaryButton(
                text = if (form.editingId == null) "Thêm gói" else "Lưu thay đổi",
                onClick = viewModel::submitForm,
                loading = form.isSaving,
                tag = "package_form_submit"
            )
        }
    }
}

@Composable
private fun PackageDetailDialog(detail: PackageDetailUi, onEdit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AdminColors.Surface,
        title = { Text(detail.title, color = AdminColors.TextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(detail.summary, color = AdminColors.TextSecondary, fontSize = 12.sp)
                if (detail.description.isNotBlank()) Text(detail.description, color = AdminColors.TextPrimary, fontSize = 14.sp)
                if (detail.benefits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AdminLabel("Quyền lợi")
                    detail.benefits.forEach { Text("– $it", color = AdminColors.TextPrimary, fontSize = 13.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Sửa", color = AdminColors.Primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng", color = AdminColors.TextSecondary) } }
    )
}
