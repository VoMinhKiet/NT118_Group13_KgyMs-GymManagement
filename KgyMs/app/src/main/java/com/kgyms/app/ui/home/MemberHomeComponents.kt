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
import com.kgyms.app.ui.profile.DarkProfilePalette
import com.kgyms.app.ui.profile.ProfileAvatar

private val homePalette = DarkProfilePalette
private val homeShape = RoundedCornerShape(24.dp)

@Composable
internal fun HomeHeader(home: MemberHomeUiData, onProfile: () -> Unit, onNotifications: () -> Unit) {
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
                    Icon(painterResource(R.drawable.home_icon_notification), "Notifications", tint = homePalette.text,
                        modifier = Modifier.size(24.dp))
                    if (home.notifications.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).size(6.dp)
                        .background(homePalette.accent, CircleShape))
                }
            }
            Surface(onClick = onProfile, shape = CircleShape, color = homePalette.card,
                contentColor = homePalette.text,
                modifier = Modifier.size(48.dp).testTag("home_avatar")
                    .semantics { contentDescription = "Open Profile" }) {
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
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF30214E), homePalette.card)))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("YOUR WEEKLY GOAL", fontSize = 10.sp, letterSpacing = 1.sp, color = Color(0xFFCEB9F5))
                Text("Keep Going!", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = homePalette.text)
                Text(if (goal.remaining == 0) "You've reached your weekly goal!" else
                    "You're ${goal.remaining} workouts away from your weekly goal.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFD2C9E4))
            }
            Box(Modifier.size(86.dp).testTag("home_weekly_goal"), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxSize(),
                    color = homePalette.accent, trackColor = Color(0xFF393545), strokeWidth = 7.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${goal.completed}/${goal.target}", color = homePalette.text,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("workouts", color = homePalette.muted, fontSize = 10.sp)
                }
            }
        }
    }
}

private data class HomeShortcut(val label: String, val icon: Int, val tint: Color, val action: MemberHomeAction?)
private val shortcuts = listOf(
    HomeShortcut("Workouts", R.drawable.home_icon_workout, Color(0xFFB98BFF), MemberHomeAction.Workouts),
    HomeShortcut("Nutrition", R.drawable.home_icon_nutrition, Color(0xFF8CCFBC), MemberHomeAction.Nutrition),
    HomeShortcut("Progress", R.drawable.home_icon_progress, Color(0xFF92BFEF), MemberHomeAction.Progress),
    HomeShortcut("Community", R.drawable.home_icon_community, Color(0xFFC4A9E9), null),
)

@Composable
internal fun HomeQuickActions(onAction: (MemberHomeAction) -> Unit, onCommunity: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shortcuts.forEach { shortcut ->
            Surface(
                onClick = { shortcut.action?.let(onAction) ?: onCommunity() },
                modifier = Modifier.weight(1f).testTag("home_action_${shortcut.label.lowercase()}"),
                color = Color.Transparent, shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(54.dp).background(homePalette.card, RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(shortcut.icon), null, tint = shortcut.tint, modifier = Modifier.size(25.dp))
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
            Text("See All", color = homePalette.accent, fontSize = 12.sp)
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
                        Text("${workout.minutes} min  •  ${workout.level}", color = Color(0xFFD2D4DE), fontSize = 12.sp)
                    }
                    Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(
                        Color(0xFFA66CFF), Color(0xFF773CEB))), CircleShape), contentAlignment = Alignment.Center) {
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
                Text("${meal.mealType.uppercase()} • HIGH PROTEIN", color = Color(0xFFBBA3E1), fontSize = 10.sp)
                Text(meal.name, color = homePalette.text, style = MaterialTheme.typography.titleMedium)
                Text("${meal.calories} kcal • ${meal.proteinGrams} g protein", color = homePalette.muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun HomeMembershipCard(member: HomeMembership, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = homeShape, color = homePalette.card,
        border = BorderStroke(1.dp, Color(0xFF4A356B)),
        modifier = Modifier.fillMaxWidth().testTag("home_membership")) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF251C38), homePalette.card))).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("KGYMS • MEMBER ACCESS", color = homePalette.muted, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(member.packageName, color = homePalette.text, style = MaterialTheme.typography.titleMedium)
                }
                Surface(color = Color(0xFF193B36), shape = RoundedCornerShape(10.dp)) {
                    Text(member.status, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color(0xFFA4DBCB), fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = homePalette.border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("EXPIRY DATE", color = homePalette.muted, fontSize = 10.sp)
                    Text(member.expiresOn, color = homePalette.text, style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("DAYS REMAINING", color = homePalette.muted, fontSize = 10.sp)
                    Text("${member.daysRemaining} days  ›", color = homePalette.accent,
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
            Text("This week's rhythm", color = homePalette.text, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                days.forEachIndexed { index, day ->
                    Column(Modifier.weight(1f).semantics {
                        contentDescription = "Sample day ${index + 1}: ${if (day.completed) "workout completed" else "rest day"}"
                    }, horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(28.dp).background(if (day.completed) homePalette.accent else homePalette.tint, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text(if (day.completed) "✓" else "·", color = Color.White, fontSize = 13.sp)
                        }
                        Text(day.label, color = homePalette.muted, fontSize = 11.sp)
                    }
                }
            }
            Text("Small steps. Stronger habits.", color = homePalette.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
