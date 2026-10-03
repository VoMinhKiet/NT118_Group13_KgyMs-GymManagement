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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors

@Composable
fun StaffDetailScreen(
    staffName: String,
    onBack: () -> Unit
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
             * ============================
             * STAFF HEADER CARD
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(
                        RoundedCornerShape(20.dp)
                    )
                    .background(
                        AdminColors.Surface
                    )
                    .padding(14.dp)
            ) {

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {

                    /*
                     * Chức vụ
                     */
                    Text(
                        text = "PT",
                        color = AdminColors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )


                    Spacer(
                        modifier = Modifier.height(3.dp)
                    )


                    /*
                     * Tên nhân sự
                     */
                    Text(
                        text = staffName,
                        color = AdminColors.TextPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )


                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )


                    /*
                     * Thông tin phụ
                     */
                    Text(
                        text = "Strength Coach • NV-PT012",
                        color = AdminColors.TextSecondary,
                        fontSize = 8.sp
                    )


                    Spacer(
                        modifier = Modifier.height(7.dp)
                    )


                    /*
                     * Thanh accent
                     */
                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .height(4.dp)
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
             * SỐ ĐIỆN THOẠI
             * ============================
             */

            StaffDetailItem(
                text = "SĐT • 0909 123 456"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * CA LÀM
             * ============================
             */

            StaffDetailItem(
                text = "Ca làm • 14:00–22:00"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * KHÁCH PHỤ TRÁCH
             * ============================
             */

            StaffDetailItem(
                text = "Khách phụ trách • 18"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * RATING
             * ============================
             */

            StaffDetailItem(
                text = "Rating • 4.9 / 5"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * TRẠNG THÁI
             * ============================
             */

            StaffDetailItem(
                text = "Trạng thái • Đang hoạt động"
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            /*
             * ============================
             * EDIT BUTTON
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
                        // Mở màn hình chỉnh sửa hồ sơ
                    },
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "Chỉnh sửa hồ sơ",
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
private fun StaffDetailItem(
    text: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(
                RoundedCornerShape(15.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 8.dp
            ),

        verticalAlignment = Alignment.CenterVertically
    ) {

        /*
         * Icon placeholder
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
            color = AdminColors.TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )


        /*
         * Arrow
         */
        Text(
            text = "›",
            color = AdminColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light
        )
    }
}