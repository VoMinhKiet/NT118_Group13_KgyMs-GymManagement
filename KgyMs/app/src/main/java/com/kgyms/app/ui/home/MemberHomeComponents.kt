package com.kgyms.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.R
import com.kgyms.app.ui.profile.LocalProfilePalette
import com.kgyms.app.ui.profile.ProfilePalette
import com.kgyms.app.ui.profile.profileText as tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kgyms.app.ui.profile.ProfileAvatar

private val homePalette: ProfilePalette
    @Composable get() = LocalProfilePalette.current
private val homeShape = RoundedCornerShape(24.dp)

@Composable
internal fun HomeHeader(home: MemberHomeUiData, onProfile: () -> Unit, onNotifications: () -> Unit) {
    val profileLabel = tr("Mở hồ sơ", "Open Profile")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(home.greeting, color = homePalette.accent, style = MaterialTheme.typography.titleSmall)
                Text(home.memberName, fontSize = 23.sp, lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold, color = homePalette.text)
            }
            IconButton(onClick = onNotifications, modifier = Modifier.size(48.dp).testTag("home_notifications")) {
                Box {
                    Icon(painterResource(R.drawable.home_icon_notification), tr("Thông báo", "Notifications"), tint = homePalette.text,
                        modifier = Modifier.size(24.dp))
                    if (home.notifications.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).size(6.dp)
                        .background(homePalette.accent, CircleShape))
                }
            }
            Surface(onClick = onProfile, shape = CircleShape, color = homePalette.card,
                contentColor = homePalette.text,
                modifier = Modifier.size(48.dp).testTag("home_avatar")
                    .semantics { contentDescription = profileLabel }) {
                ProfileAvatar(home.avatarUri, home.initials, Modifier.fillMaxSize())
            }
        }
        Text(home.motivation, color = homePalette.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun WeeklyGoalCard(goal: HomeWeeklyGoal) {
    Surface(shape = homeShape, color = homePalette.card, border = BorderStroke(1.dp, homePalette.border)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(homePalette.tint, homePalette.card)))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("MỤC TIÊU TUẦN", "YOUR WEEKLY GOAL"), fontSize = 10.sp, letterSpacing = 1.sp, color = homePalette.accent)
                Text(tr("Tiếp tục nhé!", "Keep Going!"), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = homePalette.text)
                Text(if (goal.remaining == 0) tr("Bạn đã đạt mục tiêu tuần!", "You've reached your weekly goal!") else
                    tr("Bạn còn ${goal.remaining} buổi để đạt mục tiêu tuần.", "You're ${goal.remaining} workouts away from your weekly goal."),
                    style = MaterialTheme.typography.bodySmall, color = homePalette.muted)
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                Text("${dateFormat.format(Date(goal.weekStartMillis))} – ${dateFormat.format(Date(goal.weekEndMillis))}",
                    color = homePalette.muted, fontSize = 10.sp)
                Text(tr("${goal.percentage}% mục tiêu tuần", "${goal.percentage}% of weekly target"), color = homePalette.accent, fontSize = 10.sp)
                Text(tr("Dữ liệu mẫu đã xác nhận", "Confirmed sample records"), color = homePalette.muted, fontSize = 10.sp)
            }
            Box(Modifier.size(86.dp).testTag("home_weekly_goal"), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxSize(),
                    color = homePalette.accent, trackColor = homePalette.border, strokeWidth = 7.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${goal.completed}/${goal.target}", color = homePalette.text,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(tr("buổi tập", "workouts"), color = homePalette.muted, fontSize = 10.sp)
                }
            }
        }
    }
}

