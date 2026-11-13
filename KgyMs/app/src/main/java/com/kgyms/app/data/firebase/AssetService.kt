package com.kgyms.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.model.Asset
import com.kgyms.app.data.model.AssetCategory
import com.kgyms.app.data.model.AssetStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Đọc/ghi node assets/{assetId} trên Firebase Realtime Database.
 * Rules chỉ cho admin đọc và ghi node này.
 */
class AssetService(
    database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
) {
    private val assetsReference = database.getReference("assets")

    /** Lắng nghe realtime toàn bộ danh sách máy, sắp xếp theo tên. */
    fun observeAssets(): Flow<List<Asset>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val assets = snapshot.children
                    .mapNotNull { child ->
                        val key = child.key ?: return@mapNotNull null
                        // Bỏ qua bản ghi giữ chỗ như "_sample".
                        if (key.startsWith("_")) null else child.toAsset(key)
                    }
                    .sortedBy { it.name.lowercase() }

                trySend(assets)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        assetsReference.addValueEventListener(listener)

        awaitClose {
            assetsReference.removeEventListener(listener)
        }
    }

    /** Thêm máy mới với key do Firebase sinh (push), trả về id. */
    suspend fun addAsset(asset: Asset): String {
        val node = assetsReference.push()
        val id = requireNotNull(node.key) { "Không tạo được mã máy." }

        node.setValue(asset.toMap()).await()

        return id
    }

    suspend fun deleteAsset(id: String) {
        require(id.isNotBlank()) {
            "Mã máy không hợp lệ."
        }

        assetsReference.child(id).removeValue().await()
    }

    private fun Asset.toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "category" to category,
        "brand" to brand,
        "quantity" to quantity,
        "status" to status,
        "location" to location,
        "purchaseDate" to purchaseDate,
        "lastMaintenanceDate" to lastMaintenanceDate,
        "nextMaintenanceDate" to nextMaintenanceDate,
        "warrantyMonths" to warrantyMonths,
        "imageUrl" to imageUrl,
        "description" to description
    )

    private fun DataSnapshot.toAsset(id: String): Asset {
        return Asset(
            id = id,
            name = child("name").stringValue(),
            category = child("category").stringValue().ifBlank { AssetCategory.OTHER },
            brand = child("brand").stringValue(),
            quantity = child("quantity").intValue(),
            status = child("status").stringValue().ifBlank { AssetStatus.WORKING },
            location = child("location").stringValue(),
            purchaseDate = child("purchaseDate").stringValue(),
            lastMaintenanceDate = child("lastMaintenanceDate").stringValue(),
            nextMaintenanceDate = child("nextMaintenanceDate").stringValue(),
            warrantyMonths = child("warrantyMonths").intValue(),
            imageUrl = child("imageUrl").stringValue(),
            description = child("description").stringValue()
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
