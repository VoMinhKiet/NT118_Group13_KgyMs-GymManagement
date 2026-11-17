package com.kgyms.app.data.firebase

/** No Firebase initialization or user-configurable destination. Safe to verify on the JVM. */
internal object PersonalPlanProductionPolicy {
    const val PROJECT = "kgyms-72c74"
    const val DATABASE = "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app"
    fun endpoint(uid: String): String {
        require(uid.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        return "$DATABASE/memberWorkoutPlans/$uid.json"
    }
    fun requireIdentity(audience: String, issuer: String, subject: String, owner: String) {
        require(audience == PROJECT && issuer == "https://securetoken.google.com/$PROJECT" && subject == owner)
        endpoint(owner)
    }
}
