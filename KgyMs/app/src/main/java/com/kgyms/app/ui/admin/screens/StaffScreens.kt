package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.components.AdminStatCard
import com.kgyms.app.ui.admin.components.AdminStatRow
import com.kgyms.app.ui.admin.navigation.LocalAdminFeedback
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.StaffViewModel

@Composable
fun StaffScreen(
    onStaffClick: (String) -> Unit,
    viewModel: StaffViewModel = viewModel()
) {
    val state = viewModel.state
    val showMockAction = LocalAdminFeedback.current

    AdminPage {
        AdminStatRow { cell ->
            AdminStatCard("Nhân sự", state.totalStaff, modifier = cell, tone = AdminTone.Info)
            AdminStatCard("PT", state.trainerCount, modifier = cell, tone = AdminTone.Brand)
        }
        Spacer(modifier = Modifier.height(12.dp))

        state.rows.forEach { row ->
            AdminListRow(title = row.title, subtitle = row.subtitle, tone = row.tone, onClick = { onStaffClick(row.id) })
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
        AdminPrimaryButton("Thêm nhân sự", onClick = { showMockAction("Thêm nhân sự") })
    }
}

@Composable
fun StaffDetailScreen(
    staffId: String,
    viewModel: StaffViewModel = viewModel()
) {
    val showMockAction = LocalAdminFeedback.current
    val detail = viewModel.detail(staffId)

    AdminPage {
        if (detail == null) {
            AdminMessage("Không tìm thấy nhân sự.")
            return@AdminPage
        }

        AdminCard {
            AdminLabel(detail.role)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail.name, color = AdminColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail.headline, color = AdminColors.TextSecondary, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        DetailFields(detail.fields)
        Spacer(modifier = Modifier.height(4.dp))
        AdminPrimaryButton("Chỉnh sửa hồ sơ", onClick = { showMockAction("Chỉnh sửa hồ sơ nhân sự") })
    }
}
