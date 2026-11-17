package com.kgyms.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.kgyms.app.data.firebase.FirestoreService
import com.kgyms.app.data.model.MemberProfileEdit
import com.kgyms.app.data.model.User

/** Profile fields only. Legacy avatar URLs are preserved but this repository never contacts Storage. */
internal class MemberProfileRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirestoreService = FirestoreService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository(),
) : MemberProfileDataSource {
    private fun requireOwner(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid) { "Account changed" }
    }
    override suspend fun load(uid: String): User {
        requireOwner(uid)
        val profile = database.getUser(uid) ?: error("Missing user profile")
        requireOwner(uid)
        // Email belongs to Firebase Auth, and is intentionally read-only in the editor.
        return profile.copy(uid = uid, email = auth.currentUser?.email.orEmpty())
    }
    override suspend fun save(uid: String, edit: MemberProfileEdit) {
        requireOwner(uid)
        database.updateMemberProfile(uid, edit)
        requireOwner(uid)
        activityLogRepository.log(com.kgyms.app.data.model.ActivityLogAction.ACCOUNT, "cập nhật thông tin cá nhân")
        requireOwner(uid)
    }
}
