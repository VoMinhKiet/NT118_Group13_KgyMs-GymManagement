package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.ui.admin.components.AdminCard
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.components.AdminKeyValueRow
import com.kgyms.app.ui.admin.components.AdminLabel
import com.kgyms.app.ui.admin.components.AdminLoading
import com.kgyms.app.ui.admin.components.AdminMessage
import com.kgyms.app.ui.admin.components.AdminMessageEffect
import com.kgyms.app.ui.admin.components.AdminOutlinedButton
import com.kgyms.app.ui.admin.components.AdminPage
import com.kgyms.app.ui.admin.components.color
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.AssetViewModel

@Composable
fun AssetDetailScreen(
    assetId: String,
    onBack: () -> Unit,
    viewModel: AssetViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val detail = viewModel.detail(assetId)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    AdminMessageEffect(state.message, viewModel::onMessageShown)

    AdminPage {
        when {
            state.isLoading -> AdminLoading()
            detail == null -> AdminMessage(state.errorMessage ?: "Không tìm thấy tài sản này (có thể đã bị xóa).")
            else -> {
                Text(detail.name, color = AdminColors.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(detail.location, color = AdminColors.TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))

                AdminCard {
                    AdminLabel("TRẠNG THÁI")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(detail.statusLabel, color = detail.statusTone.color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(detail.lastCheck, color = AdminColors.TextSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))
                detail.fields.forEach { (label, value) ->
                    AdminKeyValueRow(label, value)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (detail.description.isNotBlank()) {
                    AdminCard {
                        AdminLabel("Mô tả")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(detail.description, color = AdminColors.TextPrimary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                AdminOutlinedButton(
                    text = "Xóa tài sản",
                    onClick = { confirmDelete = true },
                    tone = AdminTone.Critical,
                    loading = state.deletingId == assetId,
                    tag = "asset_delete_button"
                )
            }
        }
    }

    if (confirmDelete && detail != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = AdminColors.Surface,
            title = { Text("Xóa tài sản", color = AdminColors.TextPrimary) },
            text = {
                Text(
                    "Bạn chắc chắn muốn xóa \"${detail.name}\"? Thao tác này không thể hoàn tác.",
                    color = AdminColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteAsset(assetId, onDeleted = onBack)
                    },
                    modifier = Modifier.testTag("asset_delete_confirm")
                ) { Text("Xóa", color = AdminTone.Critical.color) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Hủy", color = AdminColors.TextSecondary) }
            }
        )
    }
}
