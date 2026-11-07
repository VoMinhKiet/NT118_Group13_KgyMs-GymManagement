package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
fun TrafficScreen() {

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
             * TODAY STATS
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                TrafficStatCard(
                    title = "Hôm nay",
                    value = "352",
                    accentColor =
                        AdminColors.Cyan,
                    modifier =
                        Modifier.weight(1f)
                )


                TrafficStatCard(
                    title = "Đang ở gym",
                    value = "84",
                    accentColor =
                        AdminColors.Green,
                    modifier =
                        Modifier.weight(1f)
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * CHECK-IN CHART
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
                    text = "Check-in 7 ngày",

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
                        0.75f,
                        0.82f,
                        1.0f,
                        0.78f,
                        1.0f,
                        0.88f
                    )

                    bars.forEach { ratio ->

                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .fillMaxHeight()
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
                                        AdminColors.Cyan
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
             * TIME PERIODS
             * ============================
             */

            TrafficTimeItem(
                time = "06:00–09:00",
                count = "92 lượt",
                subtitle = "Khung giờ"
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            TrafficTimeItem(
                time = "17:00–20:00",
                count = "168 lượt",
                subtitle = "Khung giờ"
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            TrafficTimeItem(
                time = "20:00–22:00",
                count = "64 lượt",
                subtitle = "Khung giờ"
            )
        }
    }
}


/*
 * ============================================================
 * TRAFFIC STAT CARD
 * ============================================================
 */

@Composable
private fun TrafficStatCard(
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
 * TIME ITEM
 * ============================================================
 */

@Composable
private fun TrafficTimeItem(
    time: String,
    count: String,
    subtitle: String
) {
    val showMockAction = LocalAdminFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth().clickable { showMockAction(time) }
            .height(56.dp)
            .clip(
                RoundedCornerShape(14.dp)
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


        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = "$time • $count",

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
                text = subtitle,

                color =
                    AdminColors.TextSecondary,

                fontSize = 7.sp
            )
        }


        Text(
            text = "›",

            color =
                AdminColors.TextPrimary,

            fontSize = 20.sp
        )
    }
}