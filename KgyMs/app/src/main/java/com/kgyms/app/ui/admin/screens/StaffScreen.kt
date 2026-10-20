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
fun StaffScreen(
    onNavigate: (AdminRoute) -> Unit,
    onStaffClick: (String) -> Unit
) {
    val showMockAction = LocalAdminFeedback.current

    /*
     * ============================
     * DANH SÁCH NHÂN SỰ DEMO
     * ============================
     *
     * Sau này sẽ lấy dữ liệu
     * từ Firebase Cloud Firestore.
     */
    val staffList = listOf(

        StaffItem(
            name = "Trần Hoàng",
            role = "PT",
            status = "Đang hoạt động"
        ),

        StaffItem(
            name = "Mai Anh",
            role = "PT",
            status = "Đang hoạt động"
        ),

        StaffItem(
            name = "Quốc Bảo",
            role = "Lễ tân",
            status = "Đang hoạt động"
        ),

        StaffItem(
            name = "Nguyễn Lan",
            role = "CSKH",
            status = "Đang hoạt động"
        ),

        StaffItem(
            name = "Lê Minh",
            role = "Quản lý",
            status = "Đang hoạt động"
        )
    )


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminColors.Background)
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
             * THỐNG KÊ NHÂN SỰ
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                StaffStatCard(
                    title = "Nhân sự",
                    value = "24",
                    accentColor = AdminColors.Cyan,
                    modifier = Modifier.weight(1f)
                )

                StaffStatCard(
                    title = "PT",
                    value = "12",
                    accentColor = AdminColors.PrimaryLight,
                    modifier = Modifier.weight(1f)
                )
            }


            Spacer(
                modifier = Modifier.height(10.dp)
            )


            /*
             * ============================
             * DANH SÁCH NHÂN SỰ
             * ============================
             */

            staffList.forEach { staff ->

                StaffCard(
                    staff = staff,
                    onClick = {
                        onStaffClick(staff.name)
                    }
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }


            /*
             * ============================
             * THÊM NHÂN SỰ
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
                        showMockAction("Thêm nhân sự")
                    },
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "+  Thêm nhân sự",
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
 * STAFF DATA
 * ============================
 */

data class StaffItem(
    val name: String,
    val role: String,
    val status: String
)


/*
 * ============================
 * STAT CARD
 * ============================
 */

@Composable
private fun StaffStatCard(
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
 * STAFF CARD
 * ============================
 */

@Composable
private fun StaffCard(
    staff: StaffItem,
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
         * Avatar placeholder
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
         * Tên + chức vụ + trạng thái
         */
        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = "${staff.name} • ${staff.role}",
                color = AdminColors.TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )


            Spacer(
                modifier = Modifier.height(2.dp)
            )


            Text(
                text = staff.status,
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