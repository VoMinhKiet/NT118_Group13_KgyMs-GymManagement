package com.kgyms.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.kgyms.app.data.firebase.FirestoreService
import com.kgyms.app.data.model.MemberProfileEdit
import com.kgyms.app.data.model.User
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Uses existing users/{uid}/avatarUrl and Firebase dependencies; no schema/rules migration. */
internal class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirestoreService = FirestoreService(),
    private val storageProvider: () -> FirebaseStorage = { FirebaseStorage.getInstance() },
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
    }
    override suspend fun saveAvatar(uid: String, bytes: ByteArray?): String {
        requireOwner(uid)
        val url = if (bytes == null) "" else {
            require(bytes.size <= 1024 * 1024) { "Avatar too large" }
            // Versioned objects prevent a failed DB write from changing the old avatar URL's content.
            val reference = storageProvider().reference.child("users/$uid/avatars/${UUID.randomUUID()}.jpg")
            reference.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
            requireOwner(uid)
            reference.downloadUrl.await().toString()
        }
        requireOwner(uid)
        database.updateMemberAvatar(uid, url)
        requireOwner(uid)
        return url
    }
}
