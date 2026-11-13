package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.MemberService
import com.kgyms.app.data.firebase.PackageService
import com.kgyms.app.data.firebase.PaymentService
import com.kgyms.app.data.firebase.StaffService
import com.kgyms.app.data.model.ActivityLogAction
import com.kgyms.app.data.model.MemberDirectory
import com.kgyms.app.data.model.MemberProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/**
 * Hội viên cho admin: ghép users + memberships + payments + checkIns + packages + staff.
 * Node phụ đọc lỗi thì coi như rỗng; chỉ users lỗi mới làm cả màn lỗi.
 */
class MemberRepository(
    private val memberService: MemberService = MemberService(),
    private val paymentService: PaymentService = PaymentService(),
    private val packageService: PackageService = PackageService(),
    private val staffService: StaffService = StaffService(),
    private val activityLogRepository: ActivityLogRepository = ActivityLogRepository()
) {
    fun observeDirectory(): Flow<MemberDirectory> {
        val memberships = paymentService.observeAllMemberships().orEmpty()
        val payments = paymentService.observeAllPayments().orEmpty()
        val checkIns = memberService.observeCheckIns().orEmpty()
        val packages = packageService.observePackages().orEmpty()
        val staff = staffService.observeStaff().orEmpty()

        return combine(
            memberService.observeMembers(),
            memberships,
            payments,
            combine(checkIns, packages, staff) { c, p, s -> Triple(c, p, s) }
        ) { members, ms, ps, (cs, pkgs, st) ->
            MemberDirectory(
                members = members,
                memberships = ms,
                payments = ps,
                checkIns = cs,
                packages = pkgs.associateBy { it.id },
                staffNames = st.associate { it.uid to it.fullName }
            )
        }
    }

    suspend fun assignMemberCode(member: MemberProfile): String {
        val code = memberService.assignMemberCode(member.uid)
        activityLogRepository.log(ActivityLogAction.ACCOUNT, "cấp mã hội viên $code cho ${member.displayName}")
        return code
    }

    suspend fun setLocked(member: MemberProfile, locked: Boolean) {
        memberService.setStatus(member.uid, if (locked) "locked" else "active")
        activityLogRepository.log(
            ActivityLogAction.ACCOUNT,
            if (locked) "khoá tài khoản ${member.displayName}" else "mở khoá tài khoản ${member.displayName}"
        )
    }

    private fun <T> Flow<List<T>>.orEmpty(): Flow<List<T>> = onStart { emit(emptyList()) }.catch { emit(emptyList()) }
}
