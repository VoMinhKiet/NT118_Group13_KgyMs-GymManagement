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
fun PackageScreen(
    onNavigate: () -> Unit
) {
    val showMockAction = LocalAdminFeedback.current

    /*
     * ============================
     * PACKAGE LIST
     * ============================
     */

    val packages = listOf(

        PackageItem(
            name = "Premium 6 tháng",
            price = "1.990.000đ"
        ),

        PackageItem(
            name = "Basic 1 tháng",
            price = "399.000đ"
        ),

        PackageItem(
            name = "Elite 12 tháng",
            price = "3.590.000đ"
        ),

        PackageItem(
            name = "PT Online 8 buổi",
            price = "890.000đ"
        )
    )


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
             * HEADER
             * ============================
             */

            Text(
                text = "28 gói đang hoạt động",

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
                text = "Tạo, cập nhật giá và quyền lợi.",

                color =
                    AdminColors.TextSecondary,

                fontSize = 9.sp
            )


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * PACKAGE LIST
             * ============================
             */

            packages.forEach { packageItem ->

                PackageCard(
                    packageItem = packageItem
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }


            /*
             * ============================
             * CREATE PACKAGE
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    .background(
                        AdminColors.Primary
                    )
                    .clickable {
                        showMockAction("Tạo gói mới")
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "+  Tạo gói mới",

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
 * PACKAGE DATA
 * ============================================================
 */

data class PackageItem(
    val name: String,
    val price: String
)


/*
 * ============================================================
 * PACKAGE CARD
 * ============================================================
 */

@Composable
private fun PackageCard(
    packageItem: PackageItem
) {
    val showMockAction = LocalAdminFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                AdminColors.Surface
            )
            .clickable {
                showMockAction("Chi tiết gói tập")
            }
            .padding(
                horizontal = 8.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        /*
         * Icon
         */

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


        /*
         * Package information
         */

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text =
                    "${packageItem.name} • ${packageItem.price}",

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
                text = "Đang bán",

                color =
                    AdminColors.TextSecondary,

                fontSize = 7.sp
            )
        }


        /*
         * Arrow
         */

        Text(
            text = "›",

            color =
                AdminColors.TextPrimary,

            fontSize = 20.sp
        )
    }
}