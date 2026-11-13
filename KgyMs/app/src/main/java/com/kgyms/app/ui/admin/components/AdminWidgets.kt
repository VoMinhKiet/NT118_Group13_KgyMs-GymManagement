package com.kgyms.app.ui.admin.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.viewmodel.admin.AdminTone
import com.kgyms.app.viewmodel.admin.BarUi
import com.kgyms.app.viewmodel.admin.OptionUi

/*
 * ================================================================
 * Bộ component dùng chung cho mọi màn Admin.
 * Màn hình chỉ ghép các khối này với UiState từ ViewModel,
 * không tự vẽ lại card/nút/ô nhập.
 * ================================================================
 */

/** Màu ứng với sắc thái mà ViewModel trả về. */
val AdminTone.color: Color
    get() = when (this) {
        AdminTone.Brand -> AdminColors.Primary
        AdminTone.Positive -> AdminColors.Green
        AdminTone.Warning -> AdminColors.Yellow
        AdminTone.Critical -> AdminColors.Pink
        AdminTone.Info -> AdminColors.Cyan
        AdminTone.Neutral -> AdminColors.TextSecondary
    }

/* ============================ KHUNG TRANG ============================ */

/** Nền + cuộn dọc + lề chuẩn cho một màn Admin. */
@Composable
fun AdminPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AdminColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content
        )
    }
}

/** Tiêu đề nhóm, có khoảng cách phía trên. */
@Composable
fun AdminSection(title: String, modifier: Modifier = Modifier) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        color = AdminColors.TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
    Spacer(modifier = Modifier.height(8.dp))
}

/** Nhãn nhỏ phía trên một nhóm điều khiển. */
@Composable
fun AdminLabel(text: String) {
    Text(text = text, color = AdminColors.TextSecondary, fontSize = 12.sp)
}

@Composable
fun AdminLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AdminColors.Primary)
    }
}

/** Thông báo trống / lỗi ở giữa danh sách. */
@Composable
fun AdminMessage(text: String, tone: AdminTone = AdminTone.Neutral) {
    Text(
        text = text,
        color = tone.color,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp, horizontal = 8.dp)
    )
}

/* ============================ CARD / DÒNG ============================ */

@Composable
fun AdminCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AdminCardShape)
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, AdminCardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content
    )
}

/**
 * Một dòng danh sách: vạch màu theo trạng thái (tùy chọn) + tiêu đề + mô tả + mũi tên khi bấm được.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdminListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    tone: AdminTone? = null,
    titleColor: Color = AdminColors.TextPrimary,
    trailingText: String? = null,
    showChevron: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickable = onClick != null || onLongClick != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(AdminRowShape)
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, AdminRowShape)
            .then(
                if (clickable) Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                ) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tone != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tone.color)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AdminColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!meta.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = meta, color = AdminColors.TextDark, fontSize = 11.sp, maxLines = 1)
            }
        }

        if (trailingText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingText,
                color = tone?.color ?: AdminColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (clickable && showChevron) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = AdminIcons.ChevronRight,
                contentDescription = null,
                tint = AdminColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Dòng "nhãn — giá trị" cho màn chi tiết. */
@Composable
fun AdminKeyValueRow(
    label: String,
    value: String,
    tone: AdminTone? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(AdminRowShape)
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, AdminRowShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
            color = tone?.color ?: AdminColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(AdminIcons.ChevronRight, null, tint = AdminColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/* ============================ SỐ LIỆU ============================ */

@Composable
fun AdminStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    tone: AdminTone = AdminTone.Brand,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(AdminRowShape)
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, AdminRowShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = title, color = AdminColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = AdminColors.TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                color = AdminColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tone.color)
        )
    }
}

/** Hai thẻ số liệu nằm ngang. */
@Composable
fun AdminStatRow(content: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content(Modifier.weight(1f))
    }
}

@Composable
fun AdminBarChartCard(
    title: String,
    bars: List<BarUi>,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: (() -> Unit)? = null
) {
    AdminCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = AdminColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Text(text = trailing, color = AdminColors.TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight(bar.ratio.coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (bar.highlighted) AdminColors.Primary else AdminColors.SurfaceLight)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    color = AdminColors.TextDark,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(24.dp)
                )
            }
        }
    }
}

/* ============================ NÚT ============================ */

@Composable
fun AdminPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null
) {
    val active = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(AdminRowShape)
            .background(if (active) AdminColors.Primary else AdminColors.SurfaceLight)
            .clickable(enabled = active, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = AdminColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(
                text = text,
                color = if (active) AdminColors.OnPrimary else AdminColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Nút viền; tone = Critical cho thao tác xóa. */
@Composable
fun AdminOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AdminTone = AdminTone.Brand,
    loading: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null
) {
    val active = enabled && !loading
    val color = tone.color
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(AdminRowShape)
            .border(1.dp, if (active) color else AdminColors.Border, AdminRowShape)
            .clickable(enabled = active, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(
                text = text,
                color = if (active) color else AdminColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/* ============================ NHẬP LIỆU ============================ */

@Composable
fun AdminTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    tag: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
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
        modifier = modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    )
}

/** Nhóm chip chọn 1 (hoặc nhiều, khi [isSelected] trả true cho nhiều key). Cuộn ngang khi dài. */
@Composable
fun AdminChipGroup(
    options: List<OptionUi>,
    isSelected: (String) -> Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tagPrefix: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val selected = isSelected(option.key)
            val alpha = if (enabled) 1f else 0.45f
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background((if (selected) AdminColors.SurfaceLight else AdminColors.Surface).copy(alpha = alpha))
                    .border(
                        1.dp,
                        (if (selected) AdminColors.Primary else AdminColors.Border).copy(alpha = alpha),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(enabled = enabled) { onSelect(option.key) }
                    .then(if (tagPrefix != null) Modifier.testTag("${tagPrefix}_${option.key.ifBlank { "all" }}") else Modifier)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = option.label,
                    color = (if (selected) AdminColors.Primary else AdminColors.TextSecondary).copy(alpha = alpha),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/** Ô tìm kiếm có viền, dùng AdminSearchInput sẵn có. */
@Composable
fun AdminSearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String, tag: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(AdminRowShape)
            .background(AdminColors.Surface)
            .border(1.dp, AdminColors.Border, AdminRowShape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AdminSearchInput(value, onValueChange, placeholder, tag)
    }
}

/** Nhãn trạng thái dạng viên thuốc. */
@Composable
fun AdminBadge(text: String, tone: AdminTone) {
    Text(
        text = text,
        color = tone.color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tone.color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
