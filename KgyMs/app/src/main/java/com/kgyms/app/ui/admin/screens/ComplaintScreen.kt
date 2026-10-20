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
import androidx.compose.foundation.layout.width
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
import com.kgyms.app.ui.admin.AdminColors

data class ComplaintItem(
    val id: String,
    val title: String,
    val memberName: String,
    val date: String,
    val priority: String,
    val status: String,
    val description: String
)

@Composable
fun ComplaintScreen(
    onComplaintClick: (ComplaintItem) -> Unit
) {

    /*
     * ============================================================
     * SAMPLE DATA
     * ============================================================
     */

    val complaints = listOf(

        ComplaintItem(
            id = "#CS-208",
            title = "Thiết bị hỏng",
            memberName = "Nguyễn Minh Anh",
            date = "24/09/2026",
            priority = "Ưu tiên cao",
            status = "Mới",
            description =
                "Máy chạy bộ gặp lỗi và phát tiếng ồn khi chạy tốc độ > 8km/h."
        ),

        ComplaintItem(
            id = "#CS-207",
            title = "Lịch PT",
            memberName = "Trần Gia Huy",
            date = "23/09/2026",
            priority = "Ưu tiên cao",
            status = "Mới",
            description =
                "Hội viên phản ánh lịch tập PT chưa được cập nhật đúng."
        ),

        ComplaintItem(
            id = "#CS-206",
            title = "Thanh toán",
            memberName = "Lê Phương Thảo",
            date = "22/09/2026",
            priority = "Lớn",
            status = "Đang xử lý",
            description =
                "Giao dịch thanh toán chưa được cập nhật vào hệ thống."
        ),

        ComplaintItem(
            id = "#CS-205",
            title = "Dịch vụ",
            memberName = "Vũ Hoàng Nam",
            date = "21/09/2026",
            priority = "Lớn",
            status = "Đang xử lý",
            description =
                "Hội viên phản ánh về chất lượng dịch vụ tại phòng tập."
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
             * STATISTICS
             * ====================================================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                ComplaintStatCard(
                    title = "Mới",
                    value = "8",
                    accentColor =
                        AdminColors.Pink,

                    modifier =
                        Modifier.weight(1f)
                )

                ComplaintStatCard(
                    title = "Đang xử lý",
                    value = "14",
                    accentColor =
                        AdminColors.PrimaryLight,

                    modifier =
                        Modifier.weight(1f)
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ====================================================
             * COMPLAINT LIST
             * ====================================================
             */

            complaints.forEach { complaint ->

                ComplaintCard(
                    complaint = complaint,

                    onClick = {
                        onComplaintClick(
                            complaint
                        )
                    }
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }
        }
    }
}


/*
 * ============================================================
 * STAT CARD
 * ============================================================
 */

@Composable
private fun ComplaintStatCard(
    title: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .height(64.dp)
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
    ) {

        Text(
            text = title,

            color =
                AdminColors.TextSecondary,

            fontSize = 8.sp
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
            modifier = Modifier.height(3.dp)
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
 * ============================================================
 * COMPLAINT CARD
 * ============================================================
 */

@Composable
private fun ComplaintCard(
    complaint: ComplaintItem,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                AdminColors.Surface
            )
            .clickable {
                onClick()
            }
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
                ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "!",

                color =
                    AdminColors.Pink,

                fontSize = 13.sp,

                fontWeight =
                    FontWeight.Bold
            )
        }


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
                text =
                    "${complaint.id} • ${complaint.title}",

                color =
                    AdminColors.TextPrimary,

                fontSize = 10.sp,

                fontWeight =
                    FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text =
                    complaint.priority,

                color =
                    if (
                        complaint.priority ==
                        "Ưu tiên cao"
                    ) {
                        AdminColors.Pink
                    } else {
                        AdminColors.TextSecondary
                    },

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

            fontSize = 20.sp,

            textAlign =
                TextAlign.Center
        )
    }
}