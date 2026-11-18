
package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
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

internal data class OnboardingMeasurements(val weight: Int, val unit: String, val height: Int)

/** Keep values already supplied by the member, including values written by a concurrent session. */
internal fun missingOnboardingFields(
    current: Map<*, *>,
    gender: String,
    age: Int,
    measurements: OnboardingMeasurements?,
): Map<String, Any> = buildMap {
    if (current["gender"]?.toString().isNullOrBlank()) put("gender", gender)
    val previousAge = current["age"]
    if (previousAge == null || previousAge.toString().toDoubleOrNull() == 0.0) put("age", age)
    if (measurements != null) {
        if (current["height"] == null) put("height", measurements.height)
        if (current["weight"] == null) {
            val existingUnit = current["weightUnit"]?.toString()?.trim()?.uppercase(java.util.Locale.ROOT)
            when {
                existingUnit == null -> {
                    put("weight", measurements.weight)
                    put("weightUnit", measurements.unit)
                }
                existingUnit == measurements.unit -> put("weight", measurements.weight)
                existingUnit == "KG" -> put("weight", measurements.weight * 0.45359237)
                existingUnit == "LB" -> put("weight", measurements.weight / 0.45359237)
            }
        }
    }
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

    /** Cập nhật thông tin cá nhân từ màn hình Chỉnh sửa hồ sơ (không đụng tới role/status/email). */
    suspend fun updatePersonalInfo(
        uid: String,
        fullName: String,
        phone: String,
        birthday: String
    ) {
        require(uid.isNotBlank()) {
            "UID không hợp lệ."
        }

        val updates = mapOf(
            "fullName" to fullName,
            "phone" to phone,
            "birthday" to birthday
        )

        usersReference.child(uid).updateChildren(updates).await()
    }

    /** Đồng bộ email trong hồ sơ với email của Firebase Auth. */
    suspend fun updateEmail(uid: String, email: String) {
        require(uid.isNotBlank()) {
            "UID không hợp lệ."
        }

        usersReference.child(uid).child("email").setValue(email).await()
    }

    /** Record onboarding once without replacing profile fields that already have values. */
    internal suspend fun updateOnboarding(
        uid: String,
        gender: String,
        age: Int,
        measurements: OnboardingMeasurements? = null,
        isCurrentOwner: () -> Boolean,
    ) {
        require(uid.isNotBlank())
        check(isCurrentOwner()) { "Account changed" }
        val reference = usersReference.child(uid)
        check(reference.get().await().exists()) { "Missing user profile" }
        check(isCurrentOwner()) { "Account changed" }

        val completed = CompletableDeferred<Unit>()
        reference.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (!isCurrentOwner()) return Transaction.abort()
                // A cold cache may supply null. Leave it intact so the server can retry with its snapshot.
                val fields = currentData.value as? Map<*, *> ?: return Transaction.success(currentData)
                missingOnboardingFields(fields, gender, age, measurements).forEach { (field, value) ->
                    currentData.child(field).value = value
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                when {
                    error != null -> completed.completeExceptionally(error.toException())
                    !committed || snapshot?.exists() != true || !isCurrentOwner() ->
                        completed.completeExceptionally(IllegalStateException("Onboarding could not be saved for the current account"))
                    else -> completed.complete(Unit)
                }
            }
        }, false)
        completed.await()
    }

    /**
     * Cập nhật thông tin hồ sơ Member.
     */
    suspend fun updateMemberProfile(
        uid: String,
        edit: com.kgyms.app.data.model.MemberProfileEdit
    ) {
        require(uid.isNotBlank())

        usersReference.child(uid)
            .updateChildren(edit.updates())
            .await()
    }

    /**
     * Cập nhật ảnh đại diện Member.
     */
    suspend fun updateMemberAvatar(uid: String, url: String) {
        require(uid.isNotBlank())

        usersReference.child(uid)
            .child("avatarUrl")
            .setValue(url)
            .await()
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
            role = child("role").stringValue().ifBlank { "user" },
            status = child("status").stringValue().ifBlank { "active" },
            avatarUrl = child("avatarUrl").stringValue(),
            createdAt = child("createdAt").stringValue()
                .ifBlank { child("createAt").stringValue() },
            weight = child("weight").numberValue(),
            weightUnit = child("weightUnit").stringValue(),
            height = child("height").numberValue(),
        )
    }

    private fun DataSnapshot.stringValue(): String {
        return value?.toString().orEmpty()
    }

    private fun DataSnapshot.numberValue(): Double? =
        value?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

    private fun DataSnapshot.intValue(): Int {
        return when (val raw = value) {
            is Number -> raw.toInt()
            else -> raw?.toString()?.toDoubleOrNull()?.toInt() ?: 0
        }
    }
}
