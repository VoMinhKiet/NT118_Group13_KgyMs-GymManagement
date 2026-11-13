package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgyms.app.data.model.Asset
import com.kgyms.app.data.model.AssetCategory
import com.kgyms.app.data.model.AssetStatus
import com.kgyms.app.ui.admin.components.AdminColors
import com.kgyms.app.ui.admin.navigation.AdminRoute
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.kgyms.app.viewmodel.admin.AssetViewModel
import com.kgyms.app.viewmodel.admin.AssetForm

@Composable
fun AssetScreen(
    onNavigate: (AdminRoute) -> Unit,
    onAssetClick: (String) -> Unit,
    assetViewModel: AssetViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by assetViewModel.state.collectAsState()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showTicketSheet by rememberSaveable { mutableStateOf(false) }

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
             * THỐNG KÊ TÀI SẢN (tính từ Firebase)
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                AssetStatCard(
                    title = "Thiết bị",
                    value = if (state.isLoading) "…" else state.totalQuantity.toString(),
                    accentColor = AdminColors.Cyan,
                    modifier = Modifier.weight(1f)
                )

                AssetStatCard(
                    title = "Bảo trì",
                    value = if (state.isLoading) "…" else state.maintenanceQuantity.toString(),
                    accentColor = AdminColors.Pink,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            /*
             * ============================
             * DANH SÁCH TÀI SẢN
             * ============================
             */

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AdminColors.Primary)
                    }
                }

                state.errorMessage != null -> {
                    AssetInfoText(state.errorMessage.orEmpty(), AdminColors.Pink)
                }

                state.assets.isEmpty() -> {
                    AssetInfoText("Chưa có tài sản nào. Bấm \"Thêm tài sản\" để tạo mới.", AdminColors.TextSecondary)
                }

                else -> {
                    state.assets.forEach { asset ->
                        AssetCard(
                            asset = asset,
                            onClick = { onAssetClick(asset.id) },
                            onResolve = {
                                val doneLabel = if (asset.status == AssetStatus.BROKEN) "đã sửa" else "đã bảo trì"
                                assetViewModel.markResolved(asset.id) { error ->
                                    Toast.makeText(
                                        context,
                                        error ?: "\"${asset.name}\" $doneLabel, đang hoạt động lại",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            /*
             * ============================
             * THÊM TÀI SẢN
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AdminColors.Primary)
                    .clickable { showAddSheet = true }
                    .testTag("asset_add_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+  Thêm tài sản",
                    color = AdminColors.OnPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            /*
             * ============================
             * TẠO PHIẾU BẢO TRÌ
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, AdminColors.Primary, RoundedCornerShape(18.dp))
                    .clickable {
                        if (state.assets.isEmpty()) {
                            Toast.makeText(context, "Chưa có tài sản nào để tạo phiếu.", Toast.LENGTH_SHORT).show()
                        } else {
                            showTicketSheet = true
                        }
                    }
                    .testTag("asset_ticket_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tạo phiếu bảo trì",
                    color = AdminColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Mẹo: nhấn giữ máy đang bảo trì/hỏng để đánh dấu đã sửa xong.",
                color = AdminColors.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showTicketSheet) {
        MaintenanceTicketSheet(
            assets = state.assets,
            onDismiss = { showTicketSheet = false },
            onSubmit = { assetId, status, reason, onError ->
                assetViewModel.createMaintenanceTicket(assetId, status, reason) { error ->
                    if (error == null) {
                        showTicketSheet = false
                        val name = state.assets.firstOrNull { it.id == assetId }?.name.orEmpty()
                        Toast.makeText(context, "Đã tạo phiếu cho \"$name\"", Toast.LENGTH_SHORT).show()
                    } else {
                        onError(error)
                    }
                }
            }
        )
    }

    if (showAddSheet) {
        AddAssetSheet(
            onDismiss = { showAddSheet = false },
            onSubmit = { form, onError ->
                assetViewModel.addAsset(form) { error ->
                    if (error == null) {
                        showAddSheet = false
                        Toast.makeText(context, "Đã thêm \"${form.name.trim()}\"", Toast.LENGTH_SHORT).show()
                    } else {
                        onError(error)
                    }
                }
            }
        )
    }
}

/*
 * ============================
 * NHÃN / MÀU DÙNG CHUNG (list + detail)
 * ============================
 */

internal fun assetStatusLabel(status: String): String = when (status) {
    AssetStatus.WORKING -> "Đang hoạt động"
    AssetStatus.MAINTENANCE -> "Đang bảo trì"
    AssetStatus.BROKEN -> "Hỏng"
    else -> status
}

internal fun assetStatusColor(status: String): Color = when (status) {
    AssetStatus.WORKING -> AdminColors.Green
    AssetStatus.MAINTENANCE -> AdminColors.Yellow
    else -> AdminColors.Pink
}

internal fun assetCategoryLabel(category: String): String = when (category) {
    AssetCategory.CARDIO -> "Cardio"
    AssetCategory.STRENGTH -> "Strength"
    AssetCategory.OTHER -> "Khác"
    else -> category
}

@Composable
private fun AssetInfoText(text: String, color: Color) {
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

/*
 * ============================
 * STAT CARD
 * ============================
 */

@Composable
private fun AssetStatCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(text = title, color = AdminColors.TextSecondary, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = value,
            color = AdminColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accentColor)
        )
    }
}

/*
 * ============================
 * ASSET CARD
 * ============================
 */

/**
 * Bấm: mở chi tiết. Nhấn giữ máy đang bảo trì/hỏng: hiện menu "Đã bảo trì" / "Đã sửa".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetCard(
    asset: Asset,
    onClick: () -> Unit,
    onResolve: () -> Unit
) {
    val needsFix = asset.status != AssetStatus.WORKING
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (needsFix) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                } else null
            )
            .testTag("asset_card_${asset.id}")
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        /* Chấm màu theo trạng thái */
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(AdminColors.SurfaceLight),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(assetStatusColor(asset.status))
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        /* Tên + số lượng + trạng thái */
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${asset.name} • ${asset.quantity}",
                color = AdminColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = listOf(assetStatusLabel(asset.status), asset.location)
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
                color = AdminColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        Text(
            text = "›",
            color = AdminColors.TextSecondary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light
        )
    }

    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
        containerColor = AdminColors.Surface
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = if (asset.status == AssetStatus.BROKEN) "✓  Đã sửa" else "✓  Đã bảo trì",
                    color = AdminColors.Green,
                    fontWeight = FontWeight.SemiBold
                )
            },
            onClick = {
                menuOpen = false
                onResolve()
            },
            modifier = Modifier.testTag("asset_resolve_${asset.id}")
        )
    }
    }
}

