package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors

@Composable
fun ActivityLogsScreen() {

    /*
     * ============================================================
     * SAMPLE LOG DATA
     * ============================================================
     */

    val logs = listOf(

        ActivityLogItem(
            title =
                "Admin cập nhật gói Premium",

            time =
                "10:24 • hôm nay"
        ),

        ActivityLogItem(
            title =
                "PT Trần Hoàng duyệt lịch",

            time =
                "10:15 • hôm nay"
        ),

        ActivityLogItem(
            title =
                "Lê Tân check-in KM012345",

            time =
                "09:48 • hôm nay"
        ),

        ActivityLogItem(
            title =
                "Admin tạo hóa đơn #INV-1092",

            time =
                "09:47 • hôm nay"
        ),

        ActivityLogItem(
            title =
                "Hội viên đổi mật khẩu",

            time =
                "09:46 • hôm nay"
        )
    )


    /*
     * ============================================================
     * SCREEN
     * ============================================================
     */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                AdminColors.Background
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {

            /*
             * ====================================================
             * FILTER
             * ====================================================
             */

            Text(
                text = "Bộ lọc",

                color =
                    AdminColors.TextSecondary,

                fontSize = 8.sp
            )


            Spacer(
                modifier = Modifier.height(5.dp)
            )


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(
                        RoundedCornerShape(10.dp)
                    )
                    .background(
                        AdminColors.Surface
                    )
                    .padding(
                        horizontal = 12.dp
                    ),

                contentAlignment =
                    Alignment.CenterStart
            ) {

                Text(
                    text =
                        "Người dùng, hành động, ngày",

                    color =
                        AdminColors.TextSecondary,

                    fontSize = 8.sp
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ====================================================
             * LOG LIST
             * ====================================================
             */

            logs.forEach { log ->

                ActivityLogItemView(
                    log = log
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }
        }
    }
}


/*
 * ================================================================
 * DATA
 * ================================================================
 */

data class ActivityLogItem(
    val title: String,
    val time: String
)


/*
 * ================================================================
 * LOG ITEM
 * ================================================================
 */

@Composable
private fun ActivityLogItemView(
    log: ActivityLogItem
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
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

        /*
         * ICON
         */

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


        /*
         * CONTENT
         */

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = log.title,

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
                text = log.time,

                color =
                    AdminColors.TextSecondary,

                fontSize = 7.sp
            )
        }


        /*
         * ARROW
         */

        Text(
            text = "›",

            color =
                AdminColors.TextPrimary,

            fontSize = 19.sp
        )
    }
}