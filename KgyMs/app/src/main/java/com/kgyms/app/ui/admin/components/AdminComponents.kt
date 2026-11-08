package com.kgyms.app.ui.admin.components

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kgyms.app.ui.member.profile.DarkProfilePalette
import com.kgyms.app.ui.member.profile.LightProfilePalette
import com.kgyms.app.ui.member.profile.ProfileAppearance

/*
 * ================================================================
 * ADMIN COLORS
 * ================================================================
 *
 * Hệ thống màu dùng chung cho toàn bộ Admin UI.
 *
 * Để Admin đồng bộ với giao diện Member, các màu nền, thẻ, viền,
 * chữ và màu nhấn được lấy trực tiếp từ bảng màu của Member
 * (DarkProfilePalette / LightProfilePalette). Khi Member đổi màu,
 * Admin tự đổi theo.
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

    private val palette
        get() = if (isDarkTheme) DarkProfilePalette else LightProfilePalette

    private fun pick(dark: Color, light: Color): Color =
        if (isDarkTheme) dark else light


    /*
     * ============================================================
     * PUBLIC COLORS
     * ============================================================
     */

    /** Nền màn hình — giống nền Member. */
    val Background: Color get() = palette.background

    /** Nền thẻ (card) — giống card Member. */
    val Surface: Color get() = palette.card

    /** Nền phụ: ô icon, chip đang chọn — giống tint của Member. */
    val SurfaceLight: Color get() = palette.tint

    /** Màu nhấn chính — giống accent của Member. */
    val Primary: Color get() = palette.accent

    /** Màu chữ/icon đặt trên nền Primary. */
    val OnPrimary: Color get() = Color.White

    /** Biến thể sáng của Primary, dùng cho chữ trên nền Primary nhạt. */
    val PrimaryLight: Color get() = pick(Color(0xFFD0A2FF), Color(0xFFA0235C))

    val Cyan: Color get() = pick(Color(0xFF93CDD1), Color(0xFF286B73))

    val Green: Color get() = pick(Color(0xFF8FD6A6), Color(0xFF3F7A3A))

    /** Màu cảnh báo/nguy hiểm — giống danger của Member. */
    val Pink: Color get() = palette.danger

    val Yellow: Color get() = pick(Color(0xFFF2C66D), Color(0xFF8A5A00))

    val TextPrimary: Color get() = palette.text

    val TextSecondary: Color get() = palette.muted

    val TextDark: Color get() = pick(Color(0xFF7C879A), Color(0xFF9C8893))

    val Border: Color get() = palette.border


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
 * ADMIN APPEARANCE
 * ================================================================
 *
 * Bọc toàn bộ Admin bằng cùng MaterialTheme mà Member dùng, để
 * AlertDialog, DropdownMenu, Switch, TextField... có cùng màu sắc.
 * Đồng thời đổi màu icon status bar / navigation bar theo theme
 * giống cách Member đang làm.
 */

@Composable
fun AdminAppearance(content: @Composable () -> Unit) {
    val dark = AdminColors.isDarkTheme
    val window = (LocalContext.current as? Activity)?.window
    val view = LocalView.current

    DisposableEffect(window, view, dark) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            if (previousStatus != null) controller?.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    ProfileAppearance(dark = dark, language = "vi", content = content)
}


/*
 * ================================================================
 * SHAPES (giống Member)
 * ================================================================
 */

/** Thẻ lớn: giống ProfileCard của Member. */
val AdminCardShape = RoundedCornerShape(20.dp)

/** Dòng danh sách / nút: giống ProfileSettingRow, ProfilePrimaryButton. */
val AdminRowShape = RoundedCornerShape(18.dp)


/*
 * ================================================================
 * METRIC CARD
 * ================================================================
 */

@Composable
fun MetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier,
        shape = AdminCardShape,
        color = AdminColors.Surface,
        contentColor = AdminColors.TextPrimary,
        border = BorderStroke(1.dp, AdminColors.Border)
    ) {

        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 14.dp
            )
        ) {

            Text(
                text = title,
                color = AdminColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                color = AdminColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )
        }
    }
}


/*
 * ================================================================
 * SECTION TITLE
 * ================================================================
 */

@Composable
fun AdminSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {

    Text(
        text = title,
        modifier = modifier,
        color = AdminColors.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
}


/*
 * ================================================================
 * ACTIVITY ITEM
 * ================================================================
 */

@Composable
fun ActivityItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AdminRowShape,
        color = AdminColors.Surface,
        contentColor = AdminColors.TextPrimary,
        border = BorderStroke(1.dp, AdminColors.Border)
    ) {

        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AdminColors.SurfaceLight)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = title,
                    color = AdminColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    color = AdminColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}


/*
 * ================================================================
 * CHOICE CHIP (giống ProfileChoice của Member)
 * ================================================================
 */

@Composable
fun AdminChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) AdminColors.SurfaceLight else AdminColors.Surface,
        contentColor = if (selected) AdminColors.Primary else AdminColors.TextSecondary,
        border = BorderStroke(
            1.dp,
            if (selected) AdminColors.Primary else AdminColors.Border
        )
    ) {

        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}


/*
 * ================================================================
 * SIMPLE BAR CHART
 * ================================================================
 */

@Composable
fun SimpleBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {

        values.forEach { value ->

            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(AdminColors.Primary)
            )
        }
    }
}
