package com.kgyms.app.ui.admin.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon vector nét mảnh (1.8) theo phong cách Material Outlined, dùng chung cho Admin.
 * Tự vẽ để không phải thêm thư viện icon; màu do Icon(tint = ...) quyết định.
 */
object AdminIcons {

    val ChevronRight: ImageVector by lazy {
        stroke("chevron_right") { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
    }

    val ChevronLeft: ImageVector by lazy {
        stroke("chevron_left") { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) }
    }

    val Person: ImageVector by lazy {
        stroke("person") {
            moveTo(8f, 8f); curveTo(8f, 2.7f, 16f, 2.7f, 16f, 8f)
            curveTo(16f, 13.3f, 8f, 13.3f, 8f, 8f); close()
            moveTo(4f, 21f); curveTo(4f, 14.5f, 20f, 14.5f, 20f, 21f)
        }
    }

    val DarkMode: ImageVector by lazy {
        stroke("dark_mode") {
            moveTo(20f, 14.5f)
            curveTo(16f, 16f, 10.5f, 13.5f, 9.5f, 8.5f)
            curveTo(9f, 6.5f, 9.3f, 4.8f, 10f, 3.5f)
            curveTo(5.5f, 4.5f, 2.8f, 9f, 3.8f, 13.5f)
            curveTo(4.8f, 18f, 9.5f, 21f, 14f, 20f)
            curveTo(16.6f, 19.4f, 18.8f, 17.3f, 20f, 14.5f)
            close()
        }
    }

    val LightMode: ImageVector by lazy {
        stroke("light_mode") {
            moveTo(8f, 12f); curveTo(8f, 6.7f, 16f, 6.7f, 16f, 12f)
            curveTo(16f, 17.3f, 8f, 17.3f, 8f, 12f); close()
            moveTo(12f, 2f); lineTo(12f, 4f)
            moveTo(12f, 20f); lineTo(12f, 22f)
            moveTo(2f, 12f); lineTo(4f, 12f)
            moveTo(20f, 12f); lineTo(22f, 12f)
            moveTo(4.9f, 4.9f); lineTo(6.3f, 6.3f)
            moveTo(17.7f, 17.7f); lineTo(19.1f, 19.1f)
            moveTo(4.9f, 19.1f); lineTo(6.3f, 17.7f)
            moveTo(17.7f, 6.3f); lineTo(19.1f, 4.9f)
        }
    }

    val Logout: ImageVector by lazy {
        stroke("logout") {
            moveTo(10f, 4f); lineTo(5f, 4f); lineTo(5f, 20f); lineTo(10f, 20f)
            moveTo(10f, 12f); lineTo(20f, 12f)
            moveTo(16f, 8f); lineTo(20f, 12f); lineTo(16f, 16f)
        }
    }

    private fun stroke(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "admin_$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }.build()
}
