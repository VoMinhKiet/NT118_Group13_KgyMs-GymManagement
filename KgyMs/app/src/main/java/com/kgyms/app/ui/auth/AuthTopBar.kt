package com.kgyms.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.ui.components.LanguageSwitch
import com.kgyms.app.ui.localization.appText

/**
 * Thanh trên cùng của màn hình xác thực: nút quay lại (trái) và switch ngôn ngữ (phải)
 * nằm cùng một hàng, căn giữa theo chiều dọc.
 */
@Composable
fun AuthTopBar(
    onBack: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .testTag("auth_back")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = appText("Quay lại", "Back"),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp)
            )
        }

        // Chọn ngôn ngữ EN/VI; lựa chọn được lưu và dùng cho toàn app.
        LanguageSwitch()
    }
}
