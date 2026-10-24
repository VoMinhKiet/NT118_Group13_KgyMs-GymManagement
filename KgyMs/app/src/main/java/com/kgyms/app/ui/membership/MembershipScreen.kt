package com.kgyms.app.ui.membership

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.R

data class MembershipCardInfo(
    val title: String,
    val subtitle: String,
    val price: String,
    val perks: List<String>,
    val imageResId: Int,
    val gradientColors: List<Color>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MembershipScreen(
    onNavigateBack: () -> Unit = {}
) {
    val cards = listOf(
        MembershipCardInfo(
            title = "Thẻ Basic (Hội viên thường)",
            subtitle = "Phù hợp cho người mới bắt đầu tập luyện cơ bản",
            price = "300.000 đ / tháng",
            perks = listOf("Sử dụng trang thiết bị cơ bản", "Thời gian tự do từ 6h - 22h", "Miễn phí gửi xe"),
            imageResId = R.drawable.card_1,
            gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1E40AF))
        ),
        MembershipCardInfo(
            title = "Thẻ Silver (Hội viên Bạc)",
            subtitle = "Tập luyện nâng cao kèm các lớp Group X",
            price = "600.000 đ / tháng",
            perks = listOf("Tất cả quyền lợi gói Basic", "Tham gia mọi lớp Yoga, Zumba, Aerobic", "Tặng 1 buổi tập với PT"),
            imageResId = R.drawable.card_2,
            gradientColors = listOf(Color(0xFF64748B), Color(0xFF334155))
        ),
        MembershipCardInfo(
            title = "Thẻ Gold (Hội viên V.I.P)",
            subtitle = "Trải nghiệm dịch vụ thượng đỉnh 5 sao",
            price = "1.200.000 đ / tháng",
            perks = listOf("Tất cả quyền lợi gói Silver", "Không giới hạn khung giờ & dịch vụ xông hơi", "PT kèm riêng 3 buổi/tháng", "Khăn tập & nước uống miễn phí"),
            imageResId = R.drawable.card_3,
            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFB45309))
        )
    )

    val pagerState = rememberPagerState(pageCount = { cards.size })
    val currentCard = cards[pagerState.currentPage]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quản lý thẻ và hội viên",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Card Pager Carousel (Vuốt trượt qua lại giữa 3 thẻ)
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
            val card = cards[page]
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = card.imageResId),
                    contentDescription = card.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Indicator Dots (Dấu chấm chỉ báo trang thẻ)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(cards.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (isSelected) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Animated Card Details Section (Hiệu ứng animation trượt chữ khi đổi thẻ)
        AnimatedContent(
            targetState = currentCard,
            transitionSpec = {
                (fadeIn(animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                        slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 3 }).togetherWith(
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                            slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 3 }
                )
            },
            label = "CardDetailsTransition"
        ) { card ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = card.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Đặc quyền hội viên:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                card.perks.forEach { perk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = perk,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // Action List (Menu danh sách giống ảnh mẫu của bạn)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            MembershipActionItem(
                icon = Icons.Default.Star,
                title = "Đặc quyền & Quyền lợi gói tập",
                onClick = {}
            )
            MembershipActionItem(
                icon = Icons.Default.Info,
                title = "Tuỳ chỉnh thông tin thẻ / hội viên",
                onClick = {}
            )
            MembershipActionItem(
                icon = Icons.Default.List,
                title = "Lịch sử tập luyện & Giao dịch",
                onClick = {}
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MembershipActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}
