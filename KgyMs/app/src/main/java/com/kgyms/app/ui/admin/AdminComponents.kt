package com.kgyms.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AdminColors {

    val Background = Color(0xFF080B14)

    val Surface = Color(0xFF171D2B)

    val SurfaceLight = Color(0xFF1D2535)

    val Primary = Color(0xFF8055FF)

    val PrimaryLight = Color(0xFFAA4CFF)

    val Cyan = Color(0xFF61C7FF)

    val Green = Color(0xFF4ED6A0)

    val Pink = Color(0xFFFF5E83)

    val Yellow = Color(0xFFFFC857)

    val TextPrimary = Color(0xFFF5F5F7)

    val TextSecondary = Color(0xFF9CA3B4)

    val Border = Color(0xFF283144)
}


@Composable
fun AdminBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(AdminColors.Background)
    ) {
        content()
    }
}


@Composable
fun MetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = AdminColors.Surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {

            Text(
                text = title,
                color = AdminColors.TextSecondary,
                fontSize = 10.sp
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = value,
                color = AdminColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .clip(
                        RoundedCornerShape(10.dp)
                    )
                    .background(accentColor)
            )
        }
    }
}


@Composable
fun AdminSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        modifier = modifier,
        color = AdminColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
}


@Composable
fun ActivityItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = AdminColors.Surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AdminColors.Primary)
            )

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    color = AdminColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text = subtitle,
                    color = AdminColors.TextSecondary,
                    fontSize = 9.sp
                )
            }

            Text(
                text = "›",
                color = AdminColors.TextSecondary,
                fontSize = 18.sp
            )
        }
    }
}


@Composable
fun SimpleBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(125.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {

        values.forEachIndexed { index, value ->

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(value.dp)
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

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text = "T${index + 1}",
                    color = AdminColors.TextSecondary,
                    fontSize = 7.sp
                )
            }
        }
    }
}