package com.kgyms.app.ui.admin.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.navigation.AdminRoute
import com.kgyms.app.ui.member.MemberDashboardAppearance
import com.kgyms.app.ui.member.profile.LocalProfilePalette

data class AdminBottomItem(
    val title: String,
    val icon: String,
    val route: AdminRoute
)

/*
 * ================================================================
 * ADMIN BOTTOM BAR
 * ================================================================
 *
 * Cùng kiểu với thanh điều hướng của Member: một thanh bo tròn 28dp
 * có viền, icon vector nét mảnh, mục đang chọn có nền "pill".
 * Route, testTag và menu "Thêm" giữ nguyên như cũ.
 */

@Composable
fun AdminBottomBar(
    currentRoute: AdminRoute,
    onNavigate: (AdminRoute) -> Unit
) {

    var moreExpanded by remember {
        mutableStateOf(false)
    }

    val items = listOf(
        AdminBottomItem(
            title = "Tổng quan",
            icon = "dashboard",
            route = AdminRoute.Dashboard
        ),
        AdminBottomItem(
            title = "Hội viên",
            icon = "members",
            route = AdminRoute.Members
        ),
        AdminBottomItem(
            title = "Nhân sự",
            icon = "staff",
            route = AdminRoute.Staff
        ),
        AdminBottomItem(
            title = "Tài sản",
            icon = "assets",
            route = AdminRoute.Assets
        )
    )

    MemberDashboardAppearance {

        val p = LocalProfilePalette.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.background)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = p.card,
                border = BorderStroke(1.dp, p.border),
                shadowElevation = 2.dp
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    items.forEach { item ->

                        AdminBarItem(
                            title = item.title,
                            icon = adminBarIcon(item.icon),
                            active = currentRoute == item.route,
                            tag = "admin_tab_${item.route.route}",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                moreExpanded = false
                                onNavigate(item.route)
                            }
                        )
                    }

                    /*
                     * ====================================================
                     * MORE
                     * ====================================================
                     */

                    Box(modifier = Modifier.weight(1f)) {

                        AdminBarItem(
                            title = "Thêm",
                            icon = adminBarIcon("more"),
                            active = moreExpanded,
                            tag = "admin_more",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                moreExpanded = !moreExpanded
                            }
                        )

                        AdminMoreMenu(
                            expanded = moreExpanded,
                            onDismiss = { moreExpanded = false },
                            onNavigate = { route ->
                                moreExpanded = false
                                onNavigate(route)
                            }
                        )
                    }
                }
            }
        }
    }
}


/*
 * ================================================================
 * BAR ITEM (giống mục tab của Member)
 * ================================================================
 */

@Composable
private fun AdminBarItem(
    title: String,
    icon: ImageVector,
    active: Boolean,
    tag: String,
    modifier: Modifier,
    onClick: () -> Unit
) {

    val p = LocalProfilePalette.current
    val tint = if (active) p.accent else p.muted

    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(active, role = Role.Tab, onClick = onClick)
            .testTag(tag)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (active) p.tint else Color.Transparent
        ) {

            Box(
                modifier = Modifier.size(width = 42.dp, height = 28.dp),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = tint
                )
            }
        }

        Text(
            text = title,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}


/*
 * ================================================================
 * MORE MENU
 * ================================================================
 */

private data class AdminMoreEntry(
    val icon: String,
    val color: () -> Color,
    val title: String,
    val subtitle: String,
    val route: AdminRoute
)

private val moreEntries = listOf(
    AdminMoreEntry("▣", { AdminColors.Primary }, "Quản lý gói tập", "Tạo và quản lý các gói tập", AdminRoute.Packages),
    AdminMoreEntry("▤", { AdminColors.PrimaryLight }, "Quản lý hóa đơn", "Tạo hóa đơn và giao dịch", AdminRoute.Invoice),
    AdminMoreEntry("!", { AdminColors.Pink }, "Quản lý khiếu nại", "Theo dõi và xử lý khiếu nại", AdminRoute.Complaints),
    AdminMoreEntry("🔔", { AdminColors.Green }, "Quản lý thông báo", "Gửi ưu đãi, nhắc hết hạn gói", AdminRoute.Notifications),
    AdminMoreEntry("◷", { AdminColors.Cyan }, "Nhật ký hoạt động", "Theo dõi hoạt động hệ thống", AdminRoute.ActivityLogs),
)

