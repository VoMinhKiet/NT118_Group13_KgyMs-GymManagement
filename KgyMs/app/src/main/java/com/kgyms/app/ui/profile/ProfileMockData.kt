package com.kgyms.app.ui.profile


data class ProfileUiData(
    val name: String,
    val email: String,
    val initials: String,
    val membership: String,
    val workouts: String,
    val daysActive: String,
    val averageRating: String,
)

val MockMemberProfile = ProfileUiData(
    name = "Nguyễn Minh Anh",
    email = "minhanh@example.com",
    initials = "NM",
    membership = "Premium Member",
    workouts = "12",
    daysActive = "68",
    averageRating = "4.7",
)


enum class ProfileAction {
    Membership,
    EditProfile,
    FitnessGoals,
    NotificationSettings,
    Settings,
    HelpAndSupport,
    Logout,
}
