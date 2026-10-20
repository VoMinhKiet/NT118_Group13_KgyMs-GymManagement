package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
fun InvoiceScreen(
    onTransactionClick: () -> Unit
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
                ),

            verticalArrangement =
                Arrangement.Top
        ) {

            /*
             * ============================
             * TITLE
             * ============================
             */

            Text(
                text = "Hóa đơn mới",

                color =
                    AdminColors.TextPrimary,

                fontSize = 24.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Text(
                text = "Liên kết hội viên và dịch vụ.",

                color =
                    AdminColors.TextSecondary,

                fontSize = 9.sp,

                modifier = Modifier.padding(
                    top = 4.dp
                )
            )


            /*
             * ============================
             * MEMBER
             * ============================
             */

            InvoiceField(
                label = "Hội viên",
                value = "KM012345 • Nguyễn Minh Anh"
            )


            /*
             * ============================
             * SERVICE
             * ============================
             */

            InvoiceField(
                label = "Dịch vụ",
                value = "Premium 6 tháng"
            )


            /*
             * ============================
             * DISCOUNT
             * ============================
             */

            InvoiceField(
                label = "Giảm giá",
                value = "0%"
            )


            /*
             * ============================
             * TOTAL
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(
                        RoundedCornerShape(14.dp)
                    )
                    .background(
                        AdminColors.Surface
                    )
                    .padding(12.dp)
            ) {

                Column {

                    Text(
                        text = "Tổng thanh toán",

                        color =
                            AdminColors.TextSecondary,

                        fontSize = 9.sp
                    )


                    Text(
                        text = "1.990.000đ",

                        color =
                            AdminColors.TextPrimary,

                        fontSize = 24.sp,

                        fontWeight =
                            FontWeight.Bold,

                        modifier = Modifier.padding(
                            top = 3.dp
                        )
                    )
                }
            }


            /*
             * ============================
             * CREATE INVOICE
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(
                        RoundedCornerShape(13.dp)
                    )
                    .background(
                        AdminColors.Primary
                    )
                    .clickable {
                        // TODO:
                        // Tạo hóa đơn
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "Tạo hóa đơn",

                    color =
                        AdminColors.TextPrimary,

                    fontSize = 11.sp,

                    fontWeight =
                        FontWeight.SemiBold
                )
            }


            /*
             * ============================
             * TRANSACTION HISTORY
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(
                        RoundedCornerShape(13.dp)
                    )
                    .background(
                        AdminColors.Surface
                    )
                    .clickable {
                        onTransactionClick()
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "Lịch sử giao dịch",

                    color =
                        AdminColors.TextPrimary,

                    fontSize = 11.sp,

                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
    }
}


/*
 * ============================================================
 * INVOICE FIELD
 * ============================================================
 */

@Composable
private fun InvoiceField(
    label: String,
    value: String
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 9.dp
            )
    ) {

        Text(
            text = label,

            color =
                AdminColors.TextSecondary,

            fontSize = 8.sp
        )


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(
                    RoundedCornerShape(13.dp)
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
                text = value,

                color =
                    AdminColors.TextPrimary,

                fontSize = 10.sp
            )
        }
    }
}