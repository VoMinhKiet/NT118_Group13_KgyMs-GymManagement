package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.AssetService
import com.kgyms.app.data.model.Asset
import com.kgyms.app.data.model.AssetStatus
import kotlinx.coroutines.flow.Flow

/**
 * Nguồn dữ liệu tài sản/máy móc (node assets/{assetId} trên Firebase Realtime Database).
 */
class AssetRepository(
    private val assetService: AssetService = AssetService()
) {
    fun observeAssets(): Flow<List<Asset>> = assetService.observeAssets()

    suspend fun addAsset(asset: Asset): String = assetService.addAsset(asset)

    suspend fun deleteAsset(id: String) = assetService.deleteAsset(id)

    /** Phiếu bảo trì: chuyển máy sang bảo trì/hỏng và ghi lý do vào mô tả. */
    suspend fun reportIssue(id: String, status: String, reason: String) =
        assetService.updateAssetFields(
            id,
            mapOf(
                "status" to status,
                "description" to reason
            )
        )

    /** Đã sửa / đã bảo trì xong: máy hoạt động lại, ghi ngày bảo trì gần nhất. */
    suspend fun markResolved(id: String, maintenanceDate: String) =
        assetService.updateAssetFields(
            id,
            mapOf(
                "status" to AssetStatus.WORKING,
                "lastMaintenanceDate" to maintenanceDate
            )
        )
}
