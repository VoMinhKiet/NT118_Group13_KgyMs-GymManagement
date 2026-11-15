
package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface ServerUserRead {
    data class Found(val user: User) : ServerUserRead
    data object Missing : ServerUserRead
    data object Offline : ServerUserRead
    data object Denied : ServerUserRead
    data class Failed(val cause: Throwable) : ServerUserRead
}

internal fun interface UserObservation {
    fun close()
}

class FirestoreService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val usersReference = database.getReference("users")

    suspend fun saveUser(uid: String, user: User) {
        require(uid.isNotBlank()) {
            "UID không hợp lệ."
        }

        val userData = mapOf(
            "fullName" to user.fullName,
            "email" to user.email,
            "phone" to user.phone,
            "birthday" to user.birthday,
            "gender" to user.gender,
            "age" to user.age,
            "weight" to user.weight,
            "weightUnit" to user.weightUnit,
            "height" to user.height,
            "role" to user.role,
            "status" to user.status,
            "avatarUrl" to user.avatarUrl,
            "createdAt" to user.createdAt
        )

        usersReference.child(uid).setValue(userData).await()
    }

    suspend fun getUser(uid: String): User? {
        if (uid.isBlank()) return null

        val snapshot = usersReference.child(uid).get().await()

        if (!snapshot.exists()) return null

        return snapshot.toUser(uid)
    }

    /**
     * Reads account authority only after the SDK reports a live server connection. This keeps a
     * cached RTDB snapshot from being mistaken for a fresh role/status decision during cold start.
     */
    internal suspend fun getAuthoritativeUser(
        uid: String,
        connectionTimeoutMillis: Long = 5_000,
        readTimeoutMillis: Long = 8_000,
    ): ServerUserRead {
        if (uid.isBlank()) return ServerUserRead.Missing
        if (!awaitConnection(connectionTimeoutMillis)) return ServerUserRead.Offline

        val result = CompletableDeferred<ServerUserRead>()
        val reference = usersReference.child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                result.complete(
                    if (snapshot.exists() && snapshot.hasAuthoritativeAccessFields())
                        ServerUserRead.Found(snapshot.toUser(uid))
                    else ServerUserRead.Missing
                )
            }

            override fun onCancelled(error: DatabaseError) {
                result.complete(error.asServerUserRead())
            }
        }
        reference.addListenerForSingleValueEvent(listener)
        return withTimeoutOrNull(readTimeoutMillis) { result.await() } ?: ServerUserRead.Offline
    }

    /** A wake-up listener only. Every authorization decision still performs an authoritative read. */
    internal fun observeUserRefresh(
        uid: String,
        onConnectivity: (Boolean) -> Unit,
        onRefresh: () -> Unit,
        onDenied: () -> Unit,
    ): UserObservation {
        require(uid.isNotBlank())
        val connectionReference = database.getReference(".info/connected")
        val userReference = usersReference.child(uid)
        var connected = false
        val connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val next = snapshot.getValue(Boolean::class.java) == true
                val changed = next != connected
                connected = next
                if (changed) onConnectivity(next)
                if (next && changed) onRefresh()
            }

            override fun onCancelled(error: DatabaseError) {
                connected = false
                onConnectivity(false)
                if (error.code == DatabaseError.PERMISSION_DENIED) onDenied()
            }
        }
        val userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (connected) onRefresh()
            }

            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) onDenied()
                else onConnectivity(false)
            }
        }
        connectionReference.addValueEventListener(connectionListener)
        userReference.addValueEventListener(userListener)
        return UserObservation {
            connectionReference.removeEventListener(connectionListener)
            userReference.removeEventListener(userListener)
        }
    }

    private suspend fun awaitConnection(timeoutMillis: Long): Boolean {
        val reference = database.getReference(".info/connected")
        val connected = CompletableDeferred<Boolean>()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) connected.complete(true)
            }

            override fun onCancelled(error: DatabaseError) {
                connected.complete(false)
            }
        }
        reference.addValueEventListener(listener)
        return try {
            withTimeoutOrNull(timeoutMillis) { connected.await() } == true
        } finally {
            reference.removeEventListener(listener)
        }
    }

    private fun DatabaseError.asServerUserRead(): ServerUserRead = when (code) {
        DatabaseError.PERMISSION_DENIED -> ServerUserRead.Denied
        DatabaseError.DISCONNECTED, DatabaseError.NETWORK_ERROR, DatabaseError.UNAVAILABLE ->
            ServerUserRead.Offline
        else -> ServerUserRead.Failed(toException())
    }

    private fun DataSnapshot.hasAuthoritativeAccessFields(): Boolean =
        child("role").stringValue().isNotBlank() && child("status").stringValue().isNotBlank()

    suspend fun updateUser(uid: String, user: User) {
        require(uid.isNotBlank()) {
            "UID không hợp lệ."
        }

        // Không cho phép cập nhật role qua hàm cập nhật hồ sơ thông thường.
        val updates = mapOf(
            "fullName" to user.fullName,
            "email" to user.email,
            "phone" to user.phone,
            "birthday" to user.birthday,
            "gender" to user.gender,
            "status" to user.status,
            "avatarUrl" to user.avatarUrl
        )

        usersReference.child(uid).updateChildren(updates).await()
    }

    /** Lưu thông tin hồ sơ thu thập ở luồng onboarding (không đụng tới role/status). */
    suspend fun updateOnboarding(
        uid: String,
        gender: String,
        age: Int,
        weight: Int,
        weightUnit: String,
        height: Int
    ) {
        require(uid.isNotBlank()) {
            "UID không hợp lệ."
        }

        val updates = mapOf(
            "gender" to gender,
            "age" to age,
            "weight" to weight,
            "weightUnit" to weightUnit,
            "height" to height
        )

        usersReference.child(uid).updateChildren(updates).await()
    }

    /** Member whitelist: preserve onboarding, Auth identity, role/status and teammate fields. */
    suspend fun updateMemberProfile(uid: String, edit: com.kgyms.app.data.model.MemberProfileEdit) {
        require(uid.isNotBlank())
        usersReference.child(uid).updateChildren(edit.updates()).await()
    }

    suspend fun updateMemberAvatar(uid: String, url: String) {
        require(uid.isNotBlank())
        usersReference.child(uid).child("avatarUrl").setValue(url).await()
    }

    private fun DataSnapshot.toUser(uid: String): User {
        return User(
            uid = uid,
            fullName = child("fullName").stringValue(),
            email = child("email").stringValue(),
            phone = child("phone").stringValue(),
            birthday = child("birthday").stringValue(),
            gender = child("gender").stringValue(),
            age = child("age").intValue(),
            weight = child("weight").intValue(),
            weightUnit = child("weightUnit").stringValue().ifBlank { "KG" },
            height = child("height").intValue(),
            role = child("role").stringValue().ifBlank { "user" },
            status = child("status").stringValue().ifBlank { "active" },
            avatarUrl = child("avatarUrl").stringValue(),
            createdAt = child("createdAt").stringValue()
                .ifBlank { child("createAt").stringValue() }
        )
    }

    private fun DataSnapshot.stringValue(): String {
        return value?.toString().orEmpty()
    }

    private fun DataSnapshot.intValue(): Int {
        return when (val raw = value) {
            is Number -> raw.toInt()
            else -> raw?.toString()?.toDoubleOrNull()?.toInt() ?: 0
        }
    }
}
