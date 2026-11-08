package com.kgyms.app.ui.member.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kgyms.app.R
import com.kgyms.app.ui.member.LocalMemberAvatar
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.ProfileAvatar
import com.kgyms.app.ui.member.profile.profileText as tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.kgyms.app.data.model.HomeActivityDay
import com.kgyms.app.data.model.HomeMeal
import com.kgyms.app.data.model.HomeMembership
import com.kgyms.app.data.model.HomeWeeklyGoal
import com.kgyms.app.data.model.HomeWorkout
import com.kgyms.app.data.model.MemberHomeUiData

private val homeShape = RoundedCornerShape(24.dp)

@Composable
internal fun HomeHeader(home: MemberHomeUiData, onProfile: () -> Unit, onNotifications: () -> Unit) {
    val p = LocalProfilePalette.current
    val avatar = LocalMemberAvatar.current
    val profileLabel = tr("Mở hồ sơ", "Open Profile")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(home.greeting, color = p.muted, style = MaterialTheme.typography.bodyMedium)
                Text(avatar?.name ?: home.memberName, color = p.text, style = MaterialTheme.typography.titleLarge)
            }
            IconButton(onClick = onNotifications, modifier = Modifier.size(48.dp).testTag("home_notifications")) {
                Box {
                    Icon(painterResource(R.drawable.home_icon_notification), tr("Thông báo", "Notifications"),
                        tint = p.text, modifier = Modifier.size(22.dp))
                    if (home.notifications.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).size(6.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape))
                }
            }
            Surface(onClick = onProfile, shape = CircleShape, color = p.card, contentColor = p.text,
                modifier = Modifier.size(48.dp).testTag("home_avatar")
                    .semantics { contentDescription = profileLabel }) {
                // Milestone 1 renderer and shared bitmap are deliberately unchanged.
                ProfileAvatar(home.avatarUri, home.initials, Modifier.fillMaxSize())
            }
        }
        // Keep the header's vertical footprint after removing the sample-data label.
        Spacer(Modifier.height(with(LocalDensity.current) {
            MaterialTheme.typography.labelSmall.lineHeight.toDp()
        }))
    }
}

@Composable
internal fun WeeklyGoalCard(goal: HomeWeeklyGoal, days: List<HomeActivityDay> = emptyList()) {
    val p = LocalProfilePalette.current
    val language = LocalProfileLanguage.current
    val dates = remember(goal.weekStartMillis, goal.weekEndMillis, language) {
        val format = SimpleDateFormat("dd/MM", if (language == "vi") Locale.forLanguageTag("vi-VN") else Locale.ENGLISH)
        "${format.format(Date(goal.weekStartMillis))} – ${format.format(Date(goal.weekEndMillis))}"
    }
    val goalDescription = tr("${goal.completed} trên ${goal.target} buổi hoàn thành tuần này",
        "${goal.completed} of ${goal.target} workouts completed this week")
    Surface(shape = homeShape, color = p.card, border = BorderStroke(1.dp, p.border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Mục tiêu tuần", "Weekly goal"), Modifier.weight(1f), color = p.text,
                    style = MaterialTheme.typography.titleMedium)
                Text(dates, color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).semantics { contentDescription = goalDescription }) {
                    Text("${goal.completed} / ${goal.target}", color = p.text, style = MaterialTheme.typography.headlineSmall)
                    Text(tr("buổi hoàn thành", "workouts completed"), color = p.muted, style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(56.dp).testTag("home_weekly_goal"), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.secondary, trackColor = p.border, strokeWidth = 5.dp)
                    Text("${(goal.progress * 100).roundToInt()}%", color = p.text, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (days.isNotEmpty()) {
                HorizontalDivider(color = p.border)
                HomeWeeklyActivity(days)
            }
        }
    }
}

private data class HomeShortcut(val id: String, val label: String, val icon: Int, val action: MemberHomeAction?)

