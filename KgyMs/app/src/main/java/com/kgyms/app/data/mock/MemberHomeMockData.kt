package com.kgyms.app.data.mock

import com.kgyms.app.data.mock.MockMemberWorkoutDataSource
import com.kgyms.app.utils.confirmedRecords
import com.kgyms.app.R
import com.kgyms.app.data.mock.MockMemberProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import com.kgyms.app.data.model.HomeActivityDay
import com.kgyms.app.data.model.HomeMeal
import com.kgyms.app.data.model.HomeMembership
import com.kgyms.app.data.model.HomeNotification
import com.kgyms.app.data.model.HomeTrainer
import com.kgyms.app.data.model.HomeWorkout
import com.kgyms.app.data.model.MemberHomeUiData
import com.kgyms.app.utils.calculateWeeklyGoal

/** All records and PT/membership details below are illustrative fixtures only. */
fun createMockMemberHome(
    language: String = "vi",
    weeklyTarget: Int = 6,
    nowMillis: Long = System.currentTimeMillis(),
): MemberHomeUiData {
    fun t(vi: String, en: String) = if (language == "vi") vi else en
    val zone = TimeZone.getDefault()
    val goal = calculateWeeklyGoal(weeklyTarget, MockMemberWorkoutDataSource.snapshot(nowMillis).confirmedRecords(), nowMillis, zone)
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
                t("Còn ${goal.remaining} buổi để đạt mục tiêu tuần.", "${goal.remaining} more workouts to reach your weekly goal.")),
            HomeNotification(t("Thông tin gói hội viên", "Membership reminder"),
                t("Gói Premium mẫu còn 28 ngày.", "Your sample Premium membership has 28 days remaining.")),
        ),
        activity = days,
        trainer = HomeTrainer("Trần Quốc Huy", t("Sức mạnh & kiểm soát cân nặng", "Strength & weight management")),
    )
}
