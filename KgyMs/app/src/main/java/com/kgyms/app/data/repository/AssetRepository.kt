package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.AssetService
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.Asset
import com.kgyms.app.data.model.AssetStatus
import kotlinx.coroutines.flow.Flow

/**
 * Nguồn dữ liệu tài sản/máy móc (node assets/{assetId} trên Firebase Realtime Database).
 * Mọi thao tác ghi đều được lưu vào nhật ký hoạt động.
 */
class AssetRepository(
    private val assetService: AssetService = AssetService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    fun observeAssets(): Flow<List<Asset>> = assetService.observeAssets()

    suspend fun addAsset(asset: Asset): String {
        val id = assetService.addAsset(asset)
        activityLogRepository.log(
            ActivityLogAction.ASSET,
            "thêm tài sản \"${asset.name}\" (SL ${asset.quantity})"
        )
        return id
    }

    suspend fun deleteAsset(id: String, name: String) {
        assetService.deleteAsset(id)
        activityLogRepository.log(ActivityLogAction.ASSET, "xóa tài sản \"$name\"")
    }

    /** Phiếu bảo trì: chuyển máy sang bảo trì/hỏng và ghi lý do vào mô tả. */
    suspend fun reportIssue(id: String, name: String, status: String, reason: String) {
        assetService.updateAssetFields(
            id,
            mapOf(
                "status" to status,
                "description" to reason
            )
        )

        val kind = if (status == AssetStatus.BROKEN) "báo hỏng" else "tạo phiếu bảo trì"
        activityLogRepository.log(ActivityLogAction.ASSET, "$kind \"$name\": $reason")
    }

    /** Đã sửa / đã bảo trì xong: máy hoạt động lại, ghi ngày bảo trì gần nhất. */
    suspend fun markResolved(id: String, name: String, previousStatus: String, maintenanceDate: String) {
        assetService.updateAssetFields(
            id,
            mapOf(
                "status" to AssetStatus.WORKING,
                "lastMaintenanceDate" to maintenanceDate
            )
        )

        val done = if (previousStatus == AssetStatus.BROKEN) "đã sửa xong" else "đã bảo trì xong"
        activityLogRepository.log(ActivityLogAction.ASSET, "xác nhận \"$name\" $done")
    }
}