@Composable
private fun AdminMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (AdminRoute) -> Unit
) {

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = AdminColors.Surface,
        border = BorderStroke(1.dp, AdminColors.Border),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {

        DropdownMenuItem(
            text = {
                Text(
                    text = "QUẢN LÝ",
                    color = AdminColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            onClick = {},
            enabled = false
        )

        moreEntries.forEach { entry ->

            DropdownMenuItem(
                leadingIcon = {
                    MenuIcon(icon = entry.icon, color = entry.color())
                },
                text = {
                    MenuText(title = entry.title, subtitle = entry.subtitle)
                },
                trailingIcon = {
                    MenuArrow()
                },
                onClick = {
                    onNavigate(entry.route)
                }
            )
        }
    }
}

@Composable
private fun MenuIcon(
    icon: String,
    color: Color
) {

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = icon,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MenuText(
    title: String,
    subtitle: String
) {

    Column {

        Text(
            text = title,
            color = AdminColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            color = AdminColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MenuArrow() {

    Text(
        text = "›",
        color = AdminColors.TextSecondary,
        fontSize = 24.sp
    )
}


/*
 * ================================================================
 * ICONS
 * ================================================================
 *
 * Vẽ bằng vector nét 1.8 giống icon tab của Member,
 * không cần thêm thư viện icon hay tài nguyên ảnh.
 */

private fun adminBarIcon(name: String): ImageVector =
    ImageVector.Builder(
        name = "admin_$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.8f) {
            when (name) {
                "dashboard" -> {
                    // Ngôi nhà — giống tab Trang chủ của Member.
                    moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                    moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f)
                    lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 21f)
                    lineTo(19f, 21f); lineTo(19f, 9f)
                }
                "members" -> {
                    // Một người — giống tab Hồ sơ của Member.
                    moveTo(8f, 7f); curveTo(8f, 1f, 16f, 1f, 16f, 7f)
                    curveTo(16f, 13f, 8f, 13f, 8f, 7f); close()
                    moveTo(4f, 22f); lineTo(4f, 19f)
                    curveTo(4f, 12f, 20f, 12f, 20f, 19f); lineTo(20f, 22f)
                }
                "staff" -> {
                    // Thẻ nhân viên.
                    moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 20f)
                    lineTo(4f, 20f); close()
                    moveTo(9f, 3f); lineTo(15f, 3f)
                    moveTo(9f, 11f); curveTo(9f, 8f, 13f, 8f, 13f, 11f)
                    curveTo(13f, 14f, 9f, 14f, 9f, 11f); close()
                    moveTo(7f, 17.5f); curveTo(8f, 14.5f, 14f, 14.5f, 15f, 17.5f)
                    moveTo(16f, 10f); lineTo(18f, 10f)
                    moveTo(16f, 13f); lineTo(18f, 13f)
                }
                "assets" -> {
                    // Hộp tài sản.
                    moveTo(3f, 8f); lineTo(12f, 3f); lineTo(21f, 8f)
                    lineTo(21f, 17f); lineTo(12f, 22f); lineTo(3f, 17f); close()
                    moveTo(3f, 8f); lineTo(12f, 13f); lineTo(21f, 8f)
                    moveTo(12f, 13f); lineTo(12f, 22f)
                }
                else -> {
                    // Ba chấm "Thêm".
                    moveTo(4f, 12f); lineTo(6f, 12f)
                    moveTo(11f, 12f); lineTo(13f, 12f)
                    moveTo(18f, 12f); lineTo(20f, 12f)
                }
            }
        }
    }.build()
