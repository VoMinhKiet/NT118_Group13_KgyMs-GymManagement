package com.kgyms.app.viewmodel.membership

import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.Membership
import com.kgyms.app.data.model.MembershipStatus
import com.kgyms.app.data.model.PackageType
import com.kgyms.app.data.util.AppDates

/** Local interpretation only; never persisted over server status. End date is inclusive. */
enum class MembershipValidity { Active, NotStarted, PastEnd, InvalidDates, Pending, Frozen, Expired, Cancelled, Unknown }
enum class MembershipLoadError { Permission, Unavailable }

fun membershipValidity(membership: Membership, now: Long): MembershipValidity = when (membership.status) {
    MembershipStatus.PENDING -> MembershipValidity.Pending
    MembershipStatus.FROZEN -> MembershipValidity.Frozen
    MembershipStatus.EXPIRED -> MembershipValidity.Expired
    MembershipStatus.CANCELLED -> MembershipValidity.Cancelled
    MembershipStatus.ACTIVE -> {
        fun parse(value: String): Long? = if (value.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) AppDates.parseDay(value) else null
        val start = parse(membership.startDate)
        val end = parse(membership.endDate)
        val today = AppDates.startOfDay(now)
        when {
            start == null || end == null || start > end -> MembershipValidity.InvalidDates
            today > end -> MembershipValidity.PastEnd
            today < start -> MembershipValidity.NotStarted
            else -> MembershipValidity.Active
        }
    }
    else -> MembershipValidity.Unknown
}

data class OwnedMembershipUi(
    val membership: Membership,
    val pkg: GymPackage?,
    val validity: MembershipValidity,
    val trainerName: String?,
) {
    val totalSessions: Int? get() = pkg?.takeIf { it.type == PackageType.PT && it.sessions > 0 }?.sessions
    val usedSessions: Int? get() = membership.sessionsUsed.takeIf { membership.sessionsUsedAvailable && it >= 0 }
    val remainingSessions: Int? get() = totalSessions?.let { total -> usedSessions?.let { (total - it).coerceAtLeast(0) } }
}

data class MembershipUiState(
    val uid: String? = null,
    val packages: List<GymPackage> = emptyList(),
    val memberships: List<Membership> = emptyList(),
    val trainerNames: Map<String, String> = emptyMap(),
    val packagesLoading: Boolean = false,
    val membershipsLoading: Boolean = false,
    val packagesError: MembershipLoadError? = null,
    val membershipsError: MembershipLoadError? = null,
    val trainersUnavailable: Boolean = false,
    val now: Long = System.currentTimeMillis(),
) {
    val catalog: List<GymPackage> get() = packages.filter { it.isActive && it.type in PackageType.all }.sortedBy { it.name.lowercase() }
    val owned: List<OwnedMembershipUi> get() = if (uid == null) emptyList() else memberships
        .filter { it.uid == uid }
        .map { membership ->
            val pkg = packages.firstOrNull { it.id == membership.packageId }
            // Historical trainer assignment belongs to the membership, not today's catalog.
            OwnedMembershipUi(membership, pkg, membershipValidity(membership, now), trainerNames[membership.trainerUid])
        }.sortedWith(compareBy({ it.validity.ordinal }, { it.pkg?.name.orEmpty() }, { it.membership.id }))
}
