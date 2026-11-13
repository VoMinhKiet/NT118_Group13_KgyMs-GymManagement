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
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminListRow
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.AdminPrimaryButton
import com.kgyms.app.ui.admin.navigation.LocalAdminFeedback
import com.kgyms.app.viewmodel.admin.PackageViewModel

@Composable
fun PackageScreen(
    viewModel: PackageViewModel = viewModel()
) {
    val state = viewModel.state
    val showMockAction = LocalAdminFeedback.current

    AdminPage {
        Text(state.headline, color = AdminColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Tạo, cập nhật giá và quyền lợi.", color = AdminColors.TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        state.rows.forEach { row ->
            AdminListRow(
                title = row.title,
                tone = row.tone,
                trailingText = row.statusLabel,
                onClick = { showMockAction("Chi tiết gói tập") }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
        AdminPrimaryButton("Tạo gói mới", onClick = { showMockAction("Tạo gói mới") })
    }
}