/*
 * ============================
 * FORM THÊM TÀI SẢN (BOTTOM SHEET)
 * ============================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAssetSheet(
    onDismiss: () -> Unit,
    onSubmit: (form: AssetForm, onError: (String) -> Unit) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(AssetCategory.CARDIO) }
    var brand by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(AssetStatus.WORKING) }
    var location by rememberSaveable { mutableStateOf("") }
    var purchaseDate by rememberSaveable { mutableStateOf("") }
    var lastMaintenanceDate by rememberSaveable { mutableStateOf("") }
    var nextMaintenanceDate by rememberSaveable { mutableStateOf("") }
    var warrantyMonths by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
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
                text = "Thêm tài sản",
                color = AdminColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            AssetFormField(value = name, onValueChange = { name = it }, label = "Tên máy *", tag = "asset_form_name")

            AssetChipGroup(
                title = "Loại",
                options = AssetCategory.all,
                selected = category,
                label = ::assetCategoryLabel,
                onSelect = { category = it }
            )

            AssetFormField(value = brand, onValueChange = { brand = it }, label = "Hãng / Model")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetFormField(
                    value = quantity,
                    onValueChange = { quantity = it.filter(Char::isDigit) },
                    label = "Số lượng *",
                    keyboardType = KeyboardType.Number,
                    tag = "asset_form_quantity",
                    modifier = Modifier.weight(1f)
                )
                AssetFormField(
                    value = warrantyMonths,
                    onValueChange = { warrantyMonths = it.filter(Char::isDigit) },
                    label = "Bảo hành (tháng)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            AssetChipGroup(
                title = "Trạng thái",
                options = AssetStatus.all,
                selected = status,
                label = ::assetStatusLabel,
                onSelect = { status = it }
            )

            AssetFormField(value = location, onValueChange = { location = it }, label = "Vị trí (vd: Khu Strength • Tầng 1)")

            // Ô ngày chỉ lưu chữ số (tối đa 8); dấu "/" được tự chèn khi hiển thị.
            AssetDateField(
                digits = purchaseDate,
                onDigitsChange = { purchaseDate = it },
                label = "Ngày mua (dd/MM/yyyy)"
            )
            AssetDateField(
                digits = lastMaintenanceDate,
                onDigitsChange = { lastMaintenanceDate = it },
                label = "Lần bảo trì gần nhất (dd/MM/yyyy)"
            )
            AssetDateField(
                digits = nextMaintenanceDate,
                onDigitsChange = { nextMaintenanceDate = it },
                label = "Lịch bảo trì tiếp theo (dd/MM/yyyy)"
            )

            AssetFormField(
                value = description,
                onValueChange = { description = it },
                label = "Mô tả",
                singleLine = false
            )

            errorMessage?.let {
                Text(text = it, color = AdminColors.Pink, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSaving) AdminColors.SurfaceLight else AdminColors.Primary)
                    .clickable(enabled = !isSaving) {
                        errorMessage = null
                        isSaving = true
                        onSubmit(
                            AssetForm(
                                name = name,
                                category = category,
                                brand = brand,
                                quantity = quantity,
                                status = status,
                                location = location,
                                purchaseDate = formatDateDigits(purchaseDate),
                                lastMaintenanceDate = formatDateDigits(lastMaintenanceDate),
                                nextMaintenanceDate = formatDateDigits(nextMaintenanceDate),
                                warrantyMonths = warrantyMonths,
                                description = description
                            )
                        ) { error ->
                            isSaving = false
                            errorMessage = error
                        }
                    }
                    .testTag("asset_form_submit"),
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = AdminColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = "Lưu tài sản",
                        color = AdminColors.OnPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/*
 * ============================
 * PHIẾU BẢO TRÌ (BOTTOM SHEET)
 * ============================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceTicketSheet(
    assets: List<Asset>,
    onDismiss: () -> Unit,
    onSubmit: (assetId: String?, status: String, reason: String, onError: (String) -> Unit) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf(AssetStatus.MAINTENANCE) }
    var reason by rememberSaveable { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
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
                text = "Tạo phiếu bảo trì",
                color = AdminColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Text(text = "Chọn 1 loại máy", color = AdminColors.TextSecondary, fontSize = 12.sp)

            assets.forEach { asset ->
                val isSelected = asset.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AdminColors.Surface)
                        .border(
                            1.dp,
                            if (isSelected) AdminColors.Primary else AdminColors.Border,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedId = asset.id }
                        .testTag("ticket_asset_${asset.id}")
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedId = asset.id },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AdminColors.Primary,
                            unselectedColor = AdminColors.TextSecondary
                        )
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${asset.name} • ${asset.quantity}",
                            color = AdminColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = assetStatusLabel(asset.status),
                            color = assetStatusColor(asset.status),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            AssetChipGroup(
                title = "Chuyển máy sang trạng thái",
                options = listOf(AssetStatus.MAINTENANCE, AssetStatus.BROKEN),
                selected = status,
                label = ::assetStatusLabel,
                onSelect = { status = it }
            )

            AssetFormField(
                value = reason,
                onValueChange = { reason = it },
                label = "Lý do cần sửa / bảo trì *",
                singleLine = false,
                tag = "ticket_reason"
            )

            errorMessage?.let {
                Text(text = it, color = AdminColors.Pink, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSaving) AdminColors.SurfaceLight else AdminColors.Primary)
                    .clickable(enabled = !isSaving) {
                        errorMessage = null
                        isSaving = true
                        onSubmit(selectedId, status, reason) { error ->
                            isSaving = false
                            errorMessage = error
                        }
                    }
                    .testTag("ticket_submit"),
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = AdminColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = "Gửi phiếu",
                        color = AdminColors.OnPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/*
 * ============================
 * Ô NHẬP NGÀY: gõ số, tự chèn "/"
 * ============================
 */

