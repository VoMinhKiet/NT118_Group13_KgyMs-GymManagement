package com.kgyms.app.ui.admin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.model.ExpiryReminderSettings
import com.kgyms.app.data.model.NotificationBroadcast
import com.kgyms.app.data.model.NotificationTarget
import com.kgyms.app.data.model.NotificationType
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.viewmodel.admin.NotificationAdminViewModel
import com.kgyms.app.viewmodel.admin.NotificationForm

@Composable
fun NotificationScreen(
    viewModel: NotificationAdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var showCompose by rememberSaveable { mutableStateOf(false) }
    var openedBroadcastId by rememberSaveable { mutableStateOf<String?>(null) }
    val openedBroadcast = state.broadcasts.firstOrNull { it.id == openedBroadcastId }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

            /*
             * ============================
             * NHẮC HẾT HẠN TỰ ĐỘNG
             * ============================
             */

            ExpiryReminderCard(
                settings = state.settings,
                isScanning = state.isScanning,
                onToggle = { enabled ->
                    viewModel.saveExpirySettings(enabled, state.settings.days, ::toast)
                },
                onToggleDay = { day ->
                    val days = state.settings.days.let { if (day in it) it - day else it + day }
                    viewModel.saveExpirySettings(state.settings.enabled, days, ::toast)
                },
                onScan = { viewModel.scanNow(::toast) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            /*
             * ============================
             * SOẠN THÔNG BÁO
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AdminColors.Primary)
                    .clickable {
                        viewModel.refreshReferenceData()
                        showCompose = true
                    }
                    .testTag("notification_compose_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+  Soạn thông báo",
                    color = AdminColors.OnPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            /*
             * ============================
             * LỊCH SỬ ĐÃ GỬI
             * ============================
             */

            Text(
                text = "Đã gửi (${state.broadcasts.size})",
                color = AdminColors.TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = AdminColors.Primary) }

                state.errorMessage != null -> NoticeInfoText(state.errorMessage.orEmpty(), AdminColors.Pink)

                state.broadcasts.isEmpty() -> NoticeInfoText(
                    "Chưa gửi thông báo nào. Bấm \"Soạn thông báo\" để gửi ưu đãi hoặc thông báo riêng.",
                    AdminColors.TextSecondary
                )

                else -> state.broadcasts.forEach { broadcast ->
                    BroadcastCard(broadcast) { openedBroadcastId = broadcast.id }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showCompose) {
        ComposeNotificationSheet(
            viewModel = viewModel,
            onDismiss = { showCompose = false },
            onSent = { count ->
                showCompose = false
                toast("Đã gửi thông báo tới $count người")
            }
        )
    }

    openedBroadcast?.let { broadcast ->
        BroadcastDetailDialog(
            broadcast = broadcast,
            onDismiss = { openedBroadcastId = null },
            onEdit = { title, message, onError ->
                viewModel.edit(broadcast, title, message) { error ->
                    if (error == null) {
                        openedBroadcastId = null
                        toast("Đã cập nhật thông báo cho ${broadcast.recipients.size} người")
                    } else onError(error)
                }
            },
            onDelete = {
                viewModel.delete(broadcast) { error ->
                    openedBroadcastId = null
                    toast(error ?: "Đã thu hồi thông báo")
                }
            }
        )
    }
}

/*
 * ============================
 * NHÃN DÙNG CHUNG
 * ============================
 */

private fun typeLabel(type: String) = when (type) {
    NotificationType.PROMOTION -> "Ưu đãi"
    NotificationType.REMINDER -> "Nhắc nhở"
    else -> "Thông báo chung"
}

private fun typeColor(type: String): Color = when (type) {
    NotificationType.PROMOTION -> AdminColors.Yellow
    NotificationType.REMINDER -> AdminColors.Cyan
    else -> AdminColors.Green
}

private fun targetLabel(target: String) = when (target) {
    NotificationTarget.ALL_USERS -> "Tất cả user"
    NotificationTarget.ACTIVE_MEMBERS -> "Hội viên có gói"
    else -> "1 cá nhân"
}

/*
 * ============================
 * CARD NHẮC HẾT HẠN
 * ============================
 */

@Composable
private fun ExpiryReminderCard(
    settings: ExpiryReminderSettings,
    isScanning: Boolean,
    onToggle: (Boolean) -> Unit,
    onToggleDay: (Int) -> Unit,
    onScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nhắc hết hạn gói",
                    color = AdminColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tự gửi \"Gói của bạn còn N ngày\" cho hội viên có gói active",
                    color = AdminColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AdminColors.OnPrimary,
                    checkedTrackColor = AdminColors.Primary
                ),
                modifier = Modifier.testTag("expiry_reminder_switch")
            )
        }

        Text(text = "Nhắc khi còn", color = AdminColors.TextSecondary, fontSize = 12.sp)

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExpiryReminderSettings.AVAILABLE_DAYS.forEach { day ->
                NoticeChip(
                    label = "$day ngày",
                    selected = day in settings.days,
                    enabled = settings.enabled,
                    onClick = { onToggleDay(day) },
                    tag = "expiry_day_$day"
                )
            }
        }

        Text(
            text = if (settings.lastRunAt.isBlank()) "Chưa quét lần nào. App tự quét mỗi khi admin mở app."
            else "Lần quét gần nhất: ${settings.lastRunAt} • đã gửi ${settings.lastRunCount}",
            color = AdminColors.TextSecondary,
            fontSize = 11.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, AdminColors.Primary, RoundedCornerShape(14.dp))
                .clickable(enabled = !isScanning) { onScan() }
                .testTag("expiry_scan_button"),
            contentAlignment = Alignment.Center
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    color = AdminColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "Quét & gửi ngay",
                    color = AdminColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/*
 * ============================
 * CARD LỊCH SỬ
 * ============================
 */

