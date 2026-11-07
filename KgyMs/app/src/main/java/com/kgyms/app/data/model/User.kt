
package com.kgyms.app.data.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val birthday: String = "",
    val gender: String = "",
    val role: String = "user",
    val status: String = "active",
    val avatarUrl: String = "",
    val createdAt: String = ""
) {
    val isAdmin: Boolean
        get() = role.equals("admin", ignoreCase = true)

    val isActive: Boolean
        get() = status.equals("active", ignoreCase = true)
}
