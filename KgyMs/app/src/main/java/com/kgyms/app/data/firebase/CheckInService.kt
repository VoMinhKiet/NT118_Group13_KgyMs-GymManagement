package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.kgyms.app.data.model.CheckIn
import kotlinx.coroutines.tasks.await

/**
 * Đọc/ghi node checkIns và các thông tin cần cho việc điểm danh.
 * Rules: hội viên đọc lượt của mình bằng orderByChild("uid").equalTo(uid), tạo lượt mới
 * (checkOutAt = 0) và ghi giờ ra cho lượt của mình (không đổi checkInAt).
 */
class CheckInService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val checkIns = database.getReference("checkIns")

    /** gymInfo/checkInSecret, rỗng nếu chưa cấu hình. */
    suspend fun getCheckInSecret(): String =
        database.getReference("gymInfo").child("checkInSecret").get().await().value?.toString().orEmpty()

    /** true nếu tài khoản là admin hoặc PT/nhân viên (isStaff). */
    suspend fun isStaffOrAdmin(uid: String): Boolean {
        val user = database.getReference("users").child(uid).get().await()
        return user.child("role").value == "admin" || user.child("isStaff").value == true
    }

    suspend fun getUserCheckIns(uid: String): List<CheckIn> =
        checkIns.orderByChild("uid").equalTo(uid).get().await().children.mapNotNull { child ->
            val key = child.key ?: return@mapNotNull null
            if (key.startsWith("_")) null else child.toCheckIn(key)
        }

    fun newCheckInId(): String = requireNotNull(checkIns.push().key) { "Không tạo được mã điểm danh." }

    /** Ghi nhiều đường dẫn trong 1 lần, vd "checkIns/{id}/checkOutAt". */
    suspend fun updatePaths(updates: Map<String, Any?>) {
        database.reference.updateChildren(updates).await()
    }

    private fun DataSnapshot.toCheckIn(id: String) = CheckIn(
        id = id,
        uid = child("uid").value?.toString().orEmpty(),
        checkInAt = (child("checkInAt").value as? Number)?.toLong() ?: 0L,
        checkOutAt = (child("checkOutAt").value as? Number)?.toLong() ?: 0L
    )
}
