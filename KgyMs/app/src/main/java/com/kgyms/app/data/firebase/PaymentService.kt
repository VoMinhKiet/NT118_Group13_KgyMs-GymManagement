package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.Membership
import com.kgyms.app.data.model.MembershipStatus
import com.kgyms.app.data.model.Payment
import com.kgyms.app.data.model.PaymentMethod
import com.kgyms.app.data.model.PaymentStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Đọc/ghi node payments, memberships (và đọc tên hội viên cho admin).
 *
 * Rules:
 * - Hội viên đọc của mình bằng orderByChild("uid").equalTo(uid); tạo membership + hoá đơn `pending`
 *   của chính mình; huỷ (`cancelled`) hoá đơn/membership đang `pending` của mình.
 * - Admin toàn quyền.
 */
class PaymentService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val root = database.reference
    private val payments = database.getReference("payments")
    private val memberships = database.getReference("memberships")

    /* ============================ HỘI VIÊN ============================ */

    fun observeUserPayments(uid: String): Flow<List<Payment>> =
        payments.orderByChild("uid").equalTo(uid).observe { it.toPayments() }

    fun observeUserMemberships(uid: String): Flow<List<Membership>> =
        memberships.orderByChild("uid").equalTo(uid).observe { it.toMemberships() }

    suspend fun getUserPayments(uid: String): List<Payment> =
        payments.orderByChild("uid").equalTo(uid).get().await().toPayments()

    suspend fun getUserMemberships(uid: String): List<Membership> =
        memberships.orderByChild("uid").equalTo(uid).get().await().toMemberships()

    /* ============================ ADMIN ============================ */

    fun observeAllPayments(): Flow<List<Payment>> = payments.observe { it.toPayments() }

    fun observeAllMemberships(): Flow<List<Membership>> = memberships.observe { it.toMemberships() }

    /** uid → họ tên (chỉ admin đọc được node users). */
    suspend fun getUserNames(): Map<String, String> {
        val snapshot = database.getReference("users").get().await()
        return snapshot.children.mapNotNull { user ->
            val uid = user.key ?: return@mapNotNull null
            if (uid.startsWith("_")) return@mapNotNull null
            val name = user.child("fullName").value?.toString().orEmpty()
                .ifBlank { user.child("email").value?.toString().orEmpty().substringBefore("@") }
                .ifBlank { uid }
            uid to name
        }.toMap()
    }

    suspend fun getPayment(id: String): Payment? {
        val snapshot = payments.child(id).get().await()
        return if (snapshot.exists()) snapshot.toPayment(id) else null
    }

    suspend fun getMembership(id: String): Membership? {
        val snapshot = memberships.child(id).get().await()
        return if (snapshot.exists()) snapshot.toMembership(id) else null
    }

    /* ============================ GHI ============================ */

    fun newMembershipId(): String = requireNotNull(memberships.push().key) { "Không tạo được mã gói." }

    fun newNotificationId(uid: String): String =
        requireNotNull(root.child("notifications").child(uid).push().key) { "Không tạo được mã thông báo." }

    /** Ghi nhiều node trong 1 lần (thành công hết hoặc không ghi gì). */
    suspend fun updatePaths(updates: Map<String, Any?>) {
        root.updateChildren(updates).await()
    }

    /* ============================ MAPPING ============================ */

    private fun DataSnapshot.toPayments() = children.mapNotNull { child ->
        val key = child.key ?: return@mapNotNull null
        if (key.startsWith("_")) null else child.toPayment(key)
    }

    private fun DataSnapshot.toMemberships() = children.mapNotNull { child ->
        val key = child.key ?: return@mapNotNull null
        if (key.startsWith("_")) null else child.toMembership(key)
    }

    private fun DataSnapshot.text(name: String) = child(name).value?.toString().orEmpty()

    private fun DataSnapshot.long(name: String) = (child(name).value as? Number)?.toLong() ?: 0L

    private fun DataSnapshot.toPayment(id: String) = Payment(
        id = id,
        uid = text("uid"),
        membershipId = text("membershipId"),
        amount = long("amount"),
        discount = long("discount"),
        method = text("method").ifBlank { PaymentMethod.TRANSFER },
        status = text("status").ifBlank { PaymentStatus.PENDING },
        createdAt = text("createdAt"),
        paidAt = text("paidAt")
    )

    private fun DataSnapshot.toMembership(id: String) = Membership(
        id = id,
        uid = text("uid"),
        packageId = text("packageId"),
        trainerUid = text("trainerUid"),
        status = text("status").ifBlank { MembershipStatus.PENDING },
        startDate = text("startDate"),
        endDate = text("endDate"),
        sessionsUsed = long("sessionsUsed").toInt(),
        createdAt = text("createdAt")
    )

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
