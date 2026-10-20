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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.kgyms.app.ui.admin.AdminSearchInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors

@Composable
fun TransactionsScreen(
    onReceiptClick: (TransactionItem) -> Unit = {}
) {

    /*
     * ============================
     * TRANSACTION DATA
     * ============================
     */

    var selectedFilter by rememberSaveable { mutableStateOf("Tất cả") }
    val transactions = listOf(

        TransactionItem(
            invoice = "#INV-1092",
            memberName = "Nguyễn Minh Anh",
            service = "Premium 6 tháng",
            amount = "1.990.000đ",
            status = "Đã thanh toán",
            date = "24/09/2026 09:42"
        ),

        TransactionItem(
            invoice = "#INV-1091",
            memberName = "Trần Gia Huy",
            service = "Basic 1 tháng",
            amount = "399.000đ",
            status = "Đã thanh toán",
            date = "23/09/2026 14:20"
        ),

        TransactionItem(
            invoice = "#INV-1090",
            memberName = "Lê Phương Thảo",
            service = "Elite 12 tháng",
            amount = "2.590.000đ",
            status = "Chờ thanh toán",
            date = "22/09/2026 16:10"
        ),

        TransactionItem(
            invoice = "#INV-1089",
            memberName = "Vũ Hoàng Nam",
            service = "Premium 6 tháng",
            amount = "1.990.000đ",
            status = "Đã thanh toán",
            date = "21/09/2026 11:35"
        ),

        TransactionItem(
            invoice = "#INV-1088",
            memberName = "Mai Ngọc Linh",
            service = "PT Online 8 buổi",
            amount = "890.000đ",
            status = "Đã thanh toán",
            date = "20/09/2026 18:45"
        )
    )


    /*
     * ============================
     * SCREEN
     * ============================
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
             * ============================
             * FILTER
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                TransactionFilter(
                    text = "Tất cả",
                    selected = selectedFilter == "Tất cả",
                    onClick = { selectedFilter = "Tất cả" },
                    modifier = Modifier.weight(1f)
                )

                TransactionFilter(
                    text = "Thành công",
                    selected = selectedFilter == "Thành công",
                    onClick = { selectedFilter = "Thành công" },
                    modifier = Modifier.weight(1f)
                )

                TransactionFilter(
                    text = "Chờ",
                    selected = selectedFilter == "Chờ",
                    onClick = { selectedFilter = "Chờ" },
                    modifier = Modifier.weight(1f)
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * TRANSACTION LIST
             * ============================
             */

            transactions.filter { when (selectedFilter) {
                "Thành công" -> it.status == "Đã thanh toán"
                "Chờ" -> it.status == "Chờ thanh toán"
                else -> true
            } }.forEach { transaction ->

                TransactionCard(
                    transaction = transaction,
                    onReceiptClick = {
                        onReceiptClick(transaction)
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
 * TRANSACTION DATA
 * ============================================================
 */

data class TransactionItem(
    val invoice: String,
    val memberName: String,
    val service: String,
    val amount: String,
    val status: String,
    val date: String
)


/*
 * ============================================================
 * FILTER
 * ============================================================
 */

@Composable
private fun TransactionFilter(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier.clickable(onClick = onClick)
            .height(32.dp)
            .clip(
                RoundedCornerShape(18.dp)
            )
            .background(
                if (selected) {
                    AdminColors.Primary.copy(
                        alpha = 0.18f
                    )
                } else {
                    AdminColors.Surface
                }
            ),

        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text = text,

            color =
                if (selected) {
                    AdminColors.PrimaryLight
                } else {
                    AdminColors.TextSecondary
                },

            fontSize = 8.sp,

            fontWeight =
                if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                }
        )
    }
}


/*
 * ============================================================
 * TRANSACTION CARD
 * ============================================================
 */

@Composable
private fun TransactionCard(
    transaction: TransactionItem,
    onReceiptClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 8.dp,
                vertical = 8.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        /*
         * ============================
         * ICON
         * ============================
         */

        Box(
            modifier = Modifier
                .size(34.dp)
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
         * ============================
         * TRANSACTION INFORMATION
         * ============================
         */

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text =
                    "${transaction.invoice} • ${transaction.amount}",

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

                fontSize = 7.sp
            )
        }


        /*
         * ============================
         * SHARE BUTTON
         * ============================
         */

        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(9.dp)
                )
                .background(
                    AdminColors.Primary.copy(
                        alpha = 0.16f
                    )
                )
                .clickable {
                    onReceiptClick()
                }
                .padding(
                    horizontal = 10.dp,
                    vertical = 7.dp
                ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "Chia sẻ",

                color =
                    AdminColors.PrimaryLight,

                fontSize = 8.sp,

                fontWeight =
                    FontWeight.SemiBold,

                textAlign =
                    TextAlign.Center
            )
        }
    }
}