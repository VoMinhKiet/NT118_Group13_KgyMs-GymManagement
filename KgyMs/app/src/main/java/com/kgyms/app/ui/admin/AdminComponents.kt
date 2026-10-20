package com.kgyms.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * ================================================================
 * ADMIN COLORS
 * ================================================================
 *
 * Hệ thống màu dùng chung cho toàn bộ Admin UI.
 *
 * Dark mode:
 * Background = #030D26
 * Primary   = #8555FF
 *
 * Light mode:
 * Background = #FFF7F9
 * Primary   = #FFB6C1
 *
 * Các screen khác không cần biết đang là Dark hay Light.
 * Chúng chỉ cần sử dụng AdminColors.xxx
 */

object AdminColors {

    /*
     * ============================================================
     * THEME STATE
     * ============================================================
     */

    var isDarkTheme by mutableStateOf(true)
        private set


    /*
     * ============================================================
     * DARK COLORS
     * ============================================================
     */

    private val DarkBackground =
        Color(0xFF030D26)

    private val DarkSurface =
        Color(0xFF111A30)

    private val DarkSurfaceLight =
        Color(0xFF18233B)

    private val DarkPrimary =
        Color(0xFF8555FF)

    private val DarkPrimaryLight =
        Color(0xFFB04CFF)

    private val DarkCyan =
        Color(0xFF61C7FF)

    private val DarkGreen =
        Color(0xFF4ED6A0)

    private val DarkPink =
        Color(0xFFFF5E83)

    private val DarkYellow =
        Color(0xFFFFC857)

    private val DarkTextPrimary =
        Color(0xFFF5F7FC)

    private val DarkTextSecondary =
        Color(0xFF9BA5B8)

    private val DarkTextDark =
        Color(0xFF68748A)

    private val DarkBorder =
        Color(0xFF25314A)


    /*
     * ============================================================
     * LIGHT COLORS
     * ============================================================
     */

    private val LightBackground =
        Color(0xFFFFF7F9)

    private val LightSurface =
        Color(0xFFFFFFFF)

    private val LightSurfaceLight =
        Color(0xFFFFF0F3)

    /*
     * Button chính theo yêu cầu:
     * #FFB6C1
     */
    private val LightPrimary =
        Color(0xFFFFB6C1)

    private val LightPrimaryLight =
        Color(0xFFFF8FA3)

    private val LightCyan =
        Color(0xFF4BAEDB)

    private val LightGreen =
        Color(0xFF32B77E)

    private val LightPink =
        Color(0xFFE85D7A)

    private val LightYellow =
        Color(0xFFE0A52E)

    private val LightTextPrimary =
        Color(0xFF241B20)

    private val LightTextSecondary =
        Color(0xFF75656B)

    private val LightTextDark =
        Color(0xFF9A8A90)

    private val LightBorder =
        Color(0xFFE8D8DD)


    /*
     * ============================================================
     * PUBLIC COLORS
     * ============================================================
     *
     * Toàn bộ screen hiện tại của bạn vẫn sử dụng:
     *
     * AdminColors.Background
     * AdminColors.Surface
     * AdminColors.Primary
     * ...
     *
     * nhưng giá trị sẽ tự thay đổi theo theme.
     */

    val Background: Color
        get() =
            if (isDarkTheme) {
                DarkBackground
            } else {
                LightBackground
            }


    val Surface: Color
        get() =
            if (isDarkTheme) {
                DarkSurface
            } else {
                LightSurface
            }


    val SurfaceLight: Color
        get() =
            if (isDarkTheme) {
                DarkSurfaceLight
            } else {
                LightSurfaceLight
            }


    val Primary: Color
        get() =
            if (isDarkTheme) {
                DarkPrimary
            } else {
                LightPrimary
            }


    val PrimaryLight: Color
        get() =
            if (isDarkTheme) {
                DarkPrimaryLight
            } else {
                LightPrimaryLight
            }


    val Cyan: Color
        get() =
            if (isDarkTheme) {
                DarkCyan
            } else {
                LightCyan
            }


    val Green: Color
        get() =
            if (isDarkTheme) {
                DarkGreen
            } else {
                LightGreen
            }


    val Pink: Color
        get() =
            if (isDarkTheme) {
                DarkPink
            } else {
                LightPink
            }


    val Yellow: Color
        get() =
            if (isDarkTheme) {
                DarkYellow
            } else {
                LightYellow
            }


    val TextPrimary: Color
        get() =
            if (isDarkTheme) {
                DarkTextPrimary
            } else {
                LightTextPrimary
            }


    val TextSecondary: Color
        get() =
            if (isDarkTheme) {
                DarkTextSecondary
            } else {
                LightTextSecondary
            }


    val TextDark: Color
        get() =
            if (isDarkTheme) {
                DarkTextDark
            } else {
                LightTextDark
            }


    val Border: Color
        get() =
            if (isDarkTheme) {
                DarkBorder
            } else {
                LightBorder
            }


    /*
     * ============================================================
     * CHANGE THEME
     * ============================================================
     */

    fun setDarkTheme() {

        isDarkTheme = true
    }


    fun setLightTheme() {

        isDarkTheme = false
    }


    fun toggleTheme() {

        isDarkTheme = !isDarkTheme
    }
}


/*
 * ================================================================
 * METRIC CARD
 * ================================================================
 */

@androidx.compose.runtime.Composable
fun MetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 12.dp,
                vertical = 9.dp
            )
    ) {

        Text(
            text = title,

            color =
                AdminColors.TextSecondary,

            fontSize = 8.sp
        )

        Spacer(
            modifier = Modifier.height(2.dp)
        )

        Text(
            text = value,

            color =
                AdminColors.TextPrimary,

            fontSize = 17.sp,

            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(
                    RoundedCornerShape(3.dp)
                )
                .background(
                    accentColor
                )
        )
    }
}


/*
 * ================================================================
 * SECTION TITLE
 * ================================================================
 */

@androidx.compose.runtime.Composable
fun AdminSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {

    Text(
        text = title,

        modifier = modifier,

        color =
            AdminColors.TextPrimary,

        fontSize = 12.sp,

        fontWeight =
            FontWeight.SemiBold
    )
}


/*
 * ================================================================
 * ACTIVITY ITEM
 * ================================================================
 */

@androidx.compose.runtime.Composable
fun ActivityItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(12.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 8.dp,
                vertical = 8.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(
                    RoundedCornerShape(9.dp)
                )
                .background(
                    AdminColors.SurfaceLight
                )
        )

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = title,

                color =
                    AdminColors.TextPrimary,

                fontSize = 9.sp,

                fontWeight =
                    FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text = subtitle,

                color =
                    AdminColors.TextSecondary,

                fontSize = 7.sp
            )
        }
    }
}


/*
 * ================================================================
 * SIMPLE BAR CHART
 * ================================================================
 */

@androidx.compose.runtime.Composable
fun SimpleBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),

        horizontalArrangement =
            Arrangement.SpaceEvenly,

        verticalAlignment =
            Alignment.Bottom
    ) {

        values.forEach { value ->

            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight(
                        value.coerceIn(
                            0f,
                            1f
                        )
                    )
                    .clip(
                        RoundedCornerShape(
                            topStart = 5.dp,
                            topEnd = 5.dp
                        )
                    )
                    .background(
                        AdminColors.Primary
                    )
            )
        }
    }
}