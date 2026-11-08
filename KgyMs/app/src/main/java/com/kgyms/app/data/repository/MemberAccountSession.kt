package com.kgyms.app.data.repository

import com.kgyms.app.data.model.MemberProfileEdit
import com.kgyms.app.data.model.User

internal interface MemberProfileDataSource {
    suspend fun load(uid: String): User
    suspend fun save(uid: String, edit: MemberProfileEdit)
    suspend fun saveAvatar(uid: String, bytes: ByteArray?): String
}

/** Each authenticated composition owns one session. Stale results cannot enter another account. */
internal class MemberAccountSession(
    val uid: String,
    private val source: MemberProfileDataSource,
    private val currentUid: () -> String?,
) {
    private var closed = false
    private fun checkCurrent() = check(!closed && uid.isNotBlank() && currentUid() == uid) { "Account changed" }
    fun close() { closed = true }
    suspend fun load(): User {
        checkCurrent()
        val user = source.load(uid)
        checkCurrent()
        check(user.uid == uid && user.isActive && !user.isAdmin) { "Member profile unavailable" }
        return user
    }
    suspend fun save(edit: MemberProfileEdit): User {
        edit.updates() // Validate before any network write.
        checkCurrent()
        source.save(uid, edit)
        checkCurrent()
        return load() // No optimistic success: acknowledge and read back persisted data.
    }
    suspend fun saveAvatar(bytes: ByteArray?): String {
        checkCurrent()
        val url = source.saveAvatar(uid, bytes)
        checkCurrent()
        return url
    }
}