@Composable
private fun BroadcastCard(broadcast: NotificationBroadcast, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("broadcast_${broadcast.id}")
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(typeColor(broadcast.type).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (broadcast.type) {
                    NotificationType.PROMOTION -> "🎁"
                    NotificationType.REMINDER -> "⏰"
                    else -> "🔔"
                },
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = broadcast.title,
                color = AdminColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${typeLabel(broadcast.type)} • ${broadcast.targetLabel.ifBlank { targetLabel(broadcast.target) }} • ${broadcast.recipients.size} người",
                color = AdminColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = broadcast.createdAt, color = AdminColors.TextSecondary, fontSize = 10.sp)
        }

        Text(text = "›", color = AdminColors.TextSecondary, fontSize = 24.sp, fontWeight = FontWeight.Light)
    }
}

/*
 * ============================
 * CHI TIẾT / SỬA / THU HỒI
 * ============================
 */

@Composable
private fun BroadcastDetailDialog(
    broadcast: NotificationBroadcast,
    onDismiss: () -> Unit,
    onEdit: (title: String, message: String, onError: (String) -> Unit) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var title by remember(broadcast.id) { mutableStateOf(broadcast.title) }
    var message by remember(broadcast.id) { mutableStateOf(broadcast.message) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = AdminColors.Surface,
            title = { Text("Thu hồi thông báo?", color = AdminColors.TextPrimary) },
            text = {
                Text(
                    "Thông báo sẽ bị xóa khỏi hộp thư của ${broadcast.recipients.size} người nhận.",
                    color = AdminColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; busy = true; onDelete() },
                    modifier = Modifier.testTag("broadcast_delete_confirm")
                ) { Text("Thu hồi", color = AdminColors.Pink) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Hủy", color = AdminColors.TextSecondary) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = AdminColors.Surface,
        title = {
            Text(
                text = if (editing) "Sửa thông báo" else broadcast.title,
                color = AdminColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (editing) {
                    NoticeField(title, { title = it }, "Tiêu đề", tag = "broadcast_edit_title")
                    NoticeField(message, { message = it }, "Nội dung", singleLine = false, tag = "broadcast_edit_message")
                    Text(
                        text = "Thay đổi sẽ cập nhật cho cả ${broadcast.recipients.size} người đã nhận.",
                        color = AdminColors.TextSecondary,
                        fontSize = 11.sp
                    )
                    error?.let { Text(it, color = AdminColors.Pink, fontSize = 12.sp) }
                } else {
                    Text(broadcast.message, color = AdminColors.TextPrimary, fontSize = 14.sp)
                    Text(
                        text = "${typeLabel(broadcast.type)} • ${broadcast.targetLabel} • ${broadcast.recipients.size} người\n${broadcast.createdAt}",
                        color = AdminColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        error = null
                        busy = true
                        onEdit(title, message) { e -> busy = false; error = e }
                    },
                    modifier = Modifier.testTag("broadcast_edit_save")
                ) { Text("Lưu", color = AdminColors.Primary) }
            } else {
                TextButton(
                    onClick = { editing = true },
                    modifier = Modifier.testTag("broadcast_edit")
                ) { Text("Sửa", color = AdminColors.Primary) }
            }
        },
        dismissButton = {
            if (editing) {
                TextButton(enabled = !busy, onClick = {
                    editing = false
                    title = broadcast.title
                    message = broadcast.message
                    error = null
                }) { Text("Hủy", color = AdminColors.TextSecondary) }
            } else {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.testTag("broadcast_delete")
                ) { Text("Thu hồi", color = AdminColors.Pink) }
            }
        }
    )
}

