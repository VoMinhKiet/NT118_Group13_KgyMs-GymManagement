package com.kgyms.app.ui.home

import com.kgyms.app.R
import com.kgyms.app.ui.profile.MockMemberProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class HomeWeeklyGoal(val completed: Int, val target: Int) {
    val remaining: Int get() = (target - completed).coerceAtLeast(0)
    val progress: Float get() = if (target <= 0) 0f else
        (completed.toFloat() / target).coerceIn(0f, 1f)
}

data class HomeWorkout(
    val id: String, val name: String, val minutes: Int, val level: String,
    val category: String, val imageRes: Int,
)

data class HomeMeal(
    val id: String, val name: String, val calories: Int, val proteinGrams: Int,
    val mealType: String, val imageRes: Int,
)

data class HomeMembership(
    val packageName: String, val status: String, val expiresOn: String, val daysRemaining: Int,
)

data class HomeNotification(val title: String, val message: String)
data class HomeActivityDay(val label: String, val completed: Boolean)

/** UI boundary for a future member state source; no Auth or repository dependency. */
data class MemberHomeUiData(
    val memberName: String, val initials: String, val avatarUri: String? = null,
    val greeting: String, val motivation: String,
    val weeklyGoal: HomeWeeklyGoal,
    val workouts: List<HomeWorkout>, val meals: List<HomeMeal>,
    val membership: HomeMembership,
    val notifications: List<HomeNotification>, val activity: List<HomeActivityDay>,
)

enum class MemberHomeAction { Profile, Workouts, Nutrition, Progress }

/** Generated once per Home composition. Dates are illustrative, not membership records. */
fun createMockMemberHome(): MemberHomeUiData {
    val expiry = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 28) }
    return MemberHomeUiData(
        memberName = MockMemberProfile.name,
        initials = MockMemberProfile.initials,
        greeting = "Good Morning",
        motivation = "A little stronger, every day.",
        weeklyGoal = HomeWeeklyGoal(completed = 3, target = 6),
        workouts = listOf(HomeWorkout(
            "upper-body", "Upper Body Strength", 45, "Intermediate", "Strength",
            R.drawable.home_workout_upper_body,
        )),
        meals = listOf(HomeMeal(
            "chicken-bowl", "Grilled Chicken Bowl", 520, 42, "Lunch",
            R.drawable.home_meal_chicken_bowl,
        )),
        membership = HomeMembership(
            "Premium Membership", "Active",
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(expiry.time), 28,
        ),
        notifications = listOf(
            HomeNotification("Your next workout is ready", "Discover today's Upper Body Strength recommendation."),
            HomeNotification("Keep your momentum", "Three more workouts to reach your weekly goal."),
            HomeNotification("Membership reminder", "Your sample Premium membership has 28 days remaining."),
        ),
        activity = listOf(
            HomeActivityDay("M", true), HomeActivityDay("T", false),
            HomeActivityDay("W", true), HomeActivityDay("T", false),
            HomeActivityDay("F", true), HomeActivityDay("S", false), HomeActivityDay("S", false),
        ),
    )
}
