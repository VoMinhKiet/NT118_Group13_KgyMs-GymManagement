package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.Reconciliation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Đọc node reconciliation/{sepayId}: giao dịch ngân hàng server không tự khớp được.
 * Rules: chỉ admin đọc; không ai ghi từ app (server ghi bằng Admin SDK, admin xử lý qua server).
 */
class ReconciliationService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    fun observeAll(): Flow<List<Reconciliation>> = callbackFlow {
        val ref = database.getReference("reconciliation")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    if (key.startsWith("_")) null else child.toReconciliation(key)
                })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DataSnapshot.text(name: String) = child(name).value?.toString().orEmpty()

    private fun DataSnapshot.long(name: String) = (child(name).value as? Number)?.toLong() ?: 0L

    private fun DataSnapshot.toReconciliation(id: String) = Reconciliation(
        id = id,
        reason = text("reason"),
        amount = long("amount"),
        content = text("content"),
        paymentId = text("paymentId"),
        expectedAmount = long("expectedAmount"),
        transactionDate = text("transactionDate"),
        createdAt = text("createdAt"),
        status = text("status").ifBlank { "open" },
        resolution = text("resolution"),
        resolvedBy = text("resolvedBy"),
        resolvedAt = text("resolvedAt"),
        note = text("note")
    )
}
