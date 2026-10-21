package com.kgyms.app.ui.member

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** One bar with a real circular cutout; the raised QR lives within parent hit bounds. */
internal object MemberBarShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { 46.dp.toPx() }
        val centerY = with(density) { 12.dp.toPx() }
        val corner = with(density) { 24.dp.toPx() }
        val bar = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(corner))) }
        val hole = Path().apply { addOval(Rect(size.width / 2 - radius, centerY - radius, size.width / 2 + radius, centerY + radius)) }
        return Outline.Generic(Path.combine(PathOperation.Difference, bar, hole))
    }
}
