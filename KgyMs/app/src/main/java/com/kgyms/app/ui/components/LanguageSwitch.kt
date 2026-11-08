package com.kgyms.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.localization.LocalAppLanguage
import com.kgyms.app.ui.theme.PrimaryPurple

/**
 * Switch chọn ngôn ngữ: EN (tắt) ⇄ VI (bật). Lấy ngôn ngữ và hàm đổi từ LocalAppLanguage;
 * không hiển thị nếu màn hình không được cấp hàm đổi ngôn ngữ.
 */
@Composable
fun LanguageSwitch(modifier: Modifier = Modifier) {
    val controller = LocalAppLanguage.current
    val onChange = controller.onLanguageChange ?: return
    val vietnamese = controller.isVietnamese
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "EN",
            fontSize = 13.sp,
            fontWeight = if (!vietnamese) FontWeight.Bold else FontWeight.Normal,
            color = if (!vietnamese) MaterialTheme.colorScheme.onBackground else muted
        )
        Switch(
            checked = vietnamese,
            onCheckedChange = { checked -> onChange(if (checked) "vi" else "en") },
            modifier = Modifier
                .testTag("language_switch")
                .semantics {
                    contentDescription = if (vietnamese) "Ngôn ngữ: Tiếng Việt" else "Language: English"
                },
            colors = SwitchDefaults.colors(
                checkedTrackColor = PrimaryPurple,
                uncheckedTrackColor = PrimaryPurple.copy(alpha = 0.35f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedBorderColor = PrimaryPurple.copy(alpha = 0f),
            )
        )
        Text(
            text = "VI",
            fontSize = 13.sp,
            fontWeight = if (vietnamese) FontWeight.Bold else FontWeight.Normal,
            color = if (vietnamese) MaterialTheme.colorScheme.onBackground else muted
        )
    }
}
