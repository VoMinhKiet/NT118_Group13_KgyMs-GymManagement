package com.kgyms.app.ui.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.kgyms.app.R
import com.kgyms.app.ui.components.KgyMsTextField
import com.kgyms.app.ui.localization.appText

private val ValidColor = Color(0xFF2E9E5B)

/**
 * Ô nhập mật khẩu của các màn xác thực: có nút ẩn/hiện mật khẩu và (tùy chọn) dấu tích xanh
 * khi giá trị đã hợp lệ — ví dụ đạt đủ yêu cầu, hoặc khớp với mật khẩu đã nhập ở ô trên.
 */
@Composable
fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    enabled: Boolean = true,
    showValidMark: Boolean = false,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val showLabel = appText("Hiện mật khẩu", "Show password")
    val hideLabel = appText("Ẩn mật khẩu", "Hide password")
    val validLabel = appText("Hợp lệ", "Valid")

    KgyMsTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = Modifier.testTag(tag),
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showValidMark) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = validLabel,
                        tint = ValidColor,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(22.dp)
                            .testTag("${tag}_valid")
                    )
                }
                IconButton(
                    onClick = { visible = !visible },
                    modifier = Modifier.testTag("${tag}_visibility")
                ) {
                    Icon(
                        painter = painterResource(
                            if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                        ),
                        contentDescription = if (visible) hideLabel else showLabel,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    )
}
