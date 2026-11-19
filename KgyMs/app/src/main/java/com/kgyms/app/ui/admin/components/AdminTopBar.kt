package com.kgyms.app.ui.admin.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.R

@Composable
fun AdminTopBar(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = false,
    onBack: (() -> Unit)? = null,
    onProfileClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AdminColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = { onBack?.invoke() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("admin_back")
                    ) {
                        Icon(
                            imageVector = AdminIcons.ChevronLeft,
                            contentDescription = "Quay lại",
                            tint = AdminColors.TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                }

                Column {
                    Text(
                        text = title,
                        color = AdminColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = subtitle, color = AdminColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }

            Box {
                Image(
                    painter = painterResource(id = R.drawable.profile_kgyms_logo),
                    contentDescription = "Menu tài khoản Admin",
                    modifier = Modifier
                        .size(width = 44.dp, height = 32.dp)
                        .testTag("admin_account_menu")
                        .clickable { menuExpanded = true }
                )

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = AdminColors.Surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 14.dp
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "TÀI KHOẢN",
                                color = AdminColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        onClick = {},
                        enabled = false
                    )

                    AccountMenuItem(
                        icon = AdminIcons.Person,
                        tint = AdminColors.Primary,
                        title = "Thông tin cá nhân",
                        subtitle = "Quản lý tài khoản Admin",
                        onClick = {
                            menuExpanded = false
                            onProfileClick()
                        }
                    )

                    AccountMenuItem(
                        icon = if (AdminColors.isDarkTheme) AdminIcons.DarkMode else AdminIcons.LightMode,
                        tint = AdminColors.Primary,
                        title = "Giao diện",
                        subtitle = if (AdminColors.isDarkTheme) "Đang dùng giao diện tối" else "Đang dùng giao diện sáng",
                        onClick = { AdminColors.toggleTheme() },
                        trailing = {
                            Switch(
                                checked = !AdminColors.isDarkTheme,
                                onCheckedChange = { AdminColors.toggleTheme() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AdminColors.TextPrimary,
                                    checkedTrackColor = AdminColors.Primary
                                )
                            )
                        }
                    )

                    AccountMenuItem(
                        icon = AdminIcons.Logout,
                        tint = AdminColors.Pink,
                        title = "Đăng xuất",
                        subtitle = "Đăng xuất khỏi tài khoản",
                        onClick = {
                            menuExpanded = false
                            onLogoutClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountMenuItem(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        },
        text = {
            Column {
                Text(text = title, color = AdminColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = AdminColors.TextSecondary, fontSize = 12.sp)
            }
        },
        trailingIcon = trailing,
        onClick = onClick
    )
}
