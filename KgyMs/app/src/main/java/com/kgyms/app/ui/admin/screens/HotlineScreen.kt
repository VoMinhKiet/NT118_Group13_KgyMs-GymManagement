package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors
import com.kgyms.app.ui.admin.LocalAdminFeedback

@Composable
fun HotlineScreen() {
    val showMockAction = LocalAdminFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                AdminColors.Background
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize().verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {

            /*
             * ====================================================
             * TITLE
             * ====================================================
             */

            Text(
                text = "Trung tâm hỗ trợ",

                color =
                    AdminColors.TextPrimary,

                fontSize = 21.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier = Modifier.height(5.dp)
            )


            Text(
                text =
                    "Theo dõi cuộc gọi và yêu cầu hỗ trợ.",

                color =
                    AdminColors.TextSecondary,

                fontSize = 8.sp
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            /*
             * ====================================================
             * HOTLINE
             * ====================================================
             */

            HotlineItem(
                icon = "☎",

                title =
                    "1900 1234 • Hotline chính",

                subtitle =
                    "Đang hoạt động",

                iconColor =
                    AdminColors.Green
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            /*
             * ====================================================
             * ZALO
             * ====================================================
             */

            HotlineItem(
                icon = "Z",

                title =
                    "Zalo OA • KgyMs Support",

                subtitle =
                    "Đang hoạt động",

                iconColor =
                    AdminColors.Cyan
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            /*
             * ====================================================
             * EMAIL
             * ====================================================
             */

            HotlineItem(
                icon = "@",

                title =
                    "Email • support@kgyms.vn",

                subtitle =
                    "Đang hoạt động",

                iconColor =
                    AdminColors.PrimaryLight
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            /*
             * ====================================================
             * WORKING HOURS
             * ====================================================
             */

            HotlineItem(
                icon = "◷",

                title =
                    "Giờ hỗ trợ • 08:00–22:00",

                subtitle =
                    "Đang hoạt động",

                iconColor =
                    AdminColors.Yellow
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ====================================================
             * CALL BUTTON
             * ====================================================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    .background(
                        AdminColors.Primary
                    )
                    .clickable {
                        showMockAction("Gọi Hotline")
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "Gọi Hotline",

                    color =
                        AdminColors.TextPrimary,

                    fontSize = 10.sp,

                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
    }
}


/*
 * ================================================================
 * HOTLINE ITEM
 * ================================================================
 */

@Composable
private fun HotlineItem(
    icon: String,
    title: String,
    subtitle: String,
    iconColor: androidx.compose.ui.graphics.Color
) {
    val showMockAction = LocalAdminFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth().clickable { showMockAction(title) }
            .height(48.dp)
            .clip(
                RoundedCornerShape(12.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 8.dp
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
                ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = icon,

                color = iconColor,

                fontSize = 12.sp,

                fontWeight =
                    FontWeight.Bold
            )
        }


        Spacer(
            modifier = Modifier.size(10.dp)
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


        Text(
            text = "›",

            color =
                AdminColors.TextPrimary,

            fontSize = 19.sp
        )
    }
}