package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.PackageService
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.TrainerOption
import kotlinx.coroutines.flow.Flow

/**
 * Gói tập (node packages). Mọi thao tác ghi đều được lưu vào nhật ký hoạt động.
 */
class PackageRepository(
    private val service: PackageService = PackageService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    fun observePackages(): Flow<List<GymPackage>> = service.observePackages()

    fun observeUsage(): Flow<Map<String, Int>> = service.observeUsage()

    suspend fun getTrainers(): List<TrainerOption> = service.getTrainers()

    suspend fun addPackage(pkg: GymPackage) {
        service.addPackage(pkg)
        activityLogRepository.log(ActivityLogAction.PACKAGE, "tạo gói \"${pkg.name}\"")
    }

    suspend fun updatePackage(pkg: GymPackage) {
        service.updatePackage(pkg)
        activityLogRepository.log(ActivityLogAction.PACKAGE, "cập nhật gói \"${pkg.name}\"")
    }

    suspend fun setActive(pkg: GymPackage, isActive: Boolean) {
        service.setActive(pkg.id, isActive)
        activityLogRepository.log(
            ActivityLogAction.PACKAGE,
            "${if (isActive) "mở bán lại" else "ngừng bán"} gói \"${pkg.name}\""
        )
    }

    suspend fun deletePackage(pkg: GymPackage) {
        service.deletePackage(pkg.id)
        activityLogRepository.log(ActivityLogAction.PACKAGE, "xóa gói \"${pkg.name}\"")
    }
}
