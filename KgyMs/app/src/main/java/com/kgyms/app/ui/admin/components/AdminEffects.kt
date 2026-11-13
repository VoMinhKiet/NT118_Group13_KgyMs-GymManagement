package com.kgyms.app.ui.admin.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Hiện Toast cho thông báo một lần của ViewModel rồi báo lại là đã hiện. */
@Composable
fun AdminMessageEffect(message: String?, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onShown()
        }
    }
}

/**
 * Ô nhập ngày: người dùng chỉ gõ số (bàn phím số không có "/"),
 * ô tự hiển thị dạng dd/MM/yyyy. Giá trị lưu là chữ số.
 */
@Composable
fun AdminDateField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    AdminTextField(
        value = digits,
        onValueChange = onDigitsChange,
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.Number,
        visualTransformation = DateVisualTransformation
    )
}

private object DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = buildString {
            raw.forEachIndexed { index, char ->
                if (index == 2 || index == 4) append('/')
                append(char)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 1 -> offset
                offset <= 3 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(formatted.length)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
