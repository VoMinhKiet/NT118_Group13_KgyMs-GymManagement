package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.AssetService
import com.kgyms.app.data.model.Asset
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
}