/*
 * ============================
 * FORM SOẠN THÔNG BÁO
 * ============================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeNotificationSheet(
    viewModel: NotificationAdminViewModel,
    onDismiss: () -> Unit,
    onSent: (Int) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by rememberSaveable { mutableStateOf(NotificationType.PROMOTION) }
    var target by rememberSaveable { mutableStateOf(NotificationTarget.ALL_USERS) }
    var packageId by rememberSaveable { mutableStateOf("") }
    var targetUid by rememberSaveable { mutableStateOf("") }
    var userQuery by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    val form = NotificationForm(title, message, type, target, packageId, targetUid)
    val count = viewModel.recipientCount(form)

    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        sheetState = sheetState,
        containerColor = AdminColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Soạn thông báo",
                color = AdminColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            /* Loại */
            Text("Loại", color = AdminColors.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NotificationType.all.forEach { option ->
                    NoticeChip(typeLabel(option), type == option, onClick = { type = option }, tag = "compose_type_$option")
                }
            }

            /* Gửi tới */
            Text("Gửi tới", color = AdminColors.TextSecondary, fontSize = 12.sp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NotificationTarget.all.forEach { option ->
                    NoticeChip(targetLabel(option), target == option, onClick = { target = option }, tag = "compose_target_$option")
                }
            }

            when (target) {
                NotificationTarget.ACTIVE_MEMBERS -> {
                    Text("Lọc theo gói", color = AdminColors.TextSecondary, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NoticeChip("Mọi gói", packageId.isBlank(), onClick = { packageId = "" }, tag = "compose_pkg_all")
                        state.packages.forEach { pkg ->
                            NoticeChip(pkg.name, packageId == pkg.id, onClick = { packageId = pkg.id }, tag = "compose_pkg_${pkg.id}")
                        }
                    }
                }

                NotificationTarget.USER -> {
                    NoticeField(userQuery, { userQuery = it }, "Tìm theo tên hoặc email", tag = "compose_user_search")

                    val keyword = userQuery.trim()
                    val matches = state.users.filter {
                        keyword.isEmpty() ||
                            it.fullName.contains(keyword, ignoreCase = true) ||
                            it.email.contains(keyword, ignoreCase = true)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (matches.isEmpty()) {
                            Text("Không tìm thấy người dùng.", color = AdminColors.TextSecondary, fontSize = 12.sp)
                        }
                        matches.forEach { user ->
                            val selected = user.uid == targetUid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AdminColors.Surface)
                                    .border(
                                        1.dp,
                                        if (selected) AdminColors.Primary else AdminColors.Border,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { targetUid = user.uid }
                                    .testTag("compose_user_${user.uid}")
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { targetUid = user.uid },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AdminColors.Primary,
                                        unselectedColor = AdminColors.TextSecondary
                                    )
                                )
                                Column {
                                    Text(user.fullName.ifBlank { "(Chưa có tên)" }, color = AdminColors.TextPrimary, fontSize = 14.sp)
                                    Text(user.email, color = AdminColors.TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            /* Nội dung */
            NoticeField(title, { title = it }, "Tiêu đề *", tag = "compose_title")
            NoticeField(message, { message = it }, "Nội dung *", singleLine = false, tag = "compose_message")

            Text(
                text = "Sẽ gửi tới: $count người",
                color = if (count > 0) AdminColors.TextPrimary else AdminColors.Pink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            error?.let { Text(it, color = AdminColors.Pink, fontSize = 12.sp) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (sending) AdminColors.SurfaceLight else AdminColors.Primary)
                    .clickable(enabled = !sending) {
                        error = null
                        sending = true
                        viewModel.send(form) { e ->
                            if (e == null) onSent(count) else {
                                sending = false
                                error = e
                            }
                        }
                    }
                    .testTag("compose_send"),
                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        color = AdminColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("Gửi thông báo", color = AdminColors.OnPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/*
 * ============================
 * COMPONENTS NHỎ
 * ============================
 */

@Composable
private fun NoticeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    tag: String
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background((if (selected) AdminColors.Primary else AdminColors.Surface).copy(alpha = alpha))
            .border(
                1.dp,
                (if (selected) AdminColors.Primary else AdminColors.Border).copy(alpha = alpha),
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = (if (selected) AdminColors.OnPrimary else AdminColors.TextPrimary).copy(alpha = alpha),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun NoticeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    tag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AdminColors.TextPrimary,
            unfocusedTextColor = AdminColors.TextPrimary,
            focusedBorderColor = AdminColors.Primary,
            unfocusedBorderColor = AdminColors.Border,
            focusedLabelColor = AdminColors.Primary,
            unfocusedLabelColor = AdminColors.TextSecondary,
            cursorColor = AdminColors.Primary,
            focusedContainerColor = AdminColors.Surface,
            unfocusedContainerColor = AdminColors.Surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    )
}

@Composable
private fun NoticeInfoText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp, horizontal = 8.dp)
    )
}
