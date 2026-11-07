package com.kgyms.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AdminBottomItem(
    val title: String,
    val icon: String,
    val route: AdminRoute
)

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
            icon = "⌂",
            route = AdminRoute.Dashboard
        ),

        AdminBottomItem(
            title = "Hội viên",
            icon = "♙",
            route = AdminRoute.Members
        ),

        AdminBottomItem(
            title = "Nhân sự",
            icon = "★",
            route = AdminRoute.Staff
        ),

        AdminBottomItem(
            title = "Tài sản",
            icon = "▣",
            route = AdminRoute.Assets
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(76.dp)
            .background(AdminColors.Surface)
            .padding(
                horizontal = 4.dp,
                vertical = 5.dp
            ),

        horizontalArrangement =
            Arrangement.SpaceEvenly,

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        /*
         * ========================================================
         * MAIN ITEMS
         * ========================================================
         */

        items.forEach { item ->

            val selected =
                currentRoute == item.route

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("admin_tab_${item.route.route}")
                    .clickable {

                        moreExpanded = false

                        onNavigate(item.route)
                    }
                    .padding(vertical = 5.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center
            ) {

                Text(
                    text = item.icon,

                    color =
                        if (selected) {
                            AdminColors.Primary
                        } else {
                            AdminColors.TextSecondary
                        },

                    fontSize = 22.sp,

                    fontWeight =
                        FontWeight.Bold,

                    textAlign =
                        TextAlign.Center
                )

                Text(
                    text = item.title,

                    color =
                        if (selected) {
                            AdminColors.TextPrimary
                        } else {
                            AdminColors.TextSecondary
                        },

                    fontSize = 9.sp,

                    fontWeight =
                        if (selected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },

                    textAlign =
                        TextAlign.Center,

                    maxLines = 1
                )
            }
        }


        /*
         * ========================================================
         * MORE
         * ========================================================
         */

        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (moreExpanded) {
                            AdminColors.SurfaceLight
                        } else {
                            AdminColors.Surface
                        }
                    )
                    .testTag("admin_more")
                    .clickable {

                        moreExpanded =
                            !moreExpanded
                    }
                    .padding(vertical = 5.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center
            ) {

                Text(
                    text = "•••",

                    color =
                        if (moreExpanded) {
                            AdminColors.Primary
                        } else {
                            AdminColors.TextSecondary
                        },

                    fontSize = 19.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text = "Thêm",

                    color =
                        if (moreExpanded) {
                            AdminColors.TextPrimary
                        } else {
                            AdminColors.TextSecondary
                        },

                    fontSize = 9.sp,

                    fontWeight =
                        if (moreExpanded) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                )
            }


            /*
             * ====================================================
             * MORE MENU
             * ====================================================
             */

            DropdownMenu(
                expanded = moreExpanded,

                onDismissRequest = {
                    moreExpanded = false
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

                        Text(
                            text = "QUẢN LÝ",

                            color =
                                AdminColors.TextSecondary,

                            fontSize = 9.sp,

                            fontWeight =
                                FontWeight.SemiBold
                        )
                    },

                    onClick = {},

                    enabled = false
                )


                /*
                 * =================================================
                 * QUẢN LÝ GÓI TẬP
                 * =================================================
                 */

                DropdownMenuItem(

                    leadingIcon = {

                        MenuIcon(
                            icon = "▣",
                            color =
                                AdminColors.Primary
                        )
                    },

                    text = {

                        MenuText(
                            title =
                                "Quản lý gói tập",

                            subtitle =
                                "Tạo và quản lý các gói tập"
                        )
                    },

                    trailingIcon = {

                        MenuArrow()
                    },

                    onClick = {

                        moreExpanded = false

                        onNavigate(
                            AdminRoute.Packages
                        )
                    }
                )


                /*
                 * =================================================
                 * QUẢN LÝ HÓA ĐƠN
                 * =================================================
                 */

                DropdownMenuItem(

                    leadingIcon = {

                        MenuIcon(
                            icon = "▤",
                            color =
                                AdminColors.PrimaryLight
                        )
                    },

                    text = {

                        MenuText(
                            title =
                                "Quản lý hóa đơn",

                            subtitle =
                                "Tạo hóa đơn và giao dịch"
                        )
                    },

                    trailingIcon = {

                        MenuArrow()
                    },

                    onClick = {

                        moreExpanded = false

                        onNavigate(
                            AdminRoute.Invoice
                        )
                    }
                )


                /*
                 * =================================================
                 * QUẢN LÝ KHIẾU NẠI
                 * =================================================
                 */

                DropdownMenuItem(

                    leadingIcon = {

                        MenuIcon(
                            icon = "!",
                            color =
                                AdminColors.Pink
                        )
                    },

                    text = {

                        MenuText(
                            title =
                                "Quản lý khiếu nại",

                            subtitle =
                                "Theo dõi và xử lý khiếu nại"
                        )
                    },

                    trailingIcon = {

                        MenuArrow()
                    },

                    onClick = {

                        moreExpanded = false

                        onNavigate(
                            AdminRoute.Complaints
                        )
                    }
                )


                /*
                 * =================================================
                 * HOTLINE CSKH
                 * =================================================
                 */

                DropdownMenuItem(

                    leadingIcon = {

                        MenuIcon(
                            icon = "☎",
                            color =
                                AdminColors.Green
                        )
                    },

                    text = {

                        MenuText(
                            title =
                                "Hotline CSKH",

                            subtitle =
                                "Thông tin và liên hệ hỗ trợ"
                        )
                    },

                    trailingIcon = {

                        MenuArrow()
                    },

                    onClick = {

                        moreExpanded = false

                        onNavigate(
                            AdminRoute.Hotline
                        )
                    }
                )


                /*
                 * =================================================
                 * NHẬT KÝ HOẠT ĐỘNG
                 * =================================================
                 */

                DropdownMenuItem(

                    leadingIcon = {

                        MenuIcon(
                            icon = "◷",
                            color =
                                AdminColors.Cyan
                        )
                    },

                    text = {

                        MenuText(
                            title =
                                "Nhật ký hoạt động",

                            subtitle =
                                "Theo dõi hoạt động hệ thống"
                        )
                    },

                    trailingIcon = {

                        MenuArrow()
                    },

                    onClick = {

                        moreExpanded = false

                        onNavigate(
                            AdminRoute.ActivityLogs
                        )
                    }
                )
            }
        }
    }
}


/*
 * ================================================================
 * MENU ICON
 * ================================================================
 */

@Composable
private fun MenuIcon(
    icon: String,
    color: androidx.compose.ui.graphics.Color
) {

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(
                RoundedCornerShape(9.dp)
            )
            .background(
                color.copy(alpha = 0.14f)
            ),

        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text = icon,

            color = color,

            fontSize = 15.sp,

            fontWeight =
                FontWeight.Bold
        )
    }
}


/*
 * ================================================================
 * MENU TEXT
 * ================================================================
 */

@Composable
private fun MenuText(
    title: String,
    subtitle: String
) {

    Column {

        Text(
            text = title,

            color =
                AdminColors.TextPrimary,

            fontSize = 11.sp,

            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(2.dp)
        )

        Text(
            text = subtitle,

            color =
                AdminColors.TextSecondary,

            fontSize = 8.sp
        )
    }
}


/*
 * ================================================================
 * MENU ARROW
 * ================================================================
 */

@Composable
private fun MenuArrow() {

    Text(
        text = "›",

        color =
            AdminColors.TextSecondary,

        fontSize = 20.sp
    )
}