package com.kgyms.app.data.repository

import com.kgyms.app.data.firebase.MemberService
import com.kgyms.app.data.firebase.PackageService
import com.kgyms.app.data.firebase.PaymentService
import com.kgyms.app.data.model.CheckIn
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.Membership
import com.kgyms.app.data.model.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/** Dữ liệu gốc cho báo cáo Doanh thu. */
data class RevenueData(
    val payments: List<Payment> = emptyList(),
    val memberships: Map<String, Membership> = emptyMap(),
    val packages: Map<String, GymPackage> = emptyMap()
)

/** Dữ liệu gốc cho báo cáo Lưu lượng ra vào. */
data class TrafficData(
    val checkIns: List<CheckIn> = emptyList(),
    /** uid → tên hiển thị. */
    val names: Map<String, String> = emptyMap()
)

/**
 * Báo cáo cho admin, đọc realtime từ Firebase:
 * - Doanh thu: payments (+ memberships, packages để biết hoá đơn thuộc loại gói nào).
 * - Lưu lượng: checkIns (+ users để hiện tên người đang ở gym).
 */
class ReportRepository(
    private val paymentService: PaymentService = PaymentService(),
    private val packageService: PackageService = PackageService(),
    private val memberService: MemberService = MemberService()
) {
    fun observeRevenue(): Flow<RevenueData> = combine(
        paymentService.observeAllPayments(),
        paymentService.observeAllMemberships().orEmpty(),
        packageService.observePackages().orEmpty()
    ) { payments, memberships, packages ->
        RevenueData(
            payments = payments,
            memberships = memberships.associateBy { it.id },
            packages = packages.associateBy { it.id }
        )
    }

    fun observeTraffic(): Flow<TrafficData> = combine(
        memberService.observeCheckIns(),
        memberService.observeMembers().orEmpty()
    ) { checkIns, members ->
        TrafficData(checkIns = checkIns, names = members.associate { it.uid to it.displayName })
    }

    private fun <T> Flow<List<T>>.orEmpty(): Flow<List<T>> = onStart { emit(emptyList()) }.catch { emit(emptyList()) }
}
