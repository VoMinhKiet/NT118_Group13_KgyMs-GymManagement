
package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.kgyms.app.data.model.User
import kotlinx.coroutines.tasks.await

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
