package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.PackageType
import com.kgyms.app.data.model.TrainerOption
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Đọc/ghi node packages/{packageId}. Rules: mọi user đăng nhập được đọc, chỉ admin được ghi.
 * Đọc thêm memberships để biết gói nào đang có hội viên dùng.
 */
class PackageService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val packagesReference = database.getReference("packages")

    fun observePackages(): Flow<List<GymPackage>> = packagesReference.observe { snapshot ->
        snapshot.children.mapNotNull { child ->
            val key = child.key ?: return@mapNotNull null
            if (key.startsWith("_")) null else child.toPackage(key)
        }
    }

    /** packageId → số memberships (mọi trạng thái) đang tham chiếu tới gói. */
    fun observeUsage(): Flow<Map<String, Int>> = database.getReference("memberships").observe { snapshot ->
        snapshot.children
            .mapNotNull { it.child("packageId").value?.toString() }
            .groupingBy { it }
            .eachCount()
    }

    suspend fun getTrainers(): List<TrainerOption> {
        val snapshot = database.getReference("users").get().await()
        return snapshot.children.mapNotNull { user ->
            val uid = user.key ?: return@mapNotNull null
            if (user.child("isStaff").value != true) return@mapNotNull null
            TrainerOption(uid, user.child("fullName").value?.toString().orEmpty().ifBlank { uid })
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun addPackage(pkg: GymPackage): String {
        val node = packagesReference.push()
        val id = requireNotNull(node.key) { "Không tạo được mã gói." }
        node.setValue(pkg.toMap()).await()
        return id
    }

    suspend fun updatePackage(pkg: GymPackage) {
        require(pkg.id.isNotBlank()) { "Mã gói không hợp lệ." }
        packagesReference.child(pkg.id).setValue(pkg.toMap()).await()
    }

    suspend fun setActive(id: String, isActive: Boolean) {
        require(id.isNotBlank()) { "Mã gói không hợp lệ." }
        packagesReference.child(id).child("isActive").setValue(isActive).await()
    }

    suspend fun deletePackage(id: String) {
        require(id.isNotBlank()) { "Mã gói không hợp lệ." }
        packagesReference.child(id).removeValue().await()
    }

    private fun GymPackage.toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "type" to type,
        "price" to price,
        "durationDays" to durationDays,
        "sessions" to if (type == PackageType.PT) sessions else 0,
        "trainerUid" to if (type == PackageType.PT) trainerUid else "",
        "isActive" to isActive,
        "benefits" to benefits
    )

    private fun DataSnapshot.toPackage(id: String) = GymPackage(
        id = id,
        name = child("name").value?.toString().orEmpty(),
        description = child("description").value?.toString().orEmpty(),
        type = child("type").value?.toString().orEmpty().ifBlank { PackageType.MEMBERSHIP },
        price = (child("price").value as? Number)?.toLong() ?: 0L,
        durationDays = (child("durationDays").value as? Number)?.toInt() ?: 0,
        sessions = (child("sessions").value as? Number)?.toInt() ?: 0,
        trainerUid = child("trainerUid").value?.toString().orEmpty(),
        isActive = child("isActive").value as? Boolean ?: true,
        benefits = child("benefits").children.mapNotNull { it.value?.toString() }.filter { it.isNotBlank() }
    )

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
