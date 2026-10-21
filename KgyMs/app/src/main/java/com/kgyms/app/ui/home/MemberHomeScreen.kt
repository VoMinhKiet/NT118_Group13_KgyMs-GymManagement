package com.kgyms.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kgyms.app.ui.profile.DarkProfilePalette
import com.kgyms.app.ui.profile.ProfileAppearance
import com.kgyms.app.ui.theme.KgyMsTheme

@Composable
fun MemberHomeScreen(
    onAction: (MemberHomeAction) -> Unit,
    modifier: Modifier = Modifier,
    data: MemberHomeUiData? = null,
) {
    val home = data ?: remember { createMockMemberHome() }
    var previewKey by rememberSaveable { mutableStateOf<String?>(null) }

    // Scoped to Home: no change to the team's shared theme or Profile preferences.
    ProfileAppearance(dark = true, language = "en") {
        Box(modifier.fillMaxSize().background(DarkProfilePalette.background), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()
                    .safeDrawingPadding().testTag("member_screen_home"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "header") {
                    HomeHeader(home, { onAction(MemberHomeAction.Profile) }, { previewKey = "notifications" })
                }
                item(key = "goal") { WeeklyGoalCard(home.weeklyGoal) }
                item(key = "actions") { HomeQuickActions(onAction, { previewKey = "community" }) }
                item(key = "workouts") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionHeading("Recommended for You", "home_workouts_all") { onAction(MemberHomeAction.Workouts) }
                        home.workouts.forEach { workout ->
                            WorkoutRecommendationCard(workout) { previewKey = "workout:${workout.id}" }
                        }
                    }
                }
                item(key = "meals") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionHeading("Today's Meal Plan", "home_meals_all") { onAction(MemberHomeAction.Nutrition) }
                        home.meals.forEach { meal ->
                            MealRecommendationCard(meal) { previewKey = "meal:${meal.id}" }
                        }
                    }
                }
                item(key = "membership") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("My Membership", color = DarkProfilePalette.text, style = MaterialTheme.typography.titleLarge)
                        HomeMembershipCard(home.membership) { previewKey = "membership" }
                    }
                }
                item(key = "activity") { HomeWeeklyActivity(home.activity) }
            }
        }
        previewKey?.let { key ->
            HomePreviewDialog(key, home, onDismiss = { previewKey = null }, onAction = { action ->
                previewKey = null
                onAction(action)
            })
        }
    }
}

/** Short sample summaries only; full Workout/Meal screens belong to later stages. */
@Composable
private fun HomePreviewDialog(
    key: String,
    home: MemberHomeUiData,
    onDismiss: () -> Unit,
    onAction: (MemberHomeAction) -> Unit,
) {
    val workout = home.workouts.firstOrNull { key == "workout:${it.id}" }
    val meal = home.meals.firstOrNull { key == "meal:${it.id}" }
    val title = when {
        workout != null -> workout.name
        meal != null -> meal.name
        key == "notifications" -> "Notifications"
        key == "membership" -> "My Membership"
        else -> "Community"
    }
    AlertDialog(
        modifier = Modifier.testTag("home_preview"),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("PREVIEW • SAMPLE DATA", style = MaterialTheme.typography.labelSmall,
                    color = DarkProfilePalette.accent)
                when {
                    workout != null -> {
                        Text("${workout.minutes} min • ${workout.level} • ${workout.category}")
                        Text("Explore this recommended session in your training plan. The full workout experience is coming soon.")
                    }
                    meal != null -> {
                        Text("${meal.mealType} • ${meal.calories} kcal • ${meal.proteinGrams} g protein")
                        Text("A balanced meal idea for your day. Ingredients and the complete meal plan are coming soon.")
                    }
                    key == "notifications" -> {
                        home.notifications.forEach { notice ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(notice.title, style = MaterialTheme.typography.titleSmall)
                                Text(notice.message, style = MaterialTheme.typography.bodyMedium,
                                    color = DarkProfilePalette.muted)
                            }
                            HorizontalDivider(color = DarkProfilePalette.border)
                        }
                        Text("These are sample notices, not live notifications.", style = MaterialTheme.typography.bodySmall)
                    }
                    key == "membership" -> {
                        val member = home.membership
                        Text("${home.memberName}\n\n${member.packageName}\nStatus: ${member.status}\nExpiry: ${member.expiresOn}\n${member.daysRemaining} days remaining")
                        Text("Illustrative membership details only. No renewal or payment will be processed.")
                    }
                    else -> Text("Connect with other members, share your progress and discover challenges. Community is coming soon.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("home_preview_close")) { Text("Close") }
        },
        dismissButton = {
            if (workout != null || meal != null) {
                TextButton(onClick = { onAction(if (meal != null) MemberHomeAction.Nutrition else MemberHomeAction.Workouts) },
                    modifier = Modifier.testTag("home_preview_plan")) { Text("Open Plan") }
            }
        },
    )
}

@Preview(name = "Home • phone", widthDp = 390, heightDp = 844, showBackground = true)
@Preview(name = "Home • compact", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "Home • large text", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun MemberHomePreview() {
    KgyMsTheme(darkTheme = true, dynamicColor = false) { MemberHomeScreen(onAction = {}) }
}
