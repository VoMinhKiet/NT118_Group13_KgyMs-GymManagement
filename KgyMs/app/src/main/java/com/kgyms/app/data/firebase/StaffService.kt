package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.StaffCandidate
import com.kgyms.app.data.model.StaffMember
import com.kgyms.app.data.model.StaffPosition
import com.kgyms.app.data.model.StaffStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Nhân sự: staff/{uid} + cờ users/{uid}/isStaff (quyền PT) + gói PT được giao (packages.trainerUid).
 * Mọi thay đổi nhiều node đều ghi bằng 1 lần updateChildren để không bị ghi dở.
 */
class StaffService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val root = database.reference

    fun observeStaff(): Flow<List<StaffMember>> = root.child("staff").observe { snapshot ->
        snapshot.children.mapNotNull { child ->
            val uid = child.key ?: return@mapNotNull null
            if (uid.startsWith("_")) null else child.toStaff(uid)
        }
    }

    /** Mọi user không phải admin (admin dùng để chọn người tuyển và xem liên hệ). */
    fun observeUsers(): Flow<List<StaffCandidate>> = root.child("users").observe { snapshot ->
        snapshot.children.mapNotNull { user ->
            val uid = user.key ?: return@mapNotNull null
            if (uid.startsWith("_") || user.child("role").value == "admin") return@mapNotNull null
            StaffCandidate(
                uid = uid,
                fullName = user.text("fullName"),
                email = user.text("email"),
                phone = user.text("phone"),
                avatarUrl = user.text("avatarUrl")
            )
        }
    }

    /** trainerUid → tên các gói PT đang giao cho người đó. */
    fun observeAssignedPackages(): Flow<Map<String, List<String>>> = root.child("packages").observe { snapshot ->
        snapshot.children
            .filter { it.text("trainerUid").isNotBlank() }
            .groupBy({ it.text("trainerUid") }, { it.text("name") })
    }

    /** Tạo mới hoặc ghi đè hồ sơ, đồng thời đặt cờ isStaff và gỡ PT khỏi gói nếu không còn là PT đang làm. */
    suspend fun saveStaff(staff: StaffMember) {
        require(staff.uid.isNotBlank()) { "Thiếu người dùng." }
        val keepsTrainerRole = staff.isTrainer && staff.isActive
        val updates = mutableMapOf<String, Any?>(
            "staff/${staff.uid}" to staff.toMap(),
            "users/${staff.uid}/isStaff" to keepsTrainerRole
        )
        if (!keepsTrainerRole) updates += unassignPackages(staff.uid)
        root.updateChildren(updates).await()
    }

    /** Đổi trạng thái làm việc (nghỉ việc / làm lại). */
    suspend fun setStatus(staff: StaffMember, status: String) = saveStaff(staff.copy(status = status))

    private suspend fun unassignPackages(uid: String): Map<String, Any?> {
        val packages = root.child("packages").orderByChild("trainerUid").equalTo(uid).get().await()
        return packages.children.mapNotNull { it.key }.associate { "packages/$it/trainerUid" to "" }
    }

    private fun StaffMember.toMap(): Map<String, Any> = mapOf(
        "fullName" to fullName,
        "avatarUrl" to avatarUrl,
        "position" to position,
        "specialty" to specialty,
        "bio" to bio,
        "workShift" to workShift,
        "startDate" to startDate,
        "status" to status
    )

    private fun DataSnapshot.toStaff(uid: String) = StaffMember(
        uid = uid,
        fullName = text("fullName"),
        avatarUrl = text("avatarUrl"),
        // Bản ghi cũ chưa có position thì coi là PT (thiết kế ban đầu staff = PT).
        position = text("position").ifBlank { StaffPosition.PT },
        specialty = text("specialty"),
        bio = text("bio"),
        workShift = text("workShift"),
        startDate = text("startDate"),
        status = text("status").ifBlank { StaffStatus.ACTIVE }
    )

    private fun DataSnapshot.text(name: String) = child(name).value?.toString().orEmpty()

    private fun <T> DatabaseReference.observe(map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(map(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        addValueEventListener(listener)
        awaitClose { removeEventListener(listener) }
    }
}
