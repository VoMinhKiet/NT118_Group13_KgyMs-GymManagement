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
fun AssetDetailScreen(
    assetName: String,
    onBack: () -> Unit
) {
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
             * ============================
             * ASSET HEADER
             * ============================
             */

            Column {

                Text(
                    text = assetName,
                    color = AdminColors.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )


                Spacer(
                    modifier = Modifier.height(5.dp)
                )


                Text(
                    text = "Khu Strength • Tầng 1",
                    color = AdminColors.TextSecondary,
                    fontSize = 8.sp
                )
            }


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            /*
             * ============================
             * STATUS CARD
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(
                        RoundedCornerShape(18.dp)
                    )
                    .background(
                        AdminColors.Surface
                    )
                    .padding(12.dp)
            ) {

                Column {

                    Text(
                        text = "STATUS",
                        color = AdminColors.TextSecondary,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Medium
                    )


                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )


                    Text(
                        text = if (
                            assetName == "Máy Smith"
                        ) {
                            "Cần bảo trì"
                        } else {
                            "Đang hoạt động"
                        },
                        color = if (
                            assetName == "Máy Smith"
                        ) {
                            AdminColors.Pink
                        } else {
                            AdminColors.Green
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )


                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )


                    Text(
                        text = "Lần kiểm tra: 18/09/2026",
                        color = AdminColors.TextSecondary,
                        fontSize = 7.sp
                    )


                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )


                    Box(
                        modifier = Modifier
                            .width(45.dp)
                            .height(3.dp)
                            .clip(
                                RoundedCornerShape(3.dp)
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
             * NGÀY MUA
             * ============================
             */

            AssetDetailItem(
                text = "Ngày mua • 12/03/2024"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * BẢO HÀNH
             * ============================
             */

            AssetDetailItem(
                text = "Bảo hành • 24 tháng"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * LỊCH BẢO TRÌ
             * ============================
             */

            AssetDetailItem(
                text = "Lịch bảo trì • 30/09/2026"
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * NGƯỜI PHỤ TRÁCH
             * ============================
             */

            AssetDetailItem(
                text = "Người phụ trách • Lê Minh"
            )


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            /*
             * ============================
             * MAINTENANCE BUTTON
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
                        showMockAction("Tạo phiếu bảo trì")
                    },
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "Tạo phiếu bảo trì",
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
private fun AssetDetailItem(
    text: String
) {
    val showMockAction = LocalAdminFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth().clickable { showMockAction(text) }
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
            fontSize = 11.sp,
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