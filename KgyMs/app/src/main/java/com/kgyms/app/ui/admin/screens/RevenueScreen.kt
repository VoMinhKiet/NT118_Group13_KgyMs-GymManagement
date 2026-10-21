package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

@Composable
fun RevenueScreen() {

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
             * TOTAL REVENUE
             * ============================
             */

            Text(
                text = "128.500.000đ",

                color =
                    AdminColors.TextPrimary,

                fontSize = 24.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier = Modifier.height(4.dp)
            )


            Text(
                text = "+12.4% so với tháng trước",

                color =
                    AdminColors.Green,

                fontSize = 9.sp
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * CHART
             * ============================
             */

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp)
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
                    .background(
                        AdminColors.Surface
                    )
                    .padding(12.dp)
            ) {

                Text(
                    text = "Doanh thu theo ngày",

                    color =
                        AdminColors.TextPrimary,

                    fontSize = 10.sp,

                    fontWeight =
                        FontWeight.SemiBold
                )


                Spacer(
                    modifier = Modifier.height(8.dp)
                )


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),

                    horizontalArrangement =
                        Arrangement.SpaceEvenly,

                    verticalAlignment =
                        Alignment.Bottom
                ) {

                    val bars = listOf(
                        0.60f,
                        0.72f,
                        0.88f,
                        0.78f,
                        0.95f,
                        0.90f,
                        1.0f
                    )

                    bars.forEach { ratio ->

                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment =
                                Alignment.BottomCenter
                        ) {

                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .fillMaxHeight(
                                        ratio
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 5.dp,
                                            topEnd = 5.dp
                                        )
                                    )
                                    .background(
                                        AdminColors.Primary
                                    )
                            )
                        }
                    }
                }


                Spacer(
                    modifier = Modifier.height(3.dp)
                )


                Text(
                    text =
                        "T1   T2   T3   T4   T5   T6   T7",

                    color =
                        AdminColors.TextDark,

                    fontSize = 6.sp
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * REVENUE BREAKDOWN
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                RevenueStatCard(
                    title = "Gói tập",
                    value = "84.2M",
                    accentColor =
                        AdminColors.Primary,
                    modifier =
                        Modifier.weight(1f)
                )


                RevenueStatCard(
                    title = "PT",
                    value = "32.7M",
                    accentColor =
                        AdminColors.Cyan,
                    modifier =
                        Modifier.weight(1f)
                )
            }


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            RevenueStatCard(
                title = "Khác",
                value = "11.6M",
                accentColor =
                    AdminColors.Green,
                modifier =
                    Modifier.width(120.dp)
            )
        }
    }
}


/*
 * ============================================================
 * REVENUE STAT CARD
 * ============================================================
 */

@Composable
private fun RevenueStatCard(
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

            fontSize = 16.sp,

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