package com.kgyms.app.ui.admin

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBars
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

    /*
     * ============================================================
     * MENU STATE
     * ============================================================
     */

    var menuExpanded by remember {
        mutableStateOf(false)
    }


    /*
     * ============================================================
     * TOP BAR
     * ============================================================
     */

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                AdminColors.Background
            )
            .windowInsetsPadding(
                WindowInsets.statusBars
            )
            .padding(
                horizontal = 16.dp,
                vertical = 4.dp
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            /*
             * ====================================================
             * LEFT SIDE
             * ====================================================
             */

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                if (showBackButton) {

                    Text(
                        text = "‹",

                        color =
                            AdminColors.TextPrimary,

                        fontSize = 30.sp,

                        fontWeight =
                            FontWeight.Light,

                        modifier = Modifier
                            .clickable {
                                onBack?.invoke()
                            }
                            .padding(
                                end = 8.dp
                            )
                    )
                }


                /*
                 * TITLE
                 */

                Column {

                    Text(
                        text = title,

                        color =
                            AdminColors.TextPrimary,

                        fontSize = 18.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    if (subtitle != null) {

                        Spacer(
                            modifier =
                                Modifier.height(2.dp)
                        )

                        Text(
                            text = subtitle,

                            color =
                                AdminColors.TextSecondary,

                            fontSize = 9.sp
                        )
                    }
                }
            }


            /*
             * ====================================================
             * LOGO
             * ====================================================
             */

            Box {

                Image(
                    painter =
                        painterResource(
                            id = R.drawable.kgyms_logo
                        ),

                    contentDescription =
                        "KgyMs Admin",

                    modifier = Modifier
                        .size(42.dp)
                        .clickable {
                            menuExpanded = true
                        }
                )


                /*
                 * =================================================
                 * PROFILE MENU
                 * =================================================
                 */

                DropdownMenu(

                    expanded =
                        menuExpanded,

                    onDismissRequest = {
                        menuExpanded = false
                    },

                    shape =
                        RoundedCornerShape(18.dp),

                    containerColor =
                        AdminColors.Surface,

                    tonalElevation = 8.dp,

                    shadowElevation = 14.dp
                ) {

                    /*
                     * HEADER
                     */

                    DropdownMenuItem(

                        text = {

                            Column {

                                Text(
                                    text =
                                        "TÀI KHOẢN",

                                    color =
                                        AdminColors.TextSecondary,

                                    fontSize = 9.sp,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )
                            }
                        },

                        onClick = {},

                        enabled = false
                    )


                    /*
                     * =================================================
                     * PROFILE
                     * =================================================
                     */

                    DropdownMenuItem(

                        leadingIcon = {

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        AdminColors.Primary
                                            .copy(
                                                alpha = 0.15f
                                            ),
                                        RoundedCornerShape(
                                            9.dp
                                        )
                                    ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text = "♙",

                                    color =
                                        AdminColors.Primary,

                                    fontSize = 16.sp
                                )
                            }
                        },

                        text = {

                            Column {

                                Text(
                                    text =
                                        "Thông tin cá nhân",

                                    color =
                                        AdminColors.TextPrimary,

                                    fontSize = 11.sp,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(2.dp)
                                )

                                Text(
                                    text =
                                        "Quản lý tài khoản Admin",

                                    color =
                                        AdminColors.TextSecondary,

                                    fontSize = 8.sp
                                )
                            }
                        },

                        onClick = {

                            menuExpanded =
                                false

                            onProfileClick()
                        }
                    )


                    /*
                     * =================================================
                     * THEME
                     * =================================================
                     */

                    DropdownMenuItem(

                        leadingIcon = {

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        AdminColors.Primary
                                            .copy(
                                                alpha = 0.15f
                                            ),
                                        RoundedCornerShape(
                                            9.dp
                                        )
                                    ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text =
                                        if (
                                            AdminColors.isDarkTheme
                                        ) {
                                            "☾"
                                        } else {
                                            "☀"
                                        },

                                    color =
                                        AdminColors.Primary,

                                    fontSize = 16.sp
                                )
                            }
                        },

                        text = {

                            Column {

                                Text(
                                    text =
                                        "Giao diện",

                                    color =
                                        AdminColors.TextPrimary,

                                    fontSize = 11.sp,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(2.dp)
                                )

                                Text(
                                    text =
                                        if (
                                            AdminColors.isDarkTheme
                                        ) {
                                            "Đang sử dụng giao diện tối"
                                        } else {
                                            "Đang sử dụng giao diện sáng"
                                        },

                                    color =
                                        AdminColors.TextSecondary,

                                    fontSize = 8.sp
                                )
                            }
                        },

                        trailingIcon = {

                            Switch(

                                checked =
                                    !AdminColors.isDarkTheme,

                                onCheckedChange = {

                                    AdminColors.toggleTheme()
                                },

                                colors =
                                    SwitchDefaults.colors(

                                        checkedThumbColor =
                                            AdminColors.TextPrimary,

                                        checkedTrackColor =
                                            AdminColors.Primary,

                                        uncheckedThumbColor =
                                            AdminColors.TextPrimary,

                                        uncheckedTrackColor =
                                            AdminColors.SurfaceLight,

                                        uncheckedBorderColor =
                                            AdminColors.Border
                                    )
                            )
                        },

                        onClick = {

                            AdminColors.toggleTheme()
                        }
                    )


                    /*
                     * =================================================
                     * LOGOUT
                     * =================================================
                     */

                    DropdownMenuItem(

                        leadingIcon = {

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        AdminColors.Pink
                                            .copy(
                                                alpha = 0.14f
                                            ),
                                        RoundedCornerShape(
                                            9.dp
                                        )
                                    ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text = "↪",

                                    color =
                                        AdminColors.Pink,

                                    fontSize = 16.sp,

                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        },

                        text = {

                            Column {

                                Text(
                                    text =
                                        "Đăng xuất",

                                    color =
                                        AdminColors.TextPrimary,

                                    fontSize = 11.sp,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(2.dp)
                                )

                                Text(
                                    text =
                                        "Đăng xuất khỏi tài khoản",

                                    color =
                                        AdminColors.TextSecondary,

                                    fontSize = 8.sp
                                )
                            }
                        },

                        onClick = {

                            menuExpanded =
                                false

                            onLogoutClick()
                        }
                    )
                }
            }
        }
    }
}