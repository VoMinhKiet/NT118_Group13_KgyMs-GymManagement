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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors
import com.kgyms.app.ui.admin.LocalAdminFeedback

@Composable
fun ReceiptScreen(
    transaction: TransactionItem
) {
    val showMockAction = LocalAdminFeedback.current

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
                .fillMaxSize().verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {

            /*
             * ====================================================
             * INVOICE NUMBER
             * ====================================================
             */

            Text(
                text = transaction.invoice,

                color = AdminColors.TextPrimary,

                fontSize = 22.sp,

                fontWeight = FontWeight.Bold
            )


            Spacer(
                modifier = Modifier.height(5.dp)
            )


            /*
             * ====================================================
             * MEMBER NAME
             * ====================================================
             */

            Text(
                text = transaction.memberName,

                color = AdminColors.TextSecondary,

                fontSize = 9.sp
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ====================================================
             * RECEIPT INFORMATION CARD
             * ====================================================
             */

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(202.dp)
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
                    .background(
                        AdminColors.Surface
                    )
                    .padding(12.dp)
            ) {

                /*
                 * SERVICE
                 */

                Text(
                    text = transaction.service,

                    color = AdminColors.TextPrimary,

                    fontSize = 10.sp,

                    fontWeight = FontWeight.Medium
                )


                Spacer(
                    modifier = Modifier.height(5.dp)
                )


                /*
                 * AMOUNT
                 */

                Text(
                    text = transaction.amount,

                    color = AdminColors.TextPrimary,

                    fontSize = 10.sp
                )


                Spacer(
                    modifier = Modifier.height(5.dp)
                )


                /*
                 * STATUS
                 */

                Text(
                    text = transaction.status,

                    color =
                        if (
                            transaction.status ==
                            "Đã thanh toán"
                        ) {
                            AdminColors.Green
                        } else {
                            AdminColors.Yellow
                        },

                    fontSize = 9.sp,

                    fontWeight = FontWeight.Medium
                )


                Spacer(
                    modifier = Modifier.height(5.dp)
                )


                /*
                 * DATE
                 */

                Text(
                    text = transaction.date,

                    color = AdminColors.TextPrimary,

                    fontSize = 9.sp
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ====================================================
             * SHARE BUTTON
             * ====================================================
             */

            ReceiptActionButton(
                text = "Chia sẻ qua ứng dụng",

                onClick = {
                    showMockAction("Chia sẻ biên lai qua ứng dụng")
                }
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            /*
             * ====================================================
             * EXPORT PDF BUTTON
             * ====================================================
             */

            ReceiptActionButton(
                text = "Xuất PDF",

                onClick = {
                    showMockAction("Xuất biên lai PDF")
                }
            )
        }
    }
}


/*
 * ================================================================
 * RECEIPT ACTION BUTTON
 * ================================================================
 */

@Composable
private fun ReceiptActionButton(
    text: String,
    onClick: () -> Unit
) {

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
                onClick()
            },

        contentAlignment = Alignment.Center
    ) {

        Text(
            text = text,

            color = AdminColors.TextPrimary,

            fontSize = 10.sp,

            fontWeight = FontWeight.SemiBold,

            textAlign = TextAlign.Center
        )
    }
}