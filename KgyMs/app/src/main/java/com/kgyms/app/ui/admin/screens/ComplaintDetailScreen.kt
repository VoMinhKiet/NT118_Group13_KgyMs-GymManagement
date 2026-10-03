package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors

@Composable
fun ComplaintDetailScreen(
    complaint: ComplaintItem
) {

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
             * COMPLAINT ID
             * ====================================================
             */

            Text(
                text = complaint.id,

                color =
                    AdminColors.TextPrimary,

                fontSize = 18.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier = Modifier.height(4.dp)
            )


            /*
             * ====================================================
             * TITLE
             * ====================================================
             */

            Text(
                text = complaint.title,

                color =
                    AdminColors.Pink,

                fontSize = 21.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier = Modifier.height(5.dp)
            )


            /*
             * ====================================================
             * MEMBER + DATE
             * ====================================================
             */

            Text(
                text =
                    "${complaint.memberName} • ${complaint.date}",

                color =
                    AdminColors.TextSecondary,

                fontSize = 8.sp
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ====================================================
             * COMPLAINT CONTENT
             * ====================================================
             */

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
                    .background(
                        AdminColors.Surface
                    )
                    .padding(12.dp)
            ) {

                Text(
                    text =
                        complaint.description,

                    color =
                        AdminColors.TextPrimary,

                    fontSize = 9.sp,

                    lineHeight = 15.sp
                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                /*
                 * PRIORITY
                 */

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(20.dp)
                        )
                        .background(
                            AdminColors.Primary
                                .copy(
                                    alpha = 0.16f
                                )
                        )
                        .padding(
                            horizontal = 14.dp,
                            vertical = 6.dp
                        )
                ) {

                    Text(
                        text =
                            complaint.priority,

                        color =
                            AdminColors.PrimaryLight,

                        fontSize = 8.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            /*
             * ====================================================
             * RESPONSE FIELD
             * ====================================================
             */

            Text(
                text = "Phản hồi",

                color =
                    AdminColors.TextSecondary,

                fontSize = 8.sp
            )


            Spacer(
                modifier = Modifier.height(4.dp)
            )


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(
                        RoundedCornerShape(14.dp)
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
                        "Nhập nội dung xử lý...",

                    color =
                        AdminColors.TextSecondary,

                    fontSize = 9.sp
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ====================================================
             * UPDATE STATUS
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
                        // TODO:
                        // Update complaint status
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        "Cập nhật trạng thái",

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