private data class HomeShortcut(val id: String, val label: String, val icon: Int, val action: MemberHomeAction?)
@Composable
internal fun HomeQuickActions(onAction: (MemberHomeAction) -> Unit, onPT: () -> Unit) {
    val shortcuts = listOf(
        HomeShortcut("workouts", tr("Tập luyện", "Workouts"), R.drawable.home_icon_workout, MemberHomeAction.Workouts),
        HomeShortcut("nutrition", tr("Dinh dưỡng", "Nutrition"), R.drawable.home_icon_nutrition, MemberHomeAction.Nutrition),
        HomeShortcut("progress", tr("Tiến độ", "Progress"), R.drawable.home_icon_progress, MemberHomeAction.Progress),
        HomeShortcut("pt", tr("HLV của tôi", "My PT"), R.drawable.home_icon_pt, null),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shortcuts.forEach { shortcut ->
            Surface(
                onClick = { shortcut.action?.let(onAction) ?: onPT() },
                modifier = Modifier.weight(1f).testTag("home_action_${shortcut.id}"),
                color = Color.Transparent, shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(54.dp).background(homePalette.card, RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(shortcut.icon), null, tint = homePalette.accent, modifier = Modifier.size(25.dp))
                    }
                    Text(shortcut.label, color = homePalette.text, fontSize = 11.sp, lineHeight = 15.sp,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun HomeSectionHeading(title: String, tag: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.weight(1f), color = homePalette.text, fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold, lineHeight = 25.sp)
        TextButton(onClick = onSeeAll, modifier = Modifier.testTag(tag),
            contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(tr("Xem tất cả", "See All"), color = homePalette.accent, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun WorkoutRecommendationCard(workout: HomeWorkout, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = homeShape, color = homePalette.card,
        modifier = Modifier.fillMaxWidth().testTag("home_workout_${workout.id}")) {
        Box {
            Image(painterResource(workout.imageRes), null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = 0.06f), Color(0xFF080B12).copy(alpha = 0.96f)))))
            Column(Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                    Text(workout.category.uppercase(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(76.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(workout.name, color = Color.White, fontSize = 21.sp, lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold)
                        Text("${workout.minutes} ${tr("phút", "min")}  •  ${workout.level}", color = Color(0xFFD2D4DE), fontSize = 12.sp)
                    }
                    Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(
                        homePalette.accent, homePalette.accent.copy(alpha = 0.8f))), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.home_icon_play), null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MealRecommendationCard(meal: HomeMeal, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = homeShape, color = homePalette.card,
        border = BorderStroke(1.dp, homePalette.border),
        modifier = Modifier.fillMaxWidth().testTag("home_meal_${meal.id}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(meal.imageRes), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(82.dp).clip(RoundedCornerShape(17.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${meal.mealType.uppercase()} • ${tr("GIÀU ĐẠM", "HIGH PROTEIN")}", color = homePalette.accent, fontSize = 10.sp)
                Text(meal.name, color = homePalette.text, style = MaterialTheme.typography.titleMedium)
                Text("${meal.calories} kcal • ${meal.proteinGrams} g ${tr("đạm", "protein")}", color = homePalette.muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun HomeMembershipCard(member: HomeMembership, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = homeShape, color = homePalette.card,
        border = BorderStroke(1.dp, homePalette.accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag("home_membership")) {
        Column(Modifier.background(Brush.linearGradient(listOf(homePalette.tint, homePalette.card))).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("KGYMS • THẺ HỘI VIÊN", "KGYMS • MEMBER ACCESS"), color = homePalette.muted, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(member.packageName, color = homePalette.text, style = MaterialTheme.typography.titleMedium)
                }
                Surface(color = homePalette.tint, shape = RoundedCornerShape(10.dp)) {
                    Text(member.status, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = homePalette.accent, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = homePalette.border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(tr("NGÀY HẾT HẠN", "EXPIRY DATE"), color = homePalette.muted, fontSize = 10.sp)
                    Text(member.expiresOn, color = homePalette.text, style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(tr("THỜI HẠN CÒN LẠI", "DAYS REMAINING"), color = homePalette.muted, fontSize = 10.sp)
                    Text("${member.daysRemaining} ${tr("ngày", "days")}  ›", color = homePalette.accent,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun HomeWeeklyActivity(days: List<HomeActivityDay>) {
    Surface(shape = homeShape, color = homePalette.card, modifier = Modifier.testTag("home_weekly_activity")) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("Nhịp tập tuần này", "This week's rhythm"), color = homePalette.text, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                days.forEachIndexed { index, day ->
                    val dayDescription = tr("Ngày mẫu ${index + 1}: ${if (day.completed) "đã tập" else "nghỉ"}",
                        "Sample day ${index + 1}: ${if (day.completed) "workout completed" else "rest day"}")
                    Column(Modifier.weight(1f).semantics { contentDescription = dayDescription }, horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(28.dp).background(if (day.completed) homePalette.accent else homePalette.tint, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text(if (day.completed) "✓" else "·", color = Color.White, fontSize = 13.sp)
                        }
                        Text(day.label, color = homePalette.muted, fontSize = 11.sp)
                    }
                }
            }
            Text(tr("Từng bước nhỏ. Thói quen khỏe hơn.", "Small steps. Stronger habits."), color = homePalette.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
