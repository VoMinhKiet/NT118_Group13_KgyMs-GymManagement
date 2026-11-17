package com.kgyms.app.data.model

data class ProfileUiData(
    val name: String,
    val email: String,
    val initials: String,
    val membership: String,
    val workouts: String,
    val daysActive: String,
    val averageRating: String,
    val phone: String = "",
    val birthday: String = "",
    val gender: String = "",
    val age: String = "",
    val weight: String = "",
    val height: String = "",
    val isRealAccount: Boolean = false,
)
