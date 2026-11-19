package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kgyms.app.ui.admin.components.AdminBadge
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminChipGroup
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminKeyValueRow
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
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.DetailFieldUi
import com.kgyms.app.viewmodel.admin.MemberViewModel

/* ============================ DANH SÁCH ============================ */

@Composable
fun MemberScreen(
    onMemberClick: (String) -> Unit,
    viewModel: MemberViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        Row {
            AdminStatCard("Hội viên", state.totalCount, modifier = Modifier.weight(1f), tone = AdminTone.Info)
            Spacer(modifier = Modifier.width(8.dp))
            AdminStatCard("Có gói", state.activeCount, modifier = Modifier.weight(1f), tone = AdminTone.Positive)
            Spacer(modifier = Modifier.width(8.dp))
            AdminStatCard("Đang ở gym", state.inGymCount, modifier = Modifier.weight(1f), tone = AdminTone.Brand)
        }
        Spacer(modifier = Modifier.height(12.dp))
        AdminSearchBox(state.query, viewModel::onQueryChange, "Tên, email, SĐT, mã hội viên", "admin_member_search")
        Spacer(modifier = Modifier.height(10.dp))
        AdminChipGroup(state.filters, { it == state.filter }, viewModel::onFilterSelected, tagPrefix = "admin_member_filter")
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading -> AdminLoading()
            state.errorMessage != null -> AdminMessage(state.errorMessage.orEmpty(), AdminTone.Critical)
            state.emptyMessage != null -> AdminMessage(state.emptyMessage.orEmpty())
            else -> state.rows.forEach { row ->
                AdminListRow(
                    title = row.name,
                    subtitle = row.subtitle,
                    tone = row.tone,
                    trailingText = row.statusLabel,
                    modifier = Modifier.testTag("member_row_${row.uid}"),
                    onClick = { onMemberClick(row.uid) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/* ============================ CHI TIẾT ============================ */

@Composable
fun MemberDetailScreen(
    memberId: String,
    onCreateInvoice: (String) -> Unit,
    onSendNotification: (String) -> Unit,
    onReceiptClick: (String) -> Unit,
    viewModel: MemberViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val detail = state.details[memberId]
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        when {
            state.isLoading -> AdminLoading()
            detail == null -> AdminMessage(state.errorMessage ?: "Không tìm thấy hội viên.")
            else -> {
                AdminCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            detail.codeLine,
                            color = if (detail.hasCode) AdminColors.TextSecondary else AdminTone.Warning.color,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        AdminBadge(detail.statusLabel, detail.statusTone)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(detail.name, color = AdminColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (detail.inGym) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Đang ở gym", color = AdminTone.Positive.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (!detail.hasCode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AdminOutlinedButton(
                            "Cấp mã hội viên",
                            onClick = { viewModel.assignMemberCode(memberId) },
                            loading = state.assigningCodeFor == memberId,
                            tag = "member_assign_code"
                        )
                    }
                }

                AdminSection("Gói hiện tại")
                if (detail.currentPlan == null) {
                    AdminMessage("Hội viên chưa có gói đang hoạt động.")
                } else {
                    DetailFields(detail.currentPlan)
                }

                AdminSection("Thông tin")
                DetailFields(detail.profile)

                AdminSection("Điểm danh")
                DetailFields(detail.checkIn)

                AdminSection("Lịch sử gói (${detail.plans.size})")
                if (detail.plans.isEmpty()) AdminMessage("Chưa đăng ký gói nào.")
                detail.plans.forEach { plan ->
                    AdminListRow(title = plan.title, subtitle = plan.subtitle, tone = plan.tone, trailingText = plan.statusLabel)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                AdminSection("Hoá đơn (${detail.payments.size})")
                if (detail.payments.isEmpty()) AdminMessage("Chưa có hoá đơn nào.")
                detail.payments.forEach { payment ->
                    AdminListRow(
                        title = payment.title,
                        subtitle = payment.subtitle,
                        tone = payment.tone,
                        trailingText = payment.statusLabel,
                        onClick = { onReceiptClick(payment.id) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
                AdminPrimaryButton(
                    "Tạo hóa đơn tại quầy",
                    onClick = { onCreateInvoice(memberId) },
                    enabled = !detail.isLocked,
                    tag = "member_create_invoice"
                )
                Spacer(modifier = Modifier.height(8.dp))
                AdminOutlinedButton("Gửi thông báo riêng", onClick = { onSendNotification(memberId) }, tag = "member_send_notification")
                Spacer(modifier = Modifier.height(8.dp))
                AdminOutlinedButton(
                    if (detail.isLocked) "Mở khoá tài khoản" else "Khoá tài khoản",
                    onClick = { viewModel.requestLockToggle(memberId) },
                    tone = if (detail.isLocked) AdminTone.Positive else AdminTone.Critical,
                    tag = "member_lock_toggle"
                )
            }
        }
    }

    state.confirmLock?.let { confirm ->
        AlertDialog(
            onDismissRequest = viewModel::cancelLockToggle,
            containerColor = AdminColors.Surface,
            title = {
                Text(
                    if (confirm.lock) "Khoá tài khoản ${confirm.name}?" else "Mở khoá ${confirm.name}?",
                    color = AdminColors.TextPrimary
                )
            },
            text = {
                Text(
                    if (confirm.lock) "Hội viên sẽ không đăng nhập được cho tới khi được mở khoá. Gói và hoá đơn vẫn được giữ nguyên."
                    else "Hội viên sẽ đăng nhập và sử dụng app bình thường.",
                    color = AdminColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLockToggle, modifier = Modifier.testTag("member_lock_confirm")) {
                    Text("Xác nhận", color = if (confirm.lock) AdminTone.Critical.color else AdminTone.Positive.color)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelLockToggle) { Text("Hủy", color = AdminColors.TextSecondary) }
            }
        )
    }
}

/** Danh sách "nhãn — giá trị" dùng chung cho các màn chi tiết. */
@Composable
internal fun DetailFields(fields: List<DetailFieldUi>) {
    fields.forEach { field ->
        AdminKeyValueRow(field.label, field.value, field.tone)
        Spacer(modifier = Modifier.height(8.dp))
    }
}
