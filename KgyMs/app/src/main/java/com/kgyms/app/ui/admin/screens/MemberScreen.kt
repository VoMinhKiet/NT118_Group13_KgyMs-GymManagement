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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors
import com.kgyms.app.ui.admin.AdminRoute


@Composable
fun MemberScreen(
    onNavigate: (AdminRoute) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                AdminColors.Background
            )
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
    ) {



        // =================================================
        // SEARCH
        // =================================================

        Text(
            text = "Tìm kiếm",

            color =
                AdminColors.TextSecondary,

            fontSize = 9.sp
        )

        Spacer(
            modifier = Modifier.height(5.dp)
        )


        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(10.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        AdminColors.Surface
                )
        ) {

            Text(
                text =
                    "Tên, SĐT, mã hội viên",

                color =
                    AdminColors.TextSecondary,

                fontSize = 10.sp,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = 12.dp
                        )
            )
        }


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        // =================================================
        // FILTER
        // =================================================

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            MemberFilter(
                text = "Tất cả",
                selected = true,
                modifier = Modifier.weight(1f)
            )

            MemberFilter(
                text = "Hoạt động",
                selected = false,
                modifier = Modifier.weight(1f)
            )

            MemberFilter(
                text = "Sắp hết hạn",
                selected = false,
                modifier = Modifier.weight(1f)
            )
        }


        Spacer(
            modifier = Modifier.height(9.dp)
        )


        // =================================================
        // MEMBERS
        // =================================================

        MemberItem(
            name = "Nguyễn Minh Anh",
            description = "Premium • hoạt động"
        )

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        MemberItem(
            name = "Trần Gia Huy",
            description = "Basic • còn 12 ngày"
        )

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        MemberItem(
            name = "Lê Phương Thảo",
            description = "Premium • hoạt động"
        )

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        MemberItem(
            name = "Vũ Hoàng Nam",
            description = "Basic • còn 12 ngày"
        )

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        MemberItem(
            name = "Mai Ngọc Linh",
            description = "Premium • hoạt động"
        )


        Spacer(
            modifier = Modifier.height(12.dp)
        )


        // =================================================
        // ADD MEMBER
        // =================================================

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(12.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        AdminColors.Primary
                )
        ) {

            Text(
                text = "+  Thêm hội viên",

                color =
                    Color.White,

                fontSize = 11.sp,

                fontWeight =
                    FontWeight.Medium,

                textAlign =
                    TextAlign.Center,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 12.dp
                        )
            )
        }
    }
}


// =====================================================
// FILTER
// =====================================================

@Composable
private fun MemberFilter(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,

        shape =
            RoundedCornerShape(20.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected)
                        AdminColors.Primary
                    else
                        AdminColors.Surface
            )
    ) {

        Text(
            text = text,

            color =
                if (selected)
                    Color.White
                else
                    AdminColors.TextSecondary,

            fontSize = 8.sp,

            fontWeight =
                if (selected)
                    FontWeight.Medium
                else
                    FontWeight.Normal,

            textAlign =
                TextAlign.Center,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 7.dp
                    )
        )
    }
}


// =====================================================
// MEMBER ITEM
// =====================================================

@Composable
private fun MemberItem(
    name: String,
    description: String
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(11.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    AdminColors.Surface
            )
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 8.dp
                    ),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            // =================================================
            // AVATAR
            // =================================================

            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(
                            RoundedCornerShape(8.dp)
                        )
                        .background(
                            AdminColors.SurfaceLight
                        )
            )


            Spacer(
                modifier =
                    Modifier.size(10.dp)
            )


            // =================================================
            // MEMBER INFO
            // =================================================

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = name,

                    color =
                        AdminColors.TextPrimary,

                    fontSize = 10.sp,

                    fontWeight =
                        FontWeight.Medium
                )

                Spacer(
                    modifier =
                        Modifier.height(2.dp)
                )

                Text(
                    text = description,

                    color =
                        AdminColors.TextSecondary,

                    fontSize = 7.sp
                )
            }


            // =================================================
            // ARROW
            // =================================================

            Text(
                text = "›",

                color =
                    AdminColors.TextSecondary,

                fontSize = 18.sp
            )
        }
    }
}