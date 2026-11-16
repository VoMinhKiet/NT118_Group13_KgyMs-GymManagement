package com.kgyms.app.data.model

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

data class HomeNotification(
    val title: String,
    val message: String,
    /** Dữ liệu mẫu mặc định là chưa đọc để vẫn hiện chấm báo như trước. */
    val isRead: Boolean = false,
    /** "HH:mm dd/MM/yyyy"; rỗng với dữ liệu mẫu. */
    val time: String = "",
    /** Có giá trị khi lấy từ Firebase (notifications/{uid}/{id}). */
    val id: String = ""
)

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
