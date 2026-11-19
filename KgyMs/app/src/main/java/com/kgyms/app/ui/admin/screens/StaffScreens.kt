package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminCard
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
import com.kgyms.app.ui.admin.components.AdminStatCard
import com.kgyms.app.ui.admin.components.AdminStatRow
import com.kgyms.app.ui.admin.components.AdminTextField
import com.kgyms.app.ui.admin.components.AdminTimeField
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.StaffFormUi
import com.kgyms.app.viewmodel.admin.StaffUiState
import com.kgyms.app.viewmodel.admin.StaffViewModel

/* ============================ DANH SÁCH ============================ */

@Composable
fun StaffScreen(
    onStaffClick: (String) -> Unit,
    viewModel: StaffViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        AdminStatRow { cell ->
            AdminStatCard("Đang làm việc", state.activeCount, modifier = cell, tone = AdminTone.Info)
            AdminStatCard("PT", state.trainerCount, modifier = cell, tone = AdminTone.Brand)
        }
        Spacer(modifier = Modifier.height(10.dp))
        AdminChipGroup(state.filters, { it == state.filter }, viewModel::onFilterSelected, tagPrefix = "staff_filter")
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.emptyMessage != null -> AdminMessage(state.emptyMessage.orEmpty())
            else -> state.rows.forEach { row ->
                AdminListRow(
                    title = row.title,
                    subtitle = row.subtitle,
                    tone = row.tone,
                    modifier = Modifier.testTag("staff_row_${row.uid}"),
                    onClick = { onStaffClick(row.uid) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        AdminPrimaryButton("Thêm nhân sự", onClick = viewModel::openPicker, tag = "staff_add_button")
    }

    StaffSheets(state, viewModel)
}

/* ============================ CHI TIẾT ============================ */

@Composable
fun StaffDetailScreen(
    staffId: String,
    viewModel: StaffViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val detail = viewModel.detail(staffId)
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        when {
            state.isLoading -> AdminLoading()
            detail == null -> AdminMessage(state.errorMessage ?: "Không tìm thấy nhân sự.")
            else -> {
                AdminCard {
                    AdminLabel(detail.positionLabel)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(detail.name, color = AdminColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(detail.headline, color = AdminColors.TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(detail.statusLabel, color = detail.statusTone.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                DetailFields(detail.fields)

                if (detail.bio.isNotBlank()) {
                    AdminCard {
                        AdminLabel("Giới thiệu")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(detail.bio, color = AdminColors.TextPrimary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                AdminPrimaryButton("Chỉnh sửa hồ sơ", onClick = { viewModel.openEdit(staffId) }, tag = "staff_edit_button")
                Spacer(modifier = Modifier.height(8.dp))
                AdminOutlinedButton(
                    text = if (detail.isActive) "Cho nghỉ việc" else "Cho làm việc lại",
                    onClick = { viewModel.requestStatusChange(staffId) },
                    tone = if (detail.isActive) AdminTone.Critical else AdminTone.Positive,
                    tag = "staff_status_button"
                )
            }
        }
    }

    StaffSheets(state, viewModel)
}

/* ============================ SHEET + DIALOG ============================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffSheets(state: StaffUiState, viewModel: StaffViewModel) {
    val form = state.form
    if (state.picker != null || form != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AdminColors.Background
        ) {
            if (form != null) StaffForm(state, form, viewModel) else UserPicker(state, viewModel)
        }
    }

    state.confirmStatus?.let { confirm ->
        AlertDialog(
            onDismissRequest = viewModel::cancelStatusChange,
            containerColor = AdminColors.Surface,
            title = {
                Text(
                    if (confirm.toActive) "Cho ${confirm.name} làm việc lại?" else "Cho ${confirm.name} nghỉ việc?",
                    color = AdminColors.TextPrimary
                )
            },
            text = { Text(confirm.note, color = AdminColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmStatusChange, modifier = Modifier.testTag("staff_status_confirm")) {
                    Text(
                        "Xác nhận",
                        color = if (confirm.toActive) AdminTone.Positive.color else AdminTone.Critical.color
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelStatusChange) { Text("Hủy", color = AdminColors.TextSecondary) }
            }
        )
    }
}

/** Bước 1: chọn user để tuyển. */
@Composable
private fun UserPicker(state: StaffUiState, viewModel: StaffViewModel) {
    SheetColumn(title = "Chọn người dùng") {
        Text(
            "Chọn tài khoản sẽ trở thành nhân sự. Người đã có hồ sơ nhân sự không hiện ở đây.",
            color = AdminColors.TextSecondary,
            fontSize = 12.sp
        )
        AdminTextField(
            value = state.picker?.query.orEmpty(),
            onValueChange = viewModel::onPickerQuery,
            label = "Tìm theo tên, email hoặc SĐT",
            tag = "staff_picker_search"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.candidates.isEmpty()) AdminMessage("Không có người dùng phù hợp.")
            state.candidates.forEach { user ->
                AdminListRow(
                    title = user.name,
                    subtitle = user.email,
                    modifier = Modifier.testTag("staff_pick_${user.uid}"),
                    onClick = { viewModel.pickUser(user.uid) }
                )
            }
        }
    }
}

/** Bước 2 / sửa hồ sơ. */
@Composable
private fun StaffForm(state: StaffUiState, form: StaffFormUi, viewModel: StaffViewModel) {
    val change: (StaffFormUi) -> Unit = viewModel::onFormChange

    SheetColumn(title = form.title) {
        AdminCard {
            Text(form.name, color = AdminColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (form.contact.isNotBlank()) Text(form.contact, color = AdminColors.TextSecondary, fontSize = 12.sp)
        }

        AdminLabel("Chức vụ")
        AdminChipGroup(state.positionOptions, { it == form.position }, { change(form.copy(position = it)) }, tagPrefix = "staff_form_position")
        Text(
            if (form.position == "pt") "PT được phân công gói PT và xem học viên của mình."
            else "Nhân viên không có quyền PT.",
            color = AdminColors.TextSecondary,
            fontSize = 11.sp
        )

        AdminTextField(
            form.specialty, { change(form.copy(specialty = it)) },
            if (form.position == "pt") "Chuyên môn (vd Strength Coach)" else "Mảng phụ trách",
            tag = "staff_form_specialty"
        )

        AdminLabel("Ca làm")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminTimeField(form.shiftStartDigits, { change(form.copy(shiftStartDigits = it)) }, "Bắt đầu (HH:mm)",
                modifier = Modifier.weight(1f), tag = "staff_form_shift_start")
            AdminTimeField(form.shiftEndDigits, { change(form.copy(shiftEndDigits = it)) }, "Kết thúc (HH:mm)",
                modifier = Modifier.weight(1f), tag = "staff_form_shift_end")
        }

        AdminDateField(form.startDateDigits, { change(form.copy(startDateDigits = it)) }, "Ngày vào làm (dd/MM/yyyy)", tag = "staff_form_start_date")
        AdminTextField(form.bio, { change(form.copy(bio = it)) }, "Giới thiệu ngắn", singleLine = false, tag = "staff_form_bio")

        form.error?.let { Text(it, color = AdminTone.Critical.color, fontSize = 12.sp) }
        AdminPrimaryButton(
            text = if (form.isEdit) "Lưu thay đổi" else "Đăng ký nhân sự",
            onClick = viewModel::submitForm,
            loading = form.isSaving,
            tag = "staff_form_submit"
        )
        if (!form.isEdit) {
            AdminOutlinedButton("Chọn người khác", onClick = viewModel::backToPicker, enabled = !form.isSaving)
        }
    }
}
