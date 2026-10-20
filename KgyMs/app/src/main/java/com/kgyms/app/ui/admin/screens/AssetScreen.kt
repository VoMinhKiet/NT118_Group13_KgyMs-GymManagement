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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors
import com.kgyms.app.ui.admin.LocalAdminFeedback
import com.kgyms.app.ui.admin.AdminRoute

@Composable
fun AssetScreen(
    onNavigate: (AdminRoute) -> Unit,
    onAssetClick: (String) -> Unit
) {
    val showMockAction = LocalAdminFeedback.current

    /*
     * ============================
     * DANH SÁCH TÀI SẢN DEMO
     * ============================
     *
     * Sau này sẽ lấy dữ liệu
     * từ Firebase Cloud Firestore.
     */
    val assetList = listOf(

        AssetItem(
            name = "Máy chạy bộ",
            quantity = "18",
            description = "Tình trạng tốt"
        ),

        AssetItem(
            name = "Máy Smith",
            quantity = "8",
            description = "2 thiết bị cần bảo trì"
        ),

        AssetItem(
            name = "Dumbbell Set",
            quantity = "24",
            description = "Tình trạng tốt"
        ),

        AssetItem(
            name = "Xe đạp",
            quantity = "12",
            description = "Tình trạng tốt"
        ),

        AssetItem(
            name = "Máy kéo xô",
            quantity = "10",
            description = "Tình trạng tốt"
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
             * THỐNG KÊ TÀI SẢN
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                AssetStatCard(
                    title = "Thiết bị",
                    value = "186",
                    accentColor = AdminColors.Cyan,
                    modifier = Modifier.weight(1f)
                )

                AssetStatCard(
                    title = "Bảo trì",
                    value = "7",
                    accentColor = AdminColors.Pink,
                    modifier = Modifier.weight(1f)
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * DANH SÁCH TÀI SẢN
             * ============================
             */

            assetList.forEach { asset ->

                AssetCard(
                    asset = asset,
                    onClick = {
                        onAssetClick(asset.name)
                    }
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }


            /*
             * ============================
             * THÊM TÀI SẢN
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(
                        RoundedCornerShape(10.dp)
                    )
                    .background(
                        AdminColors.Primary
                    )
                    .clickable {
                        showMockAction("Thêm tài sản")
                    },
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "+  Thêm tài sản",
                    color = AdminColors.TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


/*
 * ============================
 * ASSET DATA
 * ============================
 */

data class AssetItem(
    val name: String,
    val quantity: String,
    val description: String
)


/*
 * ============================
 * STAT CARD
 * ============================
 */

@Composable
private fun AssetStatCard(
    title: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .height(70.dp)
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(
                AdminColors.Surface
            )
            .padding(
                horizontal = 12.dp,
                vertical = 9.dp
            )
    ) {

        Text(
            text = title,
            color = AdminColors.TextSecondary,
            fontSize = 9.sp
        )


        Spacer(
            modifier = Modifier.height(2.dp)
        )


        Text(
            text = value,
            color = AdminColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )


        Spacer(
            modifier = Modifier.height(4.dp)
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
 * ============================
 * ASSET CARD
 * ============================
 */

@Composable
private fun AssetCard(
    asset: AssetItem,
    onClick: () -> Unit
) {

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
                onClick()
            }
            .padding(
                horizontal = 8.dp
            ),

        verticalAlignment = Alignment.CenterVertically
    ) {

        /*
         * Icon / image placeholder
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
         * Tên + số lượng + tình trạng
         */
        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = "${asset.name} • ${asset.quantity}",
                color = AdminColors.TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )


            Spacer(
                modifier = Modifier.height(2.dp)
            )


            Text(
                text = asset.description,
                color = AdminColors.TextSecondary,
                fontSize = 7.sp
            )
        }


        /*
         * Arrow
         */
        Text(
            text = "›",
            color = AdminColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light
        )
    }
}