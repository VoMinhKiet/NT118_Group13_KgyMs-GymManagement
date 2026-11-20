package com.kgyms.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HorizontalRuler(
    minValue: Int,
    maxValue: Int,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    lineColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val currentCallback by rememberUpdatedState(onValueChange)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val distanceBetweenLines = with(density) { 24.dp.toPx() } // Tăng nhẹ khoảng cách để mượt và dễ nhìn hơn
    
    var rawValue by remember { mutableFloatStateOf(currentValue.coerceIn(minValue, maxValue).toFloat()) }
    val animatableValue = remember { Animatable(currentValue.toFloat()) }
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    // Đồng bộ giá trị khi bên ngoài thay đổi và không trong trạng thái kéo
    LaunchedEffect(currentValue) {
        if (!isDragging) {
            rawValue = currentValue.coerceIn(minValue, maxValue).toFloat()
            animatableValue.snapTo(rawValue)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(currentValue.toFloat(), minValue.toFloat()..maxValue.toFloat(), maxValue - minValue - 1)
                setProgress { requested ->
                    currentCallback(requested.roundToInt().coerceIn(minValue, maxValue))
                    true
                }
            }
            .pointerInput(minValue, maxValue, distanceBetweenLines) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        coroutineScope.launch { animatableValue.stop() }
                    },
                    onDragCancel = { isDragging = false },
                    onDragEnd = {
                        isDragging = false
                        coroutineScope.launch {
                            val snappedValue = rawValue.roundToInt().toFloat().coerceIn(minValue.toFloat(), maxValue.toFloat())
                            animatableValue.animateTo(snappedValue)
                            rawValue = snappedValue
                            currentCallback(snappedValue.roundToInt())
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    // Cập nhật giá trị đồng bộ trực tiếp không qua coroutine để triệt tiêu độ trễ (lag)
                    val valueChange = -dragAmount / distanceBetweenLines
                    rawValue = (rawValue + valueChange).coerceIn(minValue.toFloat(), maxValue.toFloat())
                    currentCallback(rawValue.roundToInt())
                }
            }
    ) {
        val center = size.width / 2
        val rulerTop = size.height / 2 - 20.dp.toPx()
        val rulerBottom = size.height / 2 + 20.dp.toPx()
        
        // Vẽ kim chỉ ở giữa
        drawLine(
            color = indicatorColor,
            start = Offset(center, rulerTop - 10.dp.toPx()),
            end = Offset(center, rulerBottom + 10.dp.toPx()),
            strokeWidth = 4.dp.toPx()
        )
        drawCircle(
            color = indicatorColor,
            radius = 6.dp.toPx(),
            center = Offset(center, rulerTop - 10.dp.toPx())
        )
        
        val currentValueFloat = if (isDragging) rawValue else animatableValue.value
        
        for (i in minValue..maxValue) {
            val offsetFromCurrent = (i - currentValueFloat) * distanceBetweenLines
            val xPos = center + offsetFromCurrent
            
            if (xPos in -50f..(size.width + 50f)) {
                val isMajor = i % 5 == 0
                val lineTop = if (isMajor) rulerTop else rulerTop + 10.dp.toPx()
                
                drawLine(
                    color = lineColor,
                    start = Offset(xPos, lineTop),
                    end = Offset(xPos, rulerBottom),
                    strokeWidth = 2.dp.toPx()
                )
                
                if (isMajor) {
                    val textLayoutResult = textMeasurer.measure(
                        text = i.toString(),
                        style = TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    )
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x = xPos - textLayoutResult.size.width / 2,
                            y = rulerBottom + 8.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}
