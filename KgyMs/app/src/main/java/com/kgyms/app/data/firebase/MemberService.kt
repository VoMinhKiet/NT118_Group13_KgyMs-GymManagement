package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.CheckIn
import com.kgyms.app.data.model.MemberProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Dữ liệu hội viên cho admin: users, checkIns, cấp mã hội viên (counters/memberCode), khoá tài khoản.
 * Rules: chỉ admin đọc toàn bộ users/checkIns và ghi counters.
 */
class MemberService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val users = database.getReference("users")

    /** Mọi user không phải admin. */
    fun observeMembers(): Flow<List<MemberProfile>> = users.observe { snapshot ->
        snapshot.children.mapNotNull { user ->
            val uid = user.key ?: return@mapNotNull null
            if (uid.startsWith("_") || user.text("role") == "admin") return@mapNotNull null
            MemberProfile(
                uid = uid,
                fullName = user.text("fullName"),
                email = user.text("email"),
                phone = user.text("phone"),
                gender = user.text("gender"),
                age = (user.child("age").value as? Number)?.toInt() ?: 0,
                birthday = user.text("birthday"),
                status = user.text("status").ifBlank { "active" },
                createdAt = user.text("createdAt"),
                memberCode = user.text("memberCode"),
                isStaff = user.child("isStaff").value == true
            )
        }
    }

    fun observeCheckIns(): Flow<List<CheckIn>> = database.getReference("checkIns").observe { snapshot ->
        snapshot.children.mapNotNull { c ->
            val id = c.key ?: return@mapNotNull null
            if (id.startsWith("_")) return@mapNotNull null
            CheckIn(
                id = id,
                uid = c.text("uid"),
                checkInAt = (c.child("checkInAt").value as? Number)?.toLong() ?: 0L,
                checkOutAt = (c.child("checkOutAt").value as? Number)?.toLong() ?: 0L
            )
        }
    }

    /**
     * Tăng counters/memberCode bằng transaction (2 admin bấm cùng lúc cũng không trùng số)
     * rồi ghi "KM000123" vào users/{uid}/memberCode. Trả về mã đã cấp.
     */
    suspend fun assignMemberCode(uid: String): String {
        require(uid.isNotBlank()) { "Thiếu người dùng." }
        val number = incrementCounter()
        val code = "KM" + number.toString().padStart(6, '0')
        users.child(uid).child("memberCode").setValue(code).await()
        return code
    }

    suspend fun setStatus(uid: String, status: String) {
        require(uid.isNotBlank()) { "Thiếu người dùng." }
        users.child(uid).child("status").setValue(status).await()
    }

    private suspend fun incrementCounter(): Long = suspendCancellableCoroutine { cont ->
        database.getReference("counters").child("memberCode").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = (currentData.value as? Number)?.toLong() ?: 0L
                currentData.value = current + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                when {
                    error != null -> cont.resumeWithException(error.toException())
                    !committed -> cont.resumeWithException(IllegalStateException("Không cấp được mã, vui lòng thử lại."))
                    else -> cont.resume((snapshot?.value as? Number)?.toLong() ?: 0L)
                }
            }
        })
    }

    private fun DataSnapshot.text(name: String) = child(name).value?.toString().orEmpty()

    private fun <T> Query.observe(map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
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
