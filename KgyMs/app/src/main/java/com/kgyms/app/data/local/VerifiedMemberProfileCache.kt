package com.kgyms.app.data.local

import android.content.Context
import com.kgyms.app.data.model.User
import com.kgyms.app.data.model.memberAccountKey
import com.kgyms.app.data.repository.VerifiedMemberCache
import org.json.JSONObject

/** Stores only a previously server-verified, fully onboarded Member. It never stores Admin access. */
internal class VerifiedMemberProfileCache(context: Context) : VerifiedMemberCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        "kgyms_verified_member_profiles_v1",
        Context.MODE_PRIVATE,
    )

    override fun read(uid: String): User? {
        if (uid.isBlank()) return null
        val raw = preferences.getString(key(uid), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            require(json.getInt("schemaVersion") == 1)
            require(json.getString("verificationSource") == "server_member_profile")
            require(json.getLong("verifiedAt") > 0L)
            require(json.getString("uid") == uid)
            User(
                uid = uid,
                fullName = json.optString("fullName"),
                email = json.optString("email"),
                phone = json.optString("phone"),
                birthday = json.optString("birthday"),
                gender = json.optString("gender"),
                age = json.optInt("age"),
                weight = json.optInt("weight"),
                weightUnit = json.optString("weightUnit", "KG"),
                height = json.optInt("height"),
                role = "user",
                status = "active",
                // Remote avatar content is not required for safe offline entry.
                avatarUrl = "",
                createdAt = json.optString("createdAt"),
            ).also { require(it.isActive && !it.isAdmin && it.hasCompletedOnboarding) }
        }.getOrNull()
    }

    override fun save(user: User) {
        require(user.uid.isNotBlank() && user.isActive && !user.isAdmin && user.hasCompletedOnboarding)
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("verificationSource", "server_member_profile")
            .put("verifiedAt", System.currentTimeMillis())
            .put("uid", user.uid)
            .put("fullName", user.fullName)
            .put("email", user.email)
            .put("phone", user.phone)
            .put("birthday", user.birthday)
            .put("gender", user.gender)
            .put("age", user.age)
            .put("weight", user.weight)
            .put("weightUnit", user.weightUnit)
            .put("height", user.height)
            .put("createdAt", user.createdAt)
        check(preferences.edit().putString(key(user.uid), json.toString()).commit()) {
            "Could not persist verified member profile"
        }
    }

    override fun invalidate(uid: String) {
        if (uid.isNotBlank()) preferences.edit().remove(key(uid)).commit()
    }

    private fun key(uid: String) = "member_" + memberAccountKey(uid)
}
