package com.kgyms.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
fun VerticalRuler(
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
    val externalValue by rememberUpdatedState(currentValue)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val distanceBetweenLines = with(density) { 20.dp.toPx() } // Thu hẹp khoảng cách vạch để kéo êm hơn
    
    val animatableValue = remember { Animatable(currentValue.coerceIn(minValue, maxValue).toFloat()) }
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentValue, minValue, maxValue) {
        if (!isDragging && currentValue.toFloat() != animatableValue.targetValue) {
            animatableValue.snapTo(currentValue.toFloat())
        }
    }
    
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(120.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(currentValue.toFloat(), minValue.toFloat()..maxValue.toFloat(), maxValue - minValue - 1)
                setProgress { requested ->
                    currentCallback(requested.roundToInt().coerceIn(minValue, maxValue))
                    true
                }
            }
            .pointerInput(minValue, maxValue, distanceBetweenLines) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        coroutineScope.launch { animatableValue.stop() }
                    },
                    onDragCancel = { isDragging = false },
                    onDragEnd = {
                        isDragging = false
                        coroutineScope.launch {
                            val snappedValue = animatableValue.value.roundToInt().toFloat()
                            animatableValue.animateTo(snappedValue)
                            currentCallback(snappedValue.roundToInt().coerceIn(minValue, maxValue))
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    // dragAmount > 0 là vuốt xuống -> Giá trị chiều cao tăng (vì số lớn ở trên)
                    val valueChange = dragAmount / distanceBetweenLines
                    val newValue = (animatableValue.value + valueChange).coerceIn(minValue.toFloat(), maxValue.toFloat())
                    
                    coroutineScope.launch {
                        animatableValue.snapTo(newValue)
                    }
                    
                    val newInt = newValue.roundToInt()
                    if (newInt != externalValue) {
                        currentCallback(newInt)
                    }
                }
            }
    ) {
        val center = size.height / 2
        val rulerLeft = size.width / 2 - 20.dp.toPx()
        val rulerRight = size.width / 2 + 20.dp.toPx()
        
        // Vẽ kim chỉ ở giữa (Indicator)
        drawLine(
            color = indicatorColor,
            start = Offset(rulerLeft - 10.dp.toPx(), center),
            end = Offset(rulerRight + 10.dp.toPx(), center),
            strokeWidth = 4.dp.toPx()
        )
        
        val currentValueFloat = animatableValue.value
        
        for (i in minValue..maxValue) {
            // Số lớn nằm ở trên, nên vị trí y = tâm + (hiện tại - i) * khoảng cách
            val offsetFromCurrent = (currentValueFloat - i) * distanceBetweenLines
            val yPos = center + offsetFromCurrent
            
            // Tối ưu: Chỉ vẽ những vạch đang nằm trong màn hình
            if (yPos in -50f..(size.height + 50f)) {
                val isMajor = i % 5 == 0
                val lineRight = if (isMajor) rulerRight else rulerRight - 15.dp.toPx()
                
                drawLine(
                    color = lineColor,
                    start = Offset(rulerLeft, yPos),
                    end = Offset(lineRight, yPos),
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
                            x = rulerLeft - textLayoutResult.size.width - 8.dp.toPx(),
                            y = yPos - textLayoutResult.size.height / 2
                        )
                    )
                }
            }
        }
    }
}
