package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.model.Asset
import com.kgyms.app.ui.admin.components.AdminColors
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.kgyms.app.viewmodel.admin.AssetViewModel

@Composable
fun AssetDetailScreen(
    assetId: String,
    onBack: () -> Unit,
    assetViewModel: AssetViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by assetViewModel.state.collectAsState()
    val asset = state.assets.firstOrNull { it.id == assetId }

    var confirmDelete by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminColors.Background)
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = AdminColors.Primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            asset == null -> {
                Text(
                    text = state.errorMessage ?: "Không tìm thấy tài sản này (có thể đã bị xóa).",
                    color = AdminColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }

            else -> {
                AssetDetailContent(
                    asset = asset,
                    isDeleting = isDeleting,
                    onDeleteClick = { confirmDelete = true }
                )
            }
        }
    }

    if (confirmDelete && asset != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa tài sản") },
            text = { Text("Bạn chắc chắn muốn xóa \"${asset.name}\"? Thao tác này không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        isDeleting = true
                        val deletedName = asset.name
                        assetViewModel.deleteAsset(asset.id) { error ->
                            isDeleting = false
                            if (error == null) {
                                onBack()
                                Toast.makeText(context, "Đã xóa \"$deletedName\"", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.testTag("asset_delete_confirm")
                ) {
                    Text("Xóa", color = AdminColors.Pink)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun AssetDetailContent(
    asset: Asset,
    isDeleting: Boolean,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {

        /*
         * ============================
         * ASSET HEADER
         * ============================
         */

        Text(
            text = asset.name,
            color = AdminColors.TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = asset.location.ifBlank { "Chưa có vị trí" },
            color = AdminColors.TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        /*
         * ============================
         * STATUS CARD
         * ============================
         */

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AdminColors.Surface)
                .border(1.dp, AdminColors.Border, RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "STATUS",
                    color = AdminColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = assetStatusLabel(asset.status),
                    color = assetStatusColor(asset.status),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Lần kiểm tra: ${asset.lastMaintenanceDate.ifBlank { "—" }}",
                    color = AdminColors.TextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .width(45.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AdminColors.Primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        /*
         * ============================
         * THÔNG TIN CHI TIẾT
         * ============================
         */

        listOf(
            "Loại" to assetCategoryLabel(asset.category),
            "Hãng / Model" to asset.brand,
            "Số lượng" to asset.quantity.toString(),
            "Ngày mua" to asset.purchaseDate,
            "Bảo hành" to if (asset.warrantyMonths > 0) "${asset.warrantyMonths} tháng" else "",
            "Lịch bảo trì" to asset.nextMaintenanceDate
        ).forEach { (label, value) ->
            AssetDetailItem(label = label, value = value.ifBlank { "—" })
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (asset.description.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(AdminColors.Surface)
                    .border(1.dp, AdminColors.Border, RoundedCornerShape(15.dp))
                    .padding(12.dp)
            ) {
                Text(text = "Mô tả", color = AdminColors.TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = asset.description, color = AdminColors.TextPrimary, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        /*
         * ============================
         * DELETE BUTTON
         * ============================
         */

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, AdminColors.Pink, RoundedCornerShape(18.dp))
                .clickable(enabled = !isDeleting) { onDeleteClick() }
                .testTag("asset_delete_button"),
            contentAlignment = Alignment.Center
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    color = AdminColors.Pink,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = "Xóa tài sản",
                    color = AdminColors.Pink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/*
 * ============================
 * DETAIL ITEM
 * ============================
 */

@Composable
private fun AssetDetailItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AdminColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            color = AdminColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
    }
}