/** "12032024" → "12/03/2024"; chuỗi chưa đủ số vẫn được chèn "/" để ViewModel báo sai định dạng. */
private fun formatDateDigits(digits: String): String = buildString {
    digits.forEachIndexed { index, char ->
        if (index == 2 || index == 4) append('/')
        append(char)
    }
}

/** Hiển thị chữ số dạng dd/MM/yyyy mà không cần bàn phím có phím "/". */
private object DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatDateDigits(text.text)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 1 -> offset
                offset <= 3 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(formatted.length)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, text.length)
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

@Composable
private fun AssetDateField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String
) {
    AssetFormField(
        value = digits,
        onValueChange = { input -> onDigitsChange(input.filter(Char::isDigit).take(8)) },
        label = label,
        keyboardType = KeyboardType.Number,
        visualTransformation = DateVisualTransformation
    )
}

@Composable
private fun AssetFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    tag: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
        modifier = if (tag != null) modifier.testTag(tag) else modifier
    )
}

@Composable
private fun AssetChipGroup(
    title: String,
    options: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, color = AdminColors.TextSecondary, fontSize = 12.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) AdminColors.Primary else AdminColors.Surface)
                        .border(
                            1.dp,
                            if (isSelected) AdminColors.Primary else AdminColors.Border,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label(option),
                        color = if (isSelected) AdminColors.OnPrimary else AdminColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
