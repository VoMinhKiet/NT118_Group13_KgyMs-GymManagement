package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.StaffService
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.StaffCandidate
import com.kgyms.app.data.model.StaffMember
import com.kgyms.app.data.model.StaffStatus
import kotlinx.coroutines.flow.Flow

/** Nhân sự (staff + users.isStaff). Mọi thao tác ghi đều lưu vào nhật ký hoạt động. */
class StaffRepository(
    private val service: StaffService = StaffService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    fun observeStaff(): Flow<List<StaffMember>> = service.observeStaff()

    fun observeUsers(): Flow<List<StaffCandidate>> = service.observeUsers()

    fun observeAssignedPackages(): Flow<Map<String, List<String>>> = service.observeAssignedPackages()

    suspend fun addStaff(staff: StaffMember, positionLabel: String) {
        service.saveStaff(staff)
        activityLogRepository.log(ActivityLogAction.STAFF, "thêm ${staff.fullName} làm $positionLabel")
    }

    suspend fun updateStaff(staff: StaffMember) {
        service.saveStaff(staff)
        activityLogRepository.log(ActivityLogAction.STAFF, "cập nhật hồ sơ nhân sự ${staff.fullName}")
    }

    suspend fun setStatus(staff: StaffMember, status: String) {
        service.setStatus(staff, status)
        activityLogRepository.log(
            ActivityLogAction.STAFF,
            if (status == StaffStatus.ACTIVE) "cho ${staff.fullName} làm việc lại" else "cho ${staff.fullName} nghỉ việc"
        )
    }
}
