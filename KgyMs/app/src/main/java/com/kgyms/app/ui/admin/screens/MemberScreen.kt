package com.kgyms.app.ui.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.admin.AdminColors
import com.kgyms.app.ui.admin.AdminRoute

@Composable
fun MemberScreen(
    onNavigate: (AdminRoute) -> Unit,
    onMemberClick: (String) -> Unit
) {

    /*
     * Danh sách hội viên demo
     *
     * Sau này dữ liệu này sẽ lấy từ
     * Firebase Cloud Firestore.
     */
    val members = listOf(
        MemberItem(
            name = "Nguyễn Minh Anh",
            description = "Premium • Hoạt động"
        ),
        MemberItem(
            name = "Trần Gia Huy",
            description = "Basic • Còn 12 ngày"
        ),
        MemberItem(
            name = "Lê Phương Thảo",
            description = "Premium • Hoạt động"
        ),
        MemberItem(
            name = "Vũ Hoàng Nam",
            description = "Basic • Còn 12 ngày"
        ),
        MemberItem(
            name = "Mai Ngọc Linh",
            description = "Premium • Hoạt động"
        )
    )


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminColors.Background)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {

            /*
             * ============================
             * SEARCH
             * ============================
             */

            Text(
                text = "Tìm kiếm",
                color = AdminColors.TextSecondary,
                fontSize = 9.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AdminColors.Surface)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {

                Text(
                    text = "Tên, SĐT, mã hội viên",
                    color = AdminColors.TextSecondary,
                    fontSize = 9.sp
                )
            }


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            /*
             * ============================
             * FILTER
             * ============================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                modifier = Modifier.height(8.dp)
            )


            /*
             * ============================
             * MEMBER LIST
             * ============================
             */

            members.forEach { member ->

                MemberCard(
                    member = member,
                    onClick = {
                        onMemberClick(member.name)
                    }
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )
            }


            /*
             * ============================
             * ADD MEMBER
             * ============================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AdminColors.Primary)
                    .clickable {
                        // TODO:
                        // Mở màn hình thêm hội viên
                    },
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "+  Thêm hội viên",
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
 * MEMBER DATA
 * ============================
 */

data class MemberItem(
    val name: String,
    val description: String
)


/*
 * ============================
 * MEMBER CARD
 * ============================
 */

@Composable
private fun MemberCard(
    member: MemberItem,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AdminColors.Surface)
            .clickable {
                onClick()
            }
            .padding(horizontal = 8.dp),

        verticalAlignment = Alignment.CenterVertically
    ) {

        /*
         * Avatar placeholder
         */
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AdminColors.SurfaceLight)
        )


        Spacer(
            modifier = Modifier.width(10.dp)
        )


        /*
         * Tên + trạng thái
         */
        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = member.name,
                color = AdminColors.TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = member.description,
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
            fontSize = 18.sp,
            fontWeight = FontWeight.Light
        )
    }
}


/*
 * ============================
 * FILTER BUTTON
 * ============================
 */

@Composable
private fun MemberFilter(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    AdminColors.Primary
                } else {
                    AdminColors.Surface
                }
            ),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = text,
            color = AdminColors.TextPrimary,
            fontSize = 7.sp,
            fontWeight = if (selected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            },
            textAlign = TextAlign.Center
        )
    }
}