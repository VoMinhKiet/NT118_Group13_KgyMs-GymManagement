package com.kgyms.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.localization.appText
import com.kgyms.app.utils.PasswordPolicy
import com.kgyms.app.utils.PasswordRequirement

private val MetColor = Color(0xFF2E9E5B)

/**
 * Checklist yêu cầu mật khẩu: mỗi dòng chuyển sang dấu tích xanh ngay khi mật khẩu đáp ứng.
 * Dùng chung cho màn Đăng ký và Đổi mật khẩu.
 */
@Composable
fun PasswordRequirementsChecklist(
    password: String,
    modifier: Modifier = Modifier,
    /** Màu cho dòng chưa đạt; màn có bảng màu riêng (ví dụ Hồ sơ sáng/tối) truyền vào. */
    pendingColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("password_requirements"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PasswordRequirement.entries.forEach { requirement ->
            RequirementRow(
                label = requirement.label(),
                met = PasswordPolicy.isMet(requirement, password),
                tag = "password_requirement_${requirement.name}",
                muted = pendingColor,
            )
        }
    }
}

@Composable
private fun PasswordRequirement.label(): String = when (this) {
    PasswordRequirement.MinLength -> appText(
        "Ít nhất ${PasswordPolicy.MIN_LENGTH} ký tự",
        "At least ${PasswordPolicy.MIN_LENGTH} characters",
    )
    PasswordRequirement.Uppercase -> appText("Có ít nhất 1 chữ viết hoa (A-Z)", "At least 1 uppercase letter (A-Z)")
    PasswordRequirement.Digit -> appText("Có ít nhất 1 chữ số (0-9)", "At least 1 number (0-9)")
    PasswordRequirement.Special -> appText(
        "Có ít nhất 1 ký tự đặc biệt (_ . ! @ ...)",
        "At least 1 special character (_ . ! @ ...)",
    )
}

@Composable
private fun RequirementRow(label: String, met: Boolean, tag: String, muted: Color) {
    val metState = appText("Đã đạt", "Met")
    val unmetState = appText("Chưa đạt", "Not met")
    Row(
        modifier = Modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) { stateDescription = if (met) metState else unmetState },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (met) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MetColor,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(1.5.dp)
                    .size(15.dp)
                    .border(1.5.dp, muted, CircleShape),
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (met) MetColor else muted,
        )
    }
}
