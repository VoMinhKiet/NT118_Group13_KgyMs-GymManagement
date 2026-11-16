package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.model.NotificationTarget
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
import com.kgyms.app.ui.admin.components.AdminRowShape
import com.kgyms.app.ui.admin.components.AdminSection
import com.kgyms.app.ui.admin.components.AdminTextField
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.BroadcastDetailUi
import com.kgyms.app.viewmodel.admin.ComposeFormUi
import com.kgyms.app.viewmodel.admin.NotificationAdminUiState
import com.kgyms.app.viewmodel.admin.NotificationAdminViewModel

@Composable
fun NotificationScreen(
    viewModel: NotificationAdminViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        // Nhắc hết hạn tự động.
        AdminCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Nhắc hết hạn gói", color = AdminColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tự gửi \"Gói của bạn còn N ngày\" cho hội viên có gói active",
                        color = AdminColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = state.reminder.enabled,
                    onCheckedChange = viewModel::onReminderToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AdminColors.OnPrimary,
                        checkedTrackColor = AdminColors.Primary
                    ),
                    modifier = Modifier.testTag("expiry_reminder_switch")
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            AdminLabel("Nhắc khi còn")
            Spacer(modifier = Modifier.height(6.dp))
            AdminChipGroup(
                options = state.reminder.dayOptions,
                isSelected = { it in state.reminder.selectedDays },
                onSelect = viewModel::onReminderDayToggle,
                enabled = state.reminder.enabled,
                tagPrefix = "expiry_day"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.reminder.lastRunText, color = AdminColors.TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(10.dp))
            AdminOutlinedButton(
                text = "Quét và gửi ngay",
                onClick = viewModel::scanNow,
                loading = state.reminder.isScanning,
                tag = "expiry_scan_button"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        AdminPrimaryButton("Soạn thông báo", onClick = viewModel::openCompose, tag = "notification_compose_button")

        AdminSection("Đã gửi (${state.broadcasts.size})")
        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.broadcasts.isEmpty() -> AdminMessage("Chưa gửi thông báo nào. Bấm \"Soạn thông báo\" để gửi ưu đãi hoặc thông báo riêng.")
            else -> state.broadcasts.forEach { row ->
                AdminListRow(
                    title = row.title,
                    subtitle = row.subtitle,
                    meta = row.time,
                    tone = row.tone,
                    modifier = Modifier.testTag("broadcast_${row.id}"),
                    onClick = { viewModel.openDetail(row.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    state.compose?.let { form -> ComposeSheet(state, form, viewModel) }
    state.detail?.let { detail -> BroadcastDetailDialog(detail, viewModel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSheet(state: NotificationAdminUiState, form: ComposeFormUi, viewModel: NotificationAdminViewModel) {
    val change: (ComposeFormUi) -> Unit = viewModel::onComposeChange

    ModalBottomSheet(
        onDismissRequest = viewModel::closeCompose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AdminColors.Background
    ) {
        SheetColumn(title = "Soạn thông báo") {
            AdminLabel("Loại")
            AdminChipGroup(state.typeOptions, { it == form.type }, { change(form.copy(type = it)) }, tagPrefix = "compose_type")

            AdminLabel("Gửi tới")
            AdminChipGroup(state.targetOptions, { it == form.target }, { change(form.copy(target = it)) }, tagPrefix = "compose_target")

            when (form.target) {
                NotificationTarget.ACTIVE_MEMBERS -> {
                    AdminLabel("Lọc theo gói")
                    AdminChipGroup(state.packageOptions, { it == form.packageId }, { change(form.copy(packageId = it)) }, tagPrefix = "compose_pkg")
                }

                NotificationTarget.USER -> {
                    AdminTextField(form.userQuery, { change(form.copy(userQuery = it)) }, "Tìm theo tên hoặc email", tag = "compose_user_search")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (state.recipientChoices.isEmpty()) {
                            Text("Không tìm thấy người dùng.", color = AdminColors.TextSecondary, fontSize = 12.sp)
                        }
                        state.recipientChoices.forEach { user ->
                            val selected = user.uid == form.targetUid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AdminRowShape)
                                    .background(AdminColors.Surface)
                                    .border(1.dp, if (selected) AdminColors.Primary else AdminColors.Border, AdminRowShape)
                                    .clickable { change(form.copy(targetUid = user.uid)) }
                                    .testTag("compose_user_${user.uid}")
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { change(form.copy(targetUid = user.uid)) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AdminColors.Primary,
                                        unselectedColor = AdminColors.TextSecondary
                                    )
                                )
                                Column {
                                    Text(user.name, color = AdminColors.TextPrimary, fontSize = 14.sp)
                                    Text(user.email, color = AdminColors.TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            AdminTextField(form.title, { change(form.copy(title = it)) }, "Tiêu đề *", tag = "compose_title")
            AdminTextField(form.message, { change(form.copy(message = it)) }, "Nội dung *", singleLine = false, tag = "compose_message")

            Text(
                text = "Sẽ gửi tới: ${state.recipientCount} người",
                color = if (state.recipientCount > 0) AdminColors.TextPrimary else AdminTone.Critical.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            form.error?.let { Text(it, color = AdminTone.Critical.color, fontSize = 12.sp) }
            AdminPrimaryButton("Gửi thông báo", onClick = viewModel::send, loading = form.isSending, tag = "compose_send")
        }
    }
}

@Composable
private fun BroadcastDetailDialog(detail: BroadcastDetailUi, viewModel: NotificationAdminViewModel) {
    if (detail.confirmingDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = AdminColors.Surface,
            title = { Text("Thu hồi thông báo?", color = AdminColors.TextPrimary) },
            text = {
                Text(
                    "Thông báo sẽ bị xóa khỏi hộp thư của ${detail.recipientCount} người nhận.",
                    color = AdminColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("broadcast_delete_confirm")) {
                    Text("Thu hồi", color = AdminTone.Critical.color)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Hủy", color = AdminColors.TextSecondary) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = viewModel::closeDetail,
        containerColor = AdminColors.Surface,
        title = { Text(if (detail.isEditing) "Sửa thông báo" else detail.title, color = AdminColors.TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (detail.isEditing) {
                    AdminTextField(detail.editTitle, { viewModel.onEditChange(it, detail.editMessage) }, "Tiêu đề", tag = "broadcast_edit_title")
                    AdminTextField(detail.editMessage, { viewModel.onEditChange(detail.editTitle, it) }, "Nội dung", singleLine = false, tag = "broadcast_edit_message")
                    Text(
                        "Thay đổi sẽ cập nhật cho cả ${detail.recipientCount} người đã nhận.",
                        color = AdminColors.TextSecondary,
                        fontSize = 11.sp
                    )
                    detail.error?.let { Text(it, color = AdminTone.Critical.color, fontSize = 12.sp) }
                } else {
                    Text(detail.message, color = AdminColors.TextPrimary, fontSize = 14.sp)
                    Text(detail.info, color = AdminColors.TextSecondary, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            if (detail.isEditing) {
                TextButton(enabled = !detail.isBusy, onClick = viewModel::saveEdit, modifier = Modifier.testTag("broadcast_edit_save")) {
                    Text("Lưu", color = AdminColors.Primary)
                }
            } else {
                TextButton(onClick = viewModel::startEdit, modifier = Modifier.testTag("broadcast_edit")) {
                    Text("Sửa", color = AdminColors.Primary)
                }
            }
        },
        dismissButton = {
            if (detail.isEditing) {
                TextButton(enabled = !detail.isBusy, onClick = viewModel::cancelEdit) {
                    Text("Hủy", color = AdminColors.TextSecondary)
                }
            } else {
                TextButton(onClick = viewModel::askDelete, modifier = Modifier.testTag("broadcast_delete")) {
                    Text("Thu hồi", color = AdminTone.Critical.color)
                }
            }
        }
    )
}
