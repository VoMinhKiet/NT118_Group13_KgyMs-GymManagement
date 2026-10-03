package com.kgyms.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        ),

        AdminBottomItem(
            title = "Thêm",
            icon = "•••",
            route = AdminRoute.Packages
        )
    )


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 4.dp,
                vertical = 5.dp
            ),

        horizontalArrangement =
            Arrangement.SpaceEvenly,

        verticalAlignment =
            Alignment.CenterVertically
    ) {

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
                    .clickable {
                        onNavigate(item.route)
                    }
                    .padding(
                        vertical = 5.dp
                    ),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center
            ) {

                // =========================================
                // ICON
                // =========================================

                Text(
                    text = item.icon,

                    color =
                        if (selected)
                            AdminColors.Primary
                        else
                            AdminColors.TextSecondary,

                    fontSize = 22.sp,

                    fontWeight =
                        FontWeight.Bold,

                    textAlign =
                        TextAlign.Center
                )


                // =========================================
                // LABEL
                // =========================================

                Text(
                    text = item.title,

                    color =
                        if (selected)
                            AdminColors.TextPrimary
                        else
                            AdminColors.TextSecondary,

                    fontSize = 9.sp,

                    fontWeight =
                        if (selected)
                            FontWeight.SemiBold
                        else
                            FontWeight.Normal,

                    textAlign =
                        TextAlign.Center,

                    maxLines = 1
                )
            }
        }
    }
}