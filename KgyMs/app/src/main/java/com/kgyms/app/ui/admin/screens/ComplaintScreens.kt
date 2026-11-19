package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
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
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminMessageEffect
import com.kgyms.app.ui.admin.components.AdminOutlinedButton
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.components.AdminSection
import com.kgyms.app.ui.admin.components.AdminStatCard
import com.kgyms.app.ui.admin.components.AdminStatRow
import com.kgyms.app.ui.admin.components.AdminTextField
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.ComplaintViewModel

@Composable
fun ComplaintScreen(
    onComplaintClick: (String) -> Unit,
    viewModel: ComplaintViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    AdminPage {
        AdminStatRow { cell ->
            AdminStatCard("Mới", state.newCount, modifier = cell, tone = AdminTone.Critical)
            AdminStatCard("Đang xử lý", state.inProgressCount, modifier = cell, tone = AdminTone.Warning)
        }
        Spacer(modifier = Modifier.height(12.dp))

        state.rows.forEach { row ->
            AdminListRow(
                title = row.title,
                subtitle = row.subtitle,
                tone = row.tone,
                trailingText = row.priorityLabel,
                onClick = { onComplaintClick(row.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ComplaintDetailScreen(
    complaintId: String,
    onMissing: () -> Unit,
    viewModel: ComplaintViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val detail = viewModel.detail(complaintId)
    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        if (detail == null) {
            AdminMessage("Không có khiếu nại được chọn.")
            AdminOutlinedButton("Quay lại danh sách", onClick = onMissing)
            return@AdminPage
        }

        AdminCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    detail.id,
                    color = AdminColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                AdminBadge(detail.priorityLabel, detail.tone)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail.title, color = AdminColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(detail.reporter, color = AdminColors.TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            AdminLabel("Trạng thái: ${detail.statusLabel}")
            Spacer(modifier = Modifier.height(6.dp))
            Text(detail.description, color = AdminColors.TextPrimary, fontSize = 14.sp)
        }

        AdminSection("Phản hồi")
        AdminTextField(
            value = state.drafts[complaintId].orEmpty(),
            onValueChange = { viewModel.onDraftChange(complaintId, it) },
            label = "Nhập nội dung xử lý",
            singleLine = false,
            tag = "admin_complaint_response"
        )
        Spacer(modifier = Modifier.height(12.dp))
        AdminPrimaryButton("Cập nhật trạng thái", onClick = { viewModel.submitResponse(complaintId) })
    }
}
