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
    modifier: Modifier = Modifier,
    tag: String? = null
) {
    AdminTextField(
        value = digits,
        onValueChange = onDigitsChange,
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.Number,
        visualTransformation = SeparatorTransformation(separator = '/', positions = setOf(2, 4)),
        tag = tag
    )
}

/** Ô nhập giờ: gõ 4 chữ số, hiển thị HH:mm. */
@Composable
fun AdminTimeField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    tag: String? = null
) {
    AdminTextField(
        value = digits,
        onValueChange = onDigitsChange,
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.Number,
        visualTransformation = SeparatorTransformation(separator = ':', positions = setOf(2)),
        tag = tag
    )
}

/** Chèn [separator] trước các vị trí ký tự [positions] khi hiển thị (giá trị gốc không đổi). */
private class SeparatorTransformation(
    private val separator: Char,
    private val positions: Set<Int>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = buildString {
            raw.forEachIndexed { index, char ->
                if (index in positions) append(separator)
                append(char)
            }
        }
        // Vị trí của từng ký tự phân cách trong chuỗi đã định dạng (chỉ những cái thực sự xuất hiện).
        val separatorIndexes = positions.sorted()
            .filter { it < raw.length }
            .mapIndexed { order, position -> position + order }

        val mapping = object : OffsetMapping {
            // Con trỏ sau ký tự thứ [offset]: cộng thêm số phân cách đứng trước nó.
            override fun originalToTransformed(offset: Int): Int =
                (offset + positions.count { it in 1..offset }).coerceAtMost(formatted.length)

            // Bớt đi số phân cách đứng trước vị trí con trỏ.
            override fun transformedToOriginal(offset: Int): Int =
                (offset - separatorIndexes.count { it < offset }).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
