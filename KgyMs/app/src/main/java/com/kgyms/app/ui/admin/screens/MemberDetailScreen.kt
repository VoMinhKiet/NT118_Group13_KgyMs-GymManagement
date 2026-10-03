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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
fun MemberDetailScreen(
    memberName: String,
    onBack: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminColors.Background)
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
             * ============================
             * MEMBER HEADER CARD
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AdminColors.Surface)
                    .padding(16.dp)
            ) {

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {

                    /*
                     * Gói tập
                     */
                    Text(
                        text = "PREMIUM",
                        color = AdminColors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )


                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )


                    /*
                     * Tên hội viên
                     */
                    Text(
                        text = memberName,
                        color = AdminColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )


                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )


                    /*
                     * Mã hội viên + trạng thái
                     */
                    Text(
                        text = "KM012345 • Hợp lệ",
                        color = AdminColors.Green,
                        fontSize = 9.sp
                    )


                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )


                    /*
                     * Thanh màu
                     */
                    Box(
                        modifier = Modifier
                            .size(
                                width = 64.dp,
                                height = 4.dp
                            )
                            .clip(
                                RoundedCornerShape(4.dp)
                            )
                            .background(
                                AdminColors.Primary
                            )
                    )
                }
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * EXPIRATION
             * ============================
             */

            MemberDetailItem(
                text = "Hết hạn • 30/05/2027",
                textColor = AdminColors.Pink,
                showArrow = true
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * CHECK-IN
             * ============================
             */

            MemberDetailItem(
                text = "Check-in tháng này • 12"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * PT
             * ============================
             */

            MemberDetailItem(
                text = "PT phụ trách • Trần Hoàng"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * INBODY
             * ============================
             */

            MemberDetailItem(
                text = "InBody gần nhất • 18/09"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * DEBT
             * ============================
             */

            MemberDetailItem(
                text = "Công nợ • 0đ"
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            /*
             * ============================
             * RENEW BUTTON
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(
                        RoundedCornerShape(14.dp)
                    )
                    .background(
                        AdminColors.Primary
                    )
                    .clickable {
                        // TODO:
                        // Xử lý gia hạn / bảo lưu
                    },
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "Gia hạn / Bảo lưu",
                    color = AdminColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}


/*
 * ============================
 * DETAIL ITEM
 * ============================
 */

@Composable
private fun MemberDetailItem(
    text: String,
    textColor: androidx.compose.ui.graphics.Color = AdminColors.TextPrimary,
    showArrow: Boolean = true
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(
                RoundedCornerShape(16.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(horizontal = 8.dp),

        verticalAlignment = Alignment.CenterVertically
    ) {

        /*
         * Icon / placeholder
         */
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(
                    RoundedCornerShape(10.dp)
                )
                .background(
                    AdminColors.SurfaceLight
                )
        )


        Spacer(
            modifier = Modifier.width(10.dp)
        )


        /*
         * Nội dung
         */
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )


        /*
         * Arrow
         */
        if (showArrow) {

            Text(
                text = "›",
                color = AdminColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}