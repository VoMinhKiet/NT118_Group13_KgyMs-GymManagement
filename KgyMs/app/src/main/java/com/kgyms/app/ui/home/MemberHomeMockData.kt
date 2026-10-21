package com.kgyms.app.ui.home

import com.kgyms.app.R
import com.kgyms.app.ui.profile.MockMemberProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
data class HomeTrainer(val name: String, val specialty: String)

/** UI boundary for a future member state source; no Auth or repository dependency. */
data class MemberHomeUiData(
    val memberName: String, val initials: String, val avatarUri: String? = null,
    val greeting: String, val motivation: String,
    val weeklyGoal: HomeWeeklyGoal,
    val workouts: List<HomeWorkout>, val meals: List<HomeMeal>,
    val membership: HomeMembership,
    val notifications: List<HomeNotification>, val activity: List<HomeActivityDay>,
    val trainer: HomeTrainer? = null,
)

enum class MemberHomeAction { Profile, Workouts, WorkoutLibrary, RecommendedWorkout, AssignedWorkout, Nutrition, Progress }

/** All records and PT/membership details below are illustrative fixtures only. */
fun createMockMemberHome(
    language: String = "vi",
    weeklyTarget: Int = 6,
    nowMillis: Long = System.currentTimeMillis(),
): MemberHomeUiData {
    fun t(vi: String, en: String) = if (language == "vi") vi else en
    val zone = TimeZone.getDefault()
    val goal = calculateWeeklyGoal(weeklyTarget, mockWorkoutRecords(nowMillis, zone), nowMillis, zone)
    val expiry = Calendar.getInstance(zone).apply { timeInMillis = nowMillis; add(Calendar.DAY_OF_YEAR, 28) }
    val locale = if (language == "vi") Locale("vi", "VN") else Locale.ENGLISH
    val days = (0..6).map { index ->
        val day = Calendar.getInstance(zone).apply { timeInMillis = goal.weekStartMillis; add(Calendar.DAY_OF_YEAR, index) }
        val end = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        HomeActivityDay(
            if (language == "vi") listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")[index]
            else listOf("M", "T", "W", "T", "F", "S", "S")[index],
            goal.completedSessions.any { it.occurredAtMillis >= day.timeInMillis && it.occurredAtMillis < end },
        )
    }
    return MemberHomeUiData(
        memberName = MockMemberProfile.name, initials = MockMemberProfile.initials,
        greeting = t("Chào bạn", "Hello there"),
        motivation = t("Mỗi ngày, một phiên bản khỏe hơn.", "A little stronger, every day."),
        weeklyGoal = goal,
        workouts = listOf(HomeWorkout("upper-body", t("Sức mạnh thân trên", "Upper Body Strength"), 45,
            t("Trung cấp", "Intermediate"), t("Sức mạnh", "Strength"), R.drawable.home_workout_upper_body)),
        meals = listOf(HomeMeal("chicken-bowl", t("Cơm gà nướng rau củ", "Grilled Chicken Bowl"), 520, 42,
            t("Bữa trưa", "Lunch"), R.drawable.home_meal_chicken_bowl)),
        membership = HomeMembership(t("Hội viên Premium", "Premium Membership"), t("Đang hoạt động", "Active"),
            SimpleDateFormat("dd MMM yyyy", locale).format(expiry.time), 28),
        notifications = listOf(
            HomeNotification(t("Buổi tập gợi ý đã sẵn sàng", "Your next workout is ready"),
                t("Khám phá bài tập sức mạnh thân trên hôm nay.", "Discover today's Upper Body Strength recommendation.")),
            HomeNotification(t("Duy trì nhịp tập", "Keep your momentum"),
                t("Còn ${goal.remaining} buổi để đạt mục tiêu tuần mẫu.", "${goal.remaining} more workouts to reach your sample weekly goal.")),
            HomeNotification(t("Thông tin gói hội viên", "Membership reminder"),
                t("Gói Premium mẫu còn 28 ngày.", "Your sample Premium membership has 28 days remaining.")),
        ),
        activity = days,
        trainer = HomeTrainer("Trần Quốc Huy", t("Sức mạnh & kiểm soát cân nặng", "Strength & weight management")),
    )
}