@Composable
internal fun HomeQuickActions(onAction: (MemberHomeAction) -> Unit, onPT: () -> Unit) {
    val p = LocalProfilePalette.current
    val colors = MaterialTheme.colorScheme
    val shortcuts = listOf(
        HomeShortcut("workouts", tr("Tập luyện", "Workouts"), R.drawable.home_icon_workout, MemberHomeAction.Workouts),
        HomeShortcut("nutrition", tr("Dinh dưỡng", "Nutrition"), R.drawable.home_icon_nutrition, MemberHomeAction.Nutrition),
        HomeShortcut("progress", tr("Tiến độ", "Progress"), R.drawable.home_icon_progress, MemberHomeAction.Progress),
        HomeShortcut("pt", tr("HLV của tôi", "My PT"), R.drawable.home_icon_pt, null),
    )
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shortcuts.forEachIndexed { index, shortcut ->
            val tint = when (index) { 0 -> colors.primary; 1 -> colors.tertiary; else -> colors.secondary }
            Surface(onClick = { shortcut.action?.let(onAction) ?: onPT() },
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("home_action_${shortcut.id}"),
                color = p.card, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.heightIn(min = 92.dp).padding(horizontal = 3.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(shortcut.icon), null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(shortcut.label, color = p.text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun HomeSectionHeading(title: String, tag: String, onSeeAll: () -> Unit) {
    val p = LocalProfilePalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
            contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(tr("Xem tất cả", "See all"), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun WorkoutRecommendationCard(workout: HomeWorkout, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, shape = homeShape, color = p.card,
        border = BorderStroke(1.dp, p.border),
        modifier = Modifier.fillMaxWidth().testTag("home_workout_${workout.id}")) {
        Column {
            Box(Modifier.fillMaxWidth().height(164.dp)) {
                Image(painterResource(workout.imageRes), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(), alignment = Alignment.Center)
                Surface(modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = Color(0xDD111723), shape = RoundedCornerShape(10.dp)) {
                    Text(workout.category, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(workout.name, color = p.text, style = MaterialTheme.typography.titleMedium)
                    Text("${workout.minutes} ${tr("phút", "min")} · ${workout.level}", color = p.muted,
                        style = MaterialTheme.typography.bodySmall)
                    Text(tr("Xem buổi tập", "View workout"), color = p.accent, style = MaterialTheme.typography.labelLarge)
                }
                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.home_icon_arrow), null,
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MealRecommendationCard(meal: HomeMeal, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = p.card,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().testTag("home_meal_${meal.id}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(meal.imageRes), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(meal.mealType, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                Text(meal.name, color = p.text, style = MaterialTheme.typography.titleSmall)
                Text("${meal.calories} kcal · ${meal.proteinGrams} g ${tr("đạm", "protein")}",
                    color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun HomeMembershipCard(member: HomeMembership, onClick: () -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = p.card,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().testTag("home_membership")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr("THẺ HỘI VIÊN", "MEMBERSHIP"), color = p.muted, style = MaterialTheme.typography.labelSmall)
                    Text(member.packageName, color = p.text, style = MaterialTheme.typography.titleMedium)
                }
                Icon(painterResource(R.drawable.home_icon_arrow), null, tint = p.muted, modifier = Modifier.size(20.dp))
            }
            Text(if (member.available) "${member.status} · ${member.daysRemaining} ${tr("ngày còn lại", "days left")}" else member.status,
                color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun HomeWeeklyActivity(days: List<HomeActivityDay>) {
    val p = LocalProfilePalette.current
    val rhythmLabel = tr("Nhịp tập tuần này", "This week's rhythm")
    Row(Modifier.fillMaxWidth().testTag("home_weekly_activity").semantics { contentDescription = rhythmLabel },
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        days.forEachIndexed { index, day ->
            val description = tr("Ngày mẫu ${index + 1}: ${if (day.completed) "đã tập" else "nghỉ"}",
                "Sample day ${index + 1}: ${if (day.completed) "workout completed" else "rest day"}")
            Column(Modifier.weight(1f).semantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = if (day.completed) MaterialTheme.colorScheme.tertiaryContainer else p.background,
                    modifier = Modifier.size(26.dp), border = if (day.completed) null else BorderStroke(1.dp, p.border)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (day.completed) "✓" else "·", style = MaterialTheme.typography.labelMedium,
                            color = if (day.completed) MaterialTheme.colorScheme.onTertiaryContainer else p.muted)
                    }
                }
                Text(day.label, color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
