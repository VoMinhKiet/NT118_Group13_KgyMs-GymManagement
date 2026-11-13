package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminChipGroup
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminKeyValueRow
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.components.AdminSearchBox
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.ui.admin.navigation.LocalAdminFeedback
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.DetailFieldUi
import com.kgyms.app.viewmodel.admin.MemberViewModel

@Composable
fun MemberScreen(
    onMemberClick: (String) -> Unit,
    viewModel: MemberViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val showMockAction = LocalAdminFeedback.current

    AdminPage {
        AdminLabel("Tìm kiếm")
        Spacer(modifier = Modifier.height(6.dp))
        AdminSearchBox(state.query, viewModel::onQueryChange, "Tên, SĐT, mã hội viên", "admin_member_search")
        Spacer(modifier = Modifier.height(10.dp))
        AdminChipGroup(state.filters, { it == state.filter }, viewModel::onFilterSelected, tagPrefix = "admin_member_filter")
        Spacer(modifier = Modifier.height(12.dp))

        if (state.rows.isEmpty()) AdminMessage("Không tìm thấy hội viên")
        state.rows.forEach { row ->
            AdminListRow(title = row.name, subtitle = row.subtitle, tone = row.tone, onClick = { onMemberClick(row.id) })
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
        AdminPrimaryButton("Thêm hội viên", onClick = { showMockAction("Thêm hội viên") })
    }
}

@Composable
fun MemberDetailScreen(
    memberId: String,
    viewModel: MemberViewModel = viewModel()
) {
    val showMockAction = LocalAdminFeedback.current
    val detail = viewModel.detail(memberId)

    AdminPage {
        if (detail == null) {
            AdminMessage("Không tìm thấy hội viên.")
            return@AdminPage
        }

        AdminCard {
            AdminLabel(detail.planLabel)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail.name, color = AdminColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail.codeLine, color = AdminTone.Positive.color, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        DetailFields(detail.fields)
        Spacer(modifier = Modifier.height(4.dp))
        AdminPrimaryButton("Gia hạn / Bảo lưu", onClick = { showMockAction("Gia hạn / Bảo lưu") })
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